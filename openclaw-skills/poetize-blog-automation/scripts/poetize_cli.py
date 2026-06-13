#!/usr/bin/env python3
"""Unified CLI for Poetize blog automation.

Usage:
    python poetize_cli.py publish --markdown-file article.md --brief-file brief.json --publish --wait
    python poetize_cli.py manage list-articles --search-key "AI"
    python poetize_cli.py config --output openclaw.json --api-key KEY
    python poetize_cli.py smoke-test --base-url URL --api-key KEY
    python poetize_cli.py eval

Supports --stdin-brief and --stdin-payload to read JSON from stdin,
which avoids temporary files and CLI escaping issues for Agent runtimes.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Any

# Re-export helpers from existing modules
from blog_strategy import StrategyValidationError, load_json_object
from manage_blog import (
    add_article_target_args,
    build_url,
    extract_records,
    list_articles,
    post_async_update,
    read_json_file as manage_read_json_file,
    resolve_article_id,
)
from manage_blog import apply_ops_strategy
from publish_post import (
    build_payload,
    configure_stdio,
    die,
    extract_task_id,
    normalize_base_url,
    poll_task,
    request_json,
    upload_resource,
)
from publish_post import apply_article_strategy
from publish_post import ensure_payment_plugin_ready as publish_ensure_payment
from publish_post import ensure_taxonomy_ready
from render_openclaw_config import build_config, infer_base_url


# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

def _output_error(message: str, *, detail: str | None = None, code: int = 1) -> None:
    """Print a structured JSON error to stderr so Agent runtimes can read it without mixing with stdout data."""
    result: dict[str, Any] = {"ok": False, "error": message}
    if detail:
        result["detail"] = detail
    print(json.dumps(result, ensure_ascii=False, indent=2), file=sys.stderr)
    sys.exit(code)



# ---------------------------------------------------------------------------
# Stdin helpers
# ---------------------------------------------------------------------------

def _cli_die(message: str) -> None:
    """Raise SystemExit with _poetize_detail so cmd_* catchers can extract the message."""
    exc = SystemExit(1)
    exc._poetize_detail = message  # type: ignore[attr-defined]
    raise exc


def read_stdin_json(label: str) -> dict[str, Any]:
    """Read a JSON object from stdin."""
    if sys.stdin.isatty():
        _cli_die(f"{label}: stdin is a terminal. Pipe JSON or use --brief-file / --payload-file instead.")
    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        _cli_die(f"{label}: invalid JSON from stdin: {exc}")
    if not isinstance(data, dict):
        _cli_die(f"{label}: stdin JSON must be an object.")
    return data


def resolve_brief(args: argparse.Namespace) -> dict[str, Any]:
    """Load brief from --brief-file or --stdin-brief."""
    if getattr(args, "stdin_brief", False):
        return read_stdin_json("Brief")
    brief_file = getattr(args, "brief_file", None)
    if brief_file:
        return load_json_object(brief_file, label="Brief file")
    _cli_die("Provide --brief-file or --stdin-brief for this command.")


def resolve_payload(args: argparse.Namespace) -> dict[str, Any]:
    """Load payload from --payload-file or --stdin-payload."""
    if getattr(args, "stdin_payload", False):
        return read_stdin_json("Payload")
    payload_file = getattr(args, "payload_file", None)
    if payload_file:
        return manage_read_json_file(payload_file)
    _cli_die("Provide --payload-file or --stdin-payload for this command.")


# ---------------------------------------------------------------------------
# Global args
# ---------------------------------------------------------------------------

def add_global_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--base-url", default=os.getenv("POETIZE_BASE_URL"), help="Poetize base URL.")
    parser.add_argument("--api-key", default=os.getenv("POETIZE_API_KEY"), help="Poetize API key.")


# ---------------------------------------------------------------------------
# publish command
# ---------------------------------------------------------------------------

def add_publish_args(parser: argparse.ArgumentParser) -> None:
    add_global_args(parser)
    parser.add_argument("--markdown-file", required=True, help="Path to a Markdown file.")
    parser.add_argument("--article-id", type=int, help="Existing article ID to update.")
    parser.add_argument("--brief-file", help="JSON brief file for strategy validation.")
    parser.add_argument("--stdin-brief", action="store_true", help="Read brief JSON from stdin.")
    parser.add_argument("--publish", action="store_true", help="Force public publish.")
    parser.add_argument("--draft", action="store_true", help="Force draft/private save.")
    parser.add_argument("--cover-file", help="Optional local cover file path.")
    parser.add_argument("--payment-plugin-key", help="Payment plugin key for paid articles.")
    parser.add_argument("--payment-config-file", help="JSON file for payment plugin config.")
    parser.add_argument("--require-paid", action="store_true", help="Fail instead of downgrading paid.")
    parser.add_argument("--allow-create-taxonomy", action="store_true", help="Allow creating missing categories/tags.")
    parser.add_argument("--allow-create-sort", action="store_true", help="Allow creating a missing category.")
    parser.add_argument("--allow-create-label", action="store_true", help="Allow creating a missing tag.")
    parser.add_argument("--print-payload", action="store_true", help="Print JSON payload before sending.")
    parser.add_argument("--wait", action="store_true", help="Poll async task until completion.")
    parser.add_argument("--poll-interval", type=float, default=2.0, help="Seconds between poll requests (default: 2.0).")
    parser.add_argument("--timeout", type=int, default=900, help="Maximum wait time in seconds (default: 900).")


def cmd_publish(args: argparse.Namespace) -> None:
    args.base_url = normalize_base_url(str(args.base_url or ""))
    if not args.base_url:
        _output_error("Missing --base-url or POETIZE_BASE_URL.")
        return
    if not args.api_key:
        _output_error("Missing --api-key or POETIZE_API_KEY.")
        return

    try:
        brief = resolve_brief(args)

        with open(args.markdown_file, "r", encoding="utf-8") as handle:
            markdown_text = handle.read()

        payload, meta = build_payload(markdown_text, args)
        payload = apply_article_strategy(
            brief,
            payload,
            is_update=args.article_id is not None,
            cli_publish=args.publish,
            cli_draft=args.draft,
        )
        payload = ensure_taxonomy_ready(payload, meta, args)
        payload = publish_ensure_payment(payload, meta, args)

        if args.print_payload:
            print(json.dumps(payload, ensure_ascii=False, indent=2))
            return

        endpoint = "/api/api/article/updateAsync" if args.article_id is not None else "/api/api/article/createAsync"
        response = request_json("POST", f"{args.base_url.rstrip('/')}{endpoint}", args.api_key, payload)

        if response.get("code") != 200:
            _output_error("Article API returned non-200", detail=json.dumps(response, ensure_ascii=False))
            return

        if not args.wait:
            result = {"ok": True, **response}
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return

        task_id = extract_task_id(response)
        if not task_id:
            _output_error("Async article API did not return a taskId.", detail=json.dumps(response, ensure_ascii=False))
            return

        final_response = poll_task(args.base_url, args.api_key, task_id, args.poll_interval, args.timeout)
        final_data = final_response.get("data")
        if isinstance(final_data, dict) and final_data.get("status") == "failed":
            _output_error("Async task failed", detail=json.dumps(final_response, ensure_ascii=False))
            return

        result = {"ok": True, **final_response}
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except StrategyValidationError as exc:
        _output_error("Strategy validation failed", detail=exc.render())
    except SystemExit as exc:
        _output_error("Publish failed", detail=getattr(exc, "_poetize_detail", str(exc)))


# ---------------------------------------------------------------------------
# manage command
# ---------------------------------------------------------------------------

def add_manage_subparsers(parser: argparse.ArgumentParser) -> None:
    sub = parser.add_subparsers(dest="manage_command", required=True)

    # list-articles
    p = sub.add_parser("list-articles", help="List articles with filters.")
    add_global_args(p)
    p.add_argument("--current", type=int, default=1, help="Page number (default: 1).")
    p.add_argument("--size", type=int, default=10, help="Page size (default: 10).")
    p.add_argument("--search-key", help="Search keyword for article title.")
    p.add_argument("--sort-id", type=int, help="Filter by category ID.")
    p.add_argument("--sort-name", help="Filter by category name (resolved to ID).")
    p.add_argument("--label-id", type=int, help="Filter by tag ID.")
    p.add_argument("--label-name", help="Filter by tag name (resolved to ID).")
    p.add_argument("--exact-title", help="Filter to a single exact title match.")

    # get-article
    p = sub.add_parser("get-article", help="Get article detail.")
    add_global_args(p)
    add_article_target_args(p)

    # update-article
    p = sub.add_parser("update-article", help="Update an existing article.")
    add_global_args(p)
    add_article_target_args(p)
    p.add_argument("--payload-file", help="JSON payload file for article update.")
    p.add_argument("--stdin-payload", action="store_true", help="Read payload JSON from stdin.")
    p.add_argument("--brief-file", help="JSON brief file for strategy validation.")
    p.add_argument("--stdin-brief", action="store_true", help="Read brief JSON from stdin.")
    p.add_argument("--wait", action="store_true", help="Poll async task until completion.")
    p.add_argument("--poll-interval", type=float, default=2.0, help="Seconds between poll requests (default: 2.0).")
    p.add_argument("--timeout", type=int, default=900, help="Maximum wait time in seconds (default: 900).")
    p.add_argument("--print-payload", action="store_true", help="Print JSON payload before sending.")

    # hide-article
    p = sub.add_parser("hide-article", help="Hide an article (viewStatus=false).")
    add_global_args(p)
    add_article_target_args(p)
    p.add_argument("--brief-file", help="JSON brief file for strategy validation.")
    p.add_argument("--stdin-brief", action="store_true", help="Read brief JSON from stdin.")
    p.add_argument("--password", help="Password for hidden article.")
    p.add_argument("--tips", help="Preview tip for hidden article.")
    p.add_argument("--wait", action="store_true", help="Poll async task until completion.")
    p.add_argument("--poll-interval", type=float, default=2.0, help="Seconds between poll requests (default: 2.0).")
    p.add_argument("--timeout", type=int, default=900, help="Maximum wait time in seconds (default: 900).")

    # article-analytics
    p = sub.add_parser("article-analytics", help="Get article analytics.")
    add_global_args(p)
    add_article_target_args(p)

    # site-visits
    p = sub.add_parser("site-visits", help="Get site visit trends.")
    add_global_args(p)
    p.add_argument("--days", type=int, choices=[7, 30], default=7, help="Number of days for trend data (7 or 30, default: 7).")

    # theme-status
    p = sub.add_parser("theme-status", help="Get article theme status.")
    add_global_args(p)

    # activate-theme
    p = sub.add_parser("activate-theme", help="Activate a global article theme.")
    add_global_args(p)
    p.add_argument("--plugin-key", required=True, help="Plugin key of the theme to activate.")

    # seo-status
    p = sub.add_parser("seo-status", help="Get SEO status.")
    add_global_args(p)

    # seo-get-config
    p = sub.add_parser("seo-get-config", help="Get controlled SEO config.")
    add_global_args(p)

    # seo-set-config
    p = sub.add_parser("seo-set-config", help="Update controlled SEO config.")
    add_global_args(p)
    p.add_argument("--config-file", required=True, help="JSON file with allowed SEO fields.")

    # sitemap-update
    p = sub.add_parser("sitemap-update", help="Trigger sitemap update.")
    add_global_args(p)


def cmd_manage(args: argparse.Namespace) -> None:
    args.base_url = normalize_base_url(str(args.base_url or ""))
    if not args.base_url:
        _output_error("Missing --base-url or POETIZE_BASE_URL.")
        return
    if not args.api_key:
        _output_error("Missing --api-key or POETIZE_API_KEY.")
        return
    mc = args.manage_command

    try:
        if mc == "list-articles":
            response = list_articles(args)
            if args.exact_title:
                records = [
                    item for item in extract_records(response)
                    if str(item.get("articleTitle", "")).strip() == args.exact_title.strip()
                ]
                response = {"code": response.get("code"), "message": response.get("message"), "data": {"records": records, "matched": len(records)}}
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "get-article":
            article_id = resolve_article_id(args)
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/article/{article_id}", args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "update-article":
            article_id = resolve_article_id(args)
            payload = resolve_payload(args)
            brief = resolve_brief(args)
            payload["id"] = article_id
            payload = apply_ops_strategy(brief, payload, expected_task_type="update_article")
            if args.print_payload:
                print(json.dumps(payload, ensure_ascii=False, indent=2))
                return
            post_async_update(args, payload)
            return

        if mc == "hide-article":
            article_id = resolve_article_id(args)
            brief = resolve_brief(args)
            payload = {
                "id": article_id,
                "viewStatus": False,
                "password": args.password or f"hidden-{article_id}",
                "tips": args.tips or "文章已隐藏，仅供受控预览",
            }
            payload = apply_ops_strategy(brief, payload, expected_task_type="hide_article")
            post_async_update(args, payload)
            return

        if mc == "article-analytics":
            article_id = resolve_article_id(args)
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/article/analytics/{article_id}", args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "site-visits":
            response = request_json("GET", build_url(args.base_url, "/api/api/analytics/site/visits", {"days": args.days}), args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "theme-status":
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/article-theme/status", args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "activate-theme":
            response = request_json("POST", f"{args.base_url.rstrip('/')}/api/api/article-theme/activate", args.api_key, {"pluginKey": args.plugin_key})
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "seo-status":
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/seo/status", args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "seo-get-config":
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/seo/config", args.api_key)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "seo-set-config":
            payload = manage_read_json_file(args.config_file)
            response = request_json("POST", f"{args.base_url.rstrip('/')}/api/api/seo/config", args.api_key, payload)
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "sitemap-update":
            response = request_json("POST", f"{args.base_url.rstrip('/')}/api/api/seo/sitemap/update", args.api_key, {})
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        _output_error(f"Unsupported manage command: {mc}")
    except StrategyValidationError as exc:
        _output_error("Strategy validation failed", detail=exc.render())
    except SystemExit as exc:
        _output_error("Manage command failed", detail=getattr(exc, "_poetize_detail", str(exc)))


# ---------------------------------------------------------------------------
# upload-image command
# ---------------------------------------------------------------------------

def add_upload_image_args(parser: argparse.ArgumentParser) -> None:
    add_global_args(parser)
    parser.add_argument("--file", help="Local image file to upload.")
    parser.add_argument("--stdin-base64", action="store_true", help="Read base64-encoded image from stdin.")
    parser.add_argument("--filename", help="Filename for stdin-base64 upload (e.g. cover.png).")
    parser.add_argument("--relative-path", help="Storage relative path (required by backend). Defaults to filename.")
    parser.add_argument("--type", default="articleCover", choices=["articleCover", "articlePicture", "articleImage", "friendLinkCover", "seoSiteIcon", "seoFavicon"], help="Resource type (default: articleCover).")
    parser.add_argument("--store-type", help="Storage type override.")


def cmd_upload_image(args: argparse.Namespace) -> None:
    args.base_url = normalize_base_url(str(args.base_url or ""))
    if not args.base_url:
        _output_error("Missing --base-url or POETIZE_BASE_URL.")
        return
    if not args.api_key:
        _output_error("Missing --api-key or POETIZE_API_KEY.")
        return

    if args.file:
        try:
            url = upload_resource(
                args.base_url,
                args.api_key,
                args.file,
                resource_type=args.type,
                relative_path=args.relative_path,
                store_type=args.store_type,
            )
        except SystemExit as exc:
            _output_error("Upload failed", detail=getattr(exc, "_poetize_detail", str(exc)))
            return
        result = {"ok": True, "url": url, "source": "file", "file": args.file, "type": args.type}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    if args.stdin_base64:
        import base64
        import tempfile

        if sys.stdin.isatty():
            _output_error("stdin-base64: stdin is a terminal. Pipe base64 image data instead.")
            return
        raw = sys.stdin.read().strip()
        if not raw:
            _output_error("stdin-base64: empty input.")
            return

        # Handle data URI prefix: data:image/png;base64,xxxx
        if raw.startswith("data:"):
            comma = raw.find(",")
            if comma == -1:
                _output_error("stdin-base64: invalid data URI format.")
                return
            header = raw[:comma]
            raw = raw[comma + 1:]
            if not args.filename:
                mime = header.split(":")[1].split(";")[0] if ":" in header else "image/png"
                ext_map = {"image/png": ".png", "image/jpeg": ".jpg", "image/gif": ".gif", "image/webp": ".webp", "image/svg+xml": ".svg"}
                ext = ext_map.get(mime, ".png")
                args.filename = f"upload{ext}"

        if not args.filename:
            args.filename = "upload.png"

        try:
            image_bytes = base64.b64decode(raw)
        except Exception as exc:
            _output_error(f"stdin-base64: invalid base64 data: {exc}")
            return

        suffix = os.path.splitext(args.filename)[1] or ".png"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(image_bytes)
            tmp_path = tmp.name

        try:
            url = upload_resource(
                args.base_url,
                args.api_key,
                tmp_path,
                resource_type=args.type,
                relative_path=args.relative_path or args.filename,
                store_type=args.store_type,
            )
        except SystemExit as exc:
            _output_error("Upload failed", detail=getattr(exc, "_poetize_detail", str(exc)))
            return
        finally:
            os.unlink(tmp_path)

        result = {"ok": True, "url": url, "source": "stdin-base64", "filename": args.filename, "type": args.type}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    _output_error("Provide --file or --stdin-base64 to upload an image.")


# ---------------------------------------------------------------------------
# config command
# ---------------------------------------------------------------------------

def add_config_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--output", required=True, help="Path to write the generated OpenClaw JSON config.")
    parser.add_argument("--existing-config", help="Optional existing OpenClaw JSON config to merge into.")
    parser.add_argument("--api-key", default=os.getenv("POETIZE_API_KEY"), help="Poetize API key.")
    parser.add_argument("--base-url", default=os.getenv("POETIZE_BASE_URL"), help="Poetize base URL.")
    parser.add_argument("--allow-placeholder-api-key", action="store_true", help="Allow placeholder apiKey.")
    parser.add_argument("--disable-watch", action="store_true", help="Do not set watch config.")


def cmd_config(args: argparse.Namespace) -> None:
    from pathlib import Path

    script_path = Path(__file__).resolve()
    skill_root = script_path.parents[1]
    openclaw_skills_dir = skill_root.parent
    repo_root = skill_root.parents[1]

    api_key = str(args.api_key or "").strip()
    if not api_key:
        if args.allow_placeholder_api_key:
            api_key = "replace-with-poetize-api-key"
        else:
            _output_error("Missing --api-key or POETIZE_API_KEY.")
            return

    base_url = infer_base_url(args.base_url, repo_root)

    existing_config: dict[str, Any] = {}
    if args.existing_config:
        from render_openclaw_config import load_json_object
        existing_config = load_json_object(Path(args.existing_config))

    try:
        generated = build_config(
            existing_config,
            extra_skill_dir=openclaw_skills_dir,
            base_url=base_url,
            api_key=api_key,
            disable_watch=args.disable_watch,
        )
    except SystemExit as exc:
        _output_error("Config generation failed", detail=getattr(exc, "_poetize_detail", str(exc)))
        return

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(generated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    result = {"ok": True, "output": str(output_path), "baseUrl": base_url}
    print(json.dumps(result, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------------------
# smoke-test command
# ---------------------------------------------------------------------------

def add_smoke_test_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--base-url", default=os.getenv("POETIZE_BASE_URL"), help="Poetize base URL.")
    parser.add_argument("--api-key", default=os.getenv("POETIZE_API_KEY"), help="Poetize API key.")
    parser.add_argument("--size", type=int, default=1, help="Number of articles to request (default: 1).")
    parser.add_argument("--search-key", help="Optional search filter for list-articles.")


def cmd_smoke_test(args: argparse.Namespace) -> None:
    base_url = normalize_base_url(str(args.base_url or ""))
    api_key = str(args.api_key or "").strip()
    if not base_url:
        _output_error("Missing --base-url or POETIZE_BASE_URL.")
        return
    if not api_key:
        _output_error("Missing --api-key or POETIZE_API_KEY.")
        return

    params: dict[str, Any] = {"current": 1, "size": args.size}
    if args.search_key:
        params["searchKey"] = args.search_key

    try:
        checked_endpoint = build_url(base_url, "/api/api/article/list", params)
        response = request_json("GET", checked_endpoint, api_key)
    except SystemExit as exc:
        _output_error("Smoke test request failed", detail=getattr(exc, "_poetize_detail", str(exc)))
        return

    if response.get("code") != 200:
        _output_error("Smoke test API returned non-200", detail=json.dumps(response, ensure_ascii=False))
        return

    records = extract_records(response)
    summary = {
        "ok": True,
        "checkedEndpoint": checked_endpoint,
        "baseUrl": base_url,
        "recordsReturned": len(records),
        "responseCode": response.get("code"),
        "responseMessage": response.get("message"),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------------------
# eval command
# ---------------------------------------------------------------------------

def add_eval_args(parser: argparse.ArgumentParser) -> None:
    pass  # No extra args needed


def cmd_eval(_args: argparse.Namespace) -> None:
    from run_strategy_evals import main as eval_main
    try:
        eval_main()
    except SystemExit as exc:
        if exc.code != 0:
            _output_error("Eval command failed", detail=getattr(exc, "_poetize_detail", str(exc)))
        else:
            raise


# ---------------------------------------------------------------------------
# Root parser
# ---------------------------------------------------------------------------

def print_help_path(parser: argparse.ArgumentParser, path: list[str]) -> None:
    current_parser = parser
    for step in path:
        subparsers_actions = [
            action for action in current_parser._actions 
            if isinstance(action, argparse._SubParsersAction)
        ]
        if not subparsers_actions:
            _output_error(f"Unknown sub-command: {step}", code=2)
            return
        next_parser = subparsers_actions[0].choices.get(step)
        if not next_parser:
            _output_error(f"Unknown sub-command: {step}", code=2)
            return
        current_parser = next_parser
    current_parser.print_help()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="poetize",
        description="Unified CLI for Poetize blog automation.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    publish_parser = sub.add_parser("publish", help="Publish or update an article.")
    add_publish_args(publish_parser)

    manage_parser = sub.add_parser("manage", help="Manage articles, themes, analytics, and SEO.")
    add_manage_subparsers(manage_parser)

    upload_parser = sub.add_parser("upload-image", help="Upload an image and get its URL.")
    add_upload_image_args(upload_parser)

    config_parser = sub.add_parser("config", help="Generate OpenClaw config.")
    add_config_args(config_parser)

    smoke_parser = sub.add_parser("smoke-test", help="Run read-only smoke test.")
    add_smoke_test_args(smoke_parser)

    eval_parser = sub.add_parser("eval", help="Run strategy-layer evaluations.")
    add_eval_args(eval_parser)

    help_parser = sub.add_parser("help", help="Show help message for a command.")
    help_parser.add_argument("subcommand", nargs="*", help="Subcommand(s) to show help for.")

    return parser


def main() -> None:
    configure_stdio()
    parser = build_parser()

    try:
        args = parser.parse_args()
    except SystemExit as exc:
        # argparse calls sys.exit(0) for --help and sys.exit(2) for bad args.
        # Only emit a structured error for actual argument errors (code != 0).
        if exc.code != 0:
            _output_error("Invalid command-line arguments. Check stderr for usage details.", code=exc.code)
        raise SystemExit(exc.code)

    try:
        if args.command == "publish":
            cmd_publish(args)
        elif args.command == "manage":
            cmd_manage(args)
        elif args.command == "upload-image":
            cmd_upload_image(args)
        elif args.command == "config":
            cmd_config(args)
        elif args.command == "smoke-test":
            cmd_smoke_test(args)
        elif args.command == "eval":
            cmd_eval(args)
        elif args.command == "help":
            print_help_path(parser, args.subcommand or [])
        else:
            parser.print_help()
            _output_error("No command specified.")
    except Exception as exc:
        # Catch-all for unexpected Python exceptions (TypeError, KeyError, etc.)
        # that were not caught by individual cmd_* functions.
        # SystemExit is a subclass of BaseException, not Exception, so it won't
        # be caught here — it's already handled by cmd_* functions.
        import traceback
        _output_error(
            f"Unexpected error: {type(exc).__name__}: {exc}",
            detail=traceback.format_exc(),
        )


if __name__ == "__main__":
    main()

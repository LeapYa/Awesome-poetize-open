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
from pathlib import Path
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
# Credentials storage (~/.config/poetize/credentials.json, local/CWD fallback)
# ---------------------------------------------------------------------------

try:
    CREDENTIALS_PATH = Path.home() / ".config" / "poetize" / "credentials.json"
except Exception:
    CREDENTIALS_PATH = None

LOCAL_CREDENTIALS_PATH = Path(__file__).resolve().parent.parent / "credentials.json"
CWD_CREDENTIALS_PATH = Path.cwd() / "credentials.json"


def load_credentials() -> dict[str, str]:
    """Load saved credentials. Returns {base_url, api_key} or empty dict.
    Checks:
    1. Global: ~/.config/poetize/credentials.json
    2. Local: {baseDir}/credentials.json
    3. CWD: ./credentials.json
    """
    paths_to_check = []
    if CREDENTIALS_PATH:
        paths_to_check.append(CREDENTIALS_PATH)
    paths_to_check.extend([LOCAL_CREDENTIALS_PATH, CWD_CREDENTIALS_PATH])

    for path in paths_to_check:
        if path.exists():
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(data, dict):
                    res = {
                        k: str(v)
                        for k, v in data.items()
                        if k in ("base_url", "api_key") and v
                    }
                    if res.get("base_url") and res.get("api_key"):
                        return res
            except (json.JSONDecodeError, OSError):
                pass
    return {}


def save_credentials(base_url: str, api_key: str, local: bool = False) -> Path:
    """Save credentials with 0600 permissions."""
    target_path = LOCAL_CREDENTIALS_PATH if local else (CREDENTIALS_PATH if CREDENTIALS_PATH else LOCAL_CREDENTIALS_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"base_url": base_url, "api_key": api_key}
    target_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    try:
        target_path.chmod(0o600)
    except OSError:
        pass  # Windows or non-POSIX filesystems
    return target_path


def clear_credentials(local: bool = False) -> tuple[bool, Path]:
    """Delete credentials file if exists."""
    target_path = LOCAL_CREDENTIALS_PATH if local else (CREDENTIALS_PATH if CREDENTIALS_PATH else LOCAL_CREDENTIALS_PATH)
    if target_path and target_path.exists():
        target_path.unlink()
        return True, target_path
    return False, target_path


def resolve_credentials(args: argparse.Namespace) -> None:
    """Fill args.base_url and args.api_key from: CLI > env > credentials files (global, local, CWD).

    Called once in main() after argparse, before dispatching to cmd_*.
    """
    creds = load_credentials()
    if not getattr(args, "base_url", None):
        args.base_url = creds.get("base_url") or os.getenv("POETIZE_BASE_URL")
    if not getattr(args, "api_key", None):
        args.api_key = creds.get("api_key") or os.getenv("POETIZE_API_KEY")


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


# ---------------------------------------------------------------------------
# Comment endpoints: version-gap guard
# ---------------------------------------------------------------------------

# /api/api/comment/list and /api/api/comment/save only exist starting from
# this awesome-poetize-open backend version. Older backends 404/500 on these
# routes because the endpoint itself does not exist yet — that failure looks
# identical to a real bug from the CLI's perspective, so we translate it into
# an explicit version-mismatch message instead of surfacing a raw HTTP error.
COMMENT_ENDPOINTS_MIN_BACKEND_VERSION = "v5.0.1"


def request_json_comment_endpoint(
    method: str,
    url: str,
    api_key: str,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Wrap request_json for /api/api/comment/* routes.

    On HTTP 404/500 (endpoint not registered on older backends), re-raise
    with a version-mismatch explanation instead of the raw server error.
    """
    try:
        return request_json(method, url, api_key, payload)
    except SystemExit as exc:
        detail = getattr(exc, "_poetize_detail", "") or ""
        if detail.startswith("HTTP 404") or detail.startswith("HTTP 500"):
            _cli_die(
                f"{detail}\n\n"
                f"This likely means your awesome-poetize-open backend is older than "
                f"{COMMENT_ENDPOINTS_MIN_BACKEND_VERSION}, which is the first version to "
                f"expose {url.split('/api/api/', 1)[-1] if '/api/api/' in url else url}. "
                f"Comment list/reply support (manage list-comments / manage save-comment) "
                f"requires upgrading the backend to {COMMENT_ENDPOINTS_MIN_BACKEND_VERSION} or later. "
                f"All other commands in this skill are unaffected."
            )
        raise


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


def resolve_brief(args: argparse.Namespace, meta: dict[str, Any] | None = None) -> dict[str, Any]:
    """Load brief from --stdin-brief > --brief-file > inline _brief in front matter."""
    if getattr(args, "stdin_brief", False):
        return read_stdin_json("Brief")
    brief_file = getattr(args, "brief_file", None)
    if brief_file:
        return load_json_object(brief_file, label="Brief file")
    # Inline _brief from front matter (publish command only)
    if meta is not None:
        inline = meta.get("_brief")
        if isinstance(inline, dict) and inline:
            return inline
    _cli_die("Provide --brief-file, --stdin-brief, or an inline _brief block in front matter.")


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
    parser.add_argument("--base-url", default=None, help="Poetize base URL. Falls back to POETIZE_BASE_URL env or ~/.config/poetize/credentials.json.")
    parser.add_argument("--api-key", default=None, help="Poetize API key. Falls back to POETIZE_API_KEY env or ~/.config/poetize/credentials.json.")


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
    parser.add_argument("--force", action="store_true", help="Skip the heading-structure validation and publish anyway.")


def cmd_publish(args: argparse.Namespace) -> None:
    args.base_url = normalize_base_url(str(args.base_url or ""))
    if not args.base_url:
        _output_error("Missing --base-url or POETIZE_BASE_URL.")
        return
    if not args.api_key:
        _output_error("Missing --api-key or POETIZE_API_KEY.")
        return

    try:
        with open(args.markdown_file, "r", encoding="utf-8") as handle:
            markdown_text = handle.read()

        payload, meta = build_payload(markdown_text, args)
        brief = resolve_brief(args, meta)
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

        def attach_agent_guide(res: dict[str, Any], pay: dict[str, Any], resp_data: Any) -> None:
            if pay.get("viewStatus") is False:
                article_id = args.article_id
                if not article_id and isinstance(resp_data, dict):
                    article_id = resp_data.get("articleId") or resp_data.get("id")
                id_str = str(article_id) if article_id else "<article_id>"
                res["agent_guide"] = {
                    "message": "Draft created/updated successfully with a temporary password.",
                    "password": pay.get("password"),
                    "tips": pay.get("tips"),
                    "next_steps": [
                        f"Verify the draft format/metadata: python poetize_cli.py manage get-article --article-id {id_str}",
                        f"Promote this draft to public: python poetize_cli.py publish --markdown-file {args.markdown_file} --article-id {id_str} --publish --wait"
                    ]
                }

        if not args.wait:
            result = {"ok": True, **response}
            attach_agent_guide(result, payload, response.get("data"))
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
        attach_agent_guide(result, payload, final_data)
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
    p = sub.add_parser("get-article", help="Get article content by ID, slug, or exact title.")
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

    # list-comments
    p = sub.add_parser("list-comments", help="List comments for an article.")
    add_global_args(p)
    p.add_argument("--article-id", type=int, required=True, help="Target article ID.")
    p.add_argument("--floor-comment-id", type=int, help="Optional floor/thread ID to fetch replies within a specific comment tree.")
    p.add_argument("--current", type=int, default=1, help="Page number (default: 1).")
    p.add_argument("--size", type=int, default=10, help="Page size (default: 10).")

    # save-comment
    p = sub.add_parser("save-comment", help="Post or reply to a comment.")
    add_global_args(p)
    p.add_argument("--article-id", type=int, required=True, help="Target article ID.")
    p.add_argument("--content", required=True, help="Comment text content.")
    p.add_argument("--parent-comment-id", type=int, help="Optional parent comment ID (to reply).")
    p.add_argument("--parent-user-id", type=int, help="Optional parent user ID (to reply).")
    p.add_argument("--floor-comment-id", type=int, help="Ignored by the backend for writes: the server always recomputes floorCommentId from --parent-comment-id and only logs a warning if this disagrees. Safe to omit; kept for symmetry with list-comments.")
    p.add_argument("--as-ai", action="store_true", help="Comment/reply as the AI assistant persona.")

    # task-status
    p = sub.add_parser("task-status", help="Get status of an asynchronous article save/update task.")
    add_global_args(p)
    p.add_argument("--task-id", required=True, help="Async task ID.")


def format_comment_tree(records: list[dict[str, Any]]) -> str:
    if not records:
        return "No comments."
    
    lines = []
    comments_map = {c["id"]: c for c in records}
    
    children_map = {}
    for c in records:
        pid = c.get("parentCommentId") or 0
        children_map.setdefault(pid, []).append(c)
        
    for siblings in children_map.values():
        siblings.sort(key=lambda x: x.get("createTime", "") or x.get("id", 0))
        
    def build_lines(comment_id: int, indent: int = 0):
        c = comments_map[comment_id]
        username = c.get("displayUsername") or c.get("username") or f"User {c.get('userId')}"
        content = c.get("commentContent") or ""
        
        info = c.get("commentInfo")
        is_ai = False
        if info:
            try:
                info_json = json.loads(info) if isinstance(info, str) else info
                is_ai = bool(info_json.get("aiReply") or info_json.get("ai_reply"))
            except Exception:
                is_ai = "aiReply\":true" in str(info) or "ai_reply\":true" in str(info)
                
        prefix = "[AI] " if is_ai else ""
        
        parent_username = c.get("parentUsername")
        reply_str = f" Reply to {parent_username}" if parent_username else ""
        
        user_id = c.get("userId")
        user_suffix = f"(user:{user_id})" if user_id is not None else ""
        
        indent_str = "  " * indent
        lines.append(f"{indent_str}- #{c['id']}{user_suffix} {prefix}{username}{reply_str}: {content.strip()}")
        
        children = children_map.get(comment_id, [])
        for child in children:
            build_lines(child["id"], indent + 1)

    top_level = [c for c in records if (c.get("parentCommentId") or 0) not in comments_map]
    top_level.sort(key=lambda x: x.get("createTime", "") or x.get("id", 0), reverse=True)
    
    for c in top_level:
        build_lines(c["id"], 0)
        
    return "\n".join(lines)


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
            if response.get("code") == 200:
                data = response.get("data", {})
                records = data.get("records", []) if isinstance(data, dict) else []
                next_steps = []
                if records:
                    first_id = records[0].get("id") or records[0].get("articleId")
                    if first_id:
                        next_steps.append(f"Fetch article details (content/metadata): python poetize_cli.py manage get-article --article-id {first_id}")
                next_steps.append("Fetch details of any article: python poetize_cli.py manage get-article --article-id <id>")
                response["agent_guide"] = {
                    "message": "Articles listed successfully.",
                    "next_steps": next_steps
                }
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "get-article":
            article_id = resolve_article_id(args)
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/article/{article_id}", args.api_key)
            if response.get("code") == 200:
                data = response.get("data")
                next_steps = []
                if isinstance(data, dict):
                    art_id = data.get("id")
                    if art_id:
                        next_steps.extend([
                            f"Update metadata fields: python poetize_cli.py manage update-article --article-id {art_id} --payload-file payload.json --stdin-brief",
                            f"Rewrite article content: edit local markdown file, then run: python poetize_cli.py publish --markdown-file <file> --article-id {art_id} --publish --wait",
                            f"Hide this article from public view: python poetize_cli.py manage hide-article --article-id {art_id} --stdin-brief"
                        ])
                response["agent_guide"] = {
                    "message": "Article details fetched successfully.",
                    "next_steps": next_steps
                }
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
            response = post_async_update(args, payload)
            id_str = str(article_id)
            response["agent_guide"] = {
                "message": "Article updated successfully.",
                "next_steps": [
                    f"Fetch details to verify update: python poetize_cli.py manage get-article --article-id {id_str}"
                ]
            }
            print(json.dumps(response, ensure_ascii=False, indent=2))
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
            response = post_async_update(args, payload)
            id_str = str(article_id)
            response["agent_guide"] = {
                "message": "Article hidden successfully. It is no longer visible to the public.",
                "password": payload.get("password"),
                "tips": payload.get("tips"),
                "next_steps": [
                    f"Verify hidden status: python poetize_cli.py manage get-article --article-id {id_str}",
                    f"To make it public again: edit markdown and run: python poetize_cli.py publish --markdown-file <file> --article-id {id_str} --publish --wait"
                ]
            }
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "task-status":
            task_id = args.task_id
            response = request_json("GET", f"{args.base_url.rstrip('/')}/api/api/article/task/{urllib.parse.quote(task_id, safe='')}", args.api_key)
            if response.get("code") == 200:
                data = response.get("data", {})
                next_steps = []
                art_id = data.get("articleId")
                status = data.get("status")
                if art_id:
                    # Article exists!
                    next_steps.extend([
                        f"Fetch details: python poetize_cli.py manage get-article --article-id {art_id}",
                        f"Safe retry/update content (prevents duplicates): python poetize_cli.py publish --markdown-file <file> --article-id {art_id} --publish --wait"
                    ])
                elif status == "failed":
                    next_steps.append("Task failed and no article was created in DB. Safe to retry publishing as a new article: python poetize_cli.py publish --markdown-file <file> --draft/--publish --wait")
                else:
                    next_steps.append("Task is still processing or in queue. Wait a few seconds and run this command again.")
                response["agent_guide"] = {
                    "message": f"Task status retrieved. Status: {status}.",
                    "next_steps": next_steps
                }
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "list-comments":
            payload = {
                "source": args.article_id,
                "commentType": "article",
                "current": args.current,
                "size": args.size
            }
            if args.floor_comment_id is not None:
                payload["floorCommentId"] = args.floor_comment_id
            response = request_json_comment_endpoint("POST", f"{args.base_url.rstrip('/')}/api/api/comment/list", args.api_key, payload)
            if response.get("code") == 200:
                data = response.get("data", {})
                records = data.get("records", []) if isinstance(data, dict) else []
                
                all_records = list(records)
                root_comment_missing = False
                if args.floor_comment_id is None and records:
                    for r in records:
                        child_comments_info = r.get("childComments")
                        if isinstance(child_comments_info, dict) and child_comments_info.get("total", 0) > 0:
                            total_replies = child_comments_info.get("total", 0)
                            reply_payload = {
                                "source": args.article_id,
                                "commentType": "article",
                                "floorCommentId": r["id"],
                                "current": 1,
                                "size": 10
                            }
                            reply_res = request_json_comment_endpoint("POST", f"{args.base_url.rstrip('/')}/api/api/comment/list", args.api_key, reply_payload)
                            if reply_res.get("code") == 200:
                                reply_data = reply_res.get("data", {})
                                reply_records = reply_data.get("records", []) if isinstance(reply_data, dict) else []
                                all_records.extend(reply_records)
                                r["childComments"]["records"] = reply_records
                                
                                # 如果本楼层实际子回复数量大于首页加载的 10 条
                                if total_replies > len(reply_records):
                                    remaining = total_replies - len(reply_records)
                                    dummy = {
                                        "id": -r["id"],  # 用负数做主键避免冲突
                                        "parentCommentId": r["id"],
                                        "commentContent": f"... [{remaining} more replies under this floor. Run: python poetize_cli.py manage list-comments --article-id {args.article_id} --floor-comment-id {r['id']} --current 2 --size 10 to view]",
                                        "username": "System"
                                    }
                                    all_records.append(dummy)
                else:
                    # 单独拉取某楼层的分页数据，尝试获取楼层主评论作为树根以提供完整上下文。
                    # 注意：后端没有按 id 查单条评论的接口，这里只能在最新一页一级评论
                    # (size=50, 按 create_time desc 排序) 里线性查找。如果该楼层的主评论
                    # 不在最新 50 条一级评论内（文章评论数较多、楼层较老），root_comment 会
                    # 找不到——这种情况必须显式告知调用方，而不是静默丢弃楼层上下文。
                    root_comment = None
                    main_payload = {
                        "source": args.article_id,
                        "commentType": "article",
                        "current": 1,
                        "size": 50
                    }
                    main_res = request_json_comment_endpoint("POST", f"{args.base_url.rstrip('/')}/api/api/comment/list", args.api_key, main_payload)
                    if main_res.get("code") == 200:
                        main_data = main_res.get("data", {})
                        main_records = main_data.get("records", []) if isinstance(main_data, dict) else []
                        for mr in main_records:
                            if mr.get("id") == args.floor_comment_id:
                                root_comment = mr
                                break
                        if root_comment is None:
                            root_comment_missing = True

                    if root_comment:
                        all_records = [root_comment] + all_records

                    # 单独拉取某楼层的分页数据，判断是否需要翻页
                    total = data.get("total", 0)
                    if total > args.current * args.size:
                        remaining = total - (args.current * args.size)
                        dummy = {
                            "id": -9999,
                            "parentCommentId": args.floor_comment_id,
                            "commentContent": f"... [{remaining} more replies on next page. Run: python poetize_cli.py manage list-comments --article-id {args.article_id} --floor-comment-id {args.floor_comment_id} --current {args.current + 1} --size {args.size} to view]",
                            "username": "System"
                        }
                        all_records.append(dummy)
                
                response["formatted_tree"] = format_comment_tree(all_records)
                if root_comment_missing:
                    response["root_comment_missing"] = True

                next_steps = []
                if records:
                    first_comment = records[0]
                    fc_id = first_comment.get("id")
                    fc_user = first_comment.get("userId")
                    fc_floor = first_comment.get("floorCommentId") or fc_id
                    next_steps.extend([
                        f"Reply to the first comment: python poetize_cli.py manage save-comment --article-id {args.article_id} --content \"your reply\" --parent-comment-id {fc_id} --parent-user-id {fc_user} --as-ai"
                    ])
                next_steps.append(f"Write a new top-level comment: python poetize_cli.py manage save-comment --article-id {args.article_id} --content \"your comment\"")
                agent_guide_message = "Comments retrieved successfully."
                if root_comment_missing:
                    agent_guide_message += (
                        f" WARNING: the floor's root comment (id={args.floor_comment_id}) was not found among "
                        "the latest 50 top-level comments, so formatted_tree is showing replies without their "
                        "root context. This happens when the article has many top-level comments and this floor "
                        "is older. Run 'manage list-comments --article-id ... --current <N>' with increasing pages "
                        "against the default (no --floor-comment-id) call to locate the root comment manually if needed."
                    )
                response["agent_guide"] = {
                    "message": agent_guide_message,
                    "next_steps": next_steps
                }
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return

        if mc == "save-comment":
            comment_info = "{\"aiReply\":true}" if args.as_ai else "{\"aiReply\":false}"
            payload = {
                "source": args.article_id,
                "type": "article",
                "commentContent": args.content,
                "commentInfo": comment_info
            }
            if args.parent_comment_id is not None:
                payload["parentCommentId"] = args.parent_comment_id
            if args.parent_user_id is not None:
                payload["parentUserId"] = args.parent_user_id
            if args.floor_comment_id is not None:
                payload["floorCommentId"] = args.floor_comment_id
            
            response = request_json_comment_endpoint("POST", f"{args.base_url.rstrip('/')}/api/api/comment/save", args.api_key, payload)
            if response.get("code") == 200:
                response["agent_guide"] = {
                    "message": "Comment posted successfully.",
                    "next_steps": [
                        f"View updated comments list: python poetize_cli.py manage list-comments --article-id {args.article_id}"
                    ]
                }
            print(json.dumps(response, ensure_ascii=False, indent=2))
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
        result = {
            "ok": True, 
            "url": url, 
            "source": "file", 
            "file": args.file, 
            "type": args.type,
            "agent_guide": {
                "message": "Image uploaded successfully to the blog storage server.",
                "markdown_syntax": f"![image]({url})",
                "next_steps": [
                    "Embed the image in your markdown content using the markdown_syntax.",
                    "Or use it as article cover via 'cover' or 'coverFile' in your markdown front matter."
                ]
            }
        }
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

        result = {
            "ok": True, 
            "url": url, 
            "source": "stdin-base64", 
            "filename": args.filename, 
            "type": args.type,
            "agent_guide": {
                "message": "Image uploaded successfully to the blog storage server.",
                "markdown_syntax": f"![image]({url})",
                "next_steps": [
                    "Embed the image in your markdown content using the markdown_syntax.",
                    "Or use it as article cover via 'cover' or 'coverFile' in your markdown front matter."
                ]
            }
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    _output_error("Provide --file or --stdin-base64 to upload an image.")


# ---------------------------------------------------------------------------
# config command
# ---------------------------------------------------------------------------

def add_config_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--output", required=True, help="Path to write the generated config file.")
    parser.add_argument("--format", choices=["openclaw", "env"], default="openclaw", help="Output format: openclaw (JSON, default) or env (shell-sourceable KEY=VALUE).")
    parser.add_argument("--existing-config", help="Optional existing OpenClaw JSON config to merge into (openclaw format only).")
    parser.add_argument("--api-key", default=None, help="Poetize API key. Falls back to env or ~/.config/poetize/credentials.json.")
    parser.add_argument("--base-url", default=None, help="Poetize base URL. Falls back to env or ~/.config/poetize/credentials.json.")
    parser.add_argument("--allow-placeholder-api-key", action="store_true", help="Allow placeholder apiKey.")
    parser.add_argument("--disable-watch", action="store_true", help="Do not set watch config (openclaw format only).")


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

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    if args.format == "env":
        # Shell-sourceable KEY=VALUE file for Trae/Qoder/IDE agents or ~/.bashrc
        lines = [
            "# Poetize blog automation credentials",
            "# Source this file or copy into ~/.bashrc / IDE env settings.",
            f"POETIZE_BASE_URL={base_url}",
            f"POETIZE_API_KEY={api_key}",
            "",
        ]
        output_path.write_text("\n".join(lines), encoding="utf-8")
        result = {"ok": True, "output": str(output_path), "format": "env", "baseUrl": base_url}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    # openclaw format (default)
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

    output_path.write_text(json.dumps(generated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    result = {"ok": True, "output": str(output_path), "format": "openclaw", "baseUrl": base_url}
    print(json.dumps(result, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------------------
# smoke-test command
# ---------------------------------------------------------------------------

def add_smoke_test_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--base-url", default=None, help="Poetize base URL. Falls back to env or ~/.config/poetize/credentials.json.")
    parser.add_argument("--api-key", default=None, help="Poetize API key. Falls back to env or ~/.config/poetize/credentials.json.")
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
        "agent_guide": {
            "message": "Smoke test passed. API connection is fully functional.",
            "next_steps": [
                "List existing articles to plan internal links: python poetize_cli.py manage list-articles",
                "Create and publish a new draft article: python poetize_cli.py publish --markdown-file article.md --draft --wait"
            ]
        }
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------------------
# auth command
# ---------------------------------------------------------------------------

def cmd_auth(args: argparse.Namespace) -> None:
    if args.auth_command == "login":
        base_url = str(args.base_url or "").strip()
        api_key = str(args.api_key or "").strip()
        if not base_url or not api_key:
            _output_error("Both --base-url and --api-key are required for auth login.")
            return
        is_local = getattr(args, "local", False)
        saved_path = save_credentials(base_url, api_key, local=is_local)
        result = {
            "ok": True, 
            "saved": True, 
            "path": str(saved_path),
            "agent_guide": {
                "message": "API credentials stored successfully.",
                "next_steps": [
                    "Verify API connection: python poetize_cli.py smoke-test",
                    "List recent articles: python poetize_cli.py manage list-articles --size 5"
                ]
            }
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    elif args.auth_command == "show":
        creds = load_credentials()
        env_url = os.getenv("POETIZE_BASE_URL")
        env_key = os.getenv("POETIZE_API_KEY")
        cli_url = getattr(args, "base_url", None)
        cli_key = getattr(args, "api_key", None)
        result = {
            "global_file": {
                "path": str(CREDENTIALS_PATH) if CREDENTIALS_PATH else None,
                "exists": CREDENTIALS_PATH.exists() if CREDENTIALS_PATH else False,
            },
            "local_file": {
                "path": str(LOCAL_CREDENTIALS_PATH),
                "exists": LOCAL_CREDENTIALS_PATH.exists(),
            },
            "cwd_file": {
                "path": str(CWD_CREDENTIALS_PATH),
                "exists": CWD_CREDENTIALS_PATH.exists(),
            },
            "active_credentials": {
                "base_url": creds.get("base_url"),
                "api_key": "***" if creds.get("api_key") else None,
            },
            "env": {
                "POETIZE_BASE_URL": env_url,
                "POETIZE_API_KEY": "***" if env_key else None,
            },
            "cli": {
                "base_url": cli_url,
                "api_key": "***" if cli_key else None,
            },
            "resolution_order": "CLI > env > ~/.config/poetize/credentials.json > {baseDir}/credentials.json > ./credentials.json",
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    elif args.auth_command == "clear":
        is_local = getattr(args, "local", False)
        deleted, target_path = clear_credentials(local=is_local)
        result = {"ok": True, "deleted": deleted, "path": str(target_path)}
        print(json.dumps(result, ensure_ascii=False, indent=2))


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

    auth_parser = sub.add_parser("auth", help="Manage persisted credentials (~/.config/poetize/credentials.json or local fallback).")
    auth_sub = auth_parser.add_subparsers(dest="auth_command", required=True)
    auth_login = auth_sub.add_parser("login", help="Save base-url and api-key for future use.")
    auth_login.add_argument("--base-url", required=True, help="Poetize base URL.")
    auth_login.add_argument("--api-key", required=True, help="Poetize API key.")
    auth_login.add_argument("--local", action="store_true", help="Save in local skill folder instead of global home directory.")
    auth_sub.add_parser("show", help="Show where credentials are resolved from.")
    auth_clear = auth_sub.add_parser("clear", help="Delete saved credentials.")
    auth_clear.add_argument("--local", action="store_true", help="Clear from local skill folder instead of global home directory.")

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
        if args.command == "auth":
            cmd_auth(args)
        else:
            # Non-auth commands need base_url/api_key resolved from CLI > env > credentials file
            resolve_credentials(args)
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

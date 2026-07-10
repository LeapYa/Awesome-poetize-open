#!/usr/bin/env python3
"""Run local strategy-layer evaluations for the Poetize blog automation skill."""

from __future__ import annotations

import json
from types import SimpleNamespace

import manage_blog
from blog_strategy import StrategyValidationError, apply_article_strategy, apply_ops_strategy


class EvalFailure(Exception):
    """Raised when an eval case fails."""


def assert_true(condition: bool, message: str) -> None:
    if not condition:
        raise EvalFailure(message)


def expect_strategy_error(fn, contains: str) -> None:
    try:
        fn()
    except StrategyValidationError as exc:
        rendered = exc.render()
        assert_true(contains in rendered, f"Expected error to contain '{contains}', got: {rendered}")
        return
    raise EvalFailure("Expected StrategyValidationError but the call succeeded.")


def expect_die_signal(fn, contains: str) -> None:
    class DieSignal(Exception):
        pass

    original_die = manage_blog.die

    def fake_die(message: str, code: int = 1) -> None:
        raise DieSignal(message)

    manage_blog.die = fake_die
    try:
        try:
            fn()
        except DieSignal as exc:
            assert_true(contains in str(exc), f"Expected die message to contain '{contains}', got: {exc}")
            return
        raise EvalFailure("Expected command to stop with a die signal.")
    finally:
        manage_blog.die = original_die


def run_article_eval_suite() -> None:
    from publish_post import build_payload

    publish_args = SimpleNamespace(
        article_id=None,
        markdown_file="article.md",
        base_url="https://example.com",
        api_key="test",
        publish=False,
        draft=False,
        cover_file=None,
        force=False,
    )
    markdown_without_search_flag = (
        "---\n"
        "title: Example\n"
        "sort: AI\n"
        "label: Automation\n"
        "---\n\n"
        "## Section\n\nBody\n"
    )
    raw_payload, _ = build_payload(markdown_without_search_flag, publish_args)
    assert_true(
        "submitToSearchEngine" not in raw_payload,
        "Payload builder should leave omitted submitToSearchEngine unset for strategy defaults.",
    )
    markdown_with_search_disabled = markdown_without_search_flag.replace(
        "label: Automation\n",
        "label: Automation\nsubmitToSearchEngine: false\n",
    )
    raw_disabled_payload, _ = build_payload(markdown_with_search_disabled, publish_args)
    assert_true(
        raw_disabled_payload["submitToSearchEngine"] is False,
        "Payload builder should preserve explicit submitToSearchEngine=false.",
    )

    free_brief = {
        "taskType": "create_article",
        "primaryGoal": "asset_maintenance",
        "targetAudience": "Personal blog readers",
        "publishIntent": "public",
        "reasoning": "This article improves the long-term blog library.",
        "selectedAngle": "Practical maintenance guide",
        "alternativesConsidered": ["Wide beginner overview", "Compact tactical checklist"],
    }
    free_payload = {"title": "Example", "content": "Body", "payType": 4, "payAmount": 19.9}
    result = apply_article_strategy(free_brief, free_payload, is_update=False, cli_publish=False, cli_draft=False)
    assert_true(result["payType"] == 0, "Free-default article should force payType=0.")
    assert_true(result["viewStatus"] is True, "Public brief should produce viewStatus=true.")
    assert_true(result["submitToSearchEngine"] is True, "Public article should default submitToSearchEngine=true.")

    public_without_search = apply_article_strategy(
        free_brief,
        {"title": "Example", "content": "Body", "submitToSearchEngine": False},
        is_update=False,
        cli_publish=False,
        cli_draft=False,
    )
    assert_true(
        public_without_search["submitToSearchEngine"] is False,
        "Public article should preserve explicit submitToSearchEngine=false.",
    )
    expect_strategy_error(
        lambda: apply_article_strategy(
            free_brief,
            {"title": "Example", "content": "Body", "submitToSearchEngine": "false"},
            is_update=False,
            cli_publish=False,
            cli_draft=False,
        ),
        "submitToSearchEngine",
    )

    update_brief = dict(free_brief)
    update_brief["taskType"] = "refresh_article"
    update_default = apply_article_strategy(
        update_brief,
        {"title": "Example", "content": "Updated body"},
        is_update=True,
        cli_publish=False,
        cli_draft=False,
    )
    assert_true(
        update_default["submitToSearchEngine"] is True,
        "Public publish update should default submitToSearchEngine=true.",
    )
    update_without_search = apply_article_strategy(
        update_brief,
        {"title": "Example", "content": "Updated body", "submitToSearchEngine": False},
        is_update=True,
        cli_publish=False,
        cli_draft=False,
    )
    assert_true(
        update_without_search["submitToSearchEngine"] is False,
        "Public publish update should preserve explicit submitToSearchEngine=false.",
    )

    draft_brief = dict(free_brief)
    draft_brief["publishIntent"] = "draft"
    draft_result = apply_article_strategy(
        draft_brief,
        {"title": "Example", "content": "Body", "submitToSearchEngine": True},
        is_update=False,
        cli_publish=False,
        cli_draft=False,
    )
    assert_true(draft_result["viewStatus"] is False, "Draft brief should force viewStatus=false.")
    assert_true(draft_result["submitToSearchEngine"] is False, "Draft brief should force submitToSearchEngine=false.")
    assert_true(bool(draft_result.get("password")), "Draft brief should auto-fill a password.")
    assert_true(bool(draft_result.get("tips")), "Draft brief should auto-fill preview tips.")

    # Empty list must still be rejected (min_items=1).
    bad_alternatives = dict(free_brief)
    bad_alternatives["alternativesConsidered"] = []
    expect_strategy_error(
        lambda: apply_article_strategy(bad_alternatives, {"title": "Example", "content": "Body"}, is_update=False, cli_publish=False, cli_draft=False),
        "alternativesConsidered",
    )

    # Single alternative is now valid (min_items relaxed from 2 to 1).
    single_alternative = dict(free_brief)
    single_alternative["alternativesConsidered"] = ["Generic broad overview (rejected: too shallow for target audience)"]
    single_result = apply_article_strategy(single_alternative, {"title": "Example", "content": "Body"}, is_update=False, cli_publish=False, cli_draft=False)
    assert_true(single_result["viewStatus"] is True, "Single-alternative brief should still produce a valid public article.")

    invalid_paid = dict(free_brief)
    invalid_paid["monetizationIntent"] = "paid_explicit"
    invalid_paid["whyPaid"] = "Try to monetize."
    expect_strategy_error(
        lambda: apply_article_strategy(invalid_paid, {"title": "Example", "content": "Body", "payType": 4}, is_update=False, cli_publish=False, cli_draft=False),
        "primaryGoal",
    )

    valid_paid = dict(invalid_paid)
    valid_paid["primaryGoal"] = "conversion"
    paid_result = apply_article_strategy(valid_paid, {"title": "Example", "content": "Body", "payType": 4}, is_update=False, cli_publish=False, cli_draft=False)
    assert_true(paid_result["payType"] == 4, "Explicit paid article should retain payType when conversion goal is set.")


def run_ops_eval_suite() -> None:
    update_brief = {
        "taskType": "update_article",
        "primaryGoal": "asset_maintenance",
        "reasoning": "Refresh the existing article.",
        "expectedOutcome": "The post stays accurate and useful.",
    }
    expect_strategy_error(
        lambda: apply_ops_strategy(update_brief, {"id": 12, "viewStatus": False}, expected_task_type="update_article"),
        "hide-article",
    )

    expect_strategy_error(
        lambda: apply_ops_strategy(update_brief, {"id": 12, "payType": 4}, expected_task_type="update_article"),
        "paywall",
    )

    update_default = apply_ops_strategy(update_brief, {"id": 12}, expected_task_type="update_article")
    assert_true(
        update_default["submitToSearchEngine"] is True,
        "Metadata update should default submitToSearchEngine=true.",
    )
    update_without_search = apply_ops_strategy(
        update_brief,
        {"id": 12, "submitToSearchEngine": False},
        expected_task_type="update_article",
    )
    assert_true(
        update_without_search["submitToSearchEngine"] is False,
        "Metadata update should preserve explicit submitToSearchEngine=false.",
    )

    hide_brief = {
        "taskType": "hide_article",
        "primaryGoal": "asset_maintenance",
        "reasoning": "Take the post down from public view.",
        "expectedOutcome": "The article is no longer public but remains recoverable.",
    }
    hidden = apply_ops_strategy(hide_brief, {"id": 12, "submitToSearchEngine": True}, expected_task_type="hide_article")
    assert_true(hidden["viewStatus"] is False, "hide_article should force viewStatus=false.")
    assert_true(hidden["submitToSearchEngine"] is False, "hide_article should force submitToSearchEngine=false.")


def run_taxonomy_eval_suite() -> None:
    fake_args = SimpleNamespace(base_url="https://example.com", api_key="test")

    original_fetch_categories = manage_blog.fetch_categories
    manage_blog.fetch_categories = lambda args: [
        {"id": 1, "sortName": "AI实践"},
        {"id": 2, "sortName": "AI工具"},
    ]
    try:
        expect_die_signal(lambda: manage_blog.resolve_sort_id(fake_args, None, "AI"), "Closest matches")
    finally:
        manage_blog.fetch_categories = original_fetch_categories


def run_inline_brief_eval_suite() -> None:
    """Verify inline _brief block in front matter is parsed and validated like a standalone brief."""
    from publish_post import parse_front_matter

    markdown = (
        "---\n"
        "title: \"示例\"\n"
        "sort: \"AI实践\"\n"
        "label: \"自动化\"\n"
        "_brief:\n"
        "  taskType: create_article\n"
        "  primaryGoal: asset_maintenance\n"
        "  targetAudience: \"想理解 X 的读者\"\n"
        "  publishIntent: public\n"
        "  reasoning: \"补齐博客长期内容资产\"\n"
        "  selectedAngle: \"实用维护视角\"\n"
        "  alternativesConsidered: [\"宽泛入门\", \"战术清单\"]\n"
        "---\n\n"
        "# 示例\n\n正文...\n"
    )
    meta, body = parse_front_matter(markdown)
    assert_true(isinstance(meta.get("_brief"), dict), "_brief should be parsed as a dict.")
    brief = meta["_brief"]
    assert_true(brief.get("taskType") == "create_article", "taskType should be parsed.")
    assert_true(isinstance(brief.get("alternativesConsidered"), list), "alternativesConsidered should be a list.")
    assert_true(len(brief["alternativesConsidered"]) == 2, "alternativesConsidered should have 2 items.")
    assert_true(body.startswith("# 示例"), "Body should start with the H1, not the front matter.")

    # Inline brief must still pass strategy validation (Scenario 1: ordinary free article)
    payload = {"title": "示例", "content": "正文...", "payType": 4, "payAmount": 19.9}
    result = apply_article_strategy(brief, payload, is_update=False, cli_publish=False, cli_draft=False)
    assert_true(result["payType"] == 0, "Inline brief should force payType=0 for free_default.")
    assert_true(result["viewStatus"] is True, "Inline brief with publishIntent=public should produce viewStatus=true.")

    # Inline brief missing alternativesConsidered must be rejected (Scenario 8)
    bad_markdown = (
        "---\n"
        "title: \"坏示例\"\n"
        "_brief:\n"
        "  taskType: create_article\n"
        "  primaryGoal: asset_maintenance\n"
        "  targetAudience: \"读者\"\n"
        "  publishIntent: public\n"
        "  reasoning: \"测试\"\n"
        "  selectedAngle: \"角度\"\n"
        "---\n\n# 坏示例\n\n正文\n"
    )
    bad_meta, _ = parse_front_matter(bad_markdown)
    bad_brief = bad_meta["_brief"]
    expect_strategy_error(
        lambda: apply_article_strategy(bad_brief, {"title": "坏示例", "content": "正文"}, is_update=False, cli_publish=False, cli_draft=False),
        "alternativesConsidered",
    )

    # Front matter without _brief should still work (backward compat: meta has no _brief key)
    plain_markdown = "---\ntitle: \"无brief\"\nsort: \"AI实践\"\nlabel: \"自动化\"\n---\n\n# 无brief\n\n正文\n"
    plain_meta, _ = parse_front_matter(plain_markdown)
    assert_true("_brief" not in plain_meta, "Plain front matter should not have _brief key.")


def run_consistency_eval_suite() -> None:
    """Run static consistency checks between SKILL.md and Python code."""
    from skill_consistency_check import run_consistency_check

    if not run_consistency_check():
        raise AssertionError("Skill consistency check failed. See output above.")


def main() -> None:
    suites = [
        ("article", run_article_eval_suite),
        ("ops", run_ops_eval_suite),
        ("taxonomy", run_taxonomy_eval_suite),
        ("inline_brief", run_inline_brief_eval_suite),
        ("consistency", run_consistency_eval_suite),
    ]

    results: list[dict[str, str]] = []
    for name, suite in suites:
        try:
            suite()
            results.append({"suite": name, "status": "passed"})
        except Exception as exc:  # noqa: BLE001
            results.append({"suite": name, "status": "failed", "message": str(exc)})

    failed = [item for item in results if item["status"] != "passed"]
    print(json.dumps({"results": results}, ensure_ascii=False, indent=2))
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    import sys

    # Delegate to the unified CLI: run_strategy_evals.py -> poetize_cli.py eval
    sys.argv = [sys.argv[0].replace("run_strategy_evals.py", "poetize_cli.py"), "eval"] + sys.argv[1:]
    from poetize_cli import main
    main()

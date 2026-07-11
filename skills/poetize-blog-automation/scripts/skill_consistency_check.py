#!/usr/bin/env python3
"""Static consistency checker for the poetize-blog-automation skill.

Verifies that SKILL.md documentation stays in sync with the Python code:

  1. publish command flags  <-> SKILL.md "Publish flags reference" table
  2. manage subcommands      <-> SKILL.md "Manage subcommands reference" table
  3. front matter fields     <-> SKILL.md "Front matter field reference" table

Pure standard library only. Designed to be importable by run_strategy_evals.py
via ``run_consistency_check()`` and runnable standalone via ``main()``.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_MD_PATH = SCRIPT_DIR.parent / "SKILL.md"
POETIZE_CLI_PATH = SCRIPT_DIR / "poetize_cli.py"
PUBLISH_POST_PATH = SCRIPT_DIR / "publish_post.py"


# ---------------------------------------------------------------------------
# Source helpers
# ---------------------------------------------------------------------------

def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def extract_function_body(source: str, func_name: str) -> str:
    """Return the indented body text of a top-level ``def func_name(...)``.

    Captures the def line's indented block (lines starting with whitespace or
    blank lines) until the next non-indented line. Signatures are assumed to
    have no nested parentheses in their parameter list, which holds for every
    function this checker inspects.
    """
    pattern = re.compile(
        rf'^def {re.escape(func_name)}\([^)]*\)[^\n]*:\n'
        r'((?:(?:[ \t][^\n]*)?\n)*)',
        re.MULTILINE,
    )
    match = pattern.search(source)
    return match.group(1) if match else ""


# ---------------------------------------------------------------------------
# Code-side parsers
# ---------------------------------------------------------------------------

# Captures the --flag name (first string arg) and the remainder of the call up
# to the first closing paren. The flag name always precedes any ')' inside a
# help string, so name extraction is robust even when help text contains parens.
ADD_ARGUMENT_RE = re.compile(r'\.add_argument\(\s*["\'](--[\w-]+)["\']([^)]*)\)')
REQUIRED_RE = re.compile(r'required\s*=\s*True')
SUBPARSER_RE = re.compile(r'sub\.add_parser\(\s*["\']([^"\']+)["\']')
META_GET_RE = re.compile(r'meta\.get\(\s*["\']([^"\']+)["\']')
META_VALUE_RE = re.compile(r'meta_value\(\s*meta\s*,\s*["\']([^"\']+)["\']')
META_STRING_RE = re.compile(r'meta_string\(\s*meta\s*,\s*["\']([^"\']+)["\']')
TRUTHY_META_RE = re.compile(r'truthy_meta\(\s*meta\s*,\s*["\']([^"\']+)["\']')


def parse_publish_args(cli_source: str) -> dict[str, bool]:
    """Return {flag: required_bool} for every add_argument in add_publish_args.

    Global args (--base-url, --api-key) are added by add_global_args, not by
    add_publish_args, so they are naturally excluded from this set.
    """
    body = extract_function_body(cli_source, "add_publish_args")
    result: dict[str, bool] = {}
    for flag, rest in ADD_ARGUMENT_RE.findall(body):
        result[flag] = bool(REQUIRED_RE.search(rest))
    return result


def parse_manage_subcommands(cli_source: str) -> list[str]:
    """Return the list of subcommand names added in add_manage_subparsers."""
    body = extract_function_body(cli_source, "add_manage_subparsers")
    return SUBPARSER_RE.findall(body)


def parse_front_matter_code_reads(publish_source: str, cli_source: str) -> set[str]:
    """Return front matter field names read from ``meta`` in the publish flow.

    Covers ``build_payload`` (the primary front-matter-to-payload mapper) plus
    the helper functions it composes with: ``resolve_cover`` and
    ``ensure_payment_plugin_ready`` in publish_post.py, and ``resolve_brief``
    in poetize_cli.py (which reads the inline ``_brief`` block).

    Scanning these helpers is required to avoid false "ghost doc" reports for
    fields that build_payload delegates to them (cover, coverBlank, coverFile,
    coverStoreType, storeType, paymentPluginKey, paymentConfigFile, requirePaid,
    _brief). Internal image-upload helpers (which read undocumented niche
    fields like uploadLocalImages / markdownImageStoreType) are deliberately
    not scanned, so those implementation details do not generate noise.
    """
    fields: set[str] = set()
    for func_name in ("build_payload", "resolve_cover", "ensure_payment_plugin_ready"):
        body = extract_function_body(publish_source, func_name)
        for regex in (META_GET_RE, META_VALUE_RE, META_STRING_RE, TRUTHY_META_RE):
            fields.update(regex.findall(body))
    # resolve_brief lives in poetize_cli.py and reads the inline _brief block.
    body = extract_function_body(cli_source, "resolve_brief")
    for regex in (META_GET_RE, META_VALUE_RE, META_STRING_RE, TRUTHY_META_RE):
        fields.update(regex.findall(body))
    return fields


# ---------------------------------------------------------------------------
# Markdown-side parsers
# ---------------------------------------------------------------------------

BACKTICK_RE = re.compile(r'`([^`]+)`')


def split_markdown_row(line: str) -> list[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return [cell.strip() for cell in s.split("|")]


def is_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r':?-+:?', c) for c in cells)


def parse_markdown_tables(text: str) -> list[tuple[list[str], list[list[str]]]]:
    """Return [(header_cells, data_rows)] for every pipe table in the text."""
    tables: list[tuple[list[str], list[list[str]]]] = []
    lines = text.splitlines()
    i = 0
    n = len(lines)
    while i < n:
        if lines[i].lstrip().startswith("|"):
            block: list[str] = []
            while i < n and lines[i].lstrip().startswith("|"):
                block.append(lines[i])
                i += 1
            rows = [split_markdown_row(b) for b in block]
            rows = [r for r in rows if not is_separator_row(r)]
            if rows:
                tables.append((rows[0], rows[1:]))
        else:
            i += 1
    return tables


def find_table_by_header(
    tables: list[tuple[list[str], list[list[str]]]],
    expected_header_lower: list[str],
) -> tuple[list[str] | None, list[list[str]] | None]:
    for header, data in tables:
        if [h.lower() for h in header] == expected_header_lower:
            return header, data
    return None, None


def parse_publish_flags_doc(skill_md: str) -> list[str]:
    tables = parse_markdown_tables(skill_md)
    _, data = find_table_by_header(tables, ["flag", "purpose", "when to use"])
    flags: list[str] = []
    if data is None:
        return flags
    for row in data:
        if not row:
            continue
        # A cell may list multiple aliases, e.g. `--publish` / `--draft`.
        # Capture every backtick token and take its first whitespace-delimited
        # part as the flag name (handles `--markdown-file <path>`).
        for token in BACKTICK_RE.findall(row[0]):
            flags.append(token.split()[0])
    return flags


def parse_manage_subcommands_doc(skill_md: str) -> list[str]:
    tables = parse_markdown_tables(skill_md)
    _, data = find_table_by_header(tables, ["subcommand", "purpose", "key flags"])
    subs: list[str] = []
    if data is None:
        return subs
    for row in data:
        if not row:
            continue
        # A cell may list multiple related subcommands, e.g.
        # `theme-status` / `activate-theme`. Capture all of them.
        subs.extend(BACKTICK_RE.findall(row[0]))
    return subs


def parse_front_matter_doc(skill_md: str) -> list[str]:
    tables = parse_markdown_tables(skill_md)
    _, data = find_table_by_header(tables, ["field", "required", "default", "notes"])
    fields: list[str] = []
    if data is None:
        return fields
    for row in data:
        if not row:
            continue
        # First column may list aliases, e.g. `sort` or `sortId` / `articleSlug` / `slug`.
        fields.extend(BACKTICK_RE.findall(row[0]))
    return fields


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_publish_args(cli_source: str, skill_md: str) -> tuple[bool, dict]:
    code = parse_publish_args(cli_source)
    doc = parse_publish_flags_doc(skill_md)
    code_set = set(code)
    doc_set = set(doc)
    code_only = sorted(code_set - doc_set)
    doc_only = sorted(doc_set - code_set)
    passed = not code_only and not doc_only
    detail = {
        "code_count": len(code_set),
        "doc_count": len(doc_set),
        "code_only_missing_doc": code_only,
        "doc_only_ghost": doc_only,
        "code_required_flags": sorted(f for f, req in code.items() if req),
    }
    return passed, detail


def check_manage_subcommands(cli_source: str, skill_md: str) -> tuple[bool, dict]:
    code = parse_manage_subcommands(cli_source)
    doc = parse_manage_subcommands_doc(skill_md)
    code_set = set(code)
    doc_set = set(doc)
    code_only = sorted(code_set - doc_set)
    doc_only = sorted(doc_set - code_set)
    passed = not code_only and not doc_only
    detail = {
        "code_count": len(code_set),
        "doc_count": len(doc_set),
        "code_only_missing_doc": code_only,
        "doc_only_ghost": doc_only,
    }
    return passed, detail


def check_front_matter_fields(
    publish_source: str, cli_source: str, skill_md: str
) -> tuple[bool, dict]:
    code = parse_front_matter_code_reads(publish_source, cli_source)
    doc = parse_front_matter_doc(skill_md)
    doc_set = set(doc)
    code_only = sorted(code - doc_set)
    doc_only = sorted(doc_set - code)
    passed = not code_only and not doc_only
    detail = {
        "code_count": len(code),
        "doc_count": len(doc_set),
        "code_only_missing_doc": code_only,
        "doc_only_ghost": doc_only,
    }
    return passed, detail


def _print_check(name: str, passed: bool, detail: dict) -> None:
    status = "PASS" if passed else "FAIL"
    print(f"=== {name} === {status}")
    if not passed:
        if detail.get("code_only_missing_doc"):
            print("  代码里有但文档里没有 (missing from doc):")
            for item in detail["code_only_missing_doc"]:
                print(f"    - {item}")
        if detail.get("doc_only_ghost"):
            print("  文档里有但代码里没有 (ghost doc):")
            for item in detail["doc_only_ghost"]:
                print(f"    - {item}")
    print(f"  (code={detail.get('code_count')}, doc={detail.get('doc_count')})")


def run_consistency_check() -> bool:
    """Run all three consistency checks, print results, return all_passed.

    Importable by run_strategy_evals.py as an eval suite.
    """
    try:
        cli_source = read_text(POETIZE_CLI_PATH)
        publish_source = read_text(PUBLISH_POST_PATH)
        skill_md = read_text(SKILL_MD_PATH)
    except OSError as exc:
        print(f"FAIL: cannot read source file: {exc}", file=sys.stderr)
        results = [
            {"name": "publish_args", "status": "failed"},
            {"name": "manage_subcommands", "status": "failed"},
            {"name": "front_matter_fields", "status": "failed"},
        ]
        print(json.dumps({"checks": results, "all_passed": False}, ensure_ascii=False))
        return False

    checks = [
        ("publish_args", check_publish_args(cli_source, skill_md)),
        ("manage_subcommands", check_manage_subcommands(cli_source, skill_md)),
        (
            "front_matter_fields",
            check_front_matter_fields(publish_source, cli_source, skill_md),
        ),
    ]

    results: list[dict[str, str]] = []
    all_passed = True
    for name, (passed, detail) in checks:
        _print_check(name, passed, detail)
        results.append({"name": name, "status": "passed" if passed else "failed"})
        if not passed:
            all_passed = False

    print()
    print(json.dumps({"checks": results, "all_passed": all_passed}, ensure_ascii=False))
    return all_passed


def main() -> None:
    all_passed = run_consistency_check()
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()

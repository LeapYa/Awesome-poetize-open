#!/usr/bin/env python3
"""AI dry-run test: validate that real LLMs can correctly understand SKILL.md.

Calls an OpenAI-compatible LLM API with the full SKILL.md content plus a
user intent, and verifies the LLM generates correct publish / manage
commands and front matter. This is an "AI perspective" test - it does
NOT actually publish articles, it only verifies the AI's understanding of
SKILL.md.

Pure Python stdlib + ``requests``. Importable via ``run_ai_dry_run()`` for
integration with run_strategy_evals.py, and runnable standalone via
``main()``.

Exit codes:
  0 - all scenarios passed
  1 - at least one scenario failed (LLM produced wrong commands / front matter)
  2 - API / network failure (tests could not run, not counted as test failure)
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

import requests

# Force UTF-8 on stdout/stderr so streamed Chinese content from the LLM does
# not get mangled into mojibake (e.g. "Vue 3 组合式 API" -> "Vue 3 Ã§Â»Ã¥Â¼ API").
# Without this, Python on POSIX defaults to the C locale and encodes via
# ascii/latin-1, producing UnicodeEncodeError or garbage output.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    # reconfigure() only exists on TextIOWrapper-backed streams; fall back to
    # wrapping stdout/stderr manually for environments where it is unavailable.
    import io as _io
    if hasattr(sys.stdout, "buffer"):
        sys.stdout = _io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "buffer"):
        sys.stderr = _io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
SKILL_MD_PATH = SKILL_DIR / "SKILL.md"
BASE_DIR = str(SKILL_DIR)

# Make sibling modules importable when running standalone or from eval suite.
sys.path.insert(0, str(SCRIPT_DIR))
from publish_post import parse_front_matter  # noqa: E402
import skill_consistency_check  # noqa: E402


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# All endpoint / model / key values come from CLI args or env vars — nothing
# hardcoded here, otherwise it leaks into the ClawHub publish artifact.
DEFAULT_BASE_URL = ""
DEFAULT_MODEL = ""
DEFAULT_API_KEY = ""
DEFAULT_TIMEOUT = 180
DEFAULT_TEMPERATURE = 0.3
# Reasoning models spend tokens on hidden reasoning; without an explicit
# max_tokens budget they may return empty content. 16000 leaves room for
# reasoning + a full article + JSON wrapper.
DEFAULT_MAX_TOKENS = 16000
# The reasoning model intermittently returns empty content (all tokens consumed
# by hidden reasoning) and the API gateway occasionally returns 502/timeouts.
# Retry enough times to ride over both kinds of flakiness.
DEFAULT_MAX_RETRIES = 5
DEFAULT_RETRY_DELAY = 3.0

# The 8 required brief fields (article-brief.template.json + blog_strategy.py).
BRIEF_REQUIRED_FIELDS = [
    "taskType",
    "primaryGoal",
    "targetAudience",
    "publishIntent",
    "reasoning",
    "selectedAngle",
    "alternativesConsidered",
    # monetizationIntent is NOT here: it has a code-level default (free_default)
    # in blog_strategy.py, so omitting it is valid and the dry-run test should
    # not flag it as missing.
]

# Legitimate flags for the `publish` command (from poetize_cli.py add_publish_args
# plus add_global_args). Hard-coded so the test does not need to import argparse
# internals; kept in sync via skill_consistency_check.py.
PUBLISH_FLAGS = {
    "--base-url", "--api-key",
    "--markdown-file", "--article-id", "--brief-file", "--stdin-brief",
    "--publish", "--draft", "--cover-file", "--payment-plugin-key",
    "--payment-config-file", "--require-paid",
    "--allow-create-taxonomy", "--allow-create-sort", "--allow-create-label",
    "--print-payload", "--wait", "--poll-interval", "--timeout", "--force",
}

# Legitimate flags for `manage hide-article` (add_article_target_args + hide-article args).
MANAGE_HIDE_FLAGS = {
    "--base-url", "--api-key",
    "--article-id", "--article-slug", "--article-title-exact",
    "--brief-file", "--stdin-brief", "--password", "--tips",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage update-section`.
MANAGE_UPDATE_SECTION_FLAGS = {
    "--base-url", "--api-key",
    "--article-id", "--heading", "--action", "--content-file",
    "--new-heading-level", "--skip-ai-translation",
    "--heading-index", "--dry-run",
    "--brief-file", "--stdin-brief",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage get-translation` (read-only, no brief).
MANAGE_GET_TRANSLATION_FLAGS = {
    "--base-url", "--api-key",
    "--article-id", "--language",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage list-translation-languages` (read-only, no brief).
MANAGE_LIST_TRANSLATION_LANGUAGES_FLAGS = {
    "--base-url", "--api-key",
    "--article-id",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage save-translation`.
MANAGE_SAVE_TRANSLATION_FLAGS = {
    "--base-url", "--api-key",
    "--article-id", "--language", "--title", "--content-file",
    "--summary", "--brief-file", "--stdin-brief",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage delete-translation`.
MANAGE_DELETE_TRANSLATION_FLAGS = {
    "--base-url", "--api-key",
    "--article-id", "--language",
    "--brief-file", "--stdin-brief",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate flags for `manage regenerate-translation`.
MANAGE_REGENERATE_TRANSLATION_FLAGS = {
    "--base-url", "--api-key",
    "--article-id",
    "--brief-file", "--stdin-brief",
    "--wait", "--poll-interval", "--timeout",
}

# Legitimate front matter field names; populated from skill_consistency_check
# so the test stays aligned with the documented field reference table.
FRONT_MATTER_FIELDS: set[str] = set()

# Phrases that indicate the LLM is exploring / confused instead of acting on
# the injected SKILL.md. If any of these appear in the raw response, the
# exploration_behavior check is marked as a warning/failure.
EXPLORATION_PATTERNS = [
    "我需要先查看",
    "让我运行 --help",
    "我需要读取 poetize_cli.py",
    "让我先看看",
    "需要先了解",
    "让我查看",
    "我先读取",
    "查看源代码",
    "查看 poetize_cli",
    "查看 SKILL",
    "让我探索",
    "先运行 --help",
    "让我看一下",
    "let me run --help",
    "i need to check",
    "let me look at",
    "i need to read the",
]


# ---------------------------------------------------------------------------
# Scenarios
# ---------------------------------------------------------------------------

SCENARIOS: list[dict[str, Any]] = [
    {
        "name": "publish_draft",
        "title": "Scenario 1: publish draft",
        "user_intent": "帮我写一篇关于 RAG 检索增强生成的技术文章,保存为草稿",
        "command_kind": "publish",
        "require_markdown_content": True,
        "require_brief": True,
        "expect_brief_publish_intent": "private",  # private or draft
    },
    {
        "name": "publish_public",
        "title": "Scenario 2: publish public",
        "user_intent": "帮我发布一篇关于 Vue 3 组合式 API 的教程,公开发布",
        "command_kind": "publish",
        "require_markdown_content": True,
        "require_brief": True,
        "expect_brief_publish_intent": "public",
    },
    {
        "name": "update_article",
        "title": "Scenario 3: update article",
        "user_intent": "帮我重写 ID 为 123 的文章全文,内容方向保持不变但需要更深入的技术细节",
        "command_kind": "publish",
        "scenario_flag_check": ("article_id_flag", "--article-id 123"),
        "require_markdown_content": True,
        "require_brief": True,
        "expect_brief_publish_intent": None,
    },
    {
        "name": "hide_article",
        "title": "Scenario 4: hide article",
        "user_intent": "帮我把 ID 为 456 的文章隐藏掉",
        "command_kind": "manage",
        "scenario_flag_check": ("hide_article_subcommand", "manage hide-article"),
        "extra_command_check": ("article_id_456", "--article-id 456"),
        "require_markdown_content": False,
        "require_brief": False,
        "expect_brief_publish_intent": None,
    },
    {
        "name": "update_section",
        "title": "Scenario 5: update section",
        "user_intent": '帮我把 ID 为 789 的文章中"性能优化"这一节的内容替换为最新的优化方案,跳过自动翻译',
        "command_kind": "manage",
        "scenario_flag_check": ("update_section_subcommand", "manage update-section"),
        "extra_command_check": ("article_id_789", "--article-id 789"),
        "require_markdown_content": False,
        "require_content_file": True,
        "require_brief": False,
        "expect_brief_publish_intent": None,
    },
    {
        "name": "get_translation",
        "title": "Scenario 6: get translation",
        "user_intent": "帮我查看 ID 为 123 的文章的英文翻译",
        "command_kind": "manage",
        "scenario_flag_check": ("get_translation_subcommand", "manage get-translation"),
        "extra_command_check": ("article_id_123", "--article-id 123"),
        "require_markdown_content": False,
        "require_brief": False,
        "expect_brief_publish_intent": None,
    },
    {
        "name": "save_translation",
        "title": "Scenario 7: save translation",
        "user_intent": "帮我为 ID 为 123 的文章保存一份手动编辑的英文翻译",
        "command_kind": "manage",
        "scenario_flag_check": ("save_translation_subcommand", "manage save-translation"),
        "extra_command_check": ("article_id_123_translation", "--article-id 123"),
        "require_markdown_content": False,
        "require_content_file": True,
        "require_brief": False,
        "expect_brief_publish_intent": None,
    },
]


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    "你是一个博客运营助手。请根据以下 SKILL.md 指令文档,完成用户的请求。"
    "只输出你要执行的命令和你要写的 markdown 文件内容,不要实际执行。"
)


def build_user_prompt(skill_md_content: str, user_intent: str) -> str:
    """Build the user prompt: full SKILL.md content + user intent.

    ``{baseDir}`` is replaced with the actual skill folder path so that
    LLM-generated commands use full, valid paths (matching what a real
    Agent runtime would see after skill injection).
    """
    skill_text = skill_md_content.replace("{baseDir}", BASE_DIR)
    return (
        f"以下是 SKILL.md 指令文档:\n\n"
        f"{skill_text}\n\n"
        f"---\n\n"
        f"用户请求: {user_intent}\n\n"
        f"请根据 SKILL.md 完成用户请求。请严格以 JSON 格式返回,不要附加多余说明:\n"
        f'{{"commands": ["poetize-blog publish ..."], '
        f'"markdown_content": "---\\n...\\n---\\n# 标题\\n正文"}}\n'
        f"要求:\n"
        f"1. commands 数组里每个命令必须通过 `poetize-blog` wrapper 调用 CLI(SKILL.md 规定的调用方式),允许用管道(如 echo '...' | poetize-blog ...)向 CLI 输送 stdin。\n"
        f"2. 如果场景需要写文章,markdown_content 必须包含 front matter 和正文;对于 update-section / save-translation 等通过 --content-file 提供内容的命令,可把内容写入临时文件并在 commands 里用 --content-file 引用,此时 markdown_content 可为空字符串。\n"
        f"3. 不要实际执行命令,只输出你打算执行的命令和打算写的 markdown 内容。"
    )


# ---------------------------------------------------------------------------
# LLM call
# ---------------------------------------------------------------------------

class LlmApiError(RuntimeError):
    """Raised when the LLM API call itself fails (network / auth / parsing).

    These failures are infrastructure problems, not test failures, so the
    caller can exit with code 2 instead of marking the suite as failed.
    """


def call_llm(
    api_key: str,
    base_url: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    timeout: int = DEFAULT_TIMEOUT,
    temperature: float = DEFAULT_TEMPERATURE,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    max_retries: int = DEFAULT_MAX_RETRIES,
    retry_delay: float = DEFAULT_RETRY_DELAY,
    stream: bool = True,
    on_chunk: Any = None,
) -> str:
    """Call an OpenAI-compatible chat completions endpoint.

    Returns the assistant message content as a string. Raises ``LlmApiError``
    on network, auth, or response-shape failures so callers can distinguish
    infrastructure problems from test-result failures.

    ``max_tokens`` is sent explicitly because reasoning models spend their
    completion allowance on hidden reasoning and may return empty content
    without an explicit output budget.

    When ``stream=True`` (default), the response is consumed as SSE chunks.
    Each delta content fragment is appended immediately and, if ``on_chunk``
    is provided, passed to the callback for real-time progress display.
    Streaming lets the caller see partial output while the model is still
    generating, avoiding long blocking waits.

    The reasoning model also intermittently returns empty content (all tokens
    consumed by hidden reasoning). We retry up to ``max_retries`` times when
    that happens before giving up.
    """
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload: dict[str, Any] = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": stream,
    }

    last_error: str = ""
    for attempt in range(1, max_retries + 1):
        try:
            response = requests.post(
                url, headers=headers, json=payload, timeout=timeout, stream=stream
            )
            response.raise_for_status()
        except requests.RequestException as exc:
            last_error = f"HTTP request to {url} failed: {exc}"
            if attempt < max_retries:
                time.sleep(retry_delay)
                continue
            raise LlmApiError(last_error) from exc

        if stream:
            # ---- Streaming mode: accumulate SSE chunks ----
            # OpenAI-compatible streaming responses return SSE text without an
            # explicit charset in Content-Type. requests therefore defaults
            # response.encoding to ISO-8859-1, which would decode the UTF-8
            # bytes wrong and produce mojibake (e.g. "Vue 3 组合式 API" ->
            # "Vue 3 Ã§Â»Ã¥Â¼ API"). Force UTF-8 before iterating.
            response.encoding = "utf-8"
            collected: list[str] = []
            try:
                for raw_line in response.iter_lines(decode_unicode=True):
                    if not raw_line:
                        continue
                    line = raw_line.strip()
                    if not line.startswith("data:"):
                        continue
                    data_str = line[len("data:"):].strip()
                    if data_str == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data_str)
                    except ValueError:
                        continue
                    choices = chunk.get("choices") or []
                    if not choices:
                        continue
                    delta = choices[0].get("delta") or {}
                    piece = delta.get("content")
                    if isinstance(piece, str) and piece:
                        collected.append(piece)
                        if on_chunk is not None:
                            on_chunk(piece)
            except requests.RequestException as exc:
                last_error = f"Stream interrupted: {exc}"
                if attempt < max_retries:
                    time.sleep(retry_delay)
                    continue
                raise LlmApiError(last_error) from exc

            content = "".join(collected)
            if content.strip():
                return content

            last_error = (
                f"LLM stream produced no content (attempt {attempt}/{max_retries})."
            )
            if attempt < max_retries:
                time.sleep(retry_delay)
                continue
        else:
            # ---- Non-streaming fallback ----
            try:
                data = response.json()
            except ValueError as exc:
                last_error = f"LLM response is not valid JSON: {exc}"
                if attempt < max_retries:
                    time.sleep(retry_delay)
                    continue
                raise LlmApiError(last_error) from exc

            choices = data.get("choices") or []
            if not choices:
                last_error = f"LLM response has no choices: {data}"
                if attempt < max_retries:
                    time.sleep(retry_delay)
                    continue
                raise LlmApiError(last_error)

            message = choices[0].get("message") or {}
            content = message.get("content")
            if isinstance(content, str) and content.strip():
                return content

            last_error = (
                f"LLM response has no string content (attempt {attempt}/{max_retries}, "
                f"completion_tokens={data.get('usage', {}).get('completion_tokens')}): {data}"
            )
            if attempt < max_retries:
                time.sleep(retry_delay)
                continue

    raise LlmApiError(last_error)


# ---------------------------------------------------------------------------
# JSON extraction (LLM output is not always clean JSON)
# ---------------------------------------------------------------------------

def extract_json(content: str) -> dict[str, Any] | None:
    """Extract a JSON object from the LLM response.

    Tries, in order:
      1. Parse the entire content as JSON.
      2. Extract a ```json ... ``` fenced code block.
      3. Extract any ``` ... ``` fenced code block.
      4. Scan for the first balanced ``{...}`` substring.
    Returns the parsed dict, or ``None`` if nothing parseable was found.
    """
    if not content:
        return None

    # Try 1: whole content
    try:
        obj = json.loads(content)
        if isinstance(obj, dict):
            return obj
    except json.JSONDecodeError:
        pass

    # Try 2: ```json ... ``` fenced block
    match = re.search(r"```json\s*(\{.*?\})\s*```", content, re.DOTALL)
    if match:
        try:
            obj = json.loads(match.group(1))
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            pass

    # Try 3: any ``` ... ``` fenced block containing a JSON object
    match = re.search(r"```[a-zA-Z]*\s*(\{.*?\})\s*```", content, re.DOTALL)
    if match:
        try:
            obj = json.loads(match.group(1))
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            pass

    # Try 4: first balanced {...} substring
    start = content.find("{")
    if start == -1:
        return None
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(content)):
        ch = content[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    candidate = content[start : i + 1]
                    try:
                        obj = json.loads(candidate)
                        if isinstance(obj, dict):
                            return obj
                    except json.JSONDecodeError:
                        break
    return None


def extract_heredoc_markdown(commands: list[str]) -> str:
    """Recover article Markdown when the LLM writes it via a heredoc.

    Some models write the article into a file with ``cat > file << 'EOF' ...
    EOF`` instead of the ``markdown_content`` JSON field. Return the first
    heredoc body that looks like Markdown front matter (starts with ``---``),
    so front-matter / brief checks can still run. Returns ``""`` if none.
    """
    for cmd in commands:
        for m in re.finditer(r"<<\s*['\"]?(\w+)['\"]?\n(.*?)\n\1\b", cmd, re.DOTALL):
            body = m.group(2)
            if body.lstrip().startswith("---"):
                return body
    return ""


# ---------------------------------------------------------------------------
# Command helpers
# ---------------------------------------------------------------------------

CLI_INVOCATION_RE = re.compile(r"(poetize_cli\.py|poetize-blog(?![-\w]))")


def _cli_subcommand_re(subcommand: str) -> re.Pattern[str]:
    """Regex matching the CLI invocation immediately followed by a subcommand.

    Accepts the documented ``poetize-blog`` wrapper as well as the
    ``poetize-blog.sh`` / ``poetize-blog.py`` shell-script forms that some LLMs
    emit, plus the legacy ``poetize_cli.py`` form. The optional script extension
    is consumed before the negative lookahead so ``poetize-blogosphere`` or other
    ``poetize-blog*`` tails are still NOT matched.
    """
    return re.compile(
        rf"(?:poetize_cli\.py|poetize-blog(?:\.sh|\.py|\.exe)?(?![-\w]))\s+{subcommand}\b"
    )


def parse_command_flags(command: str) -> list[str]:
    """Return all --flag tokens from the CLI segment of a shell command.

    If the command contains a pipe/heredoc, only the segment that invokes the
    CLI is parsed, so flags inside upstream pipe content or heredoc bodies are
    not falsely validated.
    """
    if not CLI_INVOCATION_RE.search(command):
        return []
    # Find the segment containing the CLI invocation — split on pipe/heredoc
    # boundaries and take the segment that contains it.
    segments = re.split(r'\||<<\s*\w+|>>?', command)
    cli_segment = next((seg for seg in segments if CLI_INVOCATION_RE.search(seg)), command)
    tokens = cli_segment.split()
    return [t for t in tokens if t.startswith("--")]


def command_starts_with_python(command: str) -> bool:
    stripped = command.lstrip()
    return stripped.startswith("python ") or stripped.startswith("python3 ") or stripped == "python" or stripped == "python3"


def command_contains_poetize_cli(command: str) -> bool:
    return bool(CLI_INVOCATION_RE.search(command))


def command_is_publish(command: str) -> bool:
    return bool(_cli_subcommand_re("publish").search(command))


def command_is_manage_hide(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+hide-article").search(command))


def command_is_manage_update_section(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+update-section").search(command))


def command_is_manage_get_translation(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+get-translation").search(command))


def command_is_manage_list_translation_languages(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+list-translation-languages").search(command))


def command_is_manage_save_translation(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+save-translation").search(command))


def command_is_manage_delete_translation(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+delete-translation").search(command))


def command_is_manage_regenerate_translation(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+regenerate-translation").search(command))


def command_is_manage_update_article(command: str) -> bool:
    return bool(_cli_subcommand_re(r"manage\s+update-article").search(command))


def command_is_async_long_running(command: str) -> bool:
    """Return True for commands that trigger a backend async job or AI translation.

    These are the only commands where ``--wait`` is meaningful (though still
    optional). Synchronous commands (reads, ``save-translation``,
    ``delete-translation``, ``update-section --skip-ai-translation``, etc.)
    must NOT carry ``--wait``.
    """
    if command_is_publish(command):
        return True
    if command_is_manage_hide(command):
        return True
    if command_is_manage_regenerate_translation(command):
        return True
    if command_is_manage_update_article(command):
        return True
    if command_is_manage_update_section(command):
        # update-section is async only when AI translation is NOT skipped.
        if command_has_flag(command, "--skip-ai-translation"):
            return False
        return True
    return False


def command_has_flag(command: str, flag: str) -> bool:
    tokens = command.split()
    return flag in tokens


def command_has_flag_value(command: str, flag: str, value: str) -> bool:
    """Match ``--flag value`` or ``--flag=value`` with a word boundary on value."""
    pattern = rf"{re.escape(flag)}(?:\s+|=){re.escape(value)}\b"
    return bool(re.search(pattern, command))


def command_has_substring(command: str, substring: str) -> bool:
    """Literal substring check, used for multi-token patterns like 'manage hide-article'."""
    return substring in command


# ---------------------------------------------------------------------------
# Check result
# ---------------------------------------------------------------------------

class CheckResult:
    """A single check outcome. ``tag`` controls how it is rendered."""

    def __init__(self, name: str, passed: bool, message: str = "", tag: str | None = None):
        self.name = name
        self.passed = passed
        self.message = message
        # tag overrides the default PASS/FAIL label (e.g. WARN for exploration).
        self.tag = tag

    def render(self) -> str:
        if self.tag is not None:
            label = self.tag
        else:
            label = "PASS" if self.passed else "FAIL"
        if self.message:
            return f"  [{label}] {self.name}: {self.message}"
        return f"  [{label}] {self.name}"

    def to_dict(self) -> dict[str, Any]:
        """Serialize for JSON output so multi-run summaries can show which
        check failed without re-running in verbose mode."""
        return {
            "name": self.name,
            "passed": self.passed,
            "message": self.message,
            "tag": self.tag,
        }


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_exploration_behavior(raw_content: str) -> CheckResult:
    """Detect exploration phrases that indicate the LLM is confused.

    Always rendered with the ``WARN`` tag. When no pattern is found the
    check is treated as passed ("none detected"); when a pattern is found
    the check is treated as failed but still rendered as WARN to signal
    that it is a soft signal rather than a hard schema violation.
    """
    for pat in EXPLORATION_PATTERNS:
        if pat in raw_content:
            return CheckResult(
                "exploration_behavior",
                passed=False,
                message=f"detected exploration pattern: '{pat}'",
                tag="WARN",
            )
    return CheckResult(
        "exploration_behavior",
        passed=True,
        message="none detected",
        tag="WARN",
    )


def check_command_format(commands: list[str]) -> CheckResult:
    """Validate commands are non-empty and at least one invokes poetize_cli.py.

    Tolerates two common LLM patterns that the old strict check rejected:

    1. **Pipe commands**: ``echo '...' | python poetize_cli.py ...`` — the
       upstream segment (echo/cat/heredoc) feeds stdin to the CLI and should
       not be rejected just because the command does not start with
       ``python``. We only require that ``python`` appears somewhere in the
       command so the CLI is actually invoked via the Python interpreter.
    2. **Helper commands**: ``python -c "import json; ..."`` or ``echo > file``
       — these build temp files (brief JSON, markdown content) and are not
       CLI invocations, so they are skipped as long as at least one real CLI
       command exists in the list.
    """
    if not commands:
        return CheckResult("command_format", False, "no commands returned")

    has_cli_command = False
    for cmd in commands:
        if command_contains_poetize_cli(cmd):
            # Accept the `poetize-blog` wrapper form (no python needed) and the
            # legacy `python ... poetize_cli.py` form. Only the legacy form
            # requires python to be present.
            if "poetize_cli.py" in cmd and "python" not in cmd:
                return CheckResult(
                    "command_format",
                    False,
                    f"poetize_cli.py invoked without python: {cmd}",
                )
            has_cli_command = True
        # Non-CLI commands (python -c helpers, echo, cat, etc.) are skipped.

    if not has_cli_command:
        return CheckResult("command_format", False, "no poetize_cli.py command found")
    return CheckResult("command_format", True, "valid")


def check_flags_valid(commands: list[str]) -> CheckResult:
    """Ensure every flag is in the legal flag set for its command kind."""
    bad: list[str] = []
    for cmd in commands:
        flags = parse_command_flags(cmd)
        if command_is_manage_hide(cmd):
            allowed = MANAGE_HIDE_FLAGS
        elif command_is_manage_update_section(cmd):
            allowed = MANAGE_UPDATE_SECTION_FLAGS
        elif command_is_manage_get_translation(cmd):
            allowed = MANAGE_GET_TRANSLATION_FLAGS
        elif command_is_manage_list_translation_languages(cmd):
            allowed = MANAGE_LIST_TRANSLATION_LANGUAGES_FLAGS
        elif command_is_manage_save_translation(cmd):
            allowed = MANAGE_SAVE_TRANSLATION_FLAGS
        elif command_is_manage_delete_translation(cmd):
            allowed = MANAGE_DELETE_TRANSLATION_FLAGS
        elif command_is_manage_regenerate_translation(cmd):
            allowed = MANAGE_REGENERATE_TRANSLATION_FLAGS
        elif command_is_publish(cmd):
            allowed = PUBLISH_FLAGS
        else:
            # Unknown command kind - skip flag validation (still a valid python ... call).
            continue
        for f in flags:
            base_flag = f.split("=")[0]
            if base_flag not in allowed:
                bad.append(f)
    if bad:
        return CheckResult("flags_valid", False, f"invalid flags: {bad}")
    return CheckResult("flags_valid", True, "all valid")


def check_wait_flag_usage(commands: list[str]) -> CheckResult:
    """Verify ``--wait`` is only added to async long-running commands.

    Per SKILL.md, ``--wait`` is an optional convenience flag for async
    commands (publish, hide-article, update-article, update-section without
    ``--skip-ai-translation``, regenerate-translation). Adding it or not on
    those commands is fine. But adding it to a synchronous command (reads,
    save-translation, delete-translation, update-section
    ``--skip-ai-translation``, config, smoke-test, etc.) is a misuse that
    indicates the Agent did not understand the flag's purpose.
    """
    misused: list[str] = []
    for cmd in commands:
        if not command_contains_poetize_cli(cmd):
            continue  # Skip non-CLI helper commands (e.g. python -c temp file).
        if command_has_flag(cmd, "--wait") and not command_is_async_long_running(cmd):
            misused.append(cmd)
    if misused:
        return CheckResult(
            "wait_flag_usage",
            False,
            f"--wait misused on synchronous command(s): {misused}",
        )
    return CheckResult(
        "wait_flag_usage",
        True,
        "--wait only on async commands (or absent)",
    )


def check_markdown_file_flag(commands: list[str]) -> CheckResult:
    """publish commands must include --markdown-file."""
    publish_cmds = [c for c in commands if command_is_publish(c)]
    if not publish_cmds:
        return CheckResult("markdown_file_flag", False, "no publish command found")
    missing = [c for c in publish_cmds if not command_has_flag(c, "--markdown-file")]
    if missing:
        return CheckResult("markdown_file_flag", False, "publish command missing --markdown-file")
    return CheckResult("markdown_file_flag", True, "present")


def check_content_file_flag(commands: list[str]) -> CheckResult:
    """update-section / save-translation supply body content via --content-file."""
    ok = any(command_has_flag(c, "--content-file") for c in commands)
    return CheckResult("content_file_flag", ok, "present" if ok else "missing '--content-file'")


def check_scenario_flag(commands: list[str], scenario: dict[str, Any]) -> CheckResult:
    """Scenario-specific flag / subcommand check."""
    check_name, expected = scenario["scenario_flag_check"]
    # For multi-token substrings like 'manage hide-article' or '--article-id 123',
    # use a substring check; for single flags like '--draft' / '--publish',
    # use a token check so we don't false-match '--draft-mode'.
    if " " in expected:
        ok = any(command_has_substring(c, expected) for c in commands)
    else:
        ok = any(command_has_flag(c, expected) for c in commands)
    return CheckResult(
        check_name,
        ok,
        "present" if ok else f"missing '{expected}'",
    )


def check_extra_command(commands: list[str], extra: tuple[str, str]) -> CheckResult:
    """Extra scenario-specific command check (e.g. --article-id 456 for hide)."""
    check_name, expected = extra
    ok = any(command_has_substring(c, expected) for c in commands)
    return CheckResult(
        check_name,
        ok,
        f"'{expected}' present" if ok else f"missing '{expected}'",
    )


def check_brief_required_fields(meta: dict[str, Any]) -> tuple[CheckResult, dict[str, Any] | None]:
    """Validate _brief block exists and contains all 8 required fields."""
    brief = meta.get("_brief")
    if not isinstance(brief, dict) or not brief:
        return (
            CheckResult("brief_required_fields", False, "_brief block missing or empty"),
            None,
        )
    missing = [f for f in BRIEF_REQUIRED_FIELDS if f not in brief]
    if missing:
        return (
            CheckResult("brief_required_fields", False, f"missing fields: {missing}"),
            brief,
        )
    return (
        CheckResult("brief_required_fields", True, "all 8 present"),
        brief,
    )


def check_brief_publish_intent(brief: dict[str, Any], expected: str) -> CheckResult:
    """Validate _brief.publishIntent matches the scenario expectation."""
    actual = brief.get("publishIntent")
    if expected == "private":
        # Drafts may use either 'private' or 'draft' as the intent token.
        ok = actual in ("private", "draft")
    else:
        ok = actual == expected
    if ok:
        return CheckResult("brief_publish_intent", True, f"publishIntent={actual!r}")
    return CheckResult(
        "brief_publish_intent",
        False,
        f"publishIntent={actual!r} (expected ~{expected!r})",
    )


def check_no_redundancy(meta: dict[str, Any], brief: dict[str, Any]) -> CheckResult:
    """viewStatus + _brief.publishIntent and payType + _brief.monetizationIntent are redundant."""
    view_redundant = "viewStatus" in meta and "publishIntent" in brief
    pay_redundant = "payType" in meta and "monetizationIntent" in brief
    if view_redundant and pay_redundant:
        return CheckResult("no_redundancy", False, "viewStatus AND payType both declared alongside _brief")
    if view_redundant:
        return CheckResult("no_redundancy", False, "viewStatus declared alongside _brief.publishIntent")
    if pay_redundant:
        return CheckResult("no_redundancy", False, "payType declared alongside _brief.monetizationIntent")
    return CheckResult("no_redundancy", True, "viewStatus and payType not declared")


def check_front_matter_fields_valid(meta: dict[str, Any]) -> CheckResult:
    """All front matter keys must be in the documented field reference set."""
    if not FRONT_MATTER_FIELDS:
        # Could not load field set - skip rather than false-fail.
        return CheckResult("front_matter_fields_valid", True, "field set unavailable, skipped")
    unknown = [k for k in meta.keys() if k not in FRONT_MATTER_FIELDS and k != "_brief"]
    if unknown:
        return CheckResult("front_matter_fields_valid", False, f"unknown fields: {unknown}")
    return CheckResult("front_matter_fields_valid", True, "all valid")


# ---------------------------------------------------------------------------
# Scenario validation
# ---------------------------------------------------------------------------

def validate_scenario(
    scenario: dict[str, Any],
    raw_content: str,
    parsed: dict[str, Any] | None,
) -> tuple[list[CheckResult], dict[str, Any]]:
    """Run all applicable checks for a scenario."""
    checks: list[CheckResult] = []

    # Exploration behavior is always checked against the raw response.
    checks.append(check_exploration_behavior(raw_content))

    commands: list[str] = []
    markdown_content = ""
    if parsed is not None:
        cmds = parsed.get("commands")
        if isinstance(cmds, list):
            commands = [str(c) for c in cmds if isinstance(c, str) and c.strip()]
        md = parsed.get("markdown_content")
        if isinstance(md, str):
            markdown_content = md

    # Fallback: LLMs may write the article into a file via a heredoc
    # (cat > file << 'EOF' ... EOF) instead of the markdown_content field.
    if not markdown_content:
        markdown_content = extract_heredoc_markdown(commands)

    # Command-level checks
    checks.append(check_command_format(commands))
    checks.append(check_flags_valid(commands))
    checks.append(check_wait_flag_usage(commands))

    if scenario["command_kind"] == "publish":
        checks.append(check_markdown_file_flag(commands))

    if scenario.get("scenario_flag_check"):
        checks.append(check_scenario_flag(commands, scenario))

    extra = scenario.get("extra_command_check")
    if extra:
        checks.append(check_extra_command(commands, extra))

    if scenario.get("require_content_file"):
        checks.append(check_content_file_flag(commands))

    # Front matter / brief checks (only for scenarios that need markdown content)
    if scenario["require_markdown_content"]:
        if not markdown_content:
            checks.append(CheckResult("markdown_content_present", False, "no markdown_content returned"))
        else:
            meta, _body = parse_front_matter(markdown_content)
            if scenario.get("require_brief"):
                brief_check, brief = check_brief_required_fields(meta)
                checks.append(brief_check)
                if brief is not None:
                    expected_intent = scenario.get("expect_brief_publish_intent")
                    if expected_intent:
                        checks.append(check_brief_publish_intent(brief, expected_intent))
                    checks.append(check_no_redundancy(meta, brief))
            checks.append(check_front_matter_fields_valid(meta))

    passed_count = sum(1 for c in checks if c.passed)
    summary = {
        "name": scenario["name"],
        "status": "passed" if passed_count == len(checks) else "failed",
        "checks_passed": passed_count,
        "checks_total": len(checks),
        "checks": [c.to_dict() for c in checks],
    }
    return checks, summary


# ---------------------------------------------------------------------------
# Front matter field set loader
# ---------------------------------------------------------------------------

def _init_front_matter_fields() -> None:
    """Populate FRONT_MATTER_FIELDS from the documented field reference table."""
    global FRONT_MATTER_FIELDS
    try:
        skill_md = SKILL_MD_PATH.read_text(encoding="utf-8")
        FRONT_MATTER_FIELDS = set(skill_consistency_check.parse_front_matter_doc(skill_md))
    except Exception:
        FRONT_MATTER_FIELDS = set()


# ---------------------------------------------------------------------------
# Main runner
# ---------------------------------------------------------------------------

def run_ai_dry_run(
    api_key: str,
    base_url: str,
    model: str,
    skill_md_path: Path | None = None,
    timeout: int = DEFAULT_TIMEOUT,
    temperature: float = DEFAULT_TEMPERATURE,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    verbose: bool = True,
) -> bool:
    """Run all AI dry-run scenarios against a real LLM.

    Returns ``True`` if every scenario passed. Raises ``LlmApiError`` if the
    LLM API itself is unreachable (network / auth), so callers can treat
    infrastructure failures separately from test failures.
    """
    skill_md_path = skill_md_path or SKILL_MD_PATH
    _init_front_matter_fields()
    skill_md_content = skill_md_path.read_text(encoding="utf-8")

    scenario_results: list[dict[str, Any]] = []
    all_passed = True

    for scenario in SCENARIOS:
        user_prompt = build_user_prompt(skill_md_content, scenario["user_intent"])
        if verbose:
            print(f"\n--- {scenario['title']} ---")
            print(f"[intent] {scenario['user_intent']}")
            print("[llm] streaming response:", flush=True)

        chunk_count = 0

        def _on_chunk(piece: str) -> None:
            nonlocal chunk_count
            chunk_count += 1
            if verbose:
                sys.stdout.write(piece)
                sys.stdout.flush()

        try:
            raw_content = call_llm(
                api_key=api_key,
                base_url=base_url,
                model=model,
                system_prompt=SYSTEM_PROMPT,
                user_prompt=user_prompt,
                timeout=timeout,
                temperature=temperature,
                max_tokens=max_tokens,
                stream=True,
                on_chunk=_on_chunk,
            )
        except LlmApiError as exc:
            raise LlmApiError(
                f"LLM API call failed for scenario '{scenario['name']}': {exc}"
            ) from exc

        if verbose:
            print(f"\n[llm] done ({chunk_count} chunks, {len(raw_content)} chars)\n", flush=True)

        parsed = extract_json(raw_content)
        checks, summary = validate_scenario(scenario, raw_content, parsed)

        if summary["status"] != "passed":
            all_passed = False
        scenario_results.append(summary)

        if verbose:
            cmds: list[str] = []
            md = ""
            if parsed:
                raw_cmds = parsed.get("commands") or []
                cmds = [c for c in raw_cmds if isinstance(c, str)]
                md_value = parsed.get("markdown_content")
                if isinstance(md_value, str):
                    md = md_value
            print(f"Commands: {cmds}")
            if md:
                first_line = md.splitlines()[0] if md.splitlines() else ""
                print(f"Markdown (first line): {first_line}")
            print("Checks:")
            for c in checks:
                print(c.render())
            print(
                f"Result: {'PASSED' if summary['status'] == 'passed' else 'FAILED'} "
                f"({summary['checks_passed']}/{summary['checks_total']} checks)"
            )
            print()

    summary_obj = {
        "scenarios": scenario_results,
        "all_passed": all_passed,
    }
    print(json.dumps(summary_obj, ensure_ascii=False, indent=2))
    return all_passed


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="AI dry-run test: validate that real LLMs can understand SKILL.md.",
    )
    parser.add_argument(
        "--api-key",
        default=None,
        help="OpenAI-compatible API key (default: OPENAI_API_KEY env).",
    )
    parser.add_argument(
        "--base-url",
        default=None,
        help="OpenAI-compatible base URL (default: OPENAI_BASE_URL env).",
    )
    parser.add_argument(
        "--model",
        default=None,
        help="Model name (default: OPENAI_MODEL env).",
    )
    parser.add_argument(
        "--skill-md",
        default=str(SKILL_MD_PATH),
        help="Path to SKILL.md (default: auto-detected).",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
        help=f"LLM API timeout in seconds (default: {DEFAULT_TIMEOUT}).",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=DEFAULT_TEMPERATURE,
        help=f"LLM temperature (default: {DEFAULT_TEMPERATURE}).",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=DEFAULT_MAX_TOKENS,
        help=f"Max output tokens (default: {DEFAULT_MAX_TOKENS}). Required for reasoning models.",
    )
    args = parser.parse_args()

    api_key = args.api_key or os.getenv("OPENAI_API_KEY")
    base_url = args.base_url or os.getenv("OPENAI_BASE_URL")
    model = args.model or os.getenv("OPENAI_MODEL")

    if not api_key:
        print("ERROR: --api-key or OPENAI_API_KEY env var is required.", file=sys.stderr)
        sys.exit(2)
    if not base_url:
        print("ERROR: --base-url or OPENAI_BASE_URL env var is required.", file=sys.stderr)
        sys.exit(2)
    if not model:
        print("ERROR: --model or OPENAI_MODEL env var is required.", file=sys.stderr)
        sys.exit(2)

    try:
        all_passed = run_ai_dry_run(
            api_key=api_key,
            base_url=base_url,
            model=model,
            skill_md_path=Path(args.skill_md),
            timeout=args.timeout,
            temperature=args.temperature,
            max_tokens=args.max_tokens,
            verbose=True,
        )
    except LlmApiError as exc:
        # Infrastructure failure: print and exit 2 so the eval suite does
        # not treat a network/auth issue as a test failure.
        print(f"ERROR (API failure, not a test failure): {exc}", file=sys.stderr)
        sys.exit(2)

    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()

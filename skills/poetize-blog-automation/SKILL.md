---
slug: awesome-poetize-open-blog-automation
displayName: POETIZE 博客自动化
name: poetize-blog-automation
description: 让 AI 帮你运营 POETIZE 博客：写文章并一键发布、更新或隐藏已有文章、管理分类和标签、管理评论、上传图片、翻译管理、切换博客主题、查看访问数据和趋势、配置 SEO、处理付费文章支付配置；含认证持久化、配置生成、测试等辅助能力。仅支持 awesome-poetize-open（POETIZE 的开源 fork），不适用于闭源版 POETIZE 或其他博客系统，也不用于与 POETIZE 无关的通用写作或 SEO 咨询。开源仓库：https://github.com/LeapYa/awesome-poetize-open
summary: POETIZE 博客运营助手：写文章、发布、管理分类标签与评论、上传图片、翻译管理、切换主题、查看数据、配置 SEO、处理付费文章支付配置。
license: MIT
homepage: https://github.com/LeapYa/awesome-poetize-open/tree/main/skills/poetize-blog-automation
version: 2.2.0
primaryEnv: POETIZE_API_KEY
requires:
  anyBins:
    - python
    - python3
  env:
    - POETIZE_BASE_URL
    - POETIZE_API_KEY
network:
  - POETIZE_BASE_URL (blog API, required)
  - LLM endpoint only for opt-in `eval` dry-run (user-supplied)
install:
  - id: brew-python
    kind: brew
    formula: python
    bins:
      - python3
    label: "Install Python 3 (brew)"
permissions:
  filesystem:
    - skill folder read/write
    - user-provided markdown/config/credential files
    - ~/.config/poetize/credentials.json for persisted auth
  environment:
    - POETIZE_BASE_URL
    - POETIZE_API_KEY
  network:
    - POETIZE_BASE_URL
  shell:
    - python/python3 to run bundled CLI scripts only
metadata:
  openclaw:
    skillKey: poetize-blog-automation
    emoji: "✍️"
user-invocable: true
disable-model-invocation: false
---
# POETIZE 博客自动化

个人博客运营助手，仅适配 `awesome-poetize-open`。发文默认策略：免费优先、维护优先、质量优先。

## Security Warnings

- **Credential sensitivity.** `POETIZE_API_KEY` is a high-privilege blog-admin credential. Prefer a least-privilege / revocable key. Never commit it, paste it in chat, or drop it in a repo/CWD. Store via framework secure storage, protected env, or `auth login` (writes `0600`). `poetize-blog config` emits `POETIZE_API_KEY` in plaintext into its output file — protect that path and do not commit it.
- **Live state changes need explicit confirmation.** Publishing, hiding, comments, theme/SEO changes, sitemap updates, image uploads, and paid-article payment-plugin config mutate the live blog immediately. These are live operations: before executing, surface the concrete target/action to the user and obtain explicit confirmation; do not rely on implicit intent. Preview safely with `_brief.publishIntent: draft` + `--draft`. The agent manifest disables implicit invocation (`allow_implicit_invocation: false`) and requires confirmation before state-changing actions.
- **Local image auto-upload.** Markdown/HTML image references are read from disk and uploaded to the configured blog on publish by default. Verify every local image path before running publish.
- **Paid publishing.** Inspects the payment-plugin status and, when `paymentConfigFile` is given, sends its contents to the server to configure/activate the plugin. Only on a server you own and when intentionally monetizing.
- **Optional external LLM transmission (dry-run only).** `poetize-blog eval` (and `ai_dry_run_test.py`) reads the *full* SKILL.md and sends it, with a test intent, to a user-supplied OpenAI-compatible endpoint to validate AI understanding. It is a local read-only test that never publishes, requires an explicit LLM key/URL and opt-in, and no other command contacts any third party.
- **eval never leaks secrets.** The optional `eval` dry-run transmits only the SKILL.md text and a test scenario to the user-supplied LLM endpoint. It never reads `credentials.json`, API keys, or any local secret, and requires explicit opt-in plus a confirmation that names the destination host before sending. Credential handling and the external dry-run are hard-separated.
- **Network egress is scoped.** All network calls go only to (1) the user-configured `POETIZE_BASE_URL` blog API, or (2) the user-supplied LLM endpoint for the optional dry-run. No telemetry, beaconing, or other exfiltration.
- **No arbitrary execution.** The CLI is a fixed argparse program with no `eval`/`exec`/`compile`/`subprocess`/`os.system`. The `eval` subcommand is a local strategy-test runner, not code execution. It reads/writes only explicitly supplied content/config files and never touches unrelated files.

## Agent-First Execution Rules

- This skill is for `awesome-poetize-open` only. Do not use it with the closed-source POETIZE or an unverified fork.
- **Trigger scope (explicit intent only).** Activate only on an explicit user request that names this blog / POETIZE or a concrete operation below — writing or publishing articles, managing categories/tags/themes/comments/analytics/SEO, setting up paid articles, or running the local `eval` dry-run. Do NOT auto-activate for generic "write a blog post", SEO advice, or marketing copy unrelated to an `awesome-poetize-open` instance you are configured to manage. State-changing operations additionally require a concrete target/intent per Guardrails.
- Run all operations through the `poetize-blog.sh` wrapper. The wrapper auto-detects its own directory, so you never pass baseDir to it:
  - macOS / Linux: `{baseDir}/poetize-blog.sh` (bash)
  - Windows: `python {baseDir}\scripts\poetize_cli.py` (cmd or PowerShell)
  - Or put `{baseDir}` on `PATH` and call `poetize-blog.sh` directly. Python 3 is still required at runtime (declared in `requires.anyBins`); the wrapper auto-selects `py` / `python3` / `python`.
- Invoke this skill only for explicit POETIZE tasks, not generic writing or SEO requests.
- Generate framework config with `poetize-blog.sh config`: `--format openclaw` for OpenClaw, `--format env` for IDE agents or shell.
- Credential persistence — choose by what survives a restart in your runtime, not by product name (WorkBuddy/Trae Work/Cursor/Qoder each span local+cloud): (1) framework injects `POETIZE_*` env (OpenClaw, Hermes, QwenPaw) → no action; (2) home dir persists (Trae/Cursor/CodeBuddy desktop, VS Code) → `auth login` global; (3) only skill folder persists (ima copilot, Doubao, cloud agents) → `auth login --local` (`{baseDir}/credentials.json`).
- Credential resolution priority: CLI args > env vars (`POETIZE_BASE_URL` / `POETIZE_API_KEY`) > global file (`~/.config/poetize/credentials.json`) > local file (`{baseDir}/credentials.json`). CWD discovery was removed; never place `credentials.json` in a working directory.
- Run `poetize-blog.sh smoke-test` before the first real write action in a new environment.
- Set `POETIZE_BASE_URL` to the public domain origin without trailing `/api`; requests resolve under `${POETIZE_BASE_URL}/api/api/...`.
- For `publish`, prefer an inline `_brief`; use `--stdin-brief` (with actual piped/heredoc JSON) or `--brief-file` when a separate audit trail is needed. Never pass `--stdin-brief` without stdin data, and do not create a Python wrapper merely to pipe it.

Read [references/strategy-playbook.md](references/strategy-playbook.md) before deciding whether to create, refresh, or hide content.
Read [references/decision-matrix.md](references/decision-matrix.md) before setting publish posture, search posture, or paywall posture.
Read [references/creativity-workflow.md](references/creativity-workflow.md) before drafting article copy.
Run `poetize-blog.sh eval` to verify the local strategy layer before shipping skill changes.

## Writing Voice

- Use first-person plural (`我们`) when writing about this project. Sound like an experienced developer talking with a friend: clear, practical, not academic.
- Avoid thesis-style setup lines and formulaic transitions. Do not use `说白了`/`不得不说`/`众所周知`/`接下来我们将探讨`/`不是...而是` in article copy.

## Pre-Writing Topic Validation

For a search-oriented new article, validate the target query before drafting:
1. Identify the dominant search intent.
2. If authoritative results already satisfy it, narrow to an underserved angle or longer-tail query; otherwise retain the query.
3. Run `manage list-articles` and plan links to older posts plus future backlinks. Prefer `refresh_article` when an existing post substantially overlaps.

If web search or article-list access is unavailable, record that limitation in `reasoning` and do not claim the corresponding validation. Skip query validation for intentionally non-search content such as personal narratives.

## Content Layout Rules

- For search-oriented content, put the core query and scope in the first paragraph.
- Use comparison tables when they clarify choices, tradeoffs, version differences, or troubleshooting paths. Comment code only where intent is non-obvious.
- Keep H1 out of article bodies: set the title in front matter, use `##` for top-level sections, and do not skip heading levels. For section edits, preserve the stored heading level by default and keep descendant levels relative to it.
- Use blockquotes (`>`) for tips, notes, and quotations; add relevant legal, security, or compatibility disclaimers.

## Workflow

1. Classify intent and safety posture.
   Determine the target operation, article, taxonomy, visibility, and monetization (defaults to free). Honor explicit visibility intent; when absent, stage a new article as a draft for review. Run Pre-Writing Topic Validation only when applicable.
   A strategy brief is required for `publish` and these `manage` mutations: `update-article`, `hide-article`, `update-section`, `save-translation`, `delete-translation`, and `regenerate-translation`. Other commands do not consume a brief.
2. Create the matching brief.
   Use `{baseDir}/assets/article-brief.template.json` for `publish` and `{baseDir}/assets/ops-brief.template.json` for strategy-validated `manage` commands. Article briefs require `taskType`, `primaryGoal`, `targetAudience`, `publishIntent`, `reasoning`, `selectedAngle`, and `alternativesConsidered`; `monetizationIntent` defaults to `free_default`. Ops briefs require `taskType`, `primaryGoal`, `reasoning`, and `expectedOutcome`. Infer strategy fields from the user's goal and explain them in `reasoning`. Ask only when missing information would materially change the target, visibility, monetization, or taxonomy.
3. Diverge, then converge.
   Consider 2-4 plausible directions, choose one as `selectedAngle`, and record 1-3 rejected directions in `alternativesConsidered`. Even when one direction is strongly preferred, include at least one plausible rejected alternative.
4. Write the article in Markdown following Content Layout Rules and Writing Voice.
   For images: either reference local files (CLI auto-uploads at publish time) or upload first via `poetize-blog.sh upload-image` and embed the URL. When the task is maintenance, prefer revising existing articles over creating duplicates.
5. Add front matter for the article title, routing, and publishing metadata.
   New articles require `title`, `sort`/`sortId`, and `label`/`labelId`; content updates require `title` and may omit unchanged taxonomy. With inline `_brief`, omit `viewStatus` (derived from `publishIntent`) and omit `payType` for `free_default` (forced to `0`); `paid_explicit` requires `primaryGoal: conversion`, non-empty `whyPaid`, and `payType > 0`. Use existing taxonomy names when IDs are unknown (close matches are suggestions only); set `coverBlank: true` when no cover is needed.

   Front matter field reference:

   | Field | Required | Default | Notes |
   |---|---|---|---|
   | `title` | Yes | — | Article title; do not repeat it as H1 in body |
   | `sort` / `sortName` / `sortId` | Yes for new | — | Category name or ID |
   | `label` / `labelName` / `labelId` | Yes for new | — | Tag name or ID |
   | `sortDescription` / `sortDesc` | No | sort name | Description used when auto-creating a category via `--allow-create-sort`; if omitted, the category name is used as the description |
   | `labelDescription` / `labelDesc` | No | label name | Description used when auto-creating a tag via `--allow-create-label`; if omitted, the tag name is used as the description |
   | `articleSlug` / `slug` | No | auto | SEO-friendly URL slug |
   | `commentStatus` | No | new: `true`; update: unchanged | Enable comments |
   | `recommendStatus` | No | new: `false`; update: unchanged | Feature in recommendations |
   | `submitToSearchEngine` | No | public create/update: `true`; draft: `false` | May set `false` during frequent edits |
   | `viewStatus` | No | from `_brief.publishIntent` | Omit when using inline `_brief` |
   | `cover` / `coverFile` / `coverBlank` | No | platform default | Cover image URL, local path, or `coverBlank: true` to skip |
   | `coverStoreType` / `storeType` | No | — | Override cover storage type |
   | `video` | No | — | Video URL |
   | `password` / `tips` | No | auto for drafts | Password and preview tip for private articles |
   | `payType` | No | free: `0`; paid: `> 0` | Omit for `free_default`; required for `paid_explicit` |
   | `payAmount` / `freePercent` | No | — | Price and free preview percentage (default 30) for paid articles; ignored when a `<!--paywall-->` marker is present in the body — see [Paywall cutoff marker](#paywall-cutoff-marker) |
   | `skipAiTranslation` | No | `false` | Skip AI translation |
   | `pendingTranslationLanguage` / `pendingTranslationTitle` / `pendingTranslationContent` | No | — | Pre-supplied translation |
   | `paymentPluginKey` / `paymentConfigFile` | No | — | Payment plugin key and sensitive config JSON path |
   | `_brief` | No | — | Inline strategy brief (see Workflow step 2) |
6. Write the Markdown file locally, then run `poetize-blog.sh publish --markdown-file <file>`; inline `_brief` removes the need for `--brief-file`.
   `_brief.publishIntent` determines visibility; `--draft` and `--publish` are optional consistency checks and must agree with it. For draft-first creation, start with `taskType: create_article` and `publishIntent: draft`, run with `--draft --wait`, and verify the returned ID. To promote, change the same file to `taskType: refresh_article` and `publishIntent: public`, then rerun with `--article-id <id> --publish --wait`.
   Runtime auth requires `POETIZE_BASE_URL` and `POETIZE_API_KEY`. Referenced local images are uploaded automatically. Paid publishing checks `/api/api/payment/plugin/status` and fails closed if the plugin is not ready; it never silently publishes as free.
7. Use `manage <subcommand>` for existing content, comments, themes, analytics, SEO, and taxonomy (categories/tags).
   - Use `update-section` for localized source edits, `save-translation` for a manual translation correction, `regenerate-translation` only when all translations are stale, and `publish --article-id` for full rewrites. Article deletion is unsupported; use `hide-article`.
   - Use `list-sorts`/`list-labels` to inspect taxonomy, and `create-sort`/`update-sort`/`delete-sort`/`create-label`/`update-label`/`delete-label` to manage categories and tags independently of article publishing. When creating taxonomy, always provide a meaningful `--description`.
   - Comment writes are opt-in: run them only when the user requests comment work or accepts a specific proposal. Use `--as-ai` for the configured AI persona; omit it for the Blog Owner.
   - Comment, translation, and section commands require backend `v5.1.0`+. On a version-mismatch error, ask the user to upgrade; other commands remain available.
8. Return the final result. For async commands, prefer running **without** `--wait` so the CLI returns the task id immediately; then poll status yourself (`manage task-status --task-id <id>` for article tasks, `manage list-translation-languages --article-id <id>` for translation tasks) with a sleep between checks. Reserve `--wait` for simple one-shot waits.

## Guardrails

- Free content is the default; never introduce a paywall without an explicit monetization request. New articles default to draft when visibility is unspecified.
- Strategy-validated commands require a matching, non-contradictory brief. Other writes require explicit user intent but no fabricated brief.
- Never invent taxonomy IDs or silently accept fuzzy matches. Use names when IDs are unknown; create taxonomy only after confirmation via `--allow-create-*`.
- When auto-creating taxonomy (`--allow-create-sort`/`--allow-create-label`), always provide `sortDescription`/`labelDescription` in front matter with a meaningful one-sentence description of what the category or tag covers. If omitted, the backend falls back to using the name itself as the description — acceptable but less informative.
- Preserve unspecified fields on updates. The only exception is `submitToSearchEngine` (see front matter table); hide flows force it to `false`.
- Every article brief **must** include `alternativesConsidered` — a non-empty list of 1–3 rejected angles. Never submit a `publish` brief without it, even when one angle is clearly preferred.
- Article deletion is unsupported — use `hide-article`. A requested paid publish fails closed when payment is unavailable; a separate free publish requires fresh user intent and a `free_default` brief.
- Prefer `coverBlank: true` over a fabricated cover. Stop on a missing local image rather than dropping or guessing it.
- Comment writes remain opt-in; publishing alone is not permission to inspect or create comments.
- With `list-comments --floor-comment-id <id>`, `"root_comment_missing": true` means the root fell outside the newest 50 top-level comments. Fetch additional pages before replying when root context matters.

## Image Upload Boundaries

1. **Size Limit**: Nginx/OpenResty allows up to **2048MB** per request (aligned with Spring Boot `max-file-size`). Normal user-facing image uploads (comments, love wall) are limited to **5MB** by the frontend; admin uploads (fonts, resources) can use the full 2048MB.
2. **Formats**: SVG is strictly forbidden (XSS risk). Use JPEG, PNG, GIF, BMP, WEBP, TIFF, ICO.
3. **Filenames**: No encoding restrictions (Chinese names fully supported). Server auto-renames to UUIDs.

## Monetization & Payment Settings

Paid publishing requires an explicit user request. The blog owner must configure the Afdian or Epay gateway, either in the admin panel or by supplying an approved payment config file. Set `primaryGoal: conversion`, `monetizationIntent: paid_explicit`, non-empty `whyPaid`, and `payType > 0` plus the required price fields. The CLI verifies the plugin and connection before publishing; if checks fail, publishing stops without silently removing the paywall. Never invent, request in chat, expose, or reuse gateway credentials without explicit user direction.

### Paywall cutoff marker

1. **`<!--paywall-->` marker (preferred).** HTML comment on its own line, surrounded by blank lines. Content before it is free; content after is hidden until the reader pays (or is the author, admin, member for `payType: 2`, or has already paid). Only the first match is used, and it overrides `freePercent`. Place it after a natural hook — never mid-sentence or inside a code fence/table/list (it becomes literal text and the cut is disabled). Aim for at least one complete section before it, since search engines and the summary generator see the cut body. State a user-specified cutoff in `reasoning`.
2. **`freePercent` fallback.** With no marker, the backend cuts at a paragraph boundary (`\n\n`, then `\n`) nearest to that share of the body (default `30`).

## Script Usage

Save credentials once for all future commands:

```bash
poetize-blog.sh auth login --base-url "$POETIZE_BASE_URL" --api-key "$POETIZE_API_KEY"
poetize-blog.sh auth show   # verify without printing the API key
```

After `auth login`, subsequent commands no longer need `--base-url` or `--api-key`. Start from the bundled templates: `article-brief.json` from `{baseDir}/assets/article-brief.template.json`, `ops-brief.json` from `{baseDir}/assets/ops-brief.template.json`.

Front matter example with inline `_brief` (recommended for Agent workflows):

```md
---
title: "示例文章"
slug: "ai-automation-example"
sort: "AI实践"
label: "自动化"
commentStatus: true
recommendStatus: false
submitToSearchEngine: false
_brief:
  taskType: create_article
  primaryGoal: asset_maintenance
  targetAudience: "想理解 AI 自动化的读者"
  publishIntent: draft
  reasoning: "先以草稿补齐博客的 AI 自动化内容资产，确认后再公开"
  selectedAngle: "实用维护视角"
  alternativesConsidered: ["宽泛入门 overview", "战术 checklist"]
  monetizationIntent: free_default
---

正文导语...

## 第一节

正文...
```

`viewStatus` is omitted because `_brief.publishIntent` derives it. `payType` is omitted because `monetizationIntent: free_default` forces `payType: 0`. The script rejects incomplete briefs, reports all missing fields together, and requires `alternativesConsidered` to contain 1-3 rejected angles that do not duplicate `selectedAngle`. This field is required for every `publish` brief (draft, public, and updates) — never omit it.

**Article brief** — used only by `publish`:

| Field | Valid values |
|---|---|
| `taskType` | `create_article` or `repurpose_article` for creation; `refresh_article` whenever `--article-id` is set |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `publishIntent` | `draft`, `public` |
| `monetizationIntent` | optional: `free_default` (default), `paid_explicit` |
| `whyPaid` | required non-empty string only for `paid_explicit` |
| `targetAudience`, `reasoning`, `selectedAngle` | non-empty strings |
| `alternativesConsidered` | **REQUIRED** non-empty list of 1-3 rejected angles — include for every `publish` brief (draft, public, and `--article-id` updates); never omit |

> All 7 fields above are required for **every** `publish` brief, including updates via `--article-id`. `alternativesConsidered` must list 1-3 rejected angles even when the chosen direction is obviously best.

**Ops brief** — used by strategy-validated `manage` mutations:

| Field | Valid values |
|---|---|
| `taskType` | `update_article`, `hide_article`, `update_section`, `update_translation` (for `save-translation`), `delete_translation`, or `regenerate_translation`; must match the command |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `reasoning`, `expectedOutcome` | non-empty strings |

Upload an image first when an explicit URL is preferable to publish-time upload:

```bash
poetize-blog.sh upload-image --file ./assets/flow.png --type articleImage
```

Hide an existing article (`--stdin-brief` via heredoc — no Python wrapper needed):

```bash
poetize-blog.sh manage hide-article --article-id 123 --stdin-brief --wait <<'BRIEF'
{"taskType":"hide_article","primaryGoal":"asset_maintenance","reasoning":"user requested hiding","expectedOutcome":"article is no longer public but remains recoverable"}
BRIEF
```

List and reply to comments (the "cold start" engagement loop — see Guardrails for the `root_comment_missing` caveat):

```bash
poetize-blog.sh manage list-comments --article-id 123 --size 10
# Reply as the AI persona. --floor-comment-id is not needed for save-comment:
# the backend derives it from --parent-comment-id server-side.
poetize-blog.sh manage save-comment --article-id 123 --content "感谢分享，这个思路我们后续会展开讲！" --parent-comment-id 456 --parent-user-id 78 --as-ai
# No comments yet: post a top-level welcome/question to bootstrap discussion
poetize-blog.sh manage save-comment --article-id 123 --content "欢迎留言交流～"
```

### Manage subcommands reference

`get-article`, `update-article`, `hide-article`, and `article-analytics` accept exactly one of `--article-id <id>`, `--article-slug <slug>`, or `--article-title-exact <title>`. Comment, translation, and section commands require `--article-id`.

| Subcommand | Purpose | Key flags |
|---|---|---|
| `list-articles` | List/filter articles | `--search-key` (title fragment), `--article-search` (title+content full-text; `/.../` = regex), `--sort-name`, `--label-name`, `--exact-title`, `--recommend-only`, `--created-between` / `--updated-between` / `--published-between` (repeatable `START~END` time ranges, see note below), `--order` / `--asc` (sort by create-time/update-time/publish-time), `--current`, `--size`, `--orphan-only` (only broken articles with missing/deleted category or tag, for data repair) |
| `get-article` | Fetch one article | `--article-id` / `--article-slug` / `--article-title-exact` |
| `update-article` | Update metadata via raw JSON; use `publish` or `update-section` for content | `--payload-file` / `--stdin-payload`, `--brief-file` / `--stdin-brief`, `--wait` |
| `hide-article` | Set `viewStatus=false` | `--brief-file` / `--stdin-brief`, `--password`, `--tips`, `--wait` |
| `article-analytics` | Get article stats | article target only |
| `site-visits` | Site visit trends with bot/human split and region breakdown | `--days 7` or `--days 30` |
| `theme-status` / `activate-theme` | Theme info / switch | `--plugin-key <key>` (required for activate) |
| `seo-status` / `seo-get-config` / `seo-set-config` | SEO status / read / update config | `--config-file <path>` (required for set) |
| `sitemap-update` | Refresh sitemap | none |
| `task-status` | Get async task status | `--task-id <id>` (required) |
| `list-comments` | List comments (v5.1.0+) | `--article-id <id>`, `--floor-comment-id <id>` (required to page a floor's replies), `--current`, `--size` |
| `save-comment` | Post or reply to a comment (v5.1.0+) | `--article-id <id>`, `--content <text>`, `--parent-comment-id <id>`, `--parent-user-id <id>`, `--floor-comment-id` (ignored — see note), `--as-ai` |
| `get-translation` / `list-translation-languages` | Fetch translation / list languages (v5.1.0+) | `--article-id <id>`, `--language <code>` (default: `en`, get only) |
| `save-translation` | Save/overwrite a manual translation (v5.1.0+) | article/language/title/content, `--brief-file` / `--stdin-brief` |
| `delete-translation` | Delete one translation (v5.1.0+) | article/language, `--brief-file` / `--stdin-brief` |
| `regenerate-translation` | Delete all translations and re-run AI (v5.1.0+) | article, `--brief-file` / `--stdin-brief`, `--wait` / `--poll-interval` / `--timeout` |
| `update-section` | Edit one section (v5.1.0+) | article/action, optional heading/content/`--new-heading-level`/`--skip-ai-translation`/`--heading-index`/`--dry-run`, brief, `--wait` / `--poll-interval` / `--timeout` |
| `list-sorts` | List all categories | none |
| `list-labels` | List all tags | `--sort-id <id>` (filter by category) |
| `create-sort` | Create a new category | `--name`, `--description`, `--sort-type 0\|1` (default 1), `--priority` (default 99) |
| `update-sort` | Update an existing category | `--id`, optional `--name`/`--description`/`--sort-type`/`--priority` |
| `delete-sort` | Delete a category | `--id` |
| `create-label` | Create a new tag | `--name`, `--description`, `--sort-id` (required) |
| `update-label` | Update an existing tag | `--id`, optional `--name`/`--description`/`--sort-id` |
| `delete-label` | Delete a tag | `--id` |

For `update-article`, do not combine `--stdin-payload` with `--stdin-brief`: both read the same stream sequentially. Put at least one JSON object in a file.

> **Time-range filters (`--created-between` / `--updated-between` / `--published-between`).** Each value is `START~END` with either side omittable (`~2024-05-01` = before, `2024-05-01~` = after); time points accept `yyyy-MM-dd`, `yyyy-MM-dd HH:mm`, or `yyyy-MM-dd HH:mm:ss`, and date-only values are inclusive whole days. Repeat a flag to OR-combine multiple ranges of the same field; different fields AND-combine. `--published-between` uses the first-public-publish time (RSS pubDate semantics; never-published drafts are excluded). These filters, `--order`, and `--asc` require backend `v5.2.0`+ — **older backends silently ignore them and return unfiltered results**, so verify the backend version before trusting filtered output. Example: `manage list-articles --created-between "2024-01-01~2024-03-31" --created-between "2025-01-01~" --recommend-only --order publish-time --asc`.

> `--floor-comment-id` behaves differently across commands. In `list-comments` it is a real query filter that selects which floor's replies to page through. In `save-comment` it is a no-op: the backend always recomputes `floorCommentId` from `parentCommentId` server-side and only logs a warning if the client value disagrees. Omit it when replying — just pass `--parent-comment-id` and `--parent-user-id`.

## Publish & Update Articles

Write the Markdown file first, then publish through the unified CLI. Inline `_brief` means no `--brief-file` is needed.

> **Brief self-check (required before running any `publish`):** confirm the brief contains ALL of `taskType`, `primaryGoal`, `targetAudience`, `publishIntent`, `reasoning`, `selectedAngle`, **and `alternativesConsidered`**. The last one is a **non-empty list of 1–3 rejected angles** — omitting it fails validation. Include at least one rejected alternative even when the chosen direction is obviously best.

```bash
# article.md contains front matter with publishIntent: draft
poetize-blog.sh publish --markdown-file article.md --draft --wait
```

### Publish flags reference

| Flag | Purpose | When to use |
|---|---|---|
| `--markdown-file <path>` | **(required)** Path to the Markdown file | Always |
| `--publish` / `--draft` | Assert public / draft visibility | Must match `_brief.publishIntent` |
| `--article-id <id>` | Update an existing article | Requires `_brief.taskType: refresh_article` |
| `--force` | Bypass only the required-H2 check | Intentionally sectionless body; H1 still invalid |
| `--allow-create-taxonomy` / `--allow-create-sort` / `--allow-create-label` | Auto-create missing category and/or tag | Explicitly confirmed taxonomy creation |
| `--cover-file <path>` | Local cover image path | Custom cover |
| `--payment-plugin-key <key>` / `--payment-config-file <path>` | Select/configure payment plugin | Paid articles only |
| `--require-paid` | Compatibility flag for strict paid mode | Paid checks already fail closed |
| `--brief-file <path>` / `--stdin-brief` | External article brief JSON | Separate strategy audit trail / piped heredoc JSON |
| `--print-payload` | Print payload without sending | Local debugging; may contain sensitive fields |

> **`--wait` (optional, async commands only).** Default: do NOT add it — let the CLI return the task id, then poll yourself (`manage task-status --task-id <id>` for article tasks; `manage list-translation-languages --article-id <id>` for translation tasks). When added, the CLI blocks until completion or `--timeout` (default 900s); `--poll-interval` defaults to 2.0s. `--wait` has no effect on read-only `manage` commands, `config`, `smoke-test`, `eval`, `upload-image`, or `update-section --skip-ai-translation`. True global flags (every command): `--base-url`, `--api-key`.

For an existing article, fetch it first and rebuild `updated.md` from the returned title/content. Set `_brief.taskType: refresh_article` and set `publishIntent` to the current visibility unless the user requested a change:

```bash
poetize-blog.sh manage get-article --article-id 123
poetize-blog.sh publish --markdown-file updated.md --article-id 123 --wait
```

## Section-Level Editing & Translation Management

Full-article rewrites via `publish --article-id` are wasteful when you only need to fix a typo, update one section, or correct a translation. Two lighter-weight editing paths avoid regenerating the entire article:

### Section-level content updates (`manage update-section`)

Edit a single section by heading instead of rewriting the whole Markdown file. The backend locates it case-insensitively outside code fences, saves the change, refreshes the summary, and schedules translation regeneration unless skipped.

**Heading workflow:**

1. Run `manage get-article` immediately before editing and treat returned `articleContent` as authoritative.
2. Article bodies must use H2-H6 only. For `replace`, start the content file with the stored heading level; use `--new-heading-level 2..6` only when the user explicitly requests a hierarchy change. A level jump of more than 1 (e.g. H2→H4) triggers a warning but is not blocked.
3. For inserted/appended content, choose a level consistent with its intended position; new top-level sections use H2. H1 is always rejected.
4. If a heading matches multiple sections, the error lists each match with a 1-based index (e.g. `#1: 第3行 (H2), #2: 第15行 (H2)`). Re-run with `--heading-index N` to select the Nth match. Section content files must not contain YAML front matter (`---`); front matter is only valid in `publish --markdown-file`.

| Action | `--heading` | `--content-file` | Result |
|---|---|---|---|
| `replace` | required | required | Replace heading and body; preserve level unless `--new-heading-level` is explicit |
| `insert_after` / `insert_before` | required | required | Insert content around the matched section |
| `delete` | required | omitted | Remove the matched heading and body |
| `append` | omitted | required | Append content at article end |

```bash
poetize-blog.sh manage update-section --article-id 123 \
  --heading "Installation" --action replace --content-file section.md \
  --brief-file update-section-brief.json
```

Use an ops brief with `taskType: update_section`. `--skip-ai-translation` skips translation regeneration only; the summary still updates.

### Section editing safety nets

- **Dry-run (`--dry-run`):** Returns `originalContent`, `updatedContent`, `originalSectionContent`, and optional `warning` without persisting or triggering side effects. Always use it on the first edit of a session or when the heading is ambiguous.
- **Heading disambiguation (`--heading-index N`):** When multiple headings share the same text, the error response lists each match with a 1-based index. Out-of-range values return an error with the valid range.
- **Concurrency protection (CAS):** The backend uses optimistic locking — it only writes if `articleContent` is still the value it read. On conflict ("文章内容可能已被其他请求修改，请重试"), re-fetch with `get-article` and retry.
- **Code fence safety:** Headings inside fenced code blocks (`` ``` `` or `~~~`) are never matched or counted as sections.
- **Response fields:** `replace`/`delete` responses include `originalSectionContent` (the removed content). A `warning` field is included if `newHeadingLevel` jumps more than 1 level. Both are omitted for `insert`/`append`.

### Section edit recovery

1. **CAS conflict**: Re-run `manage get-article` to get the latest content, then retry the section edit.
2. **Wrong section edited**: The `replace`/`delete` response includes `originalSectionContent`. To undo, write that content to a file and re-run `update-section --action replace --content-file <original> --heading "<same heading>"`. This is a manual rollback — verify before re-applying.
3. **Preview before applying**: Always use `--dry-run` first; it returns the full diff (`originalContent` vs `updatedContent`) without side effects.

### Translation management

For AI-translated articles, you can inspect, edit, delete, or regenerate translations without touching the original article content:

```bash
poetize-blog.sh manage save-translation --article-id 123 \
  --language en --title "My Article" --content-file translated.md \
  --brief-file update-translation-brief.json
```

**When to use which editing path:**

| Scenario | Recommended command |
|---|---|
| Full content rewrite or structural reorganization | `publish --markdown-file <file> --article-id <id>` |
| Fix one section, add a section, or delete a section | `manage update-section` |
| Correct a specific translation without re-running AI | `manage save-translation` |
| All translations are stale after major edits | `manage regenerate-translation` |
| Metadata-only update (viewStatus, password, tips, etc.) | `manage update-article` |

> Mutating translation and section commands require `--stdin-brief` (or `--brief-file`) with the matching `taskType`. Read-only commands (`get-translation`, `list-translation-languages`) do not need a brief. All commands in this section require backend `v5.1.0`+; on older backends the CLI returns an explicit version-mismatch error instead of a raw HTTP 404/500.

## Failure Recovery & Safe Retry

For an asynchronous timeout or failure, avoid duplicate articles:
1. Query `manage task-status --task-id <taskId>`. If pending/running, keep polling; do not submit another create.
2. If the response contains `articleId`, fetch that article before any write. Recover with `publish --article-id <id>` using `taskType: refresh_article`; never retry as a new create.
3. Retry creation only after the task is terminally failed and `articleId` is absent, using the original create brief after correcting the cause.
4. If the created article must be taken down, use `hide-article` with `taskType: hide_article`.
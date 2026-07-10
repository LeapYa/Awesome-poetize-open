---
name: poetize-blog-automation
description: 让 AI 帮你运营 POETIZE 博客：写文章并一键发布、更新或隐藏已有文章、评论区管理、管理分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。仅支持 awesome-poetize-open 开源版，不适用于原版 POETIZE 或其他博客系统，也不用于与 POETIZE 无关的通用写作或 SEO 咨询。开源仓库：https://github.com/LeapYa/awesome-poetize-open
homepage: https://github.com/LeapYa/awesome-poetize-open/tree/main/skills/poetize-blog-automation
version: 2.1.0
primaryEnv: POETIZE_API_KEY
requires:
  anyBins:
    - python
    - python3
  env:
    - POETIZE_BASE_URL
    - POETIZE_API_KEY
install:
  - id: brew-python
    kind: brew
    formula: python
    bins:
      - python3
    label: "Install Python 3 (brew)"
metadata:
  openclaw:
    skillKey: poetize-blog-automation
    emoji: "✍️"
user-invocable: true
disable-model-invocation: false
---
# POETIZE 博客自动化

装上这个技能，你可以让 AI 帮你完成 POETIZE 博客的日常运营：写文章并一键发布、更新或隐藏已有文章、管理评论、分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。

仅支持 `awesome-poetize-open` 开源版，不适用于原版 POETIZE。开源仓库：https://github.com/LeapYa/awesome-poetize-open

定位是个人博客运营助手。发文默认策略：免费优先、维护优先、质量优先。

## Security Warnings

- `POETIZE_API_KEY` is a high-privilege credential. Store via `auth login` (0600), framework secure storage, or protected env. Never commit to source control or paste in chat.
- Public publishing, hiding, comment writes, theme changes, and SEO changes can take effect immediately. To preview a new article, set `_brief.publishIntent: draft` and use `--draft`.
- Local images in Markdown are auto-uploaded to your blog server at publish time. Only reference images you intend to publish.
- Payment config files contain gateway credentials. Only use when intentionally configuring monetization; keep free articles as default.
- The bundled CLI only calls documented blog-management APIs and reads/writes explicitly supplied content, config, and credential files. It does not run arbitrary commands or access unrelated local files.

## Agent-First Execution Rules

- This skill is for `awesome-poetize-open` only. Do not use it with the original POETIZE or a fork whose API compatibility has not been verified.
- Use `{baseDir}` for paths inside this skill folder. Run all operations through `python {baseDir}/scripts/poetize_cli.py <command> [subcommand] ...`; use `python3` if `python` is unavailable.
- Invoke this skill only for explicit POETIZE tasks, not generic writing or SEO requests.
- Generate framework config with `poetize_cli.py config`: use `--format openclaw` for OpenClaw and `--format env` for IDE agents or shell environments.
- Use native credential persistence when the framework provides it. Otherwise use global `auth login`; in ephemeral sandboxes use `auth login --local` or CLI args/env.
- Credential resolution priority: CLI args > env vars > global config (`~/.config/poetize/credentials.json`) > local skill config (`{baseDir}/credentials.json`) > CWD config (`./credentials.json`).
- Run `poetize_cli.py smoke-test` before the first real write action in a new Agent environment.
- Set `POETIZE_BASE_URL` to the public domain origin without a trailing `/api`; requests resolve under `${POETIZE_BASE_URL}/api/api/...`.
- For `publish`, prefer an inline `_brief`; use `--stdin-brief` or `--brief-file` when a separate audit trail is needed.
- For strategy-validated `manage` mutations, pipe JSON with `--stdin-brief` (prefer a heredoc) or use `--brief-file`. Never pass `--stdin-brief` without stdin data, and do not create a Python wrapper merely to pipe it.

Read [references/strategy-playbook.md](references/strategy-playbook.md) before deciding whether to create, refresh, or hide content.
Read [references/decision-matrix.md](references/decision-matrix.md) before setting publish posture, search posture, or paywall posture.
Read [references/creativity-workflow.md](references/creativity-workflow.md) before drafting article copy.
Run `python {baseDir}/scripts/poetize_cli.py eval` to verify the local strategy layer before shipping skill changes.

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
- Keep H1 out of article bodies: set the title in front matter, use `##` for top-level sections, and do not skip heading levels.
- For section edits, preserve the stored heading level by default and keep descendant levels relative to it.
- Use blockquotes (`>`) for tips, notes, and quotations; add relevant legal, security, or compatibility disclaimers.

## Workflow

1. Classify intent and safety posture.
   Determine the target operation, article, taxonomy, visibility, and monetization. Monetization defaults to free. Honor explicit visibility intent. When visibility is absent, stage a new article as a draft for review before applying the final posture from the decision matrix; preserve current visibility for updates.
   Run Pre-Writing Topic Validation only when applicable.
   A strategy brief is required for `publish` and these `manage` mutations: `update-article`, `hide-article`, `update-section`, `save-translation`, `delete-translation`, and `regenerate-translation`. Other commands do not consume a brief.
2. Create the matching brief.
   Use `{baseDir}/assets/article-brief.template.json` for `publish` and `{baseDir}/assets/ops-brief.template.json` for the strategy-validated `manage` commands. Prefer inline `_brief` for `publish`; use `--brief-file` or correctly piped `--stdin-brief` otherwise.
   Article briefs require `taskType`, `primaryGoal`, `targetAudience`, `publishIntent`, `reasoning`, `selectedAngle`, and `alternativesConsidered`; `monetizationIntent` defaults to `free_default`. Ops briefs require `taskType`, `primaryGoal`, `reasoning`, and `expectedOutcome`.
   Infer strategy fields from the user's goal and explain them in `reasoning`. Ask only when missing information would materially change the target, public visibility, monetization, or taxonomy.
3. Diverge, then converge.
   Consider 2-4 plausible directions, choose one as `selectedAngle`, and record 1-3 rejected directions in `alternativesConsidered`. Even when one direction is strongly preferred, include at least one plausible rejected alternative.
4. Write the article in Markdown following Content Layout Rules and Writing Voice.
   For images: either reference local files (CLI auto-uploads at publish time) or upload first via `poetize_cli.py upload-image` and embed the URL.
   When the task is maintenance, prefer revising existing articles over creating duplicates.
5. Add front matter for the article title, routing, and publishing metadata.
   New articles require `title`, `sort` or `sortId`, and `label` or `labelId`; content updates require `title` and may omit unchanged taxonomy.
   With inline `_brief`, omit `viewStatus`: `_brief.publishIntent` is authoritative. For public article creation or modification, `submitToSearchEngine` defaults to `true`; set it explicitly to `false` when frequent edits should not trigger search submission. Drafts force it to `false`.
   For `free_default`, omit `payType` because the strategy forces `0`; `paid_explicit` instead requires `primaryGoal: conversion`, non-empty `whyPaid`, and `payType > 0`.
   Use existing taxonomy names when IDs are unknown. Exact matches are resolved; close matches are suggestions only. Set `coverBlank: true` when no cover is needed.

   Front matter field reference:

   | Field | Required | Default | Notes |
   |---|---|---|---|
   | `title` | Yes | — | Article title; do not repeat it as an H1 in Markdown content |
   | `sort` / `sortName` / `sortId` | Yes for new | — | Category name or ID |
   | `label` / `labelName` / `labelId` | Yes for new | — | Tag name or ID |
   | `articleSlug` / `slug` | No | auto | SEO-friendly URL slug |
   | `commentStatus` | No | new: `true`; update: unchanged | Enable comments |
   | `recommendStatus` | No | new: `false`; update: unchanged | Feature in recommendations |
   | `submitToSearchEngine` | No | public create/update: `true`; draft: `false` | Public articles may explicitly set `false` during frequent edits |
   | `viewStatus` | No | from `_brief.publishIntent` | Omit when using inline `_brief` |
   | `cover` | No | platform default | Cover image URL |
   | `coverBlank` | No | `false` | Set `true` to skip cover |
   | `coverFile` | No | — | Local cover file path (uploaded at publish time) |
   | `coverStoreType` / `storeType` | No | — | Override cover storage type |
   | `video` | No | — | Video URL |
   | `password` | No | auto for drafts | Password for private articles |
   | `tips` | No | auto for drafts | Preview tip for private articles |
   | `payType` | No | free: `0`; paid: explicit `> 0` | Omit for `free_default`; required for `paid_explicit` |
   | `payAmount` | No | — | Price for paid articles |
   | `freePercent` | No | — | Free preview percentage |
   | `skipAiTranslation` | No | `false` | Skip AI translation |
   | `pendingTranslationLanguage` | No | — | Target translation language |
   | `pendingTranslationTitle` | No | — | Translated title |
   | `pendingTranslationContent` | No | — | Translated content |
   | `paymentPluginKey` | No | — | Payment plugin key (e.g. `afdian`) |
   | `paymentConfigFile` | No | — | Payment config JSON path |
   | `_brief` | No | — | Inline strategy brief (see Workflow step 2) |
6. Write the Markdown file locally, then run `python {baseDir}/scripts/poetize_cli.py publish --markdown-file <file>`; inline `_brief` removes the need for `--brief-file`.
   `_brief.publishIntent` determines visibility; `--draft` and `--publish` are optional consistency checks and must agree with it.
   For draft-first creation, start with `taskType: create_article` and `publishIntent: draft`, run with `--draft --wait`, and verify the returned ID. To promote, change the same file to `taskType: refresh_article` and `publishIntent: public`, then rerun with `--article-id <id> --publish --wait`.
   Runtime authentication requires `POETIZE_BASE_URL` and `POETIZE_API_KEY` from the configured credential source. Referenced local Markdown and HTML images are uploaded automatically.
   Paid publishing checks `/api/api/payment/plugin/status` and fails closed if the selected plugin is not ready; it never silently publishes the requested paid article as free.
7. Use `manage <subcommand>` for existing content, comments, themes, analytics, and SEO.
   - Article deletion is unsupported; use `hide-article` with a matching ops brief.
   - Use `update-section` for localized source edits, `save-translation` for a manual translation correction, `regenerate-translation` only when all translations are stale, and `publish --article-id` for full rewrites.
   - Comment writes are opt-in: run them only when the user requests comment work or accepts a specific proposal. Use `--as-ai` for the configured AI persona; omit it for the Blog Owner.
   - Comment, translation, and section commands require backend `v5.0.1` or later. On an explicit version-mismatch error, ask the user to upgrade; other commands remain available.
8. Return the final result. For async commands (`publish`, `hide-article`, `update-article`, `update-section` without `--skip-ai-translation`, `regenerate-translation`), prefer running **without** `--wait` so the CLI returns the task id immediately; then poll the status yourself (`manage task-status --task-id <id>` for article tasks, `manage list-translation-languages --article-id <id>` for translation tasks) with a sleep between checks. Reserve `--wait` for simple one-shot waits where you have nothing else to do.

## Guardrails

- Free content is the default; never introduce a paywall without an explicit monetization request. New articles default to draft when visibility is unspecified.
- The strategy-validated commands listed in Workflow step 1 require a matching, non-contradictory brief. Other writes still require explicit user intent but no fabricated brief.
- Never invent taxonomy IDs or silently accept fuzzy matches. Use names when IDs are unknown, and create taxonomy only after confirmation through an `--allow-create-*` flag.
- Preserve unspecified fields on updates except `submitToSearchEngine`: article creation, full publish updates, and `manage update-article` default it to `true`; set it explicitly to `false` while frequent edits should not trigger search submission. Draft and hide flows force it to `false`.
- Article deletion is unsupported. Use `hide-article`; do not emulate deletion through unrelated fields.
- A requested paid publish must fail rather than silently become free when payment is unavailable. A separate free publish requires fresh user intent and a `free_default` brief.
- Prefer `coverBlank: true` over a fabricated cover. Stop on a missing local image rather than dropping or guessing it.
- Comment writes remain opt-in; publishing alone is not permission to inspect or create comments.
- With `list-comments --floor-comment-id <id>`, `"root_comment_missing": true` means the root fell outside the newest 50 top-level comments. Fetch additional top-level pages before replying when root context matters.

## Image Upload Boundaries

When uploading or embedding local images:
1. **Size Limit**: Keep files under **10MB** to avoid HTTP 413 (Request Entity Too Large) errors from default Nginx/OpenResty limits.
2. **Formats**: SVG is strictly forbidden (XSS risk). Use standard formats: JPEG, PNG, GIF, BMP, WEBP, TIFF, ICO.
3. **Filenames**: No character encoding restrictions (Chinese names are fully supported). The server automatically renames files to UUIDs, preventing path/encoding issues.

## Monetization & Payment Settings

Paid publishing is allowed only after the user explicitly requests it. The blog owner must configure the Afdian or Epay gateway, either in the admin panel or by intentionally supplying an approved payment config file.
- Set `primaryGoal: conversion`, `monetizationIntent: paid_explicit`, non-empty `whyPaid`, and front-matter `payType > 0` plus the required price fields.
- The CLI verifies the selected plugin and connection before publishing. If readiness checks fail, publishing stops; it does not silently remove the paywall.
- Never invent, request in chat, expose, or reuse gateway credentials without explicit user direction. Treat any supplied config file as sensitive.

## Script Usage

Save credentials once for all future commands (recommended for all frameworks, especially those without env persistence):

```bash
python {baseDir}/scripts/poetize_cli.py auth login --base-url "$POETIZE_BASE_URL" --api-key "$POETIZE_API_KEY"
python {baseDir}/scripts/poetize_cli.py auth show   # verify without printing the API key
```

After `auth login`, subsequent commands (`publish`, `manage`, `smoke-test`, etc.) no longer need `--base-url` or `--api-key`.

Generate framework config: `poetize_cli.py config --output <file> --format env|openclaw`. Use `--format env` for IDE agents (source into shell), `--format openclaw` for OpenClaw.
Run a read-only smoke test before first publish: `poetize_cli.py smoke-test`.

Start from the bundled strategy templates:

Create `article-brief.json` from `{baseDir}/assets/article-brief.template.json`.
Create `ops-brief.json` from `{baseDir}/assets/ops-brief.template.json`.

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

Note: `viewStatus` is omitted because `_brief.publishIntent` derives it. `payType` is omitted because `monetizationIntent: free_default` forces `payType: 0`.

The script rejects incomplete briefs, reports all missing fields together, and requires `alternativesConsidered` to contain 1-3 rejected angles that do not duplicate `selectedAngle`.

**Article brief** — used only by `publish`:

| Field | Valid values |
|---|---|
| `taskType` | `create_article` or `repurpose_article` for creation; `refresh_article` whenever `--article-id` is set |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `publishIntent` | `draft`, `public` |
| `monetizationIntent` | optional: `free_default` (default), `paid_explicit` |
| `whyPaid` | required non-empty string only for `paid_explicit` |
| `targetAudience`, `reasoning`, `selectedAngle` | non-empty strings |
| `alternativesConsidered` | required list of 1-3 rejected angles |

**Ops brief** — used by strategy-validated `manage` mutations; it has no article-only fields:

| Field | Valid values |
|---|---|
| `taskType` | `update_article`, `hide_article`, `update_section`, `update_translation` (for `save-translation`), `delete_translation`, or `regenerate_translation`; must match the command |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `reasoning`, `expectedOutcome` | non-empty strings |

Upload an image first when an explicit URL is preferable to publish-time upload:

```bash
python {baseDir}/scripts/poetize_cli.py upload-image --file ./assets/flow.png --type articleImage
```

List articles for operational filtering:

```bash
python {baseDir}/scripts/poetize_cli.py manage list-articles --search-key "AI" --sort-name "AI实践" --label-name "自动化" --current 1 --size 10
```

If an exact category or tag name does not match, the management script returns close candidates. Surface those for confirmation instead of guessing.

Fetch an existing article (use any one of `--article-id`, `--article-slug`, `--article-title-exact`):

```bash
python {baseDir}/scripts/poetize_cli.py manage get-article --article-id 123
```

Hide an existing article (`--stdin-brief` via heredoc — no Python wrapper needed):

```bash
python {baseDir}/scripts/poetize_cli.py manage hide-article --article-id 123 --stdin-brief --wait <<'BRIEF'
{"taskType":"hide_article","primaryGoal":"asset_maintenance","reasoning":"user requested hiding","expectedOutcome":"article is no longer public but remains recoverable"}
BRIEF
```

List and reply to comments (the "cold start" engagement loop — see Guardrails for the `root_comment_missing` caveat):

```bash
# Inspect the latest top-level comments for an article
python {baseDir}/scripts/poetize_cli.py manage list-comments --article-id 123 --size 10

# Reply to a specific comment as the AI persona. --floor-comment-id is not needed here —
# the backend derives it from --parent-comment-id server-side (see note below the manage table).
python {baseDir}/scripts/poetize_cli.py manage save-comment --article-id 123 --content "感谢分享，这个思路我们后续会展开讲！" --parent-comment-id 456 --parent-user-id 78 --as-ai

# No comments yet: post a top-level welcome/question to bootstrap discussion
python {baseDir}/scripts/poetize_cli.py manage save-comment --article-id 123 --content "欢迎留言交流～"
```

### Manage subcommands reference

`get-article`, `update-article`, `hide-article`, and `article-analytics` accept exactly one of `--article-id <id>`, `--article-slug <slug>`, or `--article-title-exact <title>`. Comment, translation, and section commands require `--article-id`.

| Subcommand | Purpose | Key flags |
|---|---|---|
| `list-articles` | List/filter articles | `--search-key`, `--sort-name`, `--label-name`, `--exact-title`, `--current`, `--size` |
| `get-article` | Fetch one article | `--article-id` / `--article-slug` / `--article-title-exact` |
| `update-article` | Update metadata via raw JSON; use `publish` or `update-section` for content | `--payload-file` / `--stdin-payload`, `--brief-file` / `--stdin-brief`, `--wait` |
| `hide-article` | Set `viewStatus=false` | `--brief-file` / `--stdin-brief`, `--password`, `--tips`, `--wait` |
| `article-analytics` | Get article stats | article target only |
| `site-visits` | Site visit trends | `--days 7` or `--days 30` |
| `theme-status` | Current theme info | none |
| `activate-theme` | Switch theme | `--plugin-key <key>` (required) |
| `seo-status` | SEO status | none |
| `seo-get-config` | Read SEO config | none |
| `seo-set-config` | Update SEO config | `--config-file <path>` (required) |
| `sitemap-update` | Refresh sitemap | none |
| `task-status` | Get asynchronous task status | `--task-id <id>` (required) |
| `list-comments` | List comments of an article (requires backend `v5.0.1`+) | `--article-id <id>`, `--floor-comment-id <id>` (required to page a specific floor's replies), `--current`, `--size` |
| `save-comment` | Post or reply to a comment (requires backend `v5.0.1`+) | `--article-id <id>`, `--content <text>`, `--parent-comment-id <id>`, `--parent-user-id <id>`, `--floor-comment-id <id>` (optional, ignored — see note below), `--as-ai` |
| `get-translation` | Fetch an article's translation for a specific language (requires backend `v5.0.1`+) | `--article-id <id>`, `--language <code>` (default: `en`) |
| `list-translation-languages` | List available translation languages for an article (requires backend `v5.0.1`+) | `--article-id <id>` |
| `save-translation` | Save/overwrite a manual translation (requires backend `v5.0.1`+) | article/language/title/content, `--brief-file` / `--stdin-brief` |
| `delete-translation` | Delete one translation (requires backend `v5.0.1`+) | article/language, `--brief-file` / `--stdin-brief` |
| `regenerate-translation` | Delete all translations and re-run AI translation (requires backend `v5.0.1`+) | article, `--brief-file` / `--stdin-brief`, `--wait` / `--poll-interval` / `--timeout` |
| `update-section` | Edit one section (requires backend `v5.0.1`+) | article/action, optional heading/content/`--new-heading-level`/`--skip-ai-translation`, brief, `--wait` / `--poll-interval` / `--timeout` |

For `update-article`, do not combine `--stdin-payload` with `--stdin-brief`: both read the same stream sequentially. Put at least one JSON object in a file.

> `--floor-comment-id` behaves differently across the two commands above. In `list-comments` it is a real query filter that selects which floor's replies to page through. In `save-comment` it is a no-op: the backend always recomputes `floorCommentId` from `parentCommentId` server-side and only logs a warning if the client value disagrees. Omit it when replying — just pass `--parent-comment-id` and `--parent-user-id`.

Switch the global article theme:

```bash
python {baseDir}/scripts/poetize_cli.py manage activate-theme --plugin-key academic
```

Update controlled SEO config:

```bash
python {baseDir}/scripts/poetize_cli.py manage seo-set-config --config-file seo.json
```

## Publish & Update Articles

Write the Markdown file first, then publish through the unified CLI. Inline `_brief` means no `--brief-file` is needed.

```bash
# article.md contains front matter with publishIntent: draft
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --draft --wait
```

### Publish flags reference

| Flag | Purpose | When to use |
|---|---|---|
| `--markdown-file <path>` | **(required)** Path to the Markdown file | Always |
| `--publish` | Assert public visibility | Only with `_brief.publishIntent: public` |
| `--draft` | Assert draft/private visibility | Only with `_brief.publishIntent: draft` |
| `--article-id <id>` | Update an existing article | Requires `_brief.taskType: refresh_article` |
| `--force` | Bypass only the required-H2 check | Intentionally sectionless body; H1 remains invalid |
| `--allow-create-taxonomy` | Auto-create missing category and tag | Explicitly confirmed taxonomy creation |
| `--allow-create-sort` | Auto-create missing category only | Explicitly confirmed category creation |
| `--allow-create-label` | Auto-create missing tag only | Explicitly confirmed tag creation |
| `--cover-file <path>` | Local cover image path | Custom cover |
| `--payment-plugin-key <key>` | Select payment plugin | Paid articles only |
| `--payment-config-file <path>` | Configure payment plugin from sensitive JSON | Explicitly authorized paid setup only |
| `--require-paid` | Compatibility flag for strict paid mode | Paid checks already fail closed |
| `--brief-file <path>` | External article brief JSON | Separate strategy audit trail |
| `--stdin-brief` | Read article brief JSON from stdin | Only with actual piped/heredoc JSON |
| `--print-payload` | Print payload without sending | Local debugging; payload may contain sensitive fields |

> **`--wait` — optional convenience flag for async commands.** When added, the CLI blocks and polls the task-status endpoint (or `list-translation-languages` for translation commands) until completion or `--timeout` (default 900s). Use `--poll-interval` (default 2.0s) to adjust the poll cadence.
>
> **Default recommendation: do NOT add `--wait`.** Let the CLI return the task id immediately, then poll yourself. This keeps you in control: you can interleave other work, cancel mid-flight, and avoid a long-blocking CLI process. Poll with `manage task-status --task-id <id>` for `publish` / `hide-article` / `update-article` tasks, and `manage list-translation-languages --article-id <id>` for `update-section` (without `--skip-ai-translation`) / `regenerate-translation` tasks.
>
> **When `--wait` is appropriate:** one-shot waits where you have nothing else to do (e.g. publish a draft and immediately verify the id), human terminal use, or shell scripts.
>
> **Commands where `--wait` has no effect (do not add):** all read-only `manage` commands (`list-articles`, `get-article`, `list-comments`, `get-translation`, `list-translation-languages`, analytics/SEO/theme/taxonomy reads), `config`, `smoke-test`, `eval`, `upload-image`, and `update-section --skip-ai-translation`. These return synchronously; the parser accepts `--wait` but ignores it.
>
> **True global flags** (every command): `--base-url`, `--api-key`.

For an existing article, fetch it first and rebuild `updated.md` from the returned title/content. Set `_brief.taskType: refresh_article` and set `publishIntent` to the current visibility unless the user requested a change:

```bash
python {baseDir}/scripts/poetize_cli.py manage get-article --article-id 123
python {baseDir}/scripts/poetize_cli.py publish --markdown-file updated.md --article-id 123 --wait
```

## Section-Level Editing & Translation Management

Full-article rewrites via `publish --article-id` are wasteful when you only need to fix a typo, update one section, or correct a translation. Two lighter-weight editing paths avoid regenerating the entire article:

### Section-level content updates (`manage update-section`)

Edit a single section by heading instead of rewriting the whole Markdown file. The backend locates it case-insensitively outside code fences, saves the change, refreshes the summary, and schedules translation regeneration unless skipped.

**Heading workflow:**

1. Run `manage get-article` immediately before editing and treat returned `articleContent` as authoritative.
2. Article bodies must use H2-H6 only. For `replace`, start the content file with the stored heading level; use `--new-heading-level 2..6` only when the user explicitly requests a hierarchy change.
3. For inserted/appended content, choose a level consistent with its intended position; new top-level sections use H2. H1 is always rejected.
4. If a heading matches multiple sections, use a more specific heading from the stored content.

| Action | `--heading` | `--content-file` | Result |
|---|---|---|---|
| `replace` | required | required | Replace heading and body; preserve level unless `--new-heading-level` is explicit |
| `insert_after` / `insert_before` | required | required | Insert content around the matched section |
| `delete` | required | omitted | Remove the matched heading and body |
| `append` | omitted | required | Append content at article end |

```bash
python {baseDir}/scripts/poetize_cli.py manage update-section --article-id 123 \
  --heading "Installation" --action replace --content-file section.md \
  --brief-file update-section-brief.json
```

Use an ops brief with `taskType: update_section`. `--skip-ai-translation` skips translation regeneration only; the summary still updates.

### Translation management

For AI-translated articles, you can inspect, edit, delete, or regenerate translations without touching the original article content:

```bash
# Read-only inspection
python {baseDir}/scripts/poetize_cli.py manage list-translation-languages --article-id 123
python {baseDir}/scripts/poetize_cli.py manage get-translation --article-id 123 --language en

# Each mutation uses an ops brief whose taskType matches the command
python {baseDir}/scripts/poetize_cli.py manage save-translation --article-id 123 \
  --language en --title "My Article" --content-file translated.md \
  --brief-file update-translation-brief.json
python {baseDir}/scripts/poetize_cli.py manage delete-translation --article-id 123 \
  --language en --brief-file delete-translation-brief.json
python {baseDir}/scripts/poetize_cli.py manage regenerate-translation --article-id 123 \
  --brief-file regenerate-translation-brief.json
```

**When to use which editing path:**

| Scenario | Recommended command |
|---|---|
| Full content rewrite or structural reorganization | `publish --markdown-file <file> --article-id <id>` |
| Fix one section, add a section, or delete a section | `manage update-section` |
| Correct a specific translation without re-running AI | `manage save-translation` |
| All translations are stale after major edits | `manage regenerate-translation` |
| Metadata-only update (viewStatus, password, tips, etc.) | `manage update-article` |

> Mutating translation and section commands (`save-translation`, `delete-translation`, `regenerate-translation`, `update-section`) require `--stdin-brief` (or `--brief-file`) with the matching `taskType`: `update_translation`, `delete_translation`, `regenerate_translation`, or `update_section`. Read-only commands (`get-translation`, `list-translation-languages`) do not need a brief.
>
> All commands in this section require `awesome-poetize-open` backend `v5.0.1` or later. On older backends the CLI returns an explicit version-mismatch error instead of a raw HTTP 404/500.

## Failure Recovery & Safe Retry

For an asynchronous timeout or failure, avoid duplicate articles:
1. Query `manage task-status --task-id <taskId>`. If the task is pending/running, keep polling; do not submit another create.
2. If the response contains `articleId`, fetch that article before any write. Recover with `publish --article-id <id>` and an article brief using `taskType: refresh_article`; never retry as a new create.
3. Retry creation only after the task is terminally failed and `articleId` is absent, using the original create brief after correcting the cause.
4. If the created article must be taken down, use `hide-article` with an ops brief using `taskType: hide_article`.

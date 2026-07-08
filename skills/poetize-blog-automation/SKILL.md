---
name: poetize-blog-automation
description: 让 AI 帮你运营 POETIZE 博客：写文章并一键发布、更新或隐藏已有文章、管理分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。仅支持 awesome-poetize-open 开源版，不适用于原版 POETIZE。开源仓库：https://github.com/LeapYa/awesome-poetize-open
homepage: https://github.com/LeapYa/awesome-poetize-open/tree/main/skills/poetize-blog-automation
version: 2.0.0
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

装上这个技能，你可以让 AI 帮你完成 POETIZE 博客的日常运营：写文章并一键发布、更新或隐藏已有文章、管理分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。

仅支持 `awesome-poetize-open` 开源版，不适用于原版 POETIZE。开源仓库：https://github.com/LeapYa/awesome-poetize-open

定位是个人博客运营助手。发文默认策略：免费优先、维护优先、质量优先。

## Security Warnings

- `POETIZE_API_KEY` is a high-privilege credential. Store via `auth login` (0600), framework secure storage, or protected env. Never commit to source control or paste in chat.
- Publishing/updating/hiding affects public visibility immediately. Use `--draft` to preview before going live.
- Local images in Markdown are auto-uploaded to your blog server at publish time. Only reference images you intend to publish.
- Payment config files contain gateway credentials. Only use when intentionally configuring monetization; keep free articles as default.
- This skill only manages blog content. It does not modify server settings, access files outside the skill folder, or run arbitrary commands.

## Agent-First Execution Rules

- This skill is for `awesome-poetize-open` only. Do not use with the original POETIZE project. For other forks, verify endpoint names before the first write action.

- Use `{baseDir}` for any file path that points inside this skill folder.
- Prefer single-line shell commands in examples so they remain portable across shells.
- Use `python` in examples and switch to `python3` when that is the installed binary.
- All commands go through the unified CLI: `python {baseDir}/scripts/poetize_cli.py <command> [subcommand] ...`.
- Legacy scripts (`publish_post.py`, `manage_blog.py`, etc.) can still be invoked directly; their entry points delegate to the unified CLI.
- Invoke this skill only for explicit POETIZE tasks. Do not route generic writing or generic SEO requests here.
- Prefer `poetize_cli.py config` to generate framework config instead of hand-writing JSON. Use `--format env` for IDE agents (Trae/Qoder) or `~/.bashrc`, `--format openclaw` (default) for OpenClaw.
- For frameworks without env persistence (Trae, Qoder, Tencent CodeBuddy, ima copilot), use `poetize_cli.py auth login` to save credentials to `~/.config/poetize/credentials.json` (0600). All subsequent commands auto-read from this file. Credential resolution: CLI args > env vars > `~/.config/poetize/credentials.json`.
- Run `poetize_cli.py smoke-test` before the first real write action on a new Agent environment.
- Point `POETIZE_BASE_URL` at the public nginx/domain origin.
- Actual request path = `${POETIZE_BASE_URL}/api/api/...`; do not append `/api` inside the variable value itself.
- For the `publish` command, prefer inlining the brief as a `_brief` block in Markdown front matter. This avoids temporary brief files and keeps strategy metadata with the article content. Use `--stdin-brief` or `--brief-file` only when you need a separate strategy audit trail.
- For `manage update-article` and `manage hide-article`, use `--stdin-brief` with a shell heredoc to pipe brief JSON from stdin. Example: `python ... --stdin-brief <<'BRIEF' ... BRIEF`. Do NOT write Python wrapper scripts to pipe stdin.

Read [references/strategy-playbook.md](references/strategy-playbook.md) before deciding whether to create, refresh, or hide content.
Read [references/decision-matrix.md](references/decision-matrix.md) before setting publish posture, search posture, or paywall posture.
Read [references/creativity-workflow.md](references/creativity-workflow.md) before drafting article copy.
Run `python {baseDir}/scripts/poetize_cli.py eval` to verify the local strategy layer before shipping skill changes.

## Writing Voice

- Use first-person plural (`我们`) when writing about this project. Sound like an experienced developer talking with a friend: clear, practical, not academic.
- Avoid thesis-style setup lines and formulaic transitions. Do not use `说白了`/`不得不说`/`众所周知`/`接下来我们将探讨`/`不是...而是` in article copy.

## Pre-Writing Topic Validation

Before drafting a new article, validate the target keyword:
1. Search the keyword in a search engine. If top results are strong CSDN/Juejin articles, choose a longer-tail keyword instead.
2. If results are GitHub repos, scattered forum posts, or low-quality pages, treat as an opportunity and continue.
3. List related existing articles and internal-link opportunities. Query the article list if unknown.

## Content Layout Rules

- First paragraph includes the core keyword and clearly states what the article covers.
- Use comparison tables for choices, tradeoffs, version differences, and troubleshooting paths.
- Code blocks include helpful comments. One H1 at top, `##` for sections, `###`/`####` for subsections. No skipped levels.
- Use blockquotes (`>`) for tips, notes, and quotations. Add disclaimers for gray-area techniques.

## Workflow

1. Gather the publishing intent.
   Decide whether the user wants a draft or a public article.
   Decide whether the task is new content, old-content maintenance, taxonomy cleanup, SEO follow-up, or article takedown by hiding.
   Default to free content unless the user explicitly asks for a draft or a paywalled post.
   Complete the Pre-Writing Topic Validation before drafting any new article.
   Create a strategy brief before any mutating action.
2. Create the strategy brief.
   Use `{baseDir}/assets/article-brief.template.json` for article creation or article refresh work.
   Use `{baseDir}/assets/ops-brief.template.json` for update or hide operations.
   For the `publish` command, inline the brief into front matter as a `_brief` block (see step 5).
   Fill all 7 hard-required fields (`taskType`, `primaryGoal`, `targetAudience`, `publishIntent`, `reasoning`, `selectedAngle`, `alternativesConsidered`) before calling any mutating script. The script hard-validates and rejects the brief if any is missing, reporting all missing fields at once with per-field fix suggestions. `monetizationIntent` is optional and defaults to `free_default` when omitted.
   `alternativesConsidered` is mandatory and must be a list of 2-3 strings — never omit it, even if you only have one obvious angle. Always brainstorm at least 2 alternative angles first, then list the ones you rejected.
   If required brief information is missing, stop and ask for it.
3. Diverge, then converge.
   Produce 2 or 3 candidate angles first.
   Choose one final direction and record it as `selectedAngle`.
   Record the rejected candidates in `alternativesConsidered` (must have 2-3 items).
4. Write the article in Markdown following Content Layout Rules and Writing Voice.
   For images: either reference local files (CLI auto-uploads at publish time) or upload first via `poetize_cli.py upload-image` and embed the URL.
   When the task is maintenance, prefer revising existing articles over creating duplicates.
5. Add front matter for routing and publishing metadata.
   At minimum provide `title`, `sort` or `sortId`, and `label` or `labelId`.
   When using inline `_brief`, omit `viewStatus` and `payType` — the strategy layer derives them from `_brief.publishIntent` and `_brief.monetizationIntent`.
   Use existing `sort`/`label` names when IDs are unknown. The script queries `/api/categories` and `/api/tags` for exact matches; close matches are suggestions only.
   For most personal blogs, keep `monetizationIntent: free_default`. Set `coverBlank: true` when no cover is needed.

   Front matter field reference:

   | Field | Required | Default | Notes |
   |---|---|---|---|
   | `title` | Yes (or H1 in body) | — | Article title; falls back to first H1 if omitted |
   | `sort` / `sortName` / `sortId` | Yes for new | — | Category name or ID |
   | `label` / `labelName` / `labelId` | Yes for new | — | Tag name or ID |
   | `articleSlug` / `slug` | No | auto | SEO-friendly URL slug |
   | `commentStatus` | No | `true` | Enable comments |
   | `recommendStatus` | No | `false` | Feature in recommendations |
   | `submitToSearchEngine` | No | follows `viewStatus` | Push to search engines |
   | `viewStatus` | No | derived from `_brief.publishIntent` | Omit when using inline `_brief` |
   | `cover` | No | platform default | Cover image URL |
   | `coverBlank` | No | `false` | Set `true` to skip cover |
   | `coverFile` | No | — | Local cover file path (uploaded at publish time) |
   | `coverStoreType` / `storeType` | No | — | Override cover storage type |
   | `video` | No | — | Video URL |
   | `password` | No | auto for drafts | Password for private articles |
   | `tips` | No | auto for drafts | Preview tip for private articles |
   | `payType` | No | derived from `_brief.monetizationIntent` | Omit when using inline `_brief` |
   | `payAmount` | No | — | Price for paid articles |
   | `freePercent` | No | — | Free preview percentage |
   | `skipAiTranslation` | No | `false` | Skip AI translation |
   | `pendingTranslationLanguage` | No | — | Target translation language |
   | `pendingTranslationTitle` | No | — | Translated title |
   | `pendingTranslationContent` | No | — | Translated content |
   | `paymentPluginKey` | No | — | Payment plugin key (e.g. `afdian`) |
   | `paymentConfigFile` | No | — | Payment config JSON path |
   | `requirePaid` | No | `false` | Fail if payment plugin not ready |
   | `_brief` | No | — | Inline strategy brief (see Workflow step 2) |
6. Write the Markdown file to a local path, then publish through the unified CLI.
   Use `python {baseDir}/scripts/poetize_cli.py publish --markdown-file <file>` for create or content update flows driven by Markdown.
   When the Markdown has an inline `_brief` block, no `--brief-file` is needed.
   Agent runtime only needs:
   `POETIZE_BASE_URL`
   `POETIZE_API_KEY`
  - If the markdown body references local images or local `<img src="...">` files, the `poetize_cli.py publish` command uploads them automatically before sending the article payload.
   For paid posts, the script will check `/api/payment/plugin/status` first, but paid publishing is not the default path for this skill.
   If the payment plugin is installed but not configured, provide `paymentPluginKey` in front matter and optionally pass `--payment-config-file payment.json`.
7. Operate existing articles, themes, analytics, and SEO via `python {baseDir}/scripts/poetize_cli.py manage <subcommand>` (see Manage subcommands reference below).
   This skill does not support article deletion — use `hide-article` to take down a post.
   `update-article` and `hide-article` require `--stdin-brief` (or `--brief-file`).
8. Return the final result. Prefer `--wait` so the script polls until the async task finishes.

## Guardrails

- Free, public content is the default. Do not suggest paywalls unless the user explicitly asks for monetization.
- Every mutating action requires a strategy brief. If the brief is missing or contradictory, stop and ask.
- Do not invent `sortId`/`labelId`. Use `sort`/`label` names. Do not silently create categories/tags; ask for confirmation unless `--allow-create-*` is set.
- Confirm article switches (`commentStatus`, `recommendStatus`, `viewStatus`, `submitToSearchEngine`) explicitly when the user mentions them.
- This skill does not delete articles. To take down a post, hide it (`viewStatus: false`).
- For article updates, omit fields that should stay unchanged. Do not send placeholder content.
- For paid articles, keep `payType` explicit. If paid publishing is unavailable and the user did not insist, downgrade to `payType: 0`.
- Prefer `coverBlank: true` over inventing a fake cover URL.
- Keep images as local files until publish time. If a local image file does not exist, stop and fix the path.
- All mutating commands use hard strategy validation.

## Script Usage

Save credentials once for all future commands (recommended for all frameworks, especially those without env persistence):

```bash
python {baseDir}/scripts/poetize_cli.py auth login --base-url "https://your-blog.example.com" --api-key "your-api-key"
python {baseDir}/scripts/poetize_cli.py auth show   # verify
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
submitToSearchEngine: true
_brief:
  taskType: create_article
  primaryGoal: asset_maintenance
  targetAudience: "想理解 AI 自动化的读者"
  publishIntent: public
  reasoning: "补齐博客在 AI 自动化主题上的长期内容资产"
  selectedAngle: "实用维护视角"
  alternativesConsidered: ["宽泛入门 overview", "战术 checklist"]
  monetizationIntent: free_default
---

# 示例文章

正文...
```

Note: `viewStatus` is omitted above because `_brief.publishIntent: public` derives it. `payType` is omitted because `monetizationIntent: free_default` forces `payType: 0`.

Common mistake (the script hard-rejects this — `alternativesConsidered` is the most-forgotten field):

```yaml
# WRONG — alternativesConsidered omitted, brief will be rejected
_brief:
  taskType: create_article
  selectedAngle: "实用维护视角"
  # no alternativesConsidered -> REJECTED

# RIGHT — alternativesConsidered is a 2-3 item list of rejected angles
_brief:
  taskType: create_article
  selectedAngle: "实用维护视角"
  alternativesConsidered: ["宽泛入门 overview", "战术 checklist"]
```

`_brief` required fields (7 are hard-mandatory — the script rejects the brief if any is missing and reports all missing fields at once with fix suggestions; `monetizationIntent` is optional, defaults to `free_default`):

| Field | Valid values |
|---|---|
| `taskType` | `create_article`, `refresh_article`, `hide_article`, `create_ops_post`, `refresh_ops_post`, `hide_ops_post` |
| `primaryGoal` | `asset_maintenance`, `audience_growth`, `conversion`, `content_maintenance` |
| `publishIntent` | `public`, `private`, `hidden` |
| `monetizationIntent` | optional — `free_default` (default if omitted), `free_promote`, `paid_explicit` |
| `targetAudience` | non-empty string describing the reader |
| `reasoning` | non-empty string explaining the strategy |
| `selectedAngle` | non-empty string naming the chosen angle |
| `alternativesConsidered` | **mandatory** list of 2-3 strings — the alternative angles you rejected; never omit even with a single obvious angle |

Upload an image first and get its URL (recommended for Agent workflows):

```bash
python {baseDir}/scripts/poetize_cli.py upload-image --file ./assets/flow.png --type articleImage
# Or upload base64 from stdin: echo "iVBOR..." | poetize_cli.py upload-image --stdin-base64 --filename diagram.png --type articleImage
```

Publish (write the Markdown file first, then publish — inline `_brief` in front matter, no `--brief-file` needed):

```bash
# Step 1: write article.md (front matter + _brief + body)
# Step 2: publish — use --draft for draft, --article-id <id> for update
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --publish --wait
```

### Publish flags reference

| Flag | Purpose | When to use |
|---|---|---|
| `--markdown-file <path>` | **(required)** Path to the Markdown file | Always |
| `--publish` | Force public visibility | User wants the article live immediately |
| `--draft` | Save as draft/private | User wants to preview before going live |
| `--article-id <id>` | Update an existing article | Editing an already-published article |
| `--wait` | Poll until async task finishes | Recommended for all publishes; skip for fire-and-forget |
| `--timeout <sec>` | Max wait time (default: 900) | Long articles that may exceed 15 min |
| `--poll-interval <sec>` | Seconds between polls (default: 2.0) | Rarely needed; tuning only |
| `--force` | Skip heading structure validation | Body has only H1, no subheadings |
| `--allow-create-taxonomy` | Auto-create missing category and tag | User explicitly confirms new taxonomy |
| `--allow-create-sort` | Auto-create missing category only | |
| `--allow-create-label` | Auto-create missing tag only | |
| `--cover-file <path>` | Local cover image path | Custom cover, otherwise platform default |
| `--payment-plugin-key <key>` | Payment plugin (e.g. `afdian`) | Paid articles only |
| `--payment-config-file <path>` | Payment gateway credentials JSON | Paid articles that need config |
| `--require-paid` | Fail if payment plugin not ready | Strict paid-article workflow |
| `--brief-file <path>` | External brief JSON file | Only when you need a separate strategy audit trail |
| `--stdin-brief` | Read brief JSON from stdin | Agent workflows that pipe brief from another step |
| `--print-payload` | Print JSON payload before sending | Debugging only; do not use in production |

List articles for运营筛选:

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
{"taskType":"hide_article","primaryGoal":"content_maintenance","targetAudience":"existing blog readers","publishIntent":"hidden","reasoning":"user requested hiding","selectedAngle":"direct hide by ID","alternativesConsidered":["delete (not supported)","update as draft (unnecessary)"],"monetizationIntent":"free_default"}
BRIEF
```

Update an existing article (get → edit → publish with `--article-id`):

```bash
python {baseDir}/scripts/poetize_cli.py manage get-article --article-id 123
# After editing, write to updated.md, then:
python {baseDir}/scripts/poetize_cli.py publish --markdown-file updated.md --article-id 123 --publish --wait
```

### Manage subcommands reference

All `manage` subcommands support `--article-id <id>`, `--article-slug <slug>`, or `--article-title-exact <title>` as the article target (where applicable).

| Subcommand | Purpose | Key flags |
|---|---|---|
| `list-articles` | List/filter articles | `--search-key`, `--sort-name`, `--label-name`, `--exact-title`, `--current`, `--size` |
| `get-article` | Fetch one article | `--article-id` / `--article-slug` / `--article-title-exact` |
| `update-article` | Update article fields via raw JSON payload (metadata-level: viewStatus/password/tips etc.; for content rewrites or adding sections use `publish --article-id`) | `--stdin-payload` or `--payload-file`, `--stdin-brief`, `--wait` |
| `hide-article` | Set viewStatus=false | `--stdin-brief`, `--password`, `--tips`, `--wait` |
| `article-analytics` | Get article stats | article target only |
| `site-visits` | Site visit trends | `--days 7` or `--days 30` |
| `theme-status` | Current theme info | none |
| `activate-theme` | Switch theme | `--plugin-key <key>` (required) |
| `seo-status` | SEO status | none |
| `seo-get-config` | Read SEO config | none |
| `seo-set-config` | Update SEO config | `--config-file <path>` (required) |
| `sitemap-update` | Refresh sitemap | none |

Switch the global article theme:

```bash
python {baseDir}/scripts/poetize_cli.py manage activate-theme --plugin-key academic
```

Update controlled SEO config:

```bash
python {baseDir}/scripts/poetize_cli.py manage seo-set-config --config-file seo.json
```

---
name: poetize-blog-automation
description: 让 AI 帮你运营 POETIZE 博客：写文章并一键发布、更新或隐藏已有文章、管理分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。仅支持 awesome-poetize-open 开源版，不适用于原版 POETIZE 或其他博客系统，也不用于与 POETIZE 无关的通用写作或 SEO 咨询。开源仓库：https://github.com/LeapYa/awesome-poetize-open
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
- For frameworks with native credential/env persistence (e.g. OpenClaw, Hermes): credentials/env are configured natively within the framework (via settings UI or skill config JSON) and injected automatically as environment variables.
- For local IDE/Agent runtimes lacking env persistence (e.g. Trae, Qoder): save credentials globally via `auth login` (stored in `~/.config/poetize/credentials.json`).
- For stateless/ephemeral cloud sandboxes (e.g. ima copilot, Doubao Office Task Mode): store credentials locally in the skill folder (`{baseDir}/credentials.json` via `auth login --local`) or pass via CLI args/env.
- Credential resolution priority: CLI args > env vars > global config (`~/.config/poetize/credentials.json`) > local skill config (`{baseDir}/credentials.json`) > CWD config (`./credentials.json`).
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
3. **Run `manage list-articles` to fetch existing articles and build an internal-link plan.** Record:
   - Older articles this new post should link to.
   - Older articles that should later link back to this post.
   - If the keyword overlaps heavily with an existing article, prefer `refresh_article` over `create_article`.
   If the article list cannot be fetched (no API access yet), note this in `reasoning` and revisit internal links after publishing.

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
   `alternativesConsidered` is mandatory and must be a list of **1-3** strings. Never omit it. If the angle is genuinely singular, include the one obvious alternative and briefly explain in `reasoning` why other directions were not viable. The goal is to show deliberate thinking, not to hit a number.
   If required brief information is missing, stop and ask for it.
3. Diverge, then converge.
   Produce 2 or 3 candidate angles first.
   Choose one final direction and record it as `selectedAngle`.
   Record the rejected candidates in `alternativesConsidered` (1-3 items; a single item is valid when the angle is genuinely singular, see step 2).
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
   | `_brief` | No | — | Inline strategy brief (see Workflow step 2) |
6. Write the Markdown file to a local path, then publish through the unified CLI.
   Use `python {baseDir}/scripts/poetize_cli.py publish --markdown-file <file>` for create or content update flows driven by Markdown.
   When the Markdown has an inline `_brief` block, no `--brief-file` is needed.
   **Draft-first recommended path for new articles:** publish with `--draft` first to create the article and get its ID, verify it looks correct via `manage get-article --article-id <id>`, then promote to public with `--article-id <id> --publish`. This prevents pushing an unreviewed article directly to public.
   Agent runtime only needs:
   `POETIZE_BASE_URL`
   `POETIZE_API_KEY`
  - If the markdown body references local images or local `<img src="...">` files, the `poetize_cli.py publish` command uploads them automatically before sending the article payload.
   For paid posts, the script will check `/api/payment/plugin/status` first, but paid publishing is not the default path for this skill.
   If the payment plugin is installed but not configured, provide `paymentPluginKey` in front matter and optionally pass `--payment-config-file payment.json`.
7. Operate existing articles, themes, analytics, and SEO via `python {baseDir}/scripts/poetize_cli.py manage <subcommand>` (see Manage subcommands reference below).
   - This skill does not support article deletion — use `hide-article` to take down a post.
   - `update-article` and `hide-article` require `--stdin-brief` (or `--brief-file`).
   - **Comment engagement is optional, user-decided — not a default follow-up to every publish**: after publishing, or during routine maintenance, you may mention that `manage list-comments`/`manage save-comment` are available for checking reader feedback or bootstrapping engagement, and ask whether the user wants to run them. Only execute them when the user asks about comments/interaction, or explicitly confirms after your suggestion. Do not run them unprompted just because an article was just published. When used, `manage list-comments --article-id <id>` and `manage save-comment --article-id <id> --content <text>` form a read-then-write pair: check for reader feedback, then reply to specific comments or post a welcome/discussion-starter when there are none yet. Pass `--as-ai` to respond as the configured AI Assistant persona, or omit it to speak as the Blog Owner.
   - **Backend version requirement**: `manage list-comments` and `manage save-comment` only work against `awesome-poetize-open` backend `v5.0.1` or later — the underlying `/api/api/comment/list` and `/api/api/comment/save` endpoints do not exist on older backends. If the backend predates this version, the CLI returns an explicit version-mismatch error instead of a raw HTTP 404/500; treat that error as "ask the user to upgrade the backend," not as a bug in this skill. All other commands are unaffected by this version requirement.
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
- **Comment Engagement**: This is a suggestion to offer, not an automatic action. During site maintenance, or after the user asks about comments/interaction, you may propose running `manage list-comments` to check for reader feedback and `manage save-comment` (as AI or Blog Owner) to reply or bootstrap engagement — but only act once the user agrees. Publishing an article alone is not a trigger to check or write comments.
- **`list-comments` root-comment gap**: when calling `manage list-comments --floor-comment-id <id>` on an article with more than 50 top-level comments, the response may include `"root_comment_missing": true` if that floor's root comment falls outside the newest 50. In that case `formatted_tree` shows replies without their root context — treat replies at face value or fetch more pages of top-level comments (no `--floor-comment-id`) to locate the root manually before replying.

## Image Upload Boundaries

When uploading or embedding local images:
1. **Size Limit**: Keep files under **10MB** to avoid HTTP 413 (Request Entity Too Large) errors from default Nginx/OpenResty limits.
2. **Formats**: SVG is strictly forbidden (XSS risk). Use standard formats: JPEG, PNG, GIF, BMP, WEBP, TIFF, ICO.
3. **Filenames**: No character encoding restrictions (Chinese names are fully supported). The server automatically renames files to UUIDs, preventing path/encoding issues.

## Monetization & Payment Settings

The Agent can mark articles as paid, but **the blog owner (human user) must pre-configure the payment gateway (Afdian or Epay) in the POETIZE admin panel**.
- To request paid locking: Set `monetizationIntent: paid_explicit` in the brief, and specify price details in front matter (`payType`, `payAmount`).
- If no active payment plugin is configured on the server, the CLI will automatically downgrade the article to free (`free_default`) or fail.
- The Agent should *never* attempt to configure credentials, private keys, or certificates.

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
# WRONG — alternativesConsidered omitted or empty, brief will be rejected
_brief:
  taskType: create_article
  selectedAngle: "实用维护视角"
  # no alternativesConsidered -> REJECTED

# RIGHT (typical) — list 2-3 angles you considered and rejected
_brief:
  taskType: create_article
  selectedAngle: "实用维护视角"
  alternativesConsidered: ["宽泛入门 overview", "战术 checklist"]

# ALSO VALID — single item when the angle is clearly singular (explain why in reasoning)
_brief:
  taskType: create_article
  selectedAngle: "实用维护视角"
  reasoning: "此选题目标极度聚焦，宽泛综述会稀释搜索意图，付费方向不符合当前目标"
  alternativesConsidered: ["宽泛入门 overview（已排除：搜索意图不匹配）"]
```

Two different brief shapes exist. Use the one matching your command — do not mix their fields.

**Article brief** (7 fields hard-mandatory — for `publish` command's inline `_brief` or `--brief-file`; the script rejects the brief if any is missing and reports all missing fields at once with fix suggestions; `monetizationIntent` is optional, defaults to `free_default`):

| Field | Valid values |
|---|---|
| `taskType` | `create_article`, `refresh_article` (use `refresh_article` when `--article-id` is set), `repurpose_article` |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `publishIntent` | `draft`, `public` |
| `monetizationIntent` | optional — `free_default` (default if omitted), `paid_explicit` |
| `targetAudience` | non-empty string describing the reader |
| `reasoning` | non-empty string explaining the strategy |
| `selectedAngle` | non-empty string naming the chosen angle |
| `alternativesConsidered` | **mandatory** list of **1-3** strings — angles you considered but rejected. Minimum 1 item. When only one direction makes sense, include it and explain in `reasoning` why others were ruled out. |

**Ops brief** (4 fields hard-mandatory — for `manage update-article` / `manage hide-article` via `--stdin-brief` or `--brief-file`; no `publishIntent`/`targetAudience`/`selectedAngle`/`alternativesConsidered` here):

| Field | Valid values |
|---|---|
| `taskType` | `update_article` (for `manage update-article`), `hide_article` (for `manage hide-article`) — must match the subcommand exactly |
| `primaryGoal` | `asset_maintenance`, `seo_growth`, `brand_expression`, `conversion` |
| `reasoning` | non-empty string explaining the strategy |
| `expectedOutcome` | non-empty string describing the expected outcome of the ops action |

Upload an image first and get its URL (recommended for Agent workflows):

```bash
python {baseDir}/scripts/poetize_cli.py upload-image --file ./assets/flow.png --type articleImage
# Or upload base64 from stdin: echo "iVBOR..." | poetize_cli.py upload-image --stdin-base64 --filename diagram.png --type articleImage
```

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
| `task-status` | Get asynchronous task status | `--task-id <id>` (required) |
| `list-comments` | List comments of an article (requires backend `v5.0.1`+) | `--article-id <id>`, `--floor-comment-id <id>` (required to page a specific floor's replies), `--current`, `--size` |
| `save-comment` | Post or reply to a comment (requires backend `v5.0.1`+) | `--article-id <id>`, `--content <text>`, `--parent-comment-id <id>`, `--parent-user-id <id>`, `--floor-comment-id <id>` (optional, ignored — see note below), `--as-ai` |

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

This is the core loop most invocations of this skill end in — write the Markdown file first, then publish through the unified CLI. Inline `_brief` in front matter means no `--brief-file` is needed.

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

Update an existing article (get → edit → publish with `--article-id`):

```bash
python {baseDir}/scripts/poetize_cli.py manage get-article --article-id 123
# After editing, write to updated.md, then:
python {baseDir}/scripts/poetize_cli.py publish --markdown-file updated.md --article-id 123 --publish --wait
```

## Failure Recovery & Safe Retry

Asynchronous publish may fail or time out. To prevent duplicate "zombie" articles, check status before retrying:
1. Run `python {baseDir}/scripts/poetize_cli.py manage task-status --task-id <taskId>` to query status.
2. If `articleId` is present in response, the article exists. Update it using `--article-id <id>` with publish. Do NOT retry a new publish.
3. If task failed and `articleId` is null, it is safe to retry publishing as a new article.
4. If article is corrupted, hide it via `manage hide-article --article-id <id>`.

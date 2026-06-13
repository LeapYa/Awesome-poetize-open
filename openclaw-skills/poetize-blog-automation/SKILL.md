---
name: poetize-blog-automation
description: 让 AI 帮你运营 POETIZE 博客：写文章并一键发布、更新或隐藏已有文章、管理分类和标签、切换博客主题、查看访问数据和趋势、配置 SEO。仅支持 awesome-poetize-open 开源版，不适用于原版 POETIZE。开源仓库：https://github.com/LeapYa/awesome-poetize-open
homepage: https://github.com/LeapYa/awesome-poetize-open/tree/main/openclaw-skills/poetize-blog-automation
version: 1.1.0
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

## English Overview

Use this skill to operate a POETIZE blog as a personal publishing and maintenance system.
It is built on the existing POETIZE API feature, not browser automation.
It is free-first, growth-first, and maintenance-first.
It is not monetization-first.

## Public Distribution Position

- Treat this as an Agent skill integration for the open-source awesome-poetize-open release.
- It is designed for the open-source branch of POETIZE.
- Before publishing to registries (like ClawHub), remove secrets, local machine paths, and private content from any bundled files.
- When publishing publicly, link back to the source repository and keep the applicable license and attribution notices intact.
- Prefer a project-prefixed public slug such as `awesome-poetize-open-blog-automation` so the registry entry stays tied to the open-source project.

## Compatibility

- Required project version: `awesome-poetize-open`.
- Expected API surface: the POETIZE admin API routes documented in [references/poetize-api.md](references/poetize-api.md).
- Do not use this skill with the original POETIZE project.
- For other forks, verify endpoint names and payload shapes before the first write action.

## Agent-First Execution Rules

- Use `{baseDir}` for any file path that points inside this skill folder.
- Prefer single-line shell commands in examples so they remain portable across shells.
- Use `python` in examples and switch to `python3` when that is the installed binary.
- All commands go through the unified CLI: `python {baseDir}/scripts/poetize_cli.py <command> [subcommand] ...`.
- Legacy scripts (`publish_post.py`, `manage_blog.py`, etc.) can still be invoked directly; their entry points delegate to the unified CLI.
- Invoke this skill only for explicit POETIZE tasks. Do not route generic writing or generic SEO requests here.
- Prefer `poetize_cli.py config` to generate OpenClaw config instead of hand-writing JSON.
- Run `poetize_cli.py smoke-test` before the first real write action on a new Agent environment.
- Point `POETIZE_BASE_URL` at the public nginx/domain origin.
- Actual request path = `${POETIZE_BASE_URL}/api/api/...`; do not append `/api` inside the variable value itself.
- For mutating commands, use `--stdin-brief` to pipe brief JSON from stdin instead of writing a temporary file. This avoids CLI escaping issues with Agent runtimes.

Read [references/poetize-api.md](references/poetize-api.md) before publishing, updating, querying, or operating articles.
Read [references/agent-setup.md](references/agent-setup.md) when connecting the skill to your Agent framework.
Read [references/strategy-playbook.md](references/strategy-playbook.md) before deciding whether to create, refresh, or hide content.
Read [references/decision-matrix.md](references/decision-matrix.md) before setting publish posture, search posture, or paywall posture.
Read [references/creativity-workflow.md](references/creativity-workflow.md) before drafting article copy.
Read [references/evaluation-scenarios.md](references/evaluation-scenarios.md) when validating the strategy layer.
Run `python {baseDir}/scripts/poetize_cli.py eval` to verify the local strategy layer before shipping skill changes.

### Breaking Changes

- All mutating commands now require a strategy brief. Provide it via `--brief-file <path>` or `--stdin-brief` (pipe JSON from stdin). The `--stdin-brief` option avoids writing temporary files and is recommended for Agent workflows to save tokens.
- `manage update-article` also accepts `--stdin-payload` as an alternative to `--payload-file` for the same reason.
- `manage hide-article` also requires `--brief-file` or `--stdin-brief`.
- The Agent should treat `assets/article-brief.template.json` and `assets/ops-brief.template.json` as the starting point for strategy briefs.

## Operating Position

- Act like a blog editor, content operator, and maintenance assistant.
- Optimize for long-term content quality, search visibility, and low-effort maintenance.
- Default to free, public, searchable content unless the user clearly asks for a draft or private post.
- Treat paid posts as an uncommon edge case for established blogs with clear conversion intent.
- Prefer improving old content, reorganizing taxonomy, and maintaining content quality over pushing paywalls.

## Writing Voice

- When writing about features, decisions, or tradeoffs in `awesome-poetize-open`, use first-person plural: `我们`.
- Do not describe our own project from a detached third-party angle such as `项目维护者`.
- Sound like an experienced developer talking with a friend: clear, practical, a little conversational, and not academic.
- Recommend our own project features with confidence when they fit the user need, but keep the claim grounded in what the feature actually does.
- Avoid thesis-style setup lines and formulaic transitions. The article should move because the idea moves, not because filler connectors are inserted.
- Do not use these phrases in article copy, summaries, titles, or social snippets:
  - `说白了`
  - `不得不说`
  - `众所周知`
  - `接下来我们将探讨`
  - contrast frames shaped like `不是...而是`

## Pre-Writing Topic Validation

Before drafting a new article or doing a major article refresh, answer these questions and record the answers in the article brief:

1. What is the target keyword?
   Record it as `targetKeyword`, using the user's keyword when provided: `{{目标关键词}}`.
2. What does the search result page look like?
   Search the exact keyword in a search engine and review the top 10 results.
   If the top results are already strong, polished CSDN or Juejin articles, abandon that keyword and choose a longer-tail keyword.
   If the results are mostly GitHub repositories, scattered forum posts, stale articles, or low-quality pages, treat the keyword as an opportunity and continue.
3. How does this article connect to our existing articles?
   List related existing articles, the places where internal links should be added, and whether this article should link out to older articles or receive links from them.
   If internal-link opportunities are unknown, query the existing article list or ask the user before drafting.

## Content Layout Rules

- The first paragraph must include the core keyword and clearly tell readers and search engines what the article covers.
- Use comparison tables when they make the answer easier to scan, especially for choices, tradeoffs, version differences, feature comparisons, and troubleshooting paths.
- Code blocks should include helpful comments unless the code is already trivial. Use comments to lower the reading cost, not to repeat obvious syntax.
- Each major section may end with one short summary sentence that restates the useful takeaway in plain language.
- Add a disclaimer at the end when the article touches gray-area techniques, security bypasses, scraping, automation that may violate platform rules, reverse engineering, or similar sensitive topics.

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
   Fill `targetKeyword`, `serpValidation`, `internalLinkPlan`, `contentLayoutPlan`, `primaryGoal`, `reasoning`, and the required brief fields before calling any mutating script.
   If required brief information is missing, stop and ask for it.
3. Diverge, then converge.
   Produce 2 or 3 candidate angles first.
   Choose one final direction and record it as `selectedAngle`.
   Record the rejected candidates in `alternativesConsidered`.
4. Write the article in Markdown.
   Prefer a single H1 at the top.
   Keep the body as clean Markdown.
   Use short lead paragraphs, clear section headings, lists only when they improve readability, and avoid HTML unless required.
   Follow the Writing Voice rules before optimizing for SEO density.
   Follow the Content Layout Rules before publishing or updating the article.
   If the article includes images, you have two options:
   a. Save images as local files and reference them from the Markdown file with relative paths such as `![示意图](./assets/diagram.png)`. The CLI will upload those local images through `/api/resource/upload` before publishing and replace them with returned URLs.
   b. Upload images first using `poetize_cli.py upload-image`, get the remote URL, and embed it directly in the Markdown. This avoids local file management and is recommended for Agent workflows.
   When the task is maintenance, prefer revising existing articles over creating duplicates.
5. Add front matter for routing and publishing metadata.
   Use the front matter keys documented in [references/poetize-api.md](references/poetize-api.md).
   At minimum provide `title`, `sort` or `sortId`, and `label` or `labelId`.
   Prefer existing `sort` and `label` names when IDs are unknown.
   The script will query `/api/categories` and `/api/tags` first and reuse exact matches.
   If exact matches fail, the script may return close category or tag candidates for confirmation.
   Close matches are suggestions only and must not be auto-selected.
   It will not create a new category or tag unless you explicitly allow it.
   Be explicit about article switches when the user mentions them:
   `commentStatus` for comments
   `recommendStatus` for recommendation
   `viewStatus` for visibility
   `submitToSearchEngine` for search-engine push
   `articleSlug` or `slug` for an optional SEO-friendly article URL
   Only set paid fields when the user explicitly wants a paywall and the content clearly deserves it.
   For most personal blogs, keep `payType: 0`.
   When the user does not want to upload a cover, set `coverBlank: true` or `cover: " "`.
6. Publish through the unified CLI.
   Use `python {baseDir}/scripts/poetize_cli.py publish --markdown-file <file> --brief-file <file>` for create or content update flows driven by Markdown.
   Agent runtime only needs:
   `POETIZE_BASE_URL`
   `POETIZE_API_KEY`
   `--brief-file` is mandatory.
  - If the markdown body references local images or local `<img src="...">` files, the `poetize_cli.py publish` command uploads them automatically before sending the article payload.
   For paid posts, the script will check `/api/payment/plugin/status` first, but paid publishing is not the default path for this skill.
   If the payment plugin is installed but not configured, provide `paymentPluginKey` in front matter and optionally pass `--payment-config-file payment.json`.
7. Operate existing articles, themes, analytics, and SEO through the unified CLI.
   Use `python {baseDir}/scripts/poetize_cli.py manage <subcommand>`.
   It supports:
   article listing
   exact-title lookup
   article detail
   article update
   article hide
   global article theme switching
   article analytics
   site visit trends
   controlled SEO config
   sitemap update
   It does not support article deletion.
   If the user wants a post effectively removed from public view, hide it by setting `viewStatus: false`.
   `update-article` and `hide-article` now require `--brief-file`.
8. Return the final result.
   Prefer `--wait` so the script polls until the async task finishes.
   Report the returned `articleId`, `articleSlug`, `articleUrl`, task status, visibility state, and any follow-up analytics when requested.

## Guardrails

- Treat public publishing as a deliberate action. If the user asks for a draft, keep `viewStatus: false` or run with `--draft`.
- Treat free content as the default operating mode for personal blogs.
- Do not proactively suggest paywalls for ordinary blog posts.
- Only suggest a paid article when the user explicitly wants monetization or the content is unusually high-value, structured, and conversion-oriented.
- Prefer article maintenance, article refresh, taxonomy cleanup, and SEO hygiene over monetization features.
- Every mutating action must be backed by a strategy brief.
- If the strategy brief is missing, incomplete, or contradictory, stop instead of guessing.
- Do not guess article switches from vague phrasing. Confirm or set `commentStatus`, `recommendStatus`, `viewStatus`, and `submitToSearchEngine` explicitly when the user mentions them.
- If `viewStatus: false`, the script will auto-fill a draft password and preview tip when omitted.
- Do not invent `sortId` or `labelId`. Use `sort` and `label` names when IDs are unknown.
- Do not silently create categories or tags. If the requested `sort` or `label` does not exist, stop and ask for confirmation unless `allowCreateTaxonomy`, `allowCreateSort`, or `allowCreateLabel` is explicitly set.
- Taxonomy fuzzy matching is for candidate suggestions only.
- Never auto-select a category or tag from fuzzy candidates without explicit confirmation.
- If the user gives only a topic, generate the article first, then publish it. Do not send placeholder content to the API.
- If a manual translation is provided, pass `pendingTranslationLanguage`, `pendingTranslationTitle`, and `pendingTranslationContent`.
- For article updates, omit category/tag/status fields when they should stay unchanged.
- Do not delete articles through this skill.
- If the user asks to delete a post, explain that this skill does not support deletion and use article hiding instead.
- Hiding an article is the supported "take it down" action and is implemented with `viewStatus: false`.
- For paid articles, keep the chosen `payType` explicit. Do not guess a paywall mode from prose alone.
- Paid articles require an enabled and configured payment plugin in POETIZE plugin management. The script can configure an installed payment plugin through the API-key payment endpoints, but it will not install plugin packages.
- If paid publishing is unavailable and the user did not insist on a paywall, allow the script to downgrade to `payType: 0`.
- If no cover should be uploaded, prefer `coverBlank: true` over inventing a fake cover URL.
- If the user provides images, keep them as local files until publish time and reference them from Markdown instead of inventing remote URLs.
- If a Markdown image points to a local file that does not exist, stop and fix the file path before publishing.
- `poetize_cli.py publish` uses hard strategy validation.
- `poetize_cli.py manage update-article` and `hide-article` use hard strategy validation.

## Script Usage

Show command usage or subcommand usage:

```bash
# Show root help
python {baseDir}/scripts/poetize_cli.py help

# Show help for the publish command
python {baseDir}/scripts/poetize_cli.py help publish

# Show help for the manage list-articles command
python {baseDir}/scripts/poetize_cli.py help manage list-articles
```

Generate a usable OpenClaw config (when using OpenClaw):

```bash
python {baseDir}/scripts/poetize_cli.py config --output openclaw.poetize.local.json --api-key "replace-with-poetize-api-key"
```

Run a read-only smoke test before the first publish or update:

```bash
python {baseDir}/scripts/poetize_cli.py smoke-test --base-url "https://your-blog.example.com" --api-key "replace-with-poetize-api-key"
```

Start from the bundled strategy templates:

Create `article-brief.json` from `{baseDir}/assets/article-brief.template.json`.
Create `ops-brief.json` from `{baseDir}/assets/ops-brief.template.json`.

Front matter example with the common publish switches:

```md
---
title: "示例文章"
slug: "ai-automation-example"
sort: "AI实践"
label: "自动化"
commentStatus: true
recommendStatus: false
viewStatus: true
submitToSearchEngine: true
---

# 示例文章

正文...
```

Markdown image example with local files:

```md
---
title: "带图文章示例"
sort: "AI实践"
label: "自动化"
---

# 带图文章示例

这里是一张本地图片：

![流程图](./assets/flow.png)
```

At publish time, the CLI uploads `./assets/flow.png` and rewrites the Markdown image target to the returned URL.

Upload an image first and get its URL (recommended for Agent workflows):

```bash
# Upload a local image file
python {baseDir}/scripts/poetize_cli.py upload-image --file ./assets/flow.png --type articleImage

# Upload a base64-encoded image from stdin (e.g. AI-generated image)
echo "iVBORw0KGgo..." | python {baseDir}/scripts/poetize_cli.py upload-image --stdin-base64 --filename diagram.png --type articleImage

# Upload a cover image
python {baseDir}/scripts/poetize_cli.py upload-image --file ./cover.jpg --type articleCover
```

After uploading, embed the returned URL directly in Markdown:

```md
![流程图](https://your-blog.example.com/resource/uploaded-flow.png)
```

Publish a new draft and wait:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file draft.md --brief-file article-brief.json --draft --wait
```

Publish a public article and wait:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --brief-file article-brief.json --publish --wait
```

Update an existing article:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --brief-file article-brief.json --article-id 123 --publish --wait
```

Publish a paid article and require payment readiness:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file paid-article.md --brief-file article-brief.json --payment-plugin-key afdian --payment-config-file payment.json --require-paid --publish --wait
```

Start an async task without waiting:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --brief-file article-brief.json
```

Allow creating a missing category and tag only when explicitly confirmed:

```bash
python {baseDir}/scripts/poetize_cli.py publish --markdown-file article.md --brief-file article-brief.json --allow-create-taxonomy --publish --wait
```

List articles for运营筛选:

```bash
python {baseDir}/scripts/poetize_cli.py manage list-articles --search-key "AI" --sort-name "AI实践" --label-name "自动化" --current 1 --size 10
```

If an exact category or tag name does not match, the management script returns close candidates and stops.
The Agent should surface those candidates for confirmation instead of guessing.

Hide an existing article:

```bash
python {baseDir}/scripts/poetize_cli.py manage hide-article --article-id 123 --brief-file ops-brief.json --wait
```

Deletion policy:

- This skill does not support deleting articles.
- To achieve a deletion-like effect, hide the article instead.
- Hiding removes it from normal public visibility by updating `viewStatus` to `false`.

Switch the global article theme:

```bash
python {baseDir}/scripts/poetize_cli.py manage activate-theme --plugin-key academic
```

Update controlled SEO config:

```bash
python {baseDir}/scripts/poetize_cli.py manage seo-set-config --config-file seo.json
```

## Expected Inputs

- Topic, outline, or markdown draft
- Existing article ID or exact title when operating on an existing post
- Strategy brief JSON for every mutating command
- Target category and tag
- Whether the task is publishing, updating, hiding, reorganizing, or maintaining old content
- Whether to save as draft or publish
- Whether comments are enabled, whether the article is recommended, whether it is visible, and whether it should be pushed to search engines
- Whether the article is free or paid, though free should be treated as the default
- Whether to upload a cover, reuse a cover URL, or intentionally leave it blank
- Whether the article includes local images that should be uploaded and rewritten during publish
- Whether to switch the global article theme
- Whether to read article analytics, site visit trends, or controlled SEO status/config
- Base URL and API key, usually via Agent runtime variables

## Expected Outputs

- Final markdown article
- Strategy-normalized brief decisions
- Normalized publish payload
- Uploaded cover URL when a local cover file is provided
- Uploaded article image URLs when local Markdown images are provided
- Async `taskId` and task status URL when not waiting
- Final `articleId`
- Final `articleSlug` when configured
- Final `articleUrl`
- Existing article detail or exact-title lookup result when operating on old content
- Hidden article result when the user wants deletion-like takedown behavior
- Global article theme status or activation result
- Article analytics and site visit trend data when requested
- Controlled SEO status/config result and sitemap trigger result when requested

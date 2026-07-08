# Strategy Playbook

Use this playbook when OpenClaw needs to decide what kind of blog action to take before touching the Poetize API.

## Default position

- Treat the blog as a long-term content asset, not a short-term monetization funnel.
- Default to free publishing.
- Prefer maintaining and upgrading existing articles before creating near-duplicate new ones.
- Use hiding instead of deletion when a post should be taken down from public view.

## Topic validation before writing

Before drafting a new article or making a major refresh, record:

- `targetKeyword`: the exact keyword the article is trying to win, for example `{{目标关键词}}`
- `serpValidation`: what the top 10 search results look like
- `internalLinkPlan`: how the article connects to our existing articles

Keyword decision rules:

- If the top 10 results are dominated by strong CSDN or Juejin articles, switch to a longer-tail keyword before writing.
- If the top 10 results are mostly GitHub repositories, scattered forum posts, stale articles, or low-quality pages, continue because there is likely room for a better article.
- If search results cannot be checked from the current environment, ask the user for a top-10 snapshot or postpone drafting until the keyword can be verified.

Internal-link decision rules:

- Reuse existing article list/search before drafting when article history is available.
- Identify older articles this article should link to.
- Identify older articles that should later link back to this article.
- Prefer refreshing an existing article when the keyword overlaps heavily with content we already have.

## Action selection

Choose one action before executing:

- `create_article`
  - Use when the topic is meaningfully new and does not belong inside an existing article.
- `refresh_article`
  - Use when an article already exists and the best move is to improve, expand, or fix it.
- `repurpose_article`
  - Use when the new post is clearly a different angle or audience from the source material.
- `hide_article`
  - Use when the post should be removed from normal public visibility without deleting it.

## Publishing defaults

- Public by default for finished content.
- Draft when the user explicitly asks for preview, internal review, or unfinished work.
- Recommended only when the content is unusually strong or strategically important.
- Search push on by default for public evergreen content.
- Comments on by default unless the content is sensitive, low-value, or likely to attract noise.

## Monetization rules

- Default: `payType: 0`
- Do not suggest a paywall for ordinary personal-blog posts.
- Only allow paid publishing when:
  - the user explicitly asks for it
  - the content has clear conversion value
  - the task goal is `conversion`
- If those conditions are not met, force the content back to free.

## Maintenance-first heuristics

Prefer updating or hiding over creating when:

- the topic substantially overlaps an existing article
- the old article is outdated but still relevant
- the taxonomy structure would become noisier by adding another post
- the new draft would fragment traffic or search intent

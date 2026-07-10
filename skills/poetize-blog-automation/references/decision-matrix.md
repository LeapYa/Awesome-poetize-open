# Decision Matrix

Use `primaryGoal` to decide the default posture of the article.

| primaryGoal | Typical use | viewStatus | submitToSearchEngine | recommendStatus | payType |
| --- | --- | --- | --- | --- | --- |
| `asset_maintenance` | Refresh, organize, preserve content value | public unless clearly a draft | true unless explicitly paused | usually false | `0` |
| `seo_growth` | Evergreen tutorials, searchable guides | public | true unless explicitly paused | optional | `0` |
| `brand_expression` | Voice, narrative, positioning | public or draft based on polish | true when public unless explicitly paused | optional | `0` |
| `conversion` | Explicit monetization or lead capture | public if ready | true when public unless explicitly paused | optional | can be `> 0` only when explicitly requested |

## Required implications

- If `primaryGoal != conversion`, then `payType` must resolve to `0`.
- Article creation, full `publish --article-id` updates, and `manage update-article` default `submitToSearchEngine` to `true` when omitted.
- A public article may explicitly set `submitToSearchEngine: false`, for example while frequent edits should not trigger search submission.
- If `publishIntent = draft`, then both `viewStatus` and `submitToSearchEngine` must resolve to `false`.
- If `taskType = hide_article`, then both `viewStatus` and `submitToSearchEngine` must resolve to `false`.
- Section and translation mutations preserve the article's current `submitToSearchEngine` value.
- If the user asks to delete a post, convert that request into `hide_article`.

## Taxonomy rules

- Reuse exact category and tag matches whenever possible.
- If exact matches fail, offer close candidates.
- Never auto-select a fuzzy taxonomy candidate.
- Never auto-create taxonomy unless explicit creation is confirmed.

# News posts

One markdown file per post: `website/content/news/<yyyy-mm-dd>-<slug>.md`. The date prefix orders posts (newest
first) and the slug becomes the URL: `/news/<slug>`. Use lower-case letters, digits and hyphens in the slug.

## Front matter

```md
---
title: Introducing VANTA Client
date: 2026-10-04
summary: One or two sentences for the list page and meta description (10–300 characters).
author: VANTA team
tags: [announcement, client]
---
```

| Field | Required | Rules |
| --- | --- | --- |
| `title` | yes | 3–120 characters |
| `date` | yes | `YYYY-MM-DD`; posts dated in the future are still rendered, so do not pre-date |
| `summary` | yes | plain text, no markdown |
| `author` | yes | a person or "VANTA team"; never invent names |
| `tags` | no | up to 8, inline array, `[a-z0-9-]` only |
| `draft` | no | `true` hides the post from the website |

The front matter is flat `key: value` lines; strings may be quoted, arrays are inline. Validate before committing:

```bash
node scripts/release/validate-json.mjs --front-matter shared/schemas/news-post.schema.json website/content/news/*-*.md
node scripts/release/check-links.mjs website/content
```

## Body

GitHub-flavoured markdown rendered through the website's sanitised renderer (raw HTML is dropped). Start with a
paragraph, use `##` for sections (the `#` title comes from the front matter), link to documentation with full URLs or
site-relative paths the website knows (`/documentation/installation`). Images must be real files committed to the
repository; never link to screenshots that do not exist yet.

## Honesty rules

- Announce only what exists or what the release workflow will do; no dates for unfinished work.
- No testimonials, download counts, performance numbers or quotes that were not measured or said.
- Do not mention support channels unless they are configured; the Support page handles that.

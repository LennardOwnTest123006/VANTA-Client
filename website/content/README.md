# Website content

Data-driven content rendered by the website. Everything here is loaded at build time with `import.meta.glob`
(see `website/src/lib/content.ts`), validated by the JSON schemas in `shared/schemas/` and link-checked by
`scripts/release/check-links.mjs`.

| Folder | File name | Front matter | Schema | Used by |
| --- | --- | --- | --- | --- |
| `changelog/` | `<product>-<version>.md` | `product` (client, launcher, website), `version`, `date`, `title`, `minecraftVersion` (required for client and launcher, must be `1.21.11`) | `changelog-entry.schema.json` | `/changelog`, Download page excerpts, GitHub Release notes (the release workflow strips the front matter and appends the checksums) |
| `news/` | `<yyyy-mm-dd>-<slug>.md` | `title`, `date`, `summary`, `author`, optional `tags`, `draft` | `news-post.schema.json` | `/news`, `/news/<slug>` — see [`news/README.md`](news/README.md) |

Changelog bodies use the headings `## Added`, `## Improved`, `## Fixed` (any subset, in that order) and optionally
`## Notes`. Keep the entries in sync with the root `CHANGELOG.md`; the release manifest in `shared/releases/` points
at the changelog file through its `changelog` field.

The Download page card quotes at most three bullets of the release it offers, each cut to three lines: the bullets of
`## Fixed` first, then `## Added` and `## Improved` (`releaseHighlights` in `src/lib/content.ts`). Only when these
sections are missing or have no bullets does it quote the bullets of the intro and of other sections; `## Notes` is
never quoted. Put the bullet that matters most first in its section. The launchers download the release notes of
an update from `raw.githubusercontent.com/<repository>/HEAD/<changelog path>` when they show it, so a change to the
notes of a published launcher version reaches running launchers without a new release. Their reader is plain
(`MiniMarkdown` in the launcher): link markup `[text](url)` keeps only the text there, so write a URL the reader needs
as a bare `https://…` URL (GitHub and the website still turn it into a link). The update dialog shows the notes in a
small scroll pane that starts at the intro, so when a note under `## Notes` must be read before downloading, the
intro should point to it (as the intro of `launcher-1.0.1.md` does for launcher 1.0.0 users).

Front matter is a flat `key: value` block between `---` lines: strings (optionally quoted), numbers, `true`/`false`
and inline arrays `[a, b]`. The website keeps every value as a string; the validator parses numbers, booleans and
arrays so the schemas can check them. Markdown is GitHub-flavoured and rendered through the sanitised renderer in
`website/src/lib/markdown-renderer.tsx` (raw HTML is skipped).

Validate everything in this folder:

```bash
node scripts/release/validate-json.mjs --front-matter shared/schemas/changelog-entry.schema.json website/content/changelog/*.md
node scripts/release/validate-json.mjs --front-matter shared/schemas/news-post.schema.json website/content/news/*-*.md
node scripts/release/check-links.mjs website/content
```

# VANTA website

Official website of VANTA Client — a static single-page application built with Vite 7, React 19,
TypeScript 5.9 and Tailwind CSS 4, deployed to Netlify (configuration in the repository root
`netlify.toml`, `base = "website"`).

`public/_redirects` (SPA fallback) and `public/_headers` (the security and caching headers, copied
verbatim from the `[[headers]]` blocks in `netlify.toml`) are shipped inside `dist/`. Netlify's manual
drag-and-drop deploys do not read `netlify.toml`, so these two files make a manual deploy of `dist/`
behave like a git-connected deploy. Keep them in sync when `netlify.toml` changes.
`netlify.toml` also sets `VITE_SITE_URL = "https://vanta-client.netlify.app"` in `[build.environment]`, so a
repository-linked build has the same absolute sitemap, robots.txt and social images as a local
`VITE_SITE_URL=https://vanta-client.netlify.app npm run build` deployed by hand.

## Commands

| Command                           | What it does                                                                                                                                                                                                                       |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm install`                     | Installs dependencies (Node 22, npm 10). `.npmrc` pins `legacy-peer-deps` so `npm install` and `npm ci` resolve identically.                                                                                                       |
| `npm run dev`                     | Vite dev server with HMR.                                                                                                                                                                                                          |
| `npm run build`                   | `tsc -b`, production build into `dist/`, then `scripts/check-bundle-size.mjs` enforces the 180 kB gzip budget for the initial JavaScript, prints every chunk and deletes the build manifest `dist/.vite/` so it is never deployed. |
| `npm run preview`                 | Serves `dist/` (used by the end-to-end tests).                                                                                                                                                                                     |
| `npm test` / `npm run test:watch` | Unit tests (Vitest + Testing Library, jsdom).                                                                                                                                                                                      |
| `npm run test:e2e`                | Playwright end-to-end tests against the production build. Set `PLAYWRIGHT_CHROMIUM_PATH` to use a preinstalled Chromium.                                                                                                           |
| `npm run lint`                    | ESLint (type-aware) + `tsc --noEmit` for the app and tooling projects.                                                                                                                                                             |
| `npm run format` / `format:check` | Prettier.                                                                                                                                                                                                                          |
| `npm run tokens`                  | Regenerates `src/styles/tokens.css` from `shared/design/tokens.json`. The output is committed.                                                                                                                                     |
| `npm run check`                   | The full acceptance run: `lint`, `test`, `build`, `test:e2e`.                                                                                                                                                                      |

Acceptance locally:

```bash
npm install && PLAYWRIGHT_CHROMIUM_PATH=/opt/pw-browsers/chromium npm run check
```

The end-to-end run starts two servers: `vite preview` on port 4173 serving `dist/`, and a second
build in `dist-e2e-support/` (port 4174) made with `VITE_SUPPORT_EMAIL`, `VITE_DISCORD_URL` and
`VITE_SITE_URL` set, so the support page is tested with and without configured channels and the
absolute social images of `index.html` are checked (the raw HTML of deep routes must not carry a
canonical or `og:url` for `/`). Every route is checked for
status, title, h1, a clean console, canonical/Open Graph tags and an `@axe-core/playwright` scan
with zero serious or critical violations; a crawler follows every internal link.

## Environment variables

All optional; see `.env.example`. Read through the typed, validated accessor in `src/lib/env.ts`.

| Variable                     | Purpose                                                                                                                                                                                                                                                                                                                                                                                                 |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VITE_DOWNLOAD_LAUNCHER_URL` | Overrides the launcher download URL from the release manifest.                                                                                                                                                                                                                                                                                                                                          |
| `VITE_DOWNLOAD_CLIENT_URL`   | Overrides the client jar download URL from the release manifest.                                                                                                                                                                                                                                                                                                                                        |
| `VITE_RELEASES_BASE_URL`     | Base URL of the published release manifests (link on the download page).                                                                                                                                                                                                                                                                                                                                |
| `VITE_SUPPORT_EMAIL`         | Support address shown in the footer (hidden when empty).                                                                                                                                                                                                                                                                                                                                                |
| `VITE_DISCORD_URL`           | Community Discord link (hidden when empty).                                                                                                                                                                                                                                                                                                                                                             |
| `VITE_GITHUB_URL`            | Source repository. Defaults to `https://github.com/LennardOwnTest123006/VANTA-Client`; set to an empty string to hide every GitHub link.                                                                                                                                                                                                                                                                |
| `VITE_SITE_URL`              | Public origin of the deployment (no trailing slash). Used for the canonical URLs and Open Graph tags `PageMeta` sets, absolute sitemap URLs (Netlify's `URL` is used when unset) and the absolute `og:image`/`twitter:image` of the static `index.html` (only with `VITE_SITE_URL`; without it they stay relative). `index.html` never gets `og:url` or a canonical link: it is served for every route. |

Download URLs are never hardcoded. Each card offers the newest **published** release of its product
(`latestRelease` in `src/lib/releases.ts`: the newest manifest with at least one `downloadUrl`). A new
version is committed with an unpublished manifest before the release workflow fills it; until then
the card keeps offering the previous release and mentions the upcoming version (`upcomingRelease`),
and the changelog shows that version's notes with a "Release pending" badge. Only when no manifest of
a product is published (or an environment override is missing) does the card show its honest
"Not published yet — release pending" state, keeping version, date and file facts visible.
Once the release workflow has filled a manifest, its card keeps the main button (launcher: the
`.msi`; client: exactly `vanta-client-<version>.jar`, plus the mods bundle) and lists every file of
the manifest with a label derived from the file name (`describeReleaseFile` in `src/lib/downloads.ts`),
size, SHA-256 and its own link. The GitHub release page is linked only when it can be derived from a
manifest URL of the form `https://github.com/<owner>/<repo>/releases/download/<tag>/<file>`
(`githubReleasePageUrl` in `src/lib/releases.ts`). The unit tests render both states from the fixture
manifests in `src/test/fixtures/releases.ts` (fake values, test-only), so they do not depend on the
state of the repository manifests; the end-to-end tests read `shared/releases/` and
`content/changelog/` (`e2e/repo-state.ts`) and check whichever state the build has.

The full release zip (`VantaClient-<version>-Release.zip`, built by the bundle workflow from the
published files of both products) has its own manifests in `shared/releases/bundles/vanta-<version>.json`
(`src/lib/bundles.ts`). The download page shows a third card for the newest published stable bundle
(`latestBundle`) and no card at all while no bundle manifest is published; the "Latest version" block
and the "What's new" section come from the newest published release manifests and their changelog
entries. Fixtures: `src/test/fixtures/bundles.ts`.

## Project layout

```
website/
├── content/                 Markdown content loaded at build time (changelog/, news/)
├── e2e/                     Playwright tests (every route + axe, docs search, FAQ, changelog/news,
│                            support states, SEO files, link crawler, screenshots for review)
├── plugins/                 sitemap.ts — Vite plugin emitting sitemap.xml and robots.txt
├── public/                  Static files: favicons, brand SVGs, self-hosted woff2 fonts (+ OFL licenses)
├── scripts/                 generate-tokens.mjs, check-bundle-size.mjs
└── src/
    ├── components/
    │   ├── ui/              Button, Card, Container, Section, Badge, Pill/Tag, Stat, Callout, Logo,
    │   │                    SkipLink, ThemeMeta, RouteLink, CopyButton, CopyLinkButton, Drawer,
    │   │                    EmptyState, Kbd
    │   ├── layout/          Header (sticky, mobile sheet with focus trap), Footer, Layout, PageMeta, Reveal
    │   ├── page/            PageHero, PresetTable, TocNav, PrevNext, IsoCube
    │   ├── home/            Hero, TrustSection, FeatureGrid, InterfacePreview, HowItWorks, FinalCta
    │   ├── download/        DownloadCard, ReleaseFileList, InstallOptions
    │   ├── docs/            DocsSidebar, DocsSearch (MiniSearch combobox), DocToc (scroll spy)
    │   ├── changelog/       ChangelogEntryCard
    │   ├── news/            NewsCard
    │   ├── faq/             FaqAccordion
    │   └── screenshots/     ScreenshotGrid, Lightbox
    ├── config/              site.ts (product facts), siteNav.ts (routes + readiness), footer.ts,
    │                        features.ts, presets.ts, support.ts (support topics → docs anchors)
    ├── lib/                 env.ts, releases.ts, downloads.ts, front-matter.ts, content.ts (changelog),
    │                        docs.ts, news.ts, faq.ts, search.ts, screenshots.ts, slug.ts,
    │                        markdown*.tsx (lazy renderer + remark plugins), sitemap.ts, prefetch.ts,
    │                        use-content.ts, thenable.ts, format.ts, hooks.ts, cn.ts, meta.ts
    ├── pages/               Home, Download, Features, Performance, Screenshots, Changelog, News,
    │                        NewsArticle, Documentation, Support, Faq, About, Legal, NotFound
    ├── styles/              index.css (Tailwind @theme, fonts, base, primitives), tokens.css (generated)
    ├── routes.tsx           Route table (lazy pages, `*` → NotFoundPage)
    └── main.tsx
```

## Content pipeline

All content is markdown with a flat front matter block, parsed by the dependency-free subset parser
in `src/lib/front-matter.ts` (strings, integers, booleans, inline arrays). Unit tests load every
real file and fail the build when a required field is missing.

| Source                                            | Loader                   | Loading                                        | Rendered at                                                                            |
| ------------------------------------------------- | ------------------------ | ---------------------------------------------- | -------------------------------------------------------------------------------------- |
| `../docs/*.md` (repository root)                  | `src/lib/docs.ts`        | lazy, one `docs-content` chunk                 | `/documentation`, `/documentation/:slug`, `/faq` (from `faq.md`), `/privacy`, `/terms` |
| `content/news/<date>-<slug>.md`                   | `src/lib/news.ts`        | lazy, one `news-content` chunk, drafts skipped | `/news`, `/news/:slug`                                                                 |
| `content/changelog/<product>-<v>.md`              | `src/lib/content.ts`     | eager (the download page shows excerpts)       | `/changelog`, Download page                                                            |
| `../shared/releases/*.json`                       | `src/lib/releases.ts`    | eager                                          | Download page, footer, site facts                                                      |
| `../assets/screenshots/*.png` (+ `captions.json`) | `src/lib/screenshots.ts` | eager asset URLs                               | `/screenshots` (honest empty state when none)                                          |

Markdown is rendered by react-markdown + remark-gfm in a lazy chunk with raw HTML skipped and
links/images sanitised. Two small remark plugins (`src/lib/markdown-plugins.ts`) add GitHub-style
heading ids (same algorithm as `scripts/release/check-links.mjs`) and rewrite documentation
cross-links such as `installation.md#2-verify-the-checksum` to `/documentation/installation#…`.
Documentation search indexes every page section with MiniSearch (loaded on first use) and links
straight to the matching anchor.

## Adding a page

1. Create `src/pages/<Name>Page.tsx` with a default export and a `<PageMeta>`.
2. Register a lazy `<Route>` in `src/routes.tsx` (before the `*` route) and a loader in
   `src/lib/prefetch.ts` so header/footer links prefetch the chunk on hover.
3. Flip `ready: true` for the entry in `src/config/siteNav.ts`. The header, the footer columns,
   every `RouteLink` and the sitemap pick the page up automatically.
4. Add the route to `e2e/routes.ts` so the route, accessibility and screenshot suites cover it.

## Design system

`shared/design/tokens.json` is canonical. `npm run tokens` writes `src/styles/tokens.css`
(`--vanta-*` custom properties); `src/styles/index.css` maps them onto Tailwind 4 `@theme`
namespaces (`--color-surface-1`, `--radius-lg`, `--font-display`, …) so utilities such as
`bg-surface-1`, `text-accent-violet`, `rounded-xl` and `font-display` use the shared palette.
Fonts (Inter, Space Grotesk) are self-hosted woff2 subsets under `public/fonts/` with their SIL OFL
licenses and are preloaded in `index.html`.

## Accessibility and performance

Landmarks and a skip link on every page, visible violet focus rings, keyboard-operable mobile menu
and documentation drawer (focus trap, Escape, focus restore), an accessible FAQ accordion and
search combobox, a lightbox with arrow-key navigation, and `prefers-reduced-motion` support. Every
route is scanned with axe-core in the e2e suite. Routes are code-split with hover prefetching;
React, the markdown renderer, MiniSearch and each content collection are separate chunks; the
initial JavaScript stays well under the 180 kB gzip budget. Per-route `<title>`, description,
canonical URL, Open Graph/Twitter tags and `robots` directives are set by `PageMeta`;
`sitemap.xml` and `robots.txt` are generated at build time.

After a redeploy, a tab that is still open asks for page chunks whose hashed names no longer exist.
`src/lib/stale-chunks.ts` records the chunks Vite reports with `vite:preloadError`; when one of them
keeps a page from rendering, the page error boundary in `Layout.tsx` reloads the page once per browser
session and build (guarded in `sessionStorage`, never while `navigator.onLine` is false). A failed
hover/focus prefetch never reloads the page being read. If the chunk still fails, the boundary shows
the error for that page only and clears it on the next navigation. Deep links to
an anchor (`/changelog#launcher-1.0.0`) keep the target in place while lazily rendered markdown above
it settles, until the user scrolls (`src/lib/scroll.ts`).

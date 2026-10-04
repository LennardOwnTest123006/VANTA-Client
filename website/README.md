# VANTA website

Official website of VANTA Client — a static single-page application built with Vite 7, React 19,
TypeScript 5.9 and Tailwind CSS 4, deployed to Netlify (configuration in the repository root
`netlify.toml`, `base = "website"`).

## Commands

| Command                           | What it does                                                                                                                                                     |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm install`                     | Installs dependencies (Node 22, npm 10). `.npmrc` pins `legacy-peer-deps` so `npm install` and `npm ci` resolve identically.                                     |
| `npm run dev`                     | Vite dev server with HMR.                                                                                                                                        |
| `npm run build`                   | `tsc -b`, production build into `dist/`, then `scripts/check-bundle-size.mjs` enforces the 180 kB gzip budget for the initial JavaScript and prints every chunk. |
| `npm run preview`                 | Serves `dist/` (used by the end-to-end tests).                                                                                                                   |
| `npm test` / `npm run test:watch` | Unit tests (Vitest + Testing Library, jsdom).                                                                                                                    |
| `npm run test:e2e`                | Playwright end-to-end tests against the production build. Set `PLAYWRIGHT_CHROMIUM_PATH` to use a preinstalled Chromium.                                         |
| `npm run lint`                    | ESLint (type-aware) + `tsc --noEmit` for the app and tooling projects.                                                                                           |
| `npm run format` / `format:check` | Prettier.                                                                                                                                                        |
| `npm run tokens`                  | Regenerates `src/styles/tokens.css` from `shared/design/tokens.json`. The output is committed.                                                                   |

Acceptance locally:

```bash
npm install && npm run lint && npm test && npm run build && \
PLAYWRIGHT_CHROMIUM_PATH=/opt/pw-browsers/chromium npm run test:e2e
```

## Environment variables

All optional; see `.env.example`. Read through the typed, validated accessor in `src/lib/env.ts`.

| Variable                     | Purpose                                                                                                                                  |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `VITE_DOWNLOAD_LAUNCHER_URL` | Overrides the launcher download URL from the release manifest.                                                                           |
| `VITE_DOWNLOAD_CLIENT_URL`   | Overrides the client jar download URL from the release manifest.                                                                         |
| `VITE_RELEASES_BASE_URL`     | Base URL of the published release manifests (link on the download page).                                                                 |
| `VITE_SUPPORT_EMAIL`         | Support address shown in the footer (hidden when empty).                                                                                 |
| `VITE_DISCORD_URL`           | Community Discord link (hidden when empty).                                                                                              |
| `VITE_GITHUB_URL`            | Source repository. Defaults to `https://github.com/LennardOwnTest123006/VANTA-Client`; set to an empty string to hide every GitHub link. |

Download URLs are never hardcoded. A release manifest in `shared/releases/` with an empty
`downloadUrl` (or a missing environment override) renders the download page in its honest
"Not published yet — release pending" state while keeping version, date and file facts visible.

## Project layout

```
website/
├── content/                 Markdown content loaded at build time (changelog/, news/)
├── e2e/                     Playwright tests (home, navigation, link crawler, screenshots)
├── public/                  Static files: favicons, brand SVGs, self-hosted woff2 fonts (+ OFL licenses)
├── scripts/                 generate-tokens.mjs, check-bundle-size.mjs
└── src/
    ├── components/
    │   ├── ui/              Button, Card, Container, Section, Badge, Pill/Tag, Stat, Callout, Logo,
    │   │                    SkipLink, ThemeMeta, RouteLink, CopyButton
    │   ├── layout/          Header (sticky, mobile sheet with focus trap), Footer, Layout, PageMeta, Reveal
    │   ├── page/            PageHero, PresetTable, TocNav, IsoCube
    │   ├── home/            Hero, TrustSection, FeatureGrid, InterfacePreview, HowItWorks, FinalCta
    │   └── download/        DownloadCard
    ├── config/              site.ts (product facts), siteNav.ts (routes + readiness), footer.ts,
    │                        features.ts (feature catalogue), presets.ts (Performance Center presets)
    ├── lib/                 env.ts, releases.ts, downloads.ts, content.ts, format.ts, markdown.tsx
    │                        (lazy renderer), hooks.ts, cn.ts, meta.ts
    ├── pages/               HomePage, DownloadPage, FeaturesPage, PerformancePage, NotFoundPage
    ├── styles/              index.css (Tailwind @theme, fonts, base, primitives), tokens.css (generated)
    ├── routes.tsx           Route table (lazy pages, `*` → NotFoundPage)
    └── main.tsx
```

## Adding a page

1. Create `src/pages/<Name>Page.tsx` with a default export and a `<PageMeta>`.
2. Register a lazy `<Route>` in `src/routes.tsx` (before the `*` route).
3. Flip `ready: true` for the entry in `src/config/siteNav.ts`. The header, the footer columns and
   every `RouteLink` pick the page up automatically; until then they render the label as plain text.
4. Markdown content: put files in `content/<collection>/` with front matter and load them with
   `import.meta.glob(..., { query: '?raw', import: 'default', eager: true })` through the helpers in
   `src/lib/content.ts`. Render with `<MarkdownBlock source={...} />` (react-markdown + remark-gfm in
   a lazy chunk, raw HTML skipped, links and images sanitised).

## Design system

`shared/design/tokens.json` is canonical. `npm run tokens` writes `src/styles/tokens.css`
(`--vanta-*` custom properties); `src/styles/index.css` maps them onto Tailwind 4 `@theme`
namespaces (`--color-surface-1`, `--radius-lg`, `--font-display`, …) so utilities such as
`bg-surface-1`, `text-accent-violet`, `rounded-xl` and `font-display` use the shared palette.
Fonts (Inter, Space Grotesk) are self-hosted woff2 subsets under `public/fonts/` with their SIL OFL
licenses and are preloaded in `index.html`.

## Accessibility and performance

Landmarks and a skip link on every page, visible violet focus rings, keyboard-operable mobile menu
(focus trap, Escape, focus restore), `prefers-reduced-motion` disables reveal animations and
transitions, 4.5:1 text contrast on all surfaces. Routes are code-split; React is in its own vendor
chunk; no animation or UI libraries, no analytics, no third-party requests at runtime.

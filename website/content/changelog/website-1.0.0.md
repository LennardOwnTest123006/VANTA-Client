---
product: website
version: 1.0.0
date: 2026-10-04
title: Website 1.0.0
minecraftVersion: 1.21.11
---

First version of the official VANTA Client website (Vite, React, TypeScript, Tailwind), deployed on Netlify.

## Added

- Home, Features, Performance and Download pages; the Download page reads the release manifests and shows version, date, size and SHA-256 for every file, with an honest "not published yet" state until the release workflow has run
- Changelog, News, searchable Documentation (rendered from `docs/`), Screenshots (real captures only, with an empty state until the first release), Support, FAQ, About, Privacy and Terms pages, plus a real 404 page
- Design system generated from the shared design tokens, self-hosted Inter and Space Grotesk, dark graphite look with violet/blue accents
- Accessibility: landmarks, skip link, keyboard-operable navigation, focus rings, reduced-motion support, 4.5:1 text contrast
- Netlify configuration with SPA fallback, strict security headers (CSP, frame denial, nosniff, referrer and permissions policies) and immutable caching for hashed assets
- Unit tests (vitest), end-to-end tests (Playwright) including a link crawler and accessibility scan, and a bundle size budget

## Notes

- The website has no analytics, no cookies and no third-party scripts
- Support channels (e-mail, Discord) appear only when the maintainers configure them; GitHub Issues is always linked

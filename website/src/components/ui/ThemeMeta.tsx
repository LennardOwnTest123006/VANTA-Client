import { useEffect } from 'react';

/** Theme colour reported to the browser UI (tab strip, status bar). Mirrors `bg.base`. */
export const THEME_COLOR = '#0B0B10';

/**
 * Keeps the document-level theme metadata in sync: `<meta name="theme-color">`, `color-scheme` and
 * a `data-theme` attribute for CSS hooks. The website ships a single dark theme by design.
 */
export function ThemeMeta() {
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = 'dark';
    root.style.colorScheme = 'dark';
    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'theme-color';
      document.head.appendChild(meta);
    }
    meta.content = THEME_COLOR;
  }, []);
  return null;
}

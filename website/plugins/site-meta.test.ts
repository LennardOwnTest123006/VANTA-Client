import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { ResolvedConfig } from 'vite';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { applySiteUrl, escapeAttribute, resolveSiteUrl, siteMetaPlugin } from './site-meta';

// Vitest runs with the website directory as its working directory.
const indexHtml = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');
const redirects = readFileSync(resolve(process.cwd(), 'public/_redirects'), 'utf8');

/** Runs the plugin's hooks the way Vite does for a build with the given environment. */
function transform(env: Record<string, string>, html = indexHtml): string {
  const plugin = siteMetaPlugin();
  const configResolved = plugin.configResolved as (config: ResolvedConfig) => void;
  const transformIndexHtml = plugin.transformIndexHtml as (html: string) => string;
  configResolved({ env } as unknown as ResolvedConfig);
  return transformIndexHtml(html);
}

const metaContent = (html: string, attribute: string, key: string) =>
  new RegExp(`<meta\\s+${attribute}="${key}"\\s+content="([^"]*)"`).exec(html)?.[1];

describe('index.html link-preview tags', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('is the SPA fallback for every route, so it carries no page-specific og:url or canonical', () => {
    // Netlify answers /download, /documentation/… with this very file.
    expect(redirects).toMatch(/^\/\*\s+\/index\.html\s+200\s*$/m);
    expect(indexHtml).not.toContain('og:url');
    expect(indexHtml).not.toContain('rel="canonical"');
  });

  it('keeps the relative tags while VITE_SITE_URL is not set', () => {
    const html = transform({});
    expect(html).toBe(indexHtml);
    expect(metaContent(html, 'property', 'og:image')).toBe('/icon-512.png');
    expect(metaContent(html, 'name', 'twitter:image')).toBe('/icon-512.png');
  });

  it("does not fall back to Netlify's URL build variable", () => {
    vi.stubEnv('URL', 'https://vanta-client.netlify.app');
    expect(transform({})).toBe(indexHtml);
    expect(transform({ VITE_SITE_URL: '' })).toBe(indexHtml);
  });

  it('makes only the images absolute with VITE_SITE_URL, and adds no og:url or canonical', () => {
    const html = transform({ VITE_SITE_URL: 'https://vanta-client.netlify.app/' });
    expect(metaContent(html, 'property', 'og:image')).toBe(
      'https://vanta-client.netlify.app/icon-512.png',
    );
    expect(metaContent(html, 'name', 'twitter:image')).toBe(
      'https://vanta-client.netlify.app/icon-512.png',
    );
    expect(html).not.toContain('og:url');
    expect(html).not.toContain('rel="canonical"');
    // Exactly the two image tags changed; everything else is untouched.
    expect(html.replaceAll('https://vanta-client.netlify.app/icon-512.png', '/icon-512.png')).toBe(
      indexHtml,
    );
    expect(metaContent(html, 'property', 'og:image:width')).toBe('512');
    expect(html).toContain('<link rel="icon" href="/favicon.svg" type="image/svg+xml" />');
    expect(html).toContain('<script type="module" src="/src/main.tsx"></script>');
  });

  it('ignores a site URL that is not a plain origin', () => {
    expect(transform({ VITE_SITE_URL: 'https://vanta.example/sub/path' })).toBe(indexHtml);
    expect(transform({ VITE_SITE_URL: 'ftp://vanta.example' })).toBe(indexHtml);
    expect(transform({ VITE_SITE_URL: 'not a url' })).toBe(indexHtml);
  });

  it('leaves already absolute images and existing page tags alone', () => {
    const once = applySiteUrl(indexHtml, 'https://a.example');
    const twice = applySiteUrl(once, 'https://b.example');
    expect(twice).toBe(once);
    expect(metaContent(twice, 'property', 'og:image')).toBe('https://a.example/icon-512.png');
    const withPageTags = indexHtml.replace(
      '</head>',
      '<meta property="og:url" content="/x" /><link rel="canonical" href="/x" /></head>',
    );
    const applied = applySiteUrl(withPageTags, 'https://a.example');
    expect(metaContent(applied, 'property', 'og:url')).toBe('/x');
    expect(applied).toContain('<link rel="canonical" href="/x" />');
  });
});

describe('helpers', () => {
  it('resolves the first non-empty candidate to an origin', () => {
    expect(resolveSiteUrl(undefined, '', '  ', 'https://x.example/')).toBe('https://x.example');
    expect(resolveSiteUrl(undefined, 42, undefined)).toBeUndefined();
    expect(resolveSiteUrl('https://first.example', 'https://second.example')).toBe(
      'https://first.example',
    );
  });

  it('escapes attribute values', () => {
    expect(escapeAttribute('a"b<c>&d')).toBe('a&quot;b&lt;c&gt;&amp;d');
  });
});

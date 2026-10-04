import { describe, expect, it } from 'vitest';
import { buildRobots, buildSitemap, normalizeBaseUrl } from './sitemap';

describe('buildSitemap', () => {
  it('writes absolute, de-duplicated, escaped URLs with optional fields', () => {
    const xml = buildSitemap(
      [
        { path: '/', priority: 1, changefreq: 'weekly' },
        { path: '/news/a&b', lastmod: '2026-10-04' },
        { path: '/' },
      ],
      'https://vanta.example/',
    );
    expect(xml).toContain('<loc>https://vanta.example/</loc>');
    expect(xml).toContain('<loc>https://vanta.example/news/a&amp;b</loc>');
    expect(xml).toContain('<lastmod>2026-10-04</lastmod>');
    expect(xml).toContain('<priority>1.0</priority>');
    expect(xml.match(/<url>/g)).toHaveLength(2);
    expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>')).toBe(true);
  });
  it('keeps relative paths without a base URL', () => {
    expect(buildSitemap([{ path: '/faq' }], undefined)).toContain('<loc>/faq</loc>');
    expect(buildSitemap([{ path: '/faq' }], 'not a url')).toContain('<loc>/faq</loc>');
  });
});

describe('buildRobots / normalizeBaseUrl', () => {
  it('adds the sitemap line only with a base URL', () => {
    expect(buildRobots(undefined)).toBe('User-agent: *\nAllow: /\n');
    expect(buildRobots('https://vanta.example')).toContain(
      'Sitemap: https://vanta.example/sitemap.xml',
    );
  });
  it('normalises bases', () => {
    expect(normalizeBaseUrl('https://a.b///')).toBe('https://a.b');
    expect(normalizeBaseUrl('ftp://a.b')).toBeUndefined();
    expect(normalizeBaseUrl('')).toBeUndefined();
  });
});

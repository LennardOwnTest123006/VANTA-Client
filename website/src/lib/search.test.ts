import { describe, expect, it } from 'vitest';
import { loadDocs, parseDocPage } from './docs';
import { buildSnippet, createDocsSearch, sectionize, stripMarkdown } from './search';

describe('stripMarkdown', () => {
  it('reduces markdown to readable plain text', () => {
    const text = stripMarkdown(
      [
        '## Heading',
        '',
        'Some **bold** `code` and a [link](x.md#a).',
        '',
        '| Widget | Shows |',
        '| --- | --- |',
        '| **FPS** | frames |',
        '',
        '- item',
        '1. step',
        '> quote',
        '```js',
        'ignored();',
        '```',
      ].join('\n'),
    );
    expect(text).toBe(
      'Heading Some bold code and a link. · Widget · Shows · · FPS · frames · item step quote',
    );
  });
});

describe('sectionize', () => {
  const page = parseDocPage(
    '/docs/sample.md',
    [
      '---',
      'title: Sample',
      'description: A sample page for the search tests.',
      'order: 1',
      'category: Client',
      '---',
      'Intro text about frames.',
      '',
      '## First section',
      '',
      'Body one.',
      '',
      '### Nested',
      '',
      'Body two.',
      '',
      '#### Too deep',
      '',
      'Belongs to Nested.',
      '',
      '## Empty',
    ].join('\n'),
  );
  it('creates one document per heading up to level 3 and drops empty sections', () => {
    const sections = sectionize(page);
    expect(sections.map((s) => [s.id, s.heading, s.href])).toEqual([
      ['sample', 'Sample', '/documentation/sample'],
      ['sample#first-section', 'First section', '/documentation/sample#first-section'],
      ['sample#nested', 'Nested', '/documentation/sample#nested'],
    ]);
    expect(sections[2]?.text).toBe('Body two. Too deep Belongs to Nested.');
  });
});

describe('buildSnippet', () => {
  const text =
    'The Performance Center shows live FPS, frame time and memory. Presets change vanilla video options only, and the FPS limit is separate.';
  it('centres on the first match and highlights every matched term', () => {
    const parts = buildSnippet(text, ['fps'], 30);
    expect(parts.some((p) => p.highlight && p.text === 'FPS')).toBe(true);
    expect(parts[0]?.text).toBe('… ');
    expect(parts.filter((p) => p.highlight).length).toBeGreaterThanOrEqual(1);
    expect(parts.map((p) => p.text).join('')).toContain('FPS');
  });
  it('highlights prefix matches as whole words', () => {
    const parts = buildSnippet('Profiles store settings; a profile is JSON.', ['profil']);
    expect(parts.filter((p) => p.highlight).map((p) => p.text)).toEqual(['Profiles', 'profile']);
  });
  it('falls back to the beginning without terms and handles empty text', () => {
    expect(buildSnippet('', ['x'])).toEqual([]);
    const parts = buildSnippet(text, [], 20);
    expect(parts[0]?.highlight).toBe(false);
    expect(parts[parts.length - 1]?.text).toBe(' …');
  });
  it('escapes regex characters in terms', () => {
    expect(() => buildSnippet('a (b) c', ['(b)'])).not.toThrow();
  });
});

describe('createDocsSearch over the repository docs', () => {
  it('returns results for "fps" and "profile" with snippets and section anchors', async () => {
    const search = await createDocsSearch(await loadDocs());
    expect(search.documents.length).toBeGreaterThan(50);

    const fps = search.search('fps');
    expect(fps.length).toBeGreaterThan(0);
    expect(fps.some((hit) => hit.slug === 'performance' || hit.slug === 'hud')).toBe(true);
    expect(fps[0]?.snippet.some((part) => part.highlight)).toBe(true);

    const profile = search.search('profile');
    expect(profile.length).toBeGreaterThan(0);
    expect(profile[0]?.slug).toBe('profiles');
    expect(profile[0]?.href.startsWith('/documentation/profiles')).toBe(true);

    expect(search.search('x')).toEqual([]);
    expect(search.search('qzxvwk')).toEqual([]);
    // Partial matches fall back to OR so "nothing-here" still finds "nothing".
    expect(search.search('qzxvwk nothing').length).toBeGreaterThan(0);
    expect(search.search('verify checksum').some((hit) => hit.href.includes('#'))).toBe(true);
    // Results are unique per anchor and capped.
    const many = search.search('the', 5);
    expect(many.length).toBeLessThanOrEqual(5);
    expect(new Set(many.map((hit) => hit.href)).size).toBe(many.length);
  });
});

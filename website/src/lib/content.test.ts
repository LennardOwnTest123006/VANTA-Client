import { describe, expect, it } from 'vitest';
import {
  changelog,
  findChangelog,
  loadChangelog,
  markdownExcerpt,
  parseFrontMatter,
} from './content';

describe('parseFrontMatter', () => {
  it('splits meta from body and strips quotes', () => {
    const doc = parseFrontMatter('---\ntitle: "Hello"\ndate: 2026-10-04\n---\n\n# Body\n');
    expect(doc.meta).toEqual({ title: 'Hello', date: '2026-10-04' });
    expect(doc.body).toBe('# Body');
  });
  it('handles documents without front matter and CRLF line endings', () => {
    expect(parseFrontMatter('Just text\r\n')).toEqual({ meta: {}, body: 'Just text' });
  });
});

describe('markdownExcerpt', () => {
  it('returns the first bullets as plain text', () => {
    const body =
      'Intro\n\n- **Bold** item with `code`\n- Second [link](https://x)\n* Third\n- Fourth\n- Fifth';
    expect(markdownExcerpt(body, 3)).toEqual(['Bold item with code', 'Second link', 'Third']);
  });
  it('returns an empty list when there are no bullets', () => {
    expect(markdownExcerpt('No bullets here')).toEqual([]);
  });
});

describe('loadChangelog', () => {
  it('derives product and version from the file name when the front matter lacks them', () => {
    const entries = loadChangelog({ '/content/changelog/client-1.2.3.md': '- a\n' });
    expect(entries[0]).toMatchObject({ id: 'client-1.2.3', product: 'client', version: '1.2.3' });
  });
  it('sorts newest ids first', () => {
    const entries = loadChangelog({
      '/c/client-1.0.0.md': '',
      '/c/client-1.1.0.md': '',
      '/c/launcher-1.0.0.md': '',
    });
    expect(entries.map((e) => e.id)).toEqual(['launcher-1.0.0', 'client-1.1.0', 'client-1.0.0']);
  });
});

describe('repository changelog', () => {
  it('has release notes for client and launcher 1.0.0', () => {
    expect(changelog.length).toBeGreaterThanOrEqual(2);
    const client = findChangelog('client', '1.0.0');
    expect(client?.meta.title).toBe('VANTA Client 1.0.0');
    expect(markdownExcerpt(client?.body ?? '', 1)).toHaveLength(1);
    expect(findChangelog('launcher', '1.0.0')).toBeDefined();
  });
});

import { describe, expect, it } from 'vitest';
import {
  changelog,
  changelogProducts,
  compareEntriesDesc,
  findChangelog,
  loadChangelog,
  markdownExcerpt,
  parseChangelogEntry,
  parseChangelogSections,
  parseFrontMatter,
  productLabel,
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

describe('parseChangelogSections', () => {
  it('splits the intro and the known sections, counting bullets', () => {
    const body = [
      'First release.',
      '',
      '## Added',
      '',
      '- one',
      '- two',
      '',
      '## Fixed',
      '',
      '- three',
      '',
      '## Notes',
      '',
      'Plain paragraph.',
      '',
      '## Known issues',
      '',
      '- four',
    ].join('\n');
    const { intro, sections } = parseChangelogSections(body);
    expect(intro).toBe('First release.');
    expect(sections.map((s) => [s.kind, s.heading, s.itemCount])).toEqual([
      ['added', 'Added', 2],
      ['fixed', 'Fixed', 1],
      ['notes', 'Notes', 0],
      ['other', 'Known issues', 1],
    ]);
    expect(sections[0]?.body).toBe('- one\n- two');
  });
  it('ignores headings inside code fences', () => {
    const { sections } = parseChangelogSections('```\n## not a section\n```\n## Added\n- x');
    expect(sections.map((s) => s.heading)).toEqual(['Added']);
  });
});

describe('parseChangelogEntry / loadChangelog', () => {
  it('derives product and version from the file name when the front matter lacks them', () => {
    const entry = parseChangelogEntry('/c/client-1.2.3.md', '---\ndate: 2026-01-01\n---\n- a\n');
    expect(entry).toMatchObject({
      id: 'client-1.2.3',
      product: 'client',
      version: '1.2.3',
      title: 'VANTA Client 1.2.3',
      date: '2026-01-01',
    });
    expect(entry.minecraftVersion).toBeUndefined();
  });
  it('rejects unknown products, bad versions and missing dates', () => {
    expect(() => parseChangelogEntry('/c/server-1.0.0.md', '---\ndate: 2026-01-01\n---\n')).toThrow(
      /product/,
    );
    expect(() =>
      parseChangelogEntry('/c/client-latest.md', '---\ndate: 2026-01-01\n---\n'),
    ).toThrow(/SemVer/);
    expect(() => parseChangelogEntry('/c/client-1.0.0.md', '- no front matter')).toThrow(/date/);
  });
  it('sorts newest first by date, then version', () => {
    const entries = loadChangelog({
      '/c/client-1.0.0.md': '---\ndate: 2026-01-01\n---\n',
      '/c/client-1.1.0.md': '---\ndate: 2026-02-01\n---\n',
      '/c/launcher-1.0.0.md': '---\ndate: 2026-02-01\n---\n',
      '/c/client-1.0.1.md': '---\ndate: 2026-01-01\n---\n',
      '/c/README.md': 'ignored',
    });
    // Same date: the higher version wins; same date and version: ids descend.
    expect(entries.map((e) => e.id)).toEqual([
      'client-1.1.0',
      'launcher-1.0.0',
      'client-1.0.1',
      'client-1.0.0',
    ]);
    expect(compareEntriesDesc(entries[0]!, entries[0]!)).toBe(0);
  });
  it('labels products', () => {
    expect(productLabel('client')).toBe('VANTA Client');
    expect(productLabel('website')).toBe('Website');
  });
});

describe('repository changelog', () => {
  it('has release notes for client, launcher and website 1.0.0 with the required fields', () => {
    expect(changelog.length).toBeGreaterThanOrEqual(3);
    expect(changelogProducts(changelog)).toEqual(['client', 'launcher', 'website']);
    const client = findChangelog('client', '1.0.0');
    expect(client?.title).toBe('VANTA Client 1.0.0');
    expect(client?.minecraftVersion).toBe('1.21.11');
    expect(client?.sections.map((s) => s.kind)).toEqual(['added', 'notes']);
    expect(markdownExcerpt(client?.body ?? '', 1)).toHaveLength(1);
    expect(findChangelog('launcher', '1.0.0')?.minecraftVersion).toBe('1.21.11');
    for (const entry of changelog) {
      expect(entry.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(entry.sections.length).toBeGreaterThan(0);
      for (const section of entry.sections) expect(section.kind).not.toBe('other');
    }
  });
});

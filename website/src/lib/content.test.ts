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
  releaseHighlights,
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
  it('strips single-asterisk emphasis as the release notes write it', () => {
    expect(markdownExcerpt('- Shows *Custom*; choose *Fast*, *Fancy* or **Fabulous**')).toEqual([
      'Shows Custom; choose Fast, Fancy or Fabulous',
    ]);
    expect(markdownExcerpt('- 2 * 3 stays')).toEqual(['2 * 3 stays']);
    // A `*` inside a code span never pairs with emphasis outside it (launcher 1.2.1 notes).
    expect(
      markdownExcerpt(
        '- delete `smart-fps-booster-*.jar` from the mods folder (*Home → Open game folder*)',
      ),
    ).toEqual(['delete smart-fps-booster-*.jar from the mods folder (Home → Open game folder)']);
  });
  it('returns an empty list when there are no bullets', () => {
    expect(markdownExcerpt('No bullets here')).toEqual([]);
  });
});

describe('releaseHighlights', () => {
  const entry = (body: string) => parseChangelogSections(body);

  it('quotes Fixed first, then Added and Improved, never Notes, at most three bullets', () => {
    const body = [
      'Intro',
      '## Added',
      '- added one',
      '- added two',
      '## Improved',
      '- improved one',
      '## Fixed',
      '- **fixed** one with `code`',
      '## Notes',
      '- a note',
    ].join('\n');
    expect(releaseHighlights(entry(body))).toEqual([
      'fixed one with code',
      'added one',
      'added two',
    ]);
    expect(releaseHighlights(entry(body), 5)).toEqual([
      'fixed one with code',
      'added one',
      'added two',
      'improved one',
    ]);
    expect(releaseHighlights(entry(body), 1)).toEqual(['fixed one with code']);
  });

  it('stops at three bullets of a long Fixed section', () => {
    const body = ['## Improved', '- improved', '## Fixed', '- a', '- b', '- c', '- d'].join('\n');
    expect(releaseHighlights(entry(body))).toEqual(['a', 'b', 'c']);
  });

  it('uses Improved when there is neither Fixed nor Added', () => {
    const body = ['## Improved', '- one', '## Notes', '- note'].join('\n');
    expect(releaseHighlights(entry(body))).toEqual(['one']);
  });

  it('falls back to the first bullets of entries without those sections', () => {
    expect(releaseHighlights(entry('- a\n- b\n- c\n- d'))).toEqual(['a', 'b', 'c']);
    expect(releaseHighlights(entry('No bullets'))).toEqual([]);
  });

  it('never quotes Notes, also not in the fallback', () => {
    expect(releaseHighlights(entry('## Notes\n- a note'))).toEqual([]);
    expect(releaseHighlights(entry('Intro\n\n## Notes\n- a note\n- another note'))).toEqual([]);
    const body = ['- intro bullet', '## Notes', '- a note', '## Upgrading', '- other one'].join(
      '\n',
    );
    expect(releaseHighlights(entry(body))).toEqual(['intro bullet', 'other one']);
  });

  it('falls back to the intro and other sections when Fixed, Added and Improved have no bullets', () => {
    const body = [
      '- intro bullet',
      '## Improved',
      'Small polish only.',
      '## Fixed',
      '',
      '## Notes',
      '- a note',
      '## Upgrading',
      '- other one',
      '- other two',
    ].join('\n');
    expect(releaseHighlights(entry(body))).toEqual(['intro bullet', 'other one', 'other two']);
    expect(releaseHighlights(entry(body), 2)).toEqual(['intro bullet', 'other one']);
    // As soon as Fixed, Added or Improved has a bullet, only those sections are quoted.
    const withFix = body.replace('## Fixed\n', '## Fixed\n- fixed one\n');
    expect(releaseHighlights(entry(withFix))).toEqual(['fixed one']);
  });

  it('quotes the fixes of the repository release notes of launcher 1.0.1', () => {
    const notes = findChangelog('launcher', '1.0.1');
    const fixed = notes?.sections.find((section) => section.kind === 'fixed');
    expect(fixed).toBeDefined();
    const highlights = releaseHighlights(notes!);
    expect(highlights).toHaveLength(3);
    expect(highlights).toEqual(markdownExcerpt(fixed!.body, 3));
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
  it('knows the Removed section of launcher 1.4.0 and never quotes it as a highlight', () => {
    const body = [
      '## Improved',
      '- better',
      '## Removed',
      '- the .exe installer',
      '## Notes',
      '- n',
    ].join('\n');
    const parsed = parseChangelogSections(body);
    expect(parsed.sections.map((s) => s.kind)).toEqual(['improved', 'removed', 'notes']);
    expect(releaseHighlights(parsed)).toEqual(['better']);
    const launcher = findChangelog('launcher', '1.4.0');
    expect(launcher?.sections.map((s) => s.kind)).toEqual([
      'added',
      'improved',
      'removed',
      'notes',
    ]);
    expect(launcher?.sections.find((s) => s.kind === 'removed')?.body).toMatch(/\.exe/);
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

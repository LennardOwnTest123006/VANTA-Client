import { describe, expect, it } from 'vitest';
import { extractHeadings, headingText, Slugger, slugify } from './slug';

describe('slugify', () => {
  it('matches GitHub anchors', () => {
    expect(slugify('2. Verify the checksum')).toBe('2-verify-the-checksum');
    expect(slugify('Saving and `settings.json`')).toBe('saving-and-settingsjson');
    expect(
      slugify('Windows says the installer is from an unknown publisher. Is that normal?'),
    ).toBe('windows-says-the-installer-is-from-an-unknown-publisher-is-that-normal');
    expect(slugify('Core — the pure Java library')).toBe('core--the-pure-java-library');
    expect(slugify('Colour-blind palettes')).toBe('colour-blind-palettes');
  });
});

describe('Slugger', () => {
  it('suffixes duplicates', () => {
    const slugger = new Slugger();
    expect(slugger.slug('Notes')).toBe('notes');
    expect(slugger.slug('Notes')).toBe('notes-1');
    expect(slugger.slug('notes')).toBe('notes-2');
  });
});

describe('extractHeadings', () => {
  const markdown = [
    '## Install',
    'text',
    '```bash',
    '# not a heading',
    '```',
    '### Windows',
    '### Windows',
    '#### Deep',
    '## FAQ **bold** and `code`',
  ].join('\n');

  it('returns levels, text and ids, skipping code fences', () => {
    expect(extractHeadings(markdown)).toEqual([
      { level: 2, text: 'Install', id: 'install' },
      { level: 3, text: 'Windows', id: 'windows' },
      { level: 3, text: 'Windows', id: 'windows-1' },
      { level: 4, text: 'Deep', id: 'deep' },
      { level: 2, text: 'FAQ bold and code', id: 'faq-bold-and-code' },
    ]);
  });

  it('keeps ids stable when filtering levels', () => {
    const filtered = extractHeadings(markdown, 2, 3);
    expect(filtered.map((h) => h.id)).toEqual([
      'install',
      'windows',
      'windows-1',
      'faq-bold-and-code',
    ]);
  });

  it('cleans heading markup', () => {
    expect(headingText('[Link](x.md) *em* `code`')).toBe('Link em code');
  });
});

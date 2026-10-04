import { describe, expect, it } from 'vitest';
import { loadDocs } from './docs';
import { parseFaq } from './faq';

describe('parseFaq', () => {
  it('turns level-2 headings into questions with GitHub ids and markdown answers', () => {
    const faq = parseFaq(
      [
        'Intro paragraph.',
        '',
        '## Is VANTA allowed on servers?',
        '',
        'Server rules vary.',
        '',
        '- a',
        '',
        '## Is it free?',
        '',
        'Yes. See [Privacy](privacy.md).',
        '',
        '### Not a question',
        '',
        'More of the same answer.',
        '```',
        '## inside code',
        '```',
      ].join('\n'),
    );
    expect(faq.intro).toBe('Intro paragraph.');
    expect(faq.items.map((item) => [item.id, item.question])).toEqual([
      ['is-vanta-allowed-on-servers', 'Is VANTA allowed on servers?'],
      ['is-it-free', 'Is it free?'],
    ]);
    expect(faq.items[0]?.answer).toBe('Server rules vary.\n\n- a');
    expect(faq.items[1]?.answer).toContain('### Not a question');
    expect(faq.items[1]?.answer).toContain('## inside code');
  });

  it('parses docs/faq.md into a reasonable number of unique questions', async () => {
    const pages = await loadDocs();
    const faq = parseFaq(pages.find((page) => page.slug === 'faq')?.body ?? '');
    expect(faq.items.length).toBeGreaterThanOrEqual(20);
    expect(new Set(faq.items.map((item) => item.id)).size).toBe(faq.items.length);
    for (const item of faq.items) {
      expect(item.question.endsWith('?')).toBe(true);
      expect(item.answer.length).toBeGreaterThan(20);
    }
    expect(faq.items.map((item) => item.id)).toContain('why-is-there-no-download-yet');
  });
});

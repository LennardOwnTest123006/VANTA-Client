import { headingText, Slugger, stripFencedCode } from './slug';

/**
 * Turns `docs/faq.md` into question/answer pairs: every `## Question` heading starts an item and
 * the markdown until the next level-2 heading is its answer. Ids follow the GitHub slug algorithm,
 * so `faq.md#is-vanta-allowed-on-servers` links from other docs open the matching item.
 */

export interface FaqItem {
  readonly id: string;
  readonly question: string;
  /** Markdown answer. */
  readonly answer: string;
}

export interface Faq {
  /** Markdown before the first question (usually empty). */
  readonly intro: string;
  readonly items: readonly FaqItem[];
}

export function parseFaq(markdown: string): Faq {
  const slugger = new Slugger();
  const items: { id: string; question: string; lines: string[] }[] = [];
  const intro: string[] = [];
  // Fenced code is masked only for heading detection; the answers keep the original lines.
  const masked = stripFencedCode(markdown).split('\n');
  const original = markdown.split('\n');
  masked.forEach((line, index) => {
    const match = /^##\s+(.+?)\s*#*\s*$/.exec(line);
    const source = original[index] ?? '';
    if (match) {
      const question = headingText(match[1] ?? '');
      items.push({ id: slugger.slug(question), question, lines: [] });
      return;
    }
    (items[items.length - 1]?.lines ?? intro).push(source);
  });
  return {
    intro: intro.join('\n').trim(),
    items: items
      .map((item) => ({
        id: item.id,
        question: item.question,
        answer: item.lines.join('\n').trim(),
      }))
      .filter((item) => item.question !== ''),
  };
}

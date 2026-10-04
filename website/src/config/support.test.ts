import { describe, expect, it } from 'vitest';
import { loadDocs } from '../lib/docs';
import { parseFaq } from '../lib/faq';
import { extractHeadings } from '../lib/slug';
import { supportCategories } from './support';

describe('supportCategories', () => {
  it('covers the required areas with unique ids', () => {
    const titles = supportCategories.map((category) => category.title);
    expect(titles).toEqual(
      expect.arrayContaining([
        'Installation',
        'Launcher',
        'Minecraft',
        'Fabric',
        'Performance',
        'Account',
        'Website',
      ]),
    );
    expect(new Set(supportCategories.map((c) => c.id)).size).toBe(supportCategories.length);
    for (const category of supportCategories) {
      expect(category.guides.length).toBeGreaterThan(0);
      expect(category.troubleshooting.length).toBeGreaterThan(0);
    }
  });

  it('links only to documentation pages, FAQ questions and anchors that exist', async () => {
    const pages = await loadDocs();
    const byRoute = new Map(pages.map((page) => [page.route, page]));
    const faq = parseFaq(pages.find((page) => page.slug === 'faq')?.body ?? '');
    const faqIds = new Set(faq.items.map((item) => item.id));
    const problems: string[] = [];
    for (const category of supportCategories) {
      for (const link of [...category.guides, ...category.troubleshooting]) {
        const [route = '', hash] = link.to.split('#');
        if (route === '/faq') {
          if (hash && !faqIds.has(hash)) problems.push(`${link.to}: unknown FAQ question`);
          continue;
        }
        const page = byRoute.get(route);
        if (!page) {
          problems.push(`${link.to}: unknown documentation route`);
          continue;
        }
        if (hash && !extractHeadings(page.body).some((heading) => heading.id === hash)) {
          problems.push(`${link.to}: unknown anchor`);
        }
      }
    }
    expect(problems).toEqual([]);
  });
});

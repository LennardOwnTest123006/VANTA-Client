import { describe, expect, it } from 'vitest';
import { loadNews, newsNeighbours, newsSlugs, parseNews, parseNewsPost } from './news';

const post = (date: string, slug: string, extra = '', body = 'Hello **world**.') =>
  [
    `/content/news/${date}-${slug}.md`,
    `---\ntitle: Post ${slug}\ndate: ${date}\nsummary: A summary long enough to pass.\nauthor: VANTA team\n${extra}\n---\n\n${body}\n`,
  ] as const;

describe('parseNewsPost', () => {
  it('derives the slug from the file name and reads the front matter', () => {
    const [path, raw] = post('2026-10-04', 'introducing-vanta', 'tags: [announcement, client]');
    const parsed = parseNewsPost(path, raw);
    expect(parsed).toMatchObject({
      slug: 'introducing-vanta',
      file: '2026-10-04-introducing-vanta.md',
      title: 'Post introducing-vanta',
      date: '2026-10-04',
      author: 'VANTA team',
      tags: ['announcement', 'client'],
      draft: false,
      minutes: 1,
    });
  });
  it('rejects bad file names, summaries and tags', () => {
    expect(() => parseNewsPost('/n/no-date.md', '---\ntitle: x\n---\n')).toThrow(/yyyy-mm-dd/);
    expect(() =>
      parseNewsPost(
        '/n/2026-01-01-a.md',
        '---\ntitle: x\ndate: 2026-01-01\nsummary: short\nauthor: a\n---\n',
      ),
    ).toThrow(/summary/);
    const [path, raw] = post('2026-01-01', 'a', 'tags: [Bad Tag]');
    expect(() => parseNewsPost(path, raw)).toThrow(/tag "Bad Tag"/);
  });
});

describe('parseNews', () => {
  it('skips drafts and README files and sorts newest first', () => {
    const a = post('2026-01-01', 'older');
    const b = post('2026-02-01', 'newer');
    const c = post('2026-03-01', 'draft', 'draft: true');
    const posts = parseNews({
      [a[0]]: a[1],
      [b[0]]: b[1],
      [c[0]]: c[1],
      '/content/news/README.md': '# readme',
    });
    expect(posts.map((p) => p.slug)).toEqual(['newer', 'older']);
    expect(newsNeighbours(posts, 'newer')).toMatchObject({
      newer: undefined,
      older: { slug: 'older' },
    });
    expect(newsNeighbours(posts, 'older')).toMatchObject({
      newer: { slug: 'newer' },
      older: undefined,
    });
  });
});

describe('repository news', () => {
  it('loads every post with the required fields', async () => {
    const posts = await loadNews();
    expect(posts.length).toBeGreaterThanOrEqual(1);
    expect(newsSlugs).toContain('introducing-vanta');
    const intro = posts.find((p) => p.slug === 'introducing-vanta');
    expect(intro?.title).toBe('Introducing VANTA Client');
    expect(intro?.tags).toEqual(['announcement', 'client', 'launcher']);
    expect(intro?.minutes).toBeGreaterThanOrEqual(2);
    for (const p of posts) expect(p.summary.length).toBeLessThanOrEqual(300);
  });
});

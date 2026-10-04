import { describe, expect, it } from 'vitest';
import {
  type MarkdownNode,
  nodeText,
  remarkHeadingIds,
  remarkRewriteLinks,
  visit,
} from './markdown-plugins';

function heading(depth: number, ...children: MarkdownNode[]): MarkdownNode {
  return { type: 'heading', depth, children };
}
const text = (value: string): MarkdownNode => ({ type: 'text', value });

describe('remarkHeadingIds', () => {
  it('assigns GitHub-style ids with duplicate suffixes', () => {
    const tree: MarkdownNode = {
      type: 'root',
      children: [
        heading(2, text('2. Verify the checksum')),
        heading(2, text('Notes')),
        heading(3, { type: 'inlineCode', value: 'settings.json' }, text(' file')),
        heading(2, text('Notes')),
      ],
    };
    remarkHeadingIds()(tree);
    const ids = (tree.children ?? []).map((node) => node.data?.hProperties?.id);
    expect(ids).toEqual(['2-verify-the-checksum', 'notes', 'settingsjson-file', 'notes-1']);
  });

  it('preserves existing hProperties', () => {
    const node: MarkdownNode = { ...heading(2, text('A')), data: { hProperties: { class: 'x' } } };
    remarkHeadingIds()({ type: 'root', children: [node] });
    expect(node.data?.hProperties).toEqual({ class: 'x', id: 'a' });
  });
});

describe('remarkRewriteLinks', () => {
  it('rewrites link and definition urls through the resolver, keeping unknown targets', () => {
    const tree: MarkdownNode = {
      type: 'root',
      children: [
        {
          type: 'paragraph',
          children: [
            { type: 'link', url: 'hud.md#widgets', children: [text('HUD')] },
            { type: 'link', url: 'https://example.com', children: [text('ext')] },
          ],
        },
        { type: 'definition', url: 'faq.md' },
      ],
    };
    remarkRewriteLinks((url) =>
      url.endsWith('.md') || url.includes('.md#') ? `/d/${url}` : undefined,
    )(tree);
    const urls: string[] = [];
    visit(tree, (node) => {
      if (typeof node.url === 'string') urls.push(node.url);
    });
    expect(urls).toEqual(['/d/hud.md#widgets', 'https://example.com', '/d/faq.md']);
  });
});

describe('nodeText', () => {
  it('concatenates descendant text', () => {
    expect(nodeText(heading(1, text('a '), { type: 'strong', children: [text('b')] }))).toBe('a b');
  });
});

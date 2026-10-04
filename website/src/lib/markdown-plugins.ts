import { Slugger } from './slug';

/**
 * Two tiny remark plugins used by the markdown renderer. They walk the mdast tree directly — no
 * extra dependencies — and are unit tested against the trees remark-parse produces.
 *
 * - {@link remarkHeadingIds} gives every heading a GitHub-style `id` so `#anchor` links, the
 *   on-this-page TOC and `docs/*.md` cross-links (`page.md#anchor`) resolve.
 * - {@link remarkRewriteLinks} lets a page map link targets (e.g. `installation.md#2-verify` to
 *   `/documentation/installation#2-verify`) before they are rendered.
 */

/** Minimal structural view of an mdast node; enough for headings, links and text. */
export interface MarkdownNode {
  type: string;
  children?: MarkdownNode[];
  value?: string;
  url?: string;
  depth?: number;
  data?: { hProperties?: Record<string, unknown> };
}

/** Concatenates the text of a node's descendants (text, inline code, emphasis, links …). */
export function nodeText(node: MarkdownNode): string {
  if (typeof node.value === 'string') return node.value;
  return (node.children ?? []).map(nodeText).join('');
}

/** Depth-first visit of every node. */
export function visit(node: MarkdownNode, callback: (node: MarkdownNode) => void): void {
  callback(node);
  for (const child of node.children ?? []) visit(child, callback);
}

/** remark plugin: assigns `id` attributes to headings (GitHub slug algorithm, duplicates suffixed). */
export function remarkHeadingIds() {
  return (tree: MarkdownNode): void => {
    const slugger = new Slugger();
    visit(tree, (node) => {
      if (node.type !== 'heading') return;
      const id = slugger.slug(nodeText(node));
      node.data = { ...node.data, hProperties: { ...node.data?.hProperties, id } };
    });
  };
}

export type LinkResolver = (url: string) => string | undefined;

/** remark plugin: rewrites link URLs through `resolve`; `undefined` keeps the original target. */
export function remarkRewriteLinks(resolve: LinkResolver) {
  return (tree: MarkdownNode): void => {
    visit(tree, (node) => {
      if ((node.type !== 'link' && node.type !== 'definition') || typeof node.url !== 'string')
        return;
      const next = resolve(node.url);
      if (next !== undefined) node.url = next;
    });
  };
}

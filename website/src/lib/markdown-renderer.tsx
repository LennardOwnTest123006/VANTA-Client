import { useMemo } from 'react';
import Markdown, { type Options } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { cn } from './cn';
import { markdownComponents } from './markdown-components';
import { type LinkResolver, remarkHeadingIds, remarkRewriteLinks } from './markdown-plugins';

/**
 * Markdown renderer with a sanitised component map.
 *
 * - Raw HTML in markdown is skipped (`skipHtml`), so content files can never inject markup.
 * - Links and images go through the sanitised component map in `markdown-components.tsx`.
 * - Headings receive GitHub-style ids (`remarkHeadingIds`) so anchors and tables of contents work.
 * - `resolveLink` lets a page rewrite link targets (documentation cross-links) before rendering.
 *
 * This module is loaded lazily through `MarkdownBlock` so react-markdown stays out of the
 * initial bundle.
 */

export interface MarkdownRendererProps {
  /** Markdown source (GitHub-flavoured). */
  readonly source: string;
  readonly className?: string;
  /** Adds ids to headings. Default `true`. */
  readonly headingIds?: boolean;
  /** Rewrites link targets; return `undefined` to keep a target unchanged. */
  readonly resolveLink?: LinkResolver;
}

/** Renders markdown synchronously. Prefer `MarkdownBlock` in pages so the renderer loads lazily. */
export default function MarkdownRenderer({
  source,
  className,
  headingIds = true,
  resolveLink,
}: MarkdownRendererProps) {
  const options = useMemo<Pick<Options, 'remarkPlugins' | 'skipHtml'>>(() => {
    const remarkPlugins: NonNullable<Options['remarkPlugins']> = [remarkGfm];
    if (headingIds) remarkPlugins.push(remarkHeadingIds);
    if (resolveLink) remarkPlugins.push([remarkRewriteLinks, resolveLink]);
    return { remarkPlugins, skipHtml: true };
  }, [headingIds, resolveLink]);
  return (
    <div className={cn('markdown', className)}>
      <Markdown {...options} components={markdownComponents}>
        {source}
      </Markdown>
    </div>
  );
}

import Markdown, { type Options } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { cn } from './cn';
import { markdownComponents } from './markdown-components';

/**
 * Markdown renderer with a sanitised component map.
 *
 * - Raw HTML in markdown is skipped (`skipHtml`), so content files can never inject markup.
 * - Links and images go through the sanitised component map in `markdown-components.tsx`.
 *
 * This module is loaded lazily through `MarkdownBlock` so react-markdown stays out of the
 * initial bundle.
 */

export interface MarkdownRendererProps {
  /** Markdown source (GitHub-flavoured). */
  readonly source: string;
  readonly className?: string;
}

const options: Pick<Options, 'remarkPlugins' | 'skipHtml'> = {
  remarkPlugins: [remarkGfm],
  skipHtml: true,
};

/** Renders markdown synchronously. Prefer `MarkdownBlock` in pages so the renderer loads lazily. */
export default function MarkdownRenderer({ source, className }: MarkdownRendererProps) {
  return (
    <div className={cn('markdown', className)}>
      <Markdown {...options} components={markdownComponents}>
        {source}
      </Markdown>
    </div>
  );
}

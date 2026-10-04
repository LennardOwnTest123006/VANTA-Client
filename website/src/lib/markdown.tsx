import { lazy, Suspense } from 'react';
import type { MarkdownRendererProps } from './markdown-renderer';

/**
 * Lazily loaded markdown block. The renderer (react-markdown + remark-gfm) lives in its own chunk
 * and is only fetched by pages that show markdown content (changelog, news, documentation).
 */
const LazyRenderer = lazy(() => import('./markdown-renderer'));

export type { MarkdownRendererProps } from './markdown-renderer';

export function MarkdownBlock(props: MarkdownRendererProps) {
  return (
    <Suspense
      fallback={
        <div
          role="status"
          aria-live="polite"
          className="my-4 h-24 animate-pulse rounded-lg border border-border-subtle bg-surface-1"
        >
          <span className="sr-only">Loading content…</span>
        </div>
      }
    >
      <LazyRenderer {...props} />
    </Suspense>
  );
}

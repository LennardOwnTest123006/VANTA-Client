import { use } from 'react';
import { type DocPage, loadDocs } from './docs';
import { loadNews, type NewsPost } from './news';

/**
 * React 19 `use()` wrappers around the memoised content loaders. The nearest Suspense boundary
 * (the route-level skeleton in `Layout`) shows while the content chunk loads; a validation error
 * reaches the page error boundary.
 */

export function useDocs(): readonly DocPage[] {
  return use(loadDocs());
}

export function useNews(): readonly NewsPost[] {
  return use(loadNews());
}

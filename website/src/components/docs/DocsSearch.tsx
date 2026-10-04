import { CornerDownLeft, Loader2, Search, X } from 'lucide-react';
import {
  type ChangeEvent,
  type KeyboardEvent,
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
} from 'react';
import { useNavigate } from 'react-router';
import { type DocPage } from '../../lib/docs';
import { cn } from '../../lib/cn';
import { createDocsSearch, type DocsSearch as SearchIndex, type SearchHit } from '../../lib/search';
import { Kbd } from '../ui/Kbd';

export interface DocsSearchProps {
  readonly pages: readonly DocPage[];
  /** Registers the global `/` shortcut that focuses this box. One instance per page should do so. */
  readonly shortcut?: boolean;
  /** Focus the input when mounted (mobile drawer). */
  readonly autoFocus?: boolean;
  /** Called after a result was chosen (closes the mobile drawer). */
  readonly onNavigate?: () => void;
  readonly className?: string;
}

const indexCache = new WeakMap<readonly DocPage[], Promise<SearchIndex>>();
function getIndex(pages: readonly DocPage[]): Promise<SearchIndex> {
  let promise = indexCache.get(pages);
  if (!promise) {
    promise = createDocsSearch(pages);
    indexCache.set(pages, promise);
  }
  return promise;
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT'
  );
}

/**
 * Documentation search box. Builds the MiniSearch index on first use (MiniSearch itself is loaded
 * lazily), shows section-level results with highlighted snippets as a combobox listbox, supports
 * ArrowUp/ArrowDown/Enter/Escape and the global `/` shortcut.
 */
export function DocsSearch({
  pages,
  shortcut = false,
  autoFocus = false,
  onNavigate,
  className,
}: DocsSearchProps) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = useId();
  const [query, setQuery] = useState('');
  /** Hits of the last completed search and the query they belong to. */
  const [result, setResult] = useState<{ query: string; hits: SearchHit[] }>({
    query: '',
    hits: [],
  });
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const indexRef = useRef<SearchIndex | undefined>(undefined);
  const timerRef = useRef<number | undefined>(undefined);
  const latestQuery = useRef('');

  const ensureIndex = useCallback(async () => {
    if (indexRef.current) return indexRef.current;
    setLoading(true);
    try {
      const index = await getIndex(pages);
      indexRef.current = index;
      return index;
    } finally {
      setLoading(false);
    }
  }, [pages]);

  /** Debounced search; results are dropped when the query changed in the meantime. */
  const scheduleSearch = useCallback(
    (value: string) => {
      const trimmed = value.trim();
      latestQuery.current = trimmed;
      if (timerRef.current !== undefined) window.clearTimeout(timerRef.current);
      if (trimmed.length < 2) return;
      timerRef.current = window.setTimeout(() => {
        void ensureIndex().then((index) => {
          if (latestQuery.current !== trimmed) return;
          setResult({ query: trimmed, hits: index.search(trimmed, 7) });
          setActive(0);
          setOpen(true);
        });
      }, 90);
    },
    [ensureIndex],
  );

  useEffect(
    () => () => {
      if (timerRef.current !== undefined) window.clearTimeout(timerRef.current);
    },
    [],
  );

  // Global "/" shortcut.
  useEffect(() => {
    if (!shortcut) return undefined;
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) return;
      if (isTypingTarget(event.target)) return;
      event.preventDefault();
      inputRef.current?.focus();
      inputRef.current?.select();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [shortcut]);

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  const trimmed = query.trim();
  const hits = trimmed.length >= 2 ? result.hits : [];
  const settled = result.query === trimmed;

  const reset = () => {
    latestQuery.current = '';
    if (timerRef.current !== undefined) window.clearTimeout(timerRef.current);
    setQuery('');
    setResult({ query: '', hits: [] });
    setActive(0);
    setOpen(false);
  };

  const choose = (hit: SearchHit | undefined) => {
    if (!hit) return;
    reset();
    onNavigate?.();
    void navigate(hit.href);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' && hits.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((index) => (index + 1) % hits.length);
    } else if (event.key === 'ArrowUp' && hits.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((index) => (index - 1 + hits.length) % hits.length);
    } else if (event.key === 'Enter') {
      if (open && hits.length > 0) {
        event.preventDefault();
        choose(hits[active]);
      }
    } else if (event.key === 'Escape') {
      if (query !== '') {
        event.preventDefault();
        event.stopPropagation();
        reset();
      } else {
        setOpen(false);
      }
    }
  };

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    setQuery(event.target.value);
    setOpen(true);
    scheduleSearch(event.target.value);
  };

  const showList = open && trimmed.length >= 2 && (hits.length > 0 || settled);
  const noResults = showList && settled && hits.length === 0;
  const activeId = hits[active] ? `${listId}-${active}` : undefined;

  return (
    <div className={cn('relative', className)}>
      <label htmlFor={`${listId}-input`} className="sr-only">
        Search the documentation
      </label>
      <div className="relative">
        <Search
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
          aria-hidden="true"
        />
        <input
          ref={inputRef}
          id={`${listId}-input`}
          type="search"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          placeholder="Search documentation"
          value={query}
          onChange={onChange}
          onKeyDown={onKeyDown}
          onFocus={() => {
            void ensureIndex();
            if (hits.length > 0) setOpen(true);
          }}
          onBlur={() => {
            // Delay so a click on a result registers before the list closes.
            window.setTimeout(() => {
              setOpen(false);
            }, 120);
          }}
          aria-expanded={showList}
          aria-controls={`${listId}-list`}
          aria-autocomplete="list"
          aria-activedescendant={showList ? activeId : undefined}
          className="h-10 w-full rounded-md border border-border-strong bg-surface-1 pr-16 pl-9 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-violet/70 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus [&::-webkit-search-cancel-button]:appearance-none"
        />
        <span className="pointer-events-none absolute top-1/2 right-2.5 flex -translate-y-1/2 items-center gap-1.5">
          {loading ? (
            <Loader2 className="size-3.5 animate-spin text-text-muted" aria-hidden="true" />
          ) : null}
          {query === '' ? (
            <Kbd aria-hidden="true">/</Kbd>
          ) : (
            <button
              type="button"
              onClick={() => {
                reset();
                inputRef.current?.focus();
              }}
              aria-label="Clear search"
              className="pointer-events-auto inline-flex size-6 items-center justify-center rounded-xs text-text-muted hover:text-text-primary"
            >
              <X className="size-3.5" />
            </button>
          )}
        </span>
      </div>

      <div role="status" aria-live="polite" className="sr-only">
        {noResults
          ? `No results for ${trimmed}`
          : showList
            ? `${hits.length} result${hits.length === 1 ? '' : 's'} for ${trimmed}`
            : ''}
      </div>

      <ul
        id={`${listId}-list`}
        role="listbox"
        aria-label="Search results"
        hidden={!showList}
        className="absolute inset-x-0 top-full z-30 mt-2 max-h-[min(34rem,70vh)] overflow-y-auto rounded-lg border border-border-strong bg-bg-base p-1.5 shadow-lg"
      >
        {noResults ? (
          <li className="px-3 py-4 text-sm text-text-secondary" role="option" aria-selected={false}>
            No documentation page matches “{trimmed}”.
          </li>
        ) : null}
        {hits.map((hit, index) => (
          <li
            key={hit.id}
            id={`${listId}-${index}`}
            role="option"
            aria-selected={index === active}
            onMouseDown={(event) => {
              event.preventDefault();
            }}
            onMouseEnter={() => {
              setActive(index);
            }}
            onClick={() => {
              choose(hit);
            }}
            className={cn(
              'flex cursor-pointer flex-col gap-1 rounded-md px-3 py-2.5 transition-colors',
              index === active ? 'bg-surface-2' : 'hover:bg-surface-1',
            )}
          >
            <span className="flex items-center justify-between gap-3">
              <span className="min-w-0 truncate text-sm font-medium text-text-primary">
                {hit.heading !== hit.title ? (
                  <>
                    <span className="text-text-muted">{hit.title} › </span>
                    {hit.heading}
                  </>
                ) : (
                  hit.title
                )}
              </span>
              <span className="shrink-0 text-[10px] font-semibold tracking-label text-text-muted uppercase">
                {hit.category}
              </span>
            </span>
            {hit.snippet.length > 0 ? (
              <span className="search-snippet line-clamp-2 text-xs leading-relaxed text-text-secondary">
                {hit.snippet.map((part, partIndex) =>
                  part.highlight ? (
                    <mark key={partIndex}>{part.text}</mark>
                  ) : (
                    <span key={partIndex}>{part.text}</span>
                  ),
                )}
              </span>
            ) : null}
          </li>
        ))}
        {showList && hits.length > 0 ? (
          <li
            role="option"
            aria-selected={false}
            aria-hidden="true"
            className="mt-1 flex items-center gap-3 border-t border-border-subtle px-3 pt-2 pb-1 text-[11px] text-text-muted"
          >
            <span className="inline-flex items-center gap-1">
              <Kbd>↑</Kbd>
              <Kbd>↓</Kbd> navigate
            </span>
            <span className="inline-flex items-center gap-1">
              <Kbd>
                <CornerDownLeft className="size-3" />
              </Kbd>{' '}
              open
            </span>
            <span className="inline-flex items-center gap-1">
              <Kbd>Esc</Kbd> clear
            </span>
          </li>
        ) : null}
      </ul>
    </div>
  );
}

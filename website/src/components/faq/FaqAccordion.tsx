import { ChevronDown, Link2 } from 'lucide-react';
import { type KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { type FaqItem } from '../../lib/faq';
import { cn } from '../../lib/cn';
import { MarkdownBlock } from '../../lib/markdown';
import { type LinkResolver } from '../../lib/markdown-plugins';
import { stripMarkdown } from '../../lib/search';

export interface FaqAccordionProps {
  readonly items: readonly FaqItem[];
  /** Case-insensitive filter over question and answer text. */
  readonly filter?: string;
  readonly resolveLink?: LinkResolver;
  /** Heading level of the questions; the FAQ page has no other sections, so `2` by default. */
  readonly headingLevel?: 2 | 3;
  readonly className?: string;
}

/**
 * Accessible disclosure list: each question is a `<button aria-expanded aria-controls>` inside a
 * heading, each answer a labelled region. The hash in the URL opens the matching item (deep
 * links from `faq.md#…` work), ArrowUp/ArrowDown move between questions, Home/End jump.
 */
export function FaqAccordion({
  items,
  filter = '',
  resolveLink,
  headingLevel = 2,
  className,
}: FaqAccordionProps) {
  const Heading = `h${headingLevel}` as const;
  const { hash } = useLocation();
  const hashId = hash.startsWith('#') ? decodeURIComponent(hash.slice(1)) : '';
  // Explicit user choices win; otherwise the item named by the URL hash is the open one.
  const [choices, setChoices] = useState<ReadonlyMap<string, boolean>>(() => new Map());
  const isOpen = useCallback((id: string) => choices.get(id) ?? id === hashId, [choices, hashId]);
  const buttons = useRef(new Map<string, HTMLButtonElement>());

  // Bring the deep-linked item into view once it is rendered.
  useEffect(() => {
    if (!hashId || !items.some((item) => item.id === hashId)) return undefined;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById(`faq-${hashId}`)?.scrollIntoView({ block: 'start' });
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [hashId, items]);

  const toggle = useCallback(
    (id: string) => {
      setChoices((current) => new Map(current).set(id, !(current.get(id) ?? id === hashId)));
    },
    [hashId],
  );

  const plainAnswers = useMemo(
    () => new Map(items.map((item) => [item.id, stripMarkdown(item.answer).toLowerCase()])),
    [items],
  );
  const needle = filter.trim().toLowerCase();
  const visible = needle
    ? items.filter(
        (item) =>
          item.question.toLowerCase().includes(needle) ||
          (plainAnswers.get(item.id) ?? '').includes(needle),
      )
    : items;

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const keys: Record<string, number> = {
      ArrowDown: index + 1,
      ArrowUp: index - 1,
      Home: 0,
      End: visible.length - 1,
    };
    const target = keys[event.key];
    if (target === undefined) return;
    event.preventDefault();
    const item = visible[(target + visible.length) % visible.length];
    if (item) buttons.current.get(item.id)?.focus();
  };

  return (
    <div className={className}>
      <p role="status" aria-live="polite" className="sr-only">
        {needle ? `${visible.length} of ${items.length} questions match “${filter.trim()}”` : ''}
      </p>
      {visible.length === 0 ? (
        <p className="surface-card p-6 text-sm text-text-secondary">
          No question matches “{filter.trim()}”. Try a different word, or search the documentation.
        </p>
      ) : null}
      <ul className="surface-card divide-y divide-border-subtle">
        {visible.map((item, index) => {
          const expanded = isOpen(item.id);
          const buttonId = `faq-${item.id}`;
          const panelId = `faq-${item.id}-answer`;
          return (
            <li key={item.id} id={`faq-item-${item.id}`} className="scroll-mt-24">
              <Heading className="font-ui">
                <button
                  ref={(node) => {
                    if (node) buttons.current.set(item.id, node);
                    else buttons.current.delete(item.id);
                  }}
                  id={buttonId}
                  type="button"
                  aria-expanded={expanded}
                  aria-controls={panelId}
                  onClick={() => {
                    toggle(item.id);
                  }}
                  onKeyDown={(event) => {
                    onKeyDown(event, index);
                  }}
                  className={cn(
                    'flex w-full items-center justify-between gap-4 px-5 py-4 text-left text-base font-medium transition-colors outline-none focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus sm:px-6',
                    expanded ? 'text-text-primary' : 'text-text-primary/90 hover:text-text-primary',
                  )}
                >
                  <span>{item.question}</span>
                  <ChevronDown
                    aria-hidden="true"
                    className={cn(
                      'size-4 shrink-0 text-text-muted transition-transform duration-(--vanta-duration-base)',
                      expanded && 'rotate-180 text-accent-violet-hover',
                    )}
                  />
                </button>
              </Heading>
              <div
                id={panelId}
                role="region"
                aria-labelledby={buttonId}
                hidden={!expanded}
                className="px-5 pb-5 sm:px-6"
              >
                <MarkdownBlock
                  source={item.answer}
                  className="[&_p]:my-2 [&_p:first-child]:mt-0 [&_ul]:my-2"
                  {...(resolveLink ? { resolveLink } : {})}
                />
                <a
                  href={`#${item.id}`}
                  className="mt-3 inline-flex items-center gap-1.5 text-xs text-text-muted transition-colors hover:text-text-primary"
                >
                  <Link2 className="size-3.5" aria-hidden="true" />
                  Link to this question
                </a>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

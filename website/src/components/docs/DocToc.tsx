import { useEffect, useState } from 'react';
import { cn } from '../../lib/cn';
import { type Heading } from '../../lib/slug';

export interface DocTocProps {
  readonly headings: readonly Heading[];
  readonly className?: string;
  /** Label of the list, "On this page" by default. */
  readonly label?: string;
}

/** Tracks which heading is currently at the top of the viewport. */
function useActiveHeading(ids: readonly string[]): string | undefined {
  const [active, setActive] = useState<string | undefined>(ids[0]);
  useEffect(() => {
    if (ids.length === 0 || typeof IntersectionObserver === 'undefined') return undefined;
    const elements = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);
    if (elements.length === 0) return undefined;
    const visible = new Map<string, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) visible.set(entry.target.id, entry.boundingClientRect.top);
          else visible.delete(entry.target.id);
        }
        if (visible.size > 0) {
          const [top] = [...visible.entries()].sort((a, b) => a[1] - b[1])[0] ?? [];
          if (top) setActive(top);
        }
      },
      { rootMargin: '-96px 0px -60% 0px', threshold: [0, 1] },
    );
    for (const element of elements) observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [ids]);
  return active;
}

/**
 * "On this page" navigation with scroll spy. Renders nothing for pages without headings. Mount it
 * with a `key` per document so the active heading resets when the page changes.
 */
export function DocToc({ headings, className, label = 'On this page' }: DocTocProps) {
  const ids = headings.map((heading) => heading.id);
  const active = useActiveHeading(ids);
  if (headings.length === 0) return null;
  return (
    <nav aria-label={label} className={className}>
      <p className="mb-3 text-[11px] font-semibold tracking-label text-text-muted uppercase">
        {label}
      </p>
      <ul className="flex flex-col gap-0.5 border-l border-border-subtle text-sm">
        {headings.map((heading) => (
          <li key={heading.id} className="-ml-px">
            <a
              href={`#${heading.id}`}
              aria-current={active === heading.id ? 'location' : undefined}
              className={cn(
                'block border-l py-1 pr-2 leading-snug transition-colors',
                heading.level >= 3 ? 'pl-6 text-[13px]' : 'pl-3.5',
                active === heading.id
                  ? 'border-accent-violet-hover text-text-primary'
                  : 'border-transparent text-text-secondary hover:border-border-strong hover:text-text-primary',
              )}
            >
              {heading.text}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

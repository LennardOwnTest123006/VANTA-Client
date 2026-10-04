import { cn } from '../../lib/cn';

export interface TocItem {
  readonly id: string;
  readonly label: string;
}

/** In-page table of contents (anchor links), rendered as a horizontally scrollable chip row. */
export function TocNav({ items, className }: { items: readonly TocItem[]; className?: string }) {
  return (
    <nav aria-label="On this page" className={cn('relative', className)}>
      <ul className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:thin] sm:mx-0 sm:flex-wrap sm:px-0">
        {items.map((item) => (
          <li key={item.id} className="shrink-0">
            <a
              href={`#${item.id}`}
              className="inline-flex h-8 items-center rounded-pill border border-border-strong bg-surface-1/80 px-3.5 text-xs font-medium text-text-secondary transition-colors hover:border-accent-violet/60 hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
            >
              {item.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

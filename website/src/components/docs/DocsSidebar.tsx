import { NavLink } from 'react-router';
import { type DocGroup } from '../../lib/docs';
import { cn } from '../../lib/cn';

export interface DocsSidebarProps {
  readonly groups: readonly DocGroup[];
  /** Slug of the page being read, for `aria-current`. */
  readonly currentSlug: string | undefined;
  /** Called after a link is chosen (closes the mobile drawer). */
  readonly onNavigate?: () => void;
  readonly className?: string;
}

/** Documentation navigation grouped by category, in sidebar order. */
export function DocsSidebar({ groups, currentSlug, onNavigate, className }: DocsSidebarProps) {
  return (
    <nav aria-label="Documentation" className={cn('flex flex-col gap-5', className)}>
      {groups.map((group) => (
        <div key={group.category}>
          <p className="mb-2 px-2.5 text-[11px] font-semibold tracking-label text-text-muted uppercase">
            {group.category}
          </p>
          <ul className="flex flex-col gap-0.5 border-l border-border-subtle">
            {group.pages.map((page) => {
              const active = page.slug === currentSlug;
              return (
                <li key={page.slug} className="-ml-px">
                  <NavLink
                    to={page.route}
                    end
                    onClick={onNavigate}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'block border-l py-[5px] pr-2 pl-3.5 text-sm leading-snug transition-colors',
                      active
                        ? 'border-accent-violet-hover font-medium text-text-primary'
                        : 'border-transparent text-text-secondary hover:border-border-strong hover:text-text-primary',
                    )}
                  >
                    {page.title}
                  </NavLink>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}

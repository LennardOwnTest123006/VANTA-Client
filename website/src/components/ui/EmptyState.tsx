import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface EmptyStateProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  readonly icon?: ReactNode;
  readonly eyebrow?: ReactNode;
  readonly title: ReactNode;
  readonly description?: ReactNode;
  readonly headingLevel?: 2 | 3;
  /** Actions or extra content rendered under the description. */
  readonly children?: ReactNode;
}

/**
 * Honest empty state: a dashed frame with an icon, a clear statement of what is missing and why,
 * and optional actions. Used wherever content does not exist yet (screenshots, support channels).
 */
export function EmptyState({
  icon,
  eyebrow,
  title,
  description,
  headingLevel = 2,
  className,
  children,
  ...rest
}: EmptyStateProps) {
  const Heading = `h${headingLevel}` as const;
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-xl border border-dashed border-border-strong bg-surface-1/60 px-6 py-14 text-center sm:px-10 sm:py-20',
        className,
      )}
      {...rest}
    >
      <div className="absolute inset-0 bg-isogrid mask-fade-radial opacity-40" aria-hidden="true" />
      <div className="relative mx-auto flex max-w-xl flex-col items-center">
        {icon ? (
          <span
            aria-hidden="true"
            className="mb-6 inline-flex size-14 items-center justify-center rounded-xl border border-border-subtle bg-surface-2 text-accent-violet-hover shadow-[inset_0_1px_0_rgba(255,255,255,0.04)] [&>svg]:size-6"
          >
            {icon}
          </span>
        ) : null}
        {eyebrow ? <p className="eyebrow mb-3">{eyebrow}</p> : null}
        <Heading className="font-display text-2xl leading-snug font-semibold tracking-display text-text-primary sm:text-3xl">
          {title}
        </Heading>
        {description ? (
          <div className="mt-4 text-sm leading-relaxed text-text-secondary sm:text-base">
            {description}
          </div>
        ) : null}
        {children ? (
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">{children}</div>
        ) : null}
      </div>
    </div>
  );
}

import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface StatProps extends HTMLAttributes<HTMLDivElement> {
  readonly label: ReactNode;
  readonly value: ReactNode;
  readonly hint?: ReactNode;
  readonly size?: 'sm' | 'md';
}

/**
 * Label + value pair for factual specs ("Minecraft" / "1.21.11"). Never used for marketing numbers.
 * Renders as a description list item so the pair is announced together.
 */
export function Stat({ label, value, hint, size = 'md', className, ...rest }: StatProps) {
  return (
    <div className={cn('flex flex-col gap-1', className)} {...rest}>
      <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
        {label}
      </dt>
      <dd
        className={cn(
          'font-display font-semibold tracking-display text-text-primary',
          size === 'md' ? 'text-xl sm:text-2xl' : 'text-lg',
        )}
      >
        {value}
      </dd>
      {hint ? <dd className="text-xs text-text-muted">{hint}</dd> : null}
    </div>
  );
}

export interface StatGroupProps extends HTMLAttributes<HTMLDListElement> {
  readonly children: ReactNode;
}

/** Wraps several `Stat`s in a description list. */
export function StatGroup({ className, children, ...rest }: StatGroupProps) {
  return (
    <dl className={cn('grid gap-6', className)} {...rest}>
      {children}
    </dl>
  );
}

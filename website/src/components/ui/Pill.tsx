import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface PillProps extends HTMLAttributes<HTMLSpanElement> {
  readonly icon?: ReactNode;
  readonly children: ReactNode;
  readonly tone?: 'neutral' | 'violet';
}

/** Rounded chip with an optional leading icon (e.g. the hero eyebrow). */
export function Pill({ icon, tone = 'neutral', className, children, ...rest }: PillProps) {
  return (
    <span
      className={cn(
        'inline-flex h-8 items-center gap-2 rounded-pill border px-3.5 text-xs font-medium',
        tone === 'violet'
          ? 'border-accent-violet/40 bg-accent-violet/10 text-text-primary'
          : 'border-border-strong bg-surface-1/80 text-text-secondary',
        className,
      )}
      {...rest}
    >
      {icon ? (
        <span aria-hidden="true" className="inline-flex text-accent-violet-hover [&>svg]:size-3.5">
          {icon}
        </span>
      ) : null}
      {children}
    </span>
  );
}

export interface TagProps extends HTMLAttributes<HTMLSpanElement> {
  readonly children: ReactNode;
}

/** Compact monospace tag for technical facts (`1.21.11`, `.jar`, `SHA-256`). */
export function Tag({ className, children, ...rest }: TagProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-xs border border-border-subtle bg-surface-2 px-1.5 py-0.5 font-mono text-[11px] leading-5 text-text-secondary',
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  );
}

import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type BadgeTone = 'neutral' | 'violet' | 'blue' | 'success' | 'warning' | 'danger';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  readonly tone?: BadgeTone;
  /** Shows a small status dot before the label. */
  readonly dot?: boolean;
  readonly children: ReactNode;
}

const tones: Record<BadgeTone, string> = {
  neutral: 'border-border-strong bg-surface-2 text-text-secondary',
  violet: 'border-accent-violet/40 bg-accent-violet/10 text-accent-violet-hover',
  blue: 'border-accent-blue/40 bg-accent-blue/10 text-accent-blue',
  success: 'border-success/40 bg-success/10 text-success',
  warning: 'border-warning/40 bg-warning/10 text-warning',
  danger: 'border-danger/40 bg-danger/10 text-danger',
};

const dots: Record<BadgeTone, string> = {
  neutral: 'bg-text-muted',
  violet: 'bg-accent-violet-hover',
  blue: 'bg-accent-blue',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
};

/** Small uppercase status label. */
export function Badge({ tone = 'neutral', dot = false, className, children, ...rest }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center gap-1.5 rounded-sm border px-2 text-[11px] font-semibold tracking-label uppercase',
        tones[tone],
        className,
      )}
      {...rest}
    >
      {dot ? <span aria-hidden="true" className={cn('size-1.5 rounded-full', dots[tone])} /> : null}
      {children}
    </span>
  );
}

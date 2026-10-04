import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface KbdProps extends HTMLAttributes<HTMLElement> {
  readonly children: ReactNode;
}

/** Keyboard key cap, e.g. `/` or `Esc`. */
export function Kbd({ className, children, ...rest }: KbdProps) {
  return (
    <kbd className={cn('kbd', className)} {...rest}>
      {children}
    </kbd>
  );
}

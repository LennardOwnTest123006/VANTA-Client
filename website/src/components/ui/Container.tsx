import { type ElementType, type HTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

export interface ContainerProps extends HTMLAttributes<HTMLElement> {
  /** `content` caps at 1280px, `wide` at 1440px, `narrow` at 768px for reading. */
  readonly size?: 'narrow' | 'content' | 'wide';
  readonly as?: ElementType;
}

const sizes = {
  narrow: 'max-w-3xl',
  content: 'max-w-content',
  wide: 'max-w-wide',
} as const;

/** Horizontal page container: 16px gutters on phones, growing to 48px, capped for ultrawide screens. */
export function Container({ size = 'content', as, className, ...rest }: ContainerProps) {
  const Tag: ElementType = as ?? 'div';
  return (
    <Tag
      className={cn('mx-auto w-full px-4 sm:px-6 lg:px-8 xl:px-12', sizes[size], className)}
      {...rest}
    />
  );
}

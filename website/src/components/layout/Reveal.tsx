import { type ElementType, type HTMLAttributes, useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/cn';
import { useReducedMotion } from '../../lib/hooks';

/** Content is revealed after this long even when the observer never reports an intersection. */
const FALLBACK_MS = 2500;

export interface RevealProps extends HTMLAttributes<HTMLElement> {
  readonly as?: ElementType;
  /** Stagger delay in milliseconds. */
  readonly delay?: number;
  /** Root margin passed to the IntersectionObserver. */
  readonly rootMargin?: string;
}

/**
 * Fades and slides its content in when it enters the viewport. Pure CSS transitions driven by an
 * IntersectionObserver; renders fully visible when motion is reduced or the observer is unavailable.
 */
export function Reveal({
  as,
  delay = 0,
  rootMargin = '0px 0px -10% 0px',
  className,
  style,
  children,
  ...rest
}: RevealProps) {
  const Tag: ElementType = as ?? 'div';
  const ref = useRef<HTMLElement>(null);
  const reduced = useReducedMotion();
  // Without IntersectionObserver (very old browsers, jsdom) content is simply visible.
  const [visible, setVisible] = useState<boolean>(
    () => typeof IntersectionObserver === 'undefined',
  );

  useEffect(() => {
    if (visible || reduced) return undefined;
    const node = ref.current;
    if (!node) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin, threshold: 0.08 },
    );
    observer.observe(node);
    // Safety net: content must never stay hidden (print, odd viewports, observers that never fire).
    const fallback = window.setTimeout(() => {
      setVisible(true);
      observer.disconnect();
    }, FALLBACK_MS);
    return () => {
      observer.disconnect();
      window.clearTimeout(fallback);
    };
  }, [visible, reduced, rootMargin]);

  const shown = visible || reduced;
  return (
    <Tag
      ref={ref}
      className={cn('reveal', shown && 'is-visible', className)}
      style={{ ...style, '--reveal-delay': `${delay}ms` } as React.CSSProperties}
      {...rest}
    >
      {children}
    </Tag>
  );
}

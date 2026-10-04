import { type CSSProperties } from 'react';
import { cn } from '../../lib/cn';

export interface IsoCubeProps {
  /** Edge length in pixels. */
  readonly size: number;
  readonly className?: string;
  readonly style?: CSSProperties;
  /** Adds a faint violet tint to the top face. */
  readonly accent?: boolean;
}

const TOP = 'polygon(50% 0, 100% 25%, 50% 50%, 0 25%)';
const LEFT = 'polygon(0 25%, 50% 50%, 50% 100%, 0 75%)';
const RIGHT = 'polygon(50% 50%, 100% 25%, 100% 75%, 50% 100%)';
const OUTLINE = 'polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%)';

/**
 * Decorative isometric block drawn with three clipped faces — the "Minecraft depth" of the hero,
 * built from CSS only. Purely presentational (`aria-hidden`).
 */
export function IsoCube({ size, className, style, accent = false }: IsoCubeProps) {
  const width = size * 1.732;
  const height = size * 2;
  return (
    <div
      aria-hidden="true"
      className={cn('pointer-events-none absolute select-none', className)}
      style={{ width, height, ...style }}
    >
      <div className="absolute -inset-px bg-border-strong/70" style={{ clipPath: OUTLINE }} />
      <div
        className={cn(
          'absolute inset-0',
          accent
            ? 'bg-[linear-gradient(180deg,color-mix(in_srgb,var(--color-accent-violet)_28%,var(--color-surface-4)),var(--color-surface-3))]'
            : 'bg-[linear-gradient(180deg,var(--color-surface-4),var(--color-surface-3))]',
        )}
        style={{ clipPath: TOP }}
      />
      <div className="absolute inset-0 bg-surface-2" style={{ clipPath: LEFT }} />
      <div className="absolute inset-0 bg-surface-1" style={{ clipPath: RIGHT }} />
    </div>
  );
}

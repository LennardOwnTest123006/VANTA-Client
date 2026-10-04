import { type SVGProps, useId } from 'react';
import { cn } from '../../lib/cn';

/** The VANTA mark geometry, shared by every variant (viewBox 0 0 64 64). */
export const MARK_PATH =
  'M5 9 H19.5 L32 36.4 L44.5 9 H59 L32 58 Z M32 31.6 L36.6 40.2 L33.2 51.6 L28.4 42.2 Z';

export interface LogoProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  /** `mark` is the V alone, `wordmark` the VANTA letters, `lockup` both side by side. */
  readonly variant?: 'mark' | 'wordmark' | 'lockup';
  /** Height in pixels; width follows the aspect ratio. */
  readonly size?: number;
  /** Uses `currentColor` instead of the accent gradient (footers, mono contexts). */
  readonly mono?: boolean;
  readonly title?: string;
}

function Letters() {
  // Geometric capitals from assets/brand/vanta-wordmark.svg (translate 124 28 in the 512x128 file).
  return (
    <g fill="currentColor">
      <path d="M0 0 H14 L30 48 L46 0 H60 L37 72 H23 Z" />
      <path
        fillRule="evenodd"
        d="M70 72 L94 0 H108 L132 72 H118 L113 56 H89 L84 72 Z M93 44 H109 L101 17 Z"
      />
      <path d="M144 72 V0 H157 L186 46 V0 H199 V72 H186 L157 26 V72 Z" />
      <path d="M208 0 H266 V13 H243.5 V72 H230.5 V13 H208 Z" />
      <path
        fillRule="evenodd"
        d="M270 72 L294 0 H308 L332 72 H318 L313 56 H289 L284 72 Z M293 44 H309 L301 17 Z"
      />
    </g>
  );
}

/** Inline SVG logo. Decorative by default; pass `title` to make it an accessible image. */
export function Logo({
  variant = 'lockup',
  size = 28,
  mono = false,
  title,
  className,
  ...rest
}: LogoProps) {
  const gradientId = useId();
  const fill = mono ? 'currentColor' : `url(#${gradientId})`;
  const gradient = mono ? null : (
    <defs>
      <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="#9B82FF" />
        <stop offset="1" stopColor="#4F8DFF" />
      </linearGradient>
    </defs>
  );
  const a11y = title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true as const };

  if (variant === 'mark') {
    return (
      <svg
        viewBox="0 0 64 64"
        width={size}
        height={size}
        className={cn('shrink-0', className)}
        {...a11y}
        {...rest}
      >
        {gradient}
        <path fill={fill} fillRule="evenodd" d={MARK_PATH} />
      </svg>
    );
  }

  if (variant === 'wordmark') {
    // Letters span 332 x 72 units.
    return (
      <svg
        viewBox="0 0 332 72"
        height={size}
        width={(size * 332) / 72}
        className={cn('shrink-0', className)}
        {...a11y}
        {...rest}
      >
        <Letters />
      </svg>
    );
  }

  // Lockup: mark (scaled to the letter height) + letters, matching assets/brand/vanta-wordmark.svg.
  return (
    <svg
      viewBox="0 0 496 96"
      height={size}
      width={(size * 496) / 96}
      className={cn('shrink-0', className)}
      {...a11y}
      {...rest}
    >
      {gradient}
      <g transform="translate(0 0) scale(1.5)">
        <path fill={fill} fillRule="evenodd" d={MARK_PATH} />
      </g>
      <g transform="translate(116 12)">
        <Letters />
      </g>
    </svg>
  );
}

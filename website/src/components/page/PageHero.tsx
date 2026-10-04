import { type ReactNode } from 'react';
import { Container } from '../ui/Container';
import { cn } from '../../lib/cn';

export interface PageHeroProps {
  readonly eyebrow: ReactNode;
  readonly title: ReactNode;
  readonly lead: ReactNode;
  readonly children?: ReactNode;
  readonly align?: 'left' | 'center';
  readonly className?: string;
}

/** Compact hero for sub-pages: ambience, eyebrow, H1 and lead on a faint isometric grid. */
export function PageHero({
  eyebrow,
  title,
  lead,
  children,
  align = 'left',
  className,
}: PageHeroProps) {
  const centered = align === 'center';
  return (
    <section className={cn('relative overflow-hidden border-b border-border-subtle', className)}>
      <div className="hero-ambience" aria-hidden="true" />
      <div className="absolute inset-0 bg-isogrid mask-fade-b opacity-50" aria-hidden="true" />
      <Container className="relative py-16 sm:py-20 lg:py-24">
        <div className={cn('max-w-3xl', centered && 'mx-auto text-center')}>
          <p className="eyebrow mb-4">{eyebrow}</p>
          <h1 className="font-display text-4xl leading-[1.05] font-semibold tracking-display text-text-primary sm:text-5xl lg:text-6xl">
            {title}
          </h1>
          <p className="mt-5 max-w-2xl text-lg leading-relaxed text-text-secondary sm:text-xl">
            {lead}
          </p>
          {children ? <div className="mt-8">{children}</div> : null}
        </div>
      </Container>
    </section>
  );
}

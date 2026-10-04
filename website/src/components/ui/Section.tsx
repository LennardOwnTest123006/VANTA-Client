import { type ElementType, type HTMLAttributes, type ReactNode, useId } from 'react';
import { cn } from '../../lib/cn';
import { Container, type ContainerProps } from './Container';

export interface SectionProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  readonly eyebrow?: ReactNode;
  readonly title?: ReactNode;
  /** Lead paragraph under the title. */
  readonly lead?: ReactNode;
  readonly align?: 'left' | 'center';
  readonly headingLevel?: 1 | 2 | 3;
  readonly as?: ElementType;
  readonly containerSize?: ContainerProps['size'];
  /** Vertical rhythm. */
  readonly spacing?: 'sm' | 'md' | 'lg';
  readonly children?: ReactNode;
  /** Extra content placed to the right of the heading on large screens (e.g. a button). */
  readonly aside?: ReactNode;
}

const spacings = {
  sm: 'py-12 sm:py-16',
  md: 'py-16 sm:py-20 lg:py-24',
  lg: 'py-20 sm:py-28 lg:py-32',
} as const;

/**
 * Page section with the standard heading block (eyebrow / title / lead). The section is labelled by
 * its title for assistive technology.
 */
export function Section({
  eyebrow,
  title,
  lead,
  align = 'left',
  headingLevel = 2,
  as,
  containerSize = 'content',
  spacing = 'md',
  aside,
  className,
  children,
  ...rest
}: SectionProps) {
  const Tag: ElementType = as ?? 'section';
  const Heading: ElementType = `h${headingLevel}`;
  const headingId = useId();
  const centered = align === 'center';
  return (
    <Tag
      className={cn('relative', spacings[spacing], className)}
      {...(title ? { 'aria-labelledby': headingId } : {})}
      {...rest}
    >
      <Container size={containerSize}>
        {title || eyebrow || lead ? (
          <div
            className={cn(
              'mb-10 sm:mb-14',
              centered ? 'mx-auto max-w-2xl text-center' : 'max-w-2xl',
              aside && 'lg:flex lg:max-w-none lg:items-end lg:justify-between lg:gap-8',
            )}
          >
            <div className={cn(aside && 'max-w-2xl')}>
              {eyebrow ? <p className="eyebrow mb-3">{eyebrow}</p> : null}
              {title ? (
                <Heading
                  id={headingId}
                  className="font-display text-3xl leading-[1.08] font-semibold tracking-display text-text-primary sm:text-4xl"
                >
                  {title}
                </Heading>
              ) : null}
              {lead ? (
                <p className="mt-4 text-base leading-relaxed text-text-secondary sm:text-lg">
                  {lead}
                </p>
              ) : null}
            </div>
            {aside ? <div className="mt-6 shrink-0 lg:mt-0">{aside}</div> : null}
          </div>
        ) : null}
        {children}
      </Container>
    </Tag>
  );
}

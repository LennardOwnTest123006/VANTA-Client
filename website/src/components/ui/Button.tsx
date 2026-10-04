import {
  type AnchorHTMLAttributes,
  type ButtonHTMLAttributes,
  type ReactNode,
  forwardRef,
} from 'react';
import { Link, type LinkProps } from 'react-router';
import { cn } from '../../lib/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'link';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonBaseProps {
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly fullWidth?: boolean;
  readonly leadingIcon?: ReactNode;
  readonly trailingIcon?: ReactNode;
  readonly className?: string;
  readonly children: ReactNode;
}

/** Renders a `<button>`. */
export type ButtonAsButtonProps = ButtonBaseProps &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'children'> & {
    readonly to?: undefined;
    readonly href?: undefined;
  };

/** Renders a react-router `<Link>` for internal navigation. */
export type ButtonAsLinkProps = ButtonBaseProps &
  Omit<LinkProps, 'className' | 'children' | 'to'> & {
    readonly to: string;
    readonly href?: undefined;
  };

/** Renders an `<a>`; external URLs get `target="_blank" rel="noopener noreferrer"` automatically. */
export type ButtonAsAnchorProps = ButtonBaseProps &
  Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className' | 'children' | 'href'> & {
    readonly href: string;
    readonly to?: undefined;
    readonly external?: boolean;
  };

export type ButtonProps = ButtonAsButtonProps | ButtonAsLinkProps | ButtonAsAnchorProps;

const base =
  'group/button relative inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-md font-ui font-semibold ' +
  'transition-[background-color,border-color,color,box-shadow,transform] duration-(--vanta-duration-fast) ease-standard ' +
  'select-none outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus ' +
  'disabled:cursor-not-allowed disabled:opacity-60 aria-disabled:cursor-not-allowed aria-disabled:opacity-60 ' +
  'motion-safe:active:scale-[0.985]';

const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-gradient-accent text-white shadow-[0_1px_0_rgba(255,255,255,0.18)_inset,0_8px_24px_-8px_rgba(124,92,255,0.6)] ' +
    'hover:brightness-110 hover:shadow-[0_1px_0_rgba(255,255,255,0.22)_inset,0_12px_32px_-8px_rgba(124,92,255,0.75)] ' +
    'disabled:bg-none disabled:bg-surface-3 disabled:text-text-muted disabled:opacity-100 disabled:shadow-none disabled:hover:brightness-100 ' +
    'aria-disabled:bg-none aria-disabled:bg-surface-3 aria-disabled:text-text-muted aria-disabled:opacity-100 aria-disabled:shadow-none aria-disabled:hover:brightness-100',
  secondary:
    'border border-border-strong bg-surface-2 text-text-primary ' +
    'hover:border-accent-violet/60 hover:bg-surface-3 disabled:hover:border-border-strong disabled:hover:bg-surface-2',
  ghost: 'text-text-secondary hover:bg-surface-2 hover:text-text-primary',
  link: 'h-auto px-0 text-accent-violet-hover underline-offset-4 hover:text-text-primary hover:underline',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'h-9 px-3.5 text-sm',
  md: 'h-11 px-5 text-sm',
  lg: 'h-12 px-6 text-base',
};

/** Icon slot wrapper; icons are decorative, the label carries the meaning. */
function Slot({ children }: { children: ReactNode }) {
  return (
    <span aria-hidden="true" className="inline-flex shrink-0 items-center [&>svg]:size-[1.1em]">
      {children}
    </span>
  );
}

/**
 * The button of the design system. Renders a `<button>`, an internal `<Link>` (`to`) or an anchor
 * (`href`) with identical styling. Icons are optional and decorative.
 */
export const Button = forwardRef<HTMLButtonElement | HTMLAnchorElement, ButtonProps>(
  function Button(props, ref) {
    const {
      variant = 'primary',
      size = 'md',
      fullWidth = false,
      leadingIcon,
      trailingIcon,
      className,
      children,
    } = props;
    const classes = cn(
      base,
      variants[variant],
      variant === 'link' ? '' : sizes[size],
      fullWidth && 'w-full',
      className,
    );
    const content = (
      <>
        {leadingIcon ? <Slot>{leadingIcon}</Slot> : null}
        <span>{children}</span>
        {trailingIcon ? (
          <Slot>
            <span className="inline-flex transition-transform duration-(--vanta-duration-fast) ease-standard motion-safe:group-hover/button:translate-x-0.5">
              {trailingIcon}
            </span>
          </Slot>
        ) : null}
      </>
    );

    if (props.to !== undefined) {
      const {
        to,
        variant: _v,
        size: _s,
        fullWidth: _f,
        leadingIcon: _l,
        trailingIcon: _t,
        className: _c,
        children: _ch,
        ...rest
      } = props;
      return (
        <Link ref={ref as React.Ref<HTMLAnchorElement>} to={to} className={classes} {...rest}>
          {content}
        </Link>
      );
    }

    if (props.href !== undefined) {
      const {
        href,
        external,
        variant: _v,
        size: _s,
        fullWidth: _f,
        leadingIcon: _l,
        trailingIcon: _t,
        className: _c,
        children: _ch,
        ...rest
      } = props;
      const isExternal = external ?? /^https?:/i.test(href);
      return (
        <a
          ref={ref as React.Ref<HTMLAnchorElement>}
          href={href}
          className={classes}
          {...(isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
          {...rest}
        >
          {content}
        </a>
      );
    }

    const {
      type = 'button',
      variant: _v,
      size: _s,
      fullWidth: _f,
      leadingIcon: _l,
      trailingIcon: _t,
      className: _c,
      children: _ch,
      to: _to,
      href: _href,
      ...rest
    } = props;
    return (
      <button ref={ref as React.Ref<HTMLButtonElement>} type={type} className={classes} {...rest}>
        {content}
      </button>
    );
  },
);

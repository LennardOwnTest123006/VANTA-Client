import { type AnchorHTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router';
import { isRouteReady } from '../../config/siteNav';
import { cn } from '../../lib/cn';

export interface RouteLinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> {
  readonly to: string;
  readonly children: ReactNode;
  /** Rendered after the label when the route is not ready yet. */
  readonly pendingHint?: ReactNode;
}

/**
 * Link to an internal route that degrades to plain text while the route is not registered
 * (`ready: false` in `siteNav`). This keeps copy honest: no dead links, no placeholder pages.
 */
export function RouteLink({ to, children, pendingHint, className, ...rest }: RouteLinkProps) {
  if (isRouteReady(to)) {
    return (
      <Link
        to={to}
        className={cn(
          'text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 transition-colors hover:text-text-primary hover:decoration-accent-violet-hover',
          className,
        )}
        {...rest}
      >
        {children}
      </Link>
    );
  }
  return (
    <span className={cn('text-text-primary', className)}>
      {children}
      {pendingHint ? <span className="text-text-muted"> {pendingHint}</span> : null}
    </span>
  );
}

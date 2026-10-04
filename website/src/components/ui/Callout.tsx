import { type HTMLAttributes, type ReactNode } from 'react';
import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react';
import { cn } from '../../lib/cn';

export type CalloutTone = 'info' | 'success' | 'warning' | 'danger';

export interface CalloutProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  readonly tone?: CalloutTone;
  readonly title?: ReactNode;
  readonly icon?: ReactNode;
  readonly children: ReactNode;
}

const tones: Record<CalloutTone, { frame: string; icon: ReactNode }> = {
  info: { frame: 'border-accent-blue/35 text-accent-blue', icon: <Info /> },
  success: { frame: 'border-success/35 text-success', icon: <CircleCheck /> },
  warning: { frame: 'border-warning/35 text-warning', icon: <TriangleAlert /> },
  danger: { frame: 'border-danger/35 text-danger', icon: <CircleAlert /> },
};

/** Bordered note with an icon and a tone. Used for honest caveats and verification hints. */
export function Callout({
  tone = 'info',
  title,
  icon,
  className,
  children,
  ...rest
}: CalloutProps) {
  const config = tones[tone];
  return (
    <div
      role={tone === 'warning' || tone === 'danger' ? 'alert' : 'note'}
      className={cn(
        'flex gap-4 rounded-lg border bg-surface-1/70 p-5 sm:p-6',
        config.frame,
        className,
      )}
      {...rest}
    >
      <span aria-hidden="true" className="mt-0.5 shrink-0 [&>svg]:size-5">
        {icon ?? config.icon}
      </span>
      <div className="min-w-0 text-sm leading-relaxed text-text-secondary">
        {title ? <p className="mb-1 font-semibold text-text-primary">{title}</p> : null}
        {children}
      </div>
    </div>
  );
}

import { Check, Copy } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { copyToClipboard } from '../../lib/hooks';
import { cn } from '../../lib/cn';

export interface CopyButtonProps {
  /** Text placed on the clipboard. */
  readonly value: string;
  /** Accessible name, e.g. "Copy SHA-256 of vanta-client-1.0.0.jar". */
  readonly label: string;
  readonly className?: string;
}

type CopyState = 'idle' | 'copied' | 'failed';

/** Icon button that copies `value` and confirms with an inline, screen-reader announced status. */
export function CopyButton({ value, label, className }: CopyButtonProps) {
  const [state, setState] = useState<CopyState>('idle');
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const onClick = useCallback(() => {
    void copyToClipboard(value).then((ok) => {
      setState(ok ? 'copied' : 'failed');
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => {
        setState('idle');
      }, 2000);
    });
  }, [value]);

  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <button
        type="button"
        onClick={onClick}
        aria-label={label}
        className="inline-flex size-8 items-center justify-center rounded-sm border border-border-strong bg-surface-2 text-text-secondary transition-colors hover:border-accent-violet/60 hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus [&>svg]:size-4"
      >
        {state === 'copied' ? <Check className="text-success" /> : <Copy />}
      </button>
      <span role="status" aria-live="polite" className="text-xs text-text-muted">
        {state === 'copied'
          ? 'Copied'
          : state === 'failed'
            ? 'Copy failed — select the text instead'
            : ''}
      </span>
    </span>
  );
}

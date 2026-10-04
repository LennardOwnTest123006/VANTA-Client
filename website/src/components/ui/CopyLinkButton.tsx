import { Check, Link2 } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { copyToClipboard } from '../../lib/hooks';
import { absoluteUrl } from '../../lib/meta';
import { Button } from './Button';

export interface CopyLinkButtonProps {
  /** URL to copy; defaults to the absolute URL of the current route. */
  readonly url?: string;
  readonly label?: string;
  readonly className?: string;
}

type CopyState = 'idle' | 'copied' | 'failed';

/** "Copy link" button for sharing the current page, with a screen-reader announced confirmation. */
export function CopyLinkButton({ url, label = 'Copy link', className }: CopyLinkButtonProps) {
  const { pathname } = useLocation();
  const [state, setState] = useState<CopyState>('idle');
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const onClick = useCallback(() => {
    void copyToClipboard(url ?? absoluteUrl(pathname)).then((ok) => {
      setState(ok ? 'copied' : 'failed');
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => {
        setState('idle');
      }, 2500);
    });
  }, [url, pathname]);

  return (
    <span className={className}>
      <Button
        variant="secondary"
        size="sm"
        onClick={onClick}
        leadingIcon={state === 'copied' ? <Check className="text-success" /> : <Link2 />}
      >
        {state === 'copied' ? 'Link copied' : label}
      </Button>
      <span role="status" aria-live="polite" className="sr-only">
        {state === 'copied'
          ? 'Link copied to the clipboard'
          : state === 'failed'
            ? 'Copy failed — copy the address from the browser bar instead'
            : ''}
      </span>
      {state === 'failed' ? (
        <span className="ml-3 text-xs text-text-muted">Copy the address from the browser bar.</span>
      ) : null}
    </span>
  );
}

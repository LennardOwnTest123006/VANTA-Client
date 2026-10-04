import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { useCallback, useEffect, useId, useRef } from 'react';
import { type Screenshot } from '../../lib/screenshots';
import { useBodyScrollLock, useFocusTrap } from '../../lib/hooks';

export interface LightboxProps {
  readonly items: readonly Screenshot[];
  /** Index of the open item; `undefined` closes the lightbox. */
  readonly index: number | undefined;
  readonly onClose: () => void;
  readonly onIndexChange: (index: number) => void;
}

/**
 * Full-screen image viewer. Arrow keys and buttons move between images (wrapping), Escape and the
 * backdrop close it, focus is trapped and restored, and the counter/caption are announced.
 */
export function Lightbox({ items, index, onClose, onIndexChange }: LightboxProps) {
  const item = index === undefined ? undefined : items[index];
  const open = item !== undefined;
  const dialogRef = useRef<HTMLDivElement>(null);
  const captionId = useId();
  const close = useCallback(() => {
    onClose();
  }, [onClose]);

  useFocusTrap(dialogRef, open, close);
  useBodyScrollLock(open);

  const step = useCallback(
    (delta: number) => {
      if (index === undefined || items.length === 0) return;
      onIndexChange((index + delta + items.length) % items.length);
    },
    [index, items.length, onIndexChange],
  );

  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        step(1);
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
        step(-1);
      } else if (event.key === 'Home') {
        event.preventDefault();
        onIndexChange(0);
      } else if (event.key === 'End') {
        event.preventDefault();
        onIndexChange(items.length - 1);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, step, onIndexChange, items.length]);

  if (item === undefined || index === undefined) return null;
  const buttonClass =
    'inline-flex size-11 items-center justify-center rounded-md border border-border-strong bg-surface-1/90 text-text-primary transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus disabled:opacity-40';

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-bg-void/90 p-4 backdrop-blur-sm sm:p-8">
      <div className="absolute inset-0" onClick={close} aria-hidden="true" />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={`Screenshot ${index + 1} of ${items.length}`}
        aria-describedby={captionId}
        tabIndex={-1}
        className="relative flex max-h-full w-full max-w-6xl flex-col gap-4 outline-none"
      >
        <div className="flex items-center justify-between gap-4">
          <p className="font-mono text-xs tracking-wide text-text-secondary">
            {index + 1} / {items.length}
          </p>
          <button type="button" onClick={close} aria-label="Close" className={buttonClass}>
            <X className="size-5" />
          </button>
        </div>
        <figure className="flex min-h-0 flex-1 flex-col gap-3">
          <div className="relative flex min-h-0 flex-1 items-center justify-center">
            <img
              key={item.file}
              src={item.src}
              alt={item.alt}
              decoding="async"
              className="max-h-[70vh] w-auto max-w-full rounded-lg border border-border-strong bg-bg-base object-contain shadow-lg"
            />
            {items.length > 1 ? (
              <>
                <button
                  type="button"
                  onClick={() => {
                    step(-1);
                  }}
                  aria-label="Previous screenshot"
                  className={`${buttonClass} absolute top-1/2 left-2 -translate-y-1/2`}
                >
                  <ChevronLeft className="size-5" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    step(1);
                  }}
                  aria-label="Next screenshot"
                  className={`${buttonClass} absolute top-1/2 right-2 -translate-y-1/2`}
                >
                  <ChevronRight className="size-5" />
                </button>
              </>
            ) : null}
          </div>
          <figcaption id={captionId} className="text-center">
            <span className="block text-sm text-text-primary">{item.caption}</span>
            {item.capturedWith ? (
              <span className="mt-1 block font-mono text-xs text-text-muted">
                {item.capturedWith}
              </span>
            ) : null}
          </figcaption>
        </figure>
      </div>
    </div>
  );
}

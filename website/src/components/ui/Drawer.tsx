import { X } from 'lucide-react';
import { type ReactNode, useCallback, useEffect, useId, useRef } from 'react';
import { cn } from '../../lib/cn';
import { useBodyScrollLock, useFocusTrap } from '../../lib/hooks';

export interface DrawerProps {
  readonly open: boolean;
  readonly onClose: () => void;
  /** Accessible name of the dialog. */
  readonly title: string;
  readonly children: ReactNode;
  /** Edge the panel slides in from. */
  readonly side?: 'left' | 'right';
  /**
   * Element to focus when the drawer opens (e.g. a search box); defaults to the first focusable
   * element. Pass a stable function so the trap is not re-armed on every render.
   */
  readonly initialFocus?: (root: HTMLElement) => HTMLElement | null;
  readonly className?: string;
}

/**
 * Modal side panel for small screens (documentation sidebar, filters). Focus is trapped while
 * open, Escape and the backdrop close it, body scrolling is locked and focus returns to the
 * opener. Rendered only while open so it never hides content from assistive technology.
 */
export function Drawer({
  open,
  onClose,
  title,
  children,
  side = 'left',
  initialFocus,
  className,
}: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const close = useCallback(() => {
    onClose();
  }, [onClose]);

  useFocusTrap(panelRef, open, close, initialFocus);
  useBodyScrollLock(open);

  // Close when the viewport grows past the breakpoint where the drawer is replaced by a sidebar.
  useEffect(() => {
    if (!open || typeof window.matchMedia !== 'function') return undefined;
    const media = window.matchMedia('(min-width: 1024px)');
    const onChange = (event: MediaQueryListEvent) => {
      if (event.matches) close();
    };
    media.addEventListener('change', onChange);
    return () => {
      media.removeEventListener('change', onChange);
    };
  }, [open, close]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[60] lg:hidden">
      <div className="absolute inset-0 bg-bg-void/70 backdrop-blur-sm" onClick={close} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cn(
          'absolute inset-y-0 flex w-[min(22rem,88vw)] flex-col border-border-subtle bg-bg-base shadow-lg outline-none motion-safe:animate-[drawer-in_var(--vanta-duration-base)_var(--ease-standard)]',
          side === 'left' ? 'left-0 border-r' : 'right-0 border-l',
          className,
        )}
      >
        <div className="flex h-16 shrink-0 items-center justify-between border-b border-border-subtle px-4">
          <h2 id={titleId} className="font-display text-base font-semibold text-text-primary">
            {title}
          </h2>
          <button
            type="button"
            onClick={close}
            aria-label={`Close ${title.toLowerCase()}`}
            className="inline-flex size-9 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-surface-2 hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            <X className="size-5" />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">{children}</div>
      </div>
    </div>
  );
}

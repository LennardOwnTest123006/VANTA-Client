import { type RefObject, useCallback, useEffect, useSyncExternalStore } from 'react';

/** Subscribes to a CSS media query. Returns `false` where `matchMedia` is unavailable (SSR, old jsdom). */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
        return () => undefined;
      }
      const media = window.matchMedia(query);
      media.addEventListener('change', onChange);
      return () => {
        media.removeEventListener('change', onChange);
      };
    },
    [query],
  );
  const getSnapshot = useCallback(
    () =>
      typeof window !== 'undefined' && typeof window.matchMedia === 'function'
        ? window.matchMedia(query).matches
        : false,
    [query],
  );
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}

/** True when the user asked for reduced motion. */
export function useReducedMotion(): boolean {
  return useMediaQuery('(prefers-reduced-motion: reduce)');
}

function subscribeToScroll(onChange: () => void): () => void {
  window.addEventListener('scroll', onChange, { passive: true });
  return () => {
    window.removeEventListener('scroll', onChange);
  };
}

/** True once the page is scrolled past `threshold` pixels. Passive scroll subscription. */
export function useScrolled(threshold = 8): boolean {
  const getSnapshot = useCallback(() => window.scrollY > threshold, [threshold]);
  return useSyncExternalStore(subscribeToScroll, getSnapshot, () => false);
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** True when the element is not hidden itself or by an ancestor (`hidden`, `aria-hidden`, CSS). */
function isVisible(el: HTMLElement): boolean {
  if (el.closest('[hidden], [aria-hidden="true"]')) return false;
  // `checkVisibility` is implemented by every evergreen browser; jsdom lacks it and lays nothing out.
  if (typeof el.checkVisibility === 'function') return el.checkVisibility();
  return true;
}

/** Returns the focusable descendants of an element in DOM order. */
export function focusableElements(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(isVisible);
}

/**
 * Keeps keyboard focus inside `ref` while `active` is true: Tab/Shift+Tab wrap around, the first
 * focusable element receives focus when the trap activates, and focus returns to the previously
 * focused element when it deactivates. `onEscape` is called for the Escape key.
 */
export function useFocusTrap(
  ref: RefObject<HTMLElement | null>,
  active: boolean,
  onEscape?: () => void,
): void {
  useEffect(() => {
    if (!active) return undefined;
    const root = ref.current;
    if (!root) return undefined;
    const previouslyFocused = document.activeElement as HTMLElement | null;

    const focusables = focusableElements(root);
    (focusables[0] ?? root).focus({ preventScroll: true });

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onEscape?.();
        return;
      }
      if (event.key !== 'Tab') return;
      const items = focusableElements(root);
      const first = items[0];
      const last = items[items.length - 1];
      if (!first || !last) {
        event.preventDefault();
        return;
      }
      const current = document.activeElement;
      if (event.shiftKey && (current === first || !root.contains(current))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (current === last || !root.contains(current))) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previouslyFocused?.focus({ preventScroll: true });
    };
  }, [ref, active, onEscape]);
}

/** Locks body scrolling while `locked` is true (restores the previous inline style afterwards). */
export function useBodyScrollLock(locked: boolean): void {
  useEffect(() => {
    if (!locked) return undefined;
    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = overflow;
    };
  }, [locked]);
}

/**
 * Copies text to the clipboard with the async Clipboard API. Returns `true` on success and `false`
 * when the API is unavailable (insecure context, denied permission) so the UI can tell the user to
 * select the text instead.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard || !window.isSecureContext) {
      return false;
    }
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

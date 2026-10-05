/** How long a hash target is kept in place while the content above it is still rendering. */
export const HASH_SETTLE_MS = 4000;

/** Events that mean the user scrolls or interacts: from then on the page is left alone. */
const USER_INTERACTION_EVENTS = ['wheel', 'touchstart', 'keydown', 'pointerdown'] as const;

/**
 * Scrolls `target` to the top of the viewport and keeps it there while the layout settles.
 *
 * Lazily rendered content above the target (markdown blocks replace their loading skeletons with the
 * taller rendered text) grows after the jump and would push the target out of view, so a deep link
 * such as `/changelog#launcher-1.0.0` would land on another entry. Every size change of the document
 * re-aligns the target until `settleMs` have passed or the user scrolls, taps, clicks or presses a
 * key. Returns a function that stops holding early.
 */
export function scrollToAndHold(target: HTMLElement, settleMs = HASH_SETTLE_MS): () => void {
  target.scrollIntoView({ block: 'start' });
  if (typeof ResizeObserver === 'undefined') return () => undefined;
  let stopped = false;
  const observer = new ResizeObserver(() => {
    if (!stopped && target.isConnected) target.scrollIntoView({ block: 'start' });
  });
  const stop = () => {
    if (stopped) return;
    stopped = true;
    observer.disconnect();
    window.clearTimeout(timer);
    for (const type of USER_INTERACTION_EVENTS) window.removeEventListener(type, stop, true);
  };
  observer.observe(document.body);
  for (const type of USER_INTERACTION_EVENTS) {
    window.addEventListener(type, stop, { capture: true, passive: true });
  }
  const timer = window.setTimeout(stop, settleMs);
  return stop;
}

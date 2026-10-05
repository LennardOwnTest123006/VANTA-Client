/**
 * Recovery from stale route chunks after a redeploy.
 *
 * Every page except the home page is a lazy chunk with a content hash in its file name. A tab that
 * was opened before a deploy still runs the old entry chunk and asks for old chunk names that no
 * longer exist (Netlify answers with the HTML fallback, so the import fails). Vite reports such
 * failures with a `vite:preloadError` event on `window` whose `payload` is the very error the import
 * then rejects with.
 *
 * The listener installed here only records those errors. Nothing is reloaded until the page error
 * boundary (`Layout.tsx`) catches one of them, that is, until a failed chunk actually keeps a page
 * from rendering ({@link reloadForStaleChunk}). The boundary then reloads the page once so the browser
 * fetches the new `index.html` and the current chunk names. A failed prefetch — hovering or focusing
 * a header or footer link (`prefetch.ts`) — never reaches the boundary, so it never reloads the page
 * the user is reading; the click on the link retries the import, and only that failure counts.
 *
 * Guards against a reload that cannot help or would loop:
 * - at most once per browser session and build: before reloading, a marker keyed by the build is
 *   written to `sessionStorage`. If the same build fails again after the reload (a real outage, or a
 *   cached `index.html`), the error is shown by the boundary instead. Without usable `sessionStorage`
 *   there is no guard, so nothing is reloaded automatically;
 * - never while the browser reports that it is offline (`navigator.onLine === false`): the reload
 *   would replace the page with the browser's offline error page. The boundary shows its error with a
 *   "Reload page" button instead.
 */

/** `sessionStorage` key prefix; the build id is appended. */
export const STALE_CHUNK_RELOAD_KEY = 'vanta:stale-chunk-reload:';

/** Name of the event Vite dispatches on `window` when a dynamic import or its preload fails. */
export const PRELOAD_ERROR_EVENT = 'vite:preloadError';

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>;

/** What {@link reloadForStaleChunk} uses; set by {@link installStaleChunkReload}. */
interface ReloadEnvironment {
  readonly storage: () => StorageLike | undefined;
  readonly buildId: string;
  readonly reload: () => void;
  readonly isOffline: () => boolean;
}

/** Errors Vite reported through `vite:preloadError`. Weak, so failed imports are not kept alive. */
let chunkLoadErrors = new WeakSet();
let environment: ReloadEnvironment | undefined;
let reloadPending = false;

/** True once an automatic reload has been started; the error boundary then shows a loading state. */
export function isStaleChunkReloadPending(): boolean {
  return reloadPending;
}

/** Resets the module state between tests. */
export function resetStaleChunkReloadForTests(): void {
  reloadPending = false;
  chunkLoadErrors = new WeakSet();
}

/**
 * Identifies the running build: the URL of the module that contains this code, which carries the
 * entry chunk's content hash in a production build (`/assets/index-<hash>.js`).
 */
export function currentBuildId(): string {
  return import.meta.url;
}

/**
 * Records the one automatic reload allowed for `buildId` in this session and returns `true` when it
 * may happen now; `false` when it already happened or `sessionStorage` cannot be used.
 */
export function claimStaleChunkReload(storage: StorageLike | undefined, buildId: string): boolean {
  if (!storage) return false;
  const key = `${STALE_CHUNK_RELOAD_KEY}${buildId}`;
  try {
    if (storage.getItem(key) !== null) return false;
    storage.setItem(key, String(Date.now()));
    // Read back: a storage that silently drops writes would otherwise allow a reload loop.
    return storage.getItem(key) !== null;
  } catch {
    return false;
  }
}

/** True when Vite reported `error` as a failed chunk load (see {@link installStaleChunkReload}). */
export function isChunkLoadError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && chunkLoadErrors.has(error);
}

function sessionStorageOrUndefined(target: Window): StorageLike | undefined {
  try {
    return target.sessionStorage;
  } catch {
    return undefined;
  }
}

function browserIsOffline(target: Window): boolean {
  try {
    return !target.navigator.onLine;
  } catch {
    return false;
  }
}

export interface StaleChunkReloadOptions {
  readonly target?: Window;
  readonly storage?: StorageLike | undefined;
  readonly buildId?: string;
  readonly reload?: () => void;
  /** Whether the browser is offline right now; defaults to `navigator.onLine === false`. */
  readonly isOffline?: () => boolean;
}

/**
 * Starts recording the errors Vite reports with `vite:preloadError` and configures
 * {@link reloadForStaleChunk}. The event is never cancelled: the failed import still rejects, so a
 * lazy page reaches the error boundary and a prefetch its own `catch`. Returns a function that removes
 * the listener and the configuration.
 */
export function installStaleChunkReload(options: StaleChunkReloadOptions = {}): () => void {
  const target = options.target ?? window;
  const installed: ReloadEnvironment = {
    storage: () => ('storage' in options ? options.storage : sessionStorageOrUndefined(target)),
    buildId: options.buildId ?? currentBuildId(),
    reload:
      options.reload ??
      (() => {
        target.location.reload();
      }),
    isOffline: options.isOffline ?? (() => browserIsOffline(target)),
  };
  environment = installed;
  const handler = (event: Event) => {
    const payload = (event as Event & { payload?: unknown }).payload;
    if (typeof payload === 'object' && payload !== null) chunkLoadErrors.add(payload);
  };
  target.addEventListener(PRELOAD_ERROR_EVENT, handler);
  return () => {
    target.removeEventListener(PRELOAD_ERROR_EVENT, handler);
    if (environment === installed) environment = undefined;
  };
}

/**
 * Called by the page error boundary with the error that kept a page from rendering. Reloads the page
 * once — and returns `true` — when the error is a chunk load Vite reported, the browser is not
 * offline and this session has not reloaded for the running build yet. Returns `true` as well while
 * that reload is already under way, so the boundary keeps showing its loading state; `false` in every
 * other case, where the boundary shows the error.
 */
export function reloadForStaleChunk(error: unknown): boolean {
  if (reloadPending) return true;
  if (!environment || !isChunkLoadError(error)) return false;
  if (environment.isOffline()) return false;
  if (!claimStaleChunkReload(environment.storage(), environment.buildId)) return false;
  reloadPending = true;
  console.warn('VANTA website: a page chunk failed to load, reloading once for the new build');
  environment.reload();
  return true;
}

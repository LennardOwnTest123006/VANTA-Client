import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  PRELOAD_ERROR_EVENT,
  STALE_CHUNK_RELOAD_KEY,
  claimStaleChunkReload,
  currentBuildId,
  installStaleChunkReload,
  isChunkLoadError,
  isStaleChunkReloadPending,
  reloadForStaleChunk,
  resetStaleChunkReloadForTests,
} from './stale-chunks';

/** In-memory Storage double. */
function memoryStorage(): Pick<Storage, 'getItem' | 'setItem'> & {
  readonly data: Map<string, string>;
} {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => {
      data.set(key, value);
    },
  };
}

/** Dispatches `vite:preloadError` the way Vite's preload helper does and returns its payload. */
function preloadError(target: EventTarget, url = '/assets/FeaturesPage-old.js'): Error {
  const error = new TypeError(`Failed to fetch dynamically imported module: ${url}`);
  const event = new Event(PRELOAD_ERROR_EVENT, { cancelable: true });
  Object.assign(event, { payload: error });
  target.dispatchEvent(event);
  // Vite rethrows the payload unless a listener cancelled the event; this module never does.
  expect(event.defaultPrevented).toBe(false);
  return error;
}

describe('claimStaleChunkReload', () => {
  it('allows exactly one reload per build and session', () => {
    const storage = memoryStorage();
    expect(claimStaleChunkReload(storage, 'build-a')).toBe(true);
    expect(claimStaleChunkReload(storage, 'build-a')).toBe(false);
    expect(storage.data.has(`${STALE_CHUNK_RELOAD_KEY}build-a`)).toBe(true);
    // A newer build (after the reload) gets its own single attempt.
    expect(claimStaleChunkReload(storage, 'build-b')).toBe(true);
    expect(claimStaleChunkReload(storage, 'build-b')).toBe(false);
  });

  it('never allows a reload without working sessionStorage', () => {
    expect(claimStaleChunkReload(undefined, 'build')).toBe(false);
    const throwing = {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => undefined,
    };
    expect(claimStaleChunkReload(throwing, 'build')).toBe(false);
    const full = {
      getItem: () => null,
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    };
    expect(claimStaleChunkReload(full, 'build')).toBe(false);
    // Writes that are silently dropped would allow a loop: no reload either.
    const forgetful = { getItem: () => null, setItem: () => undefined };
    expect(claimStaleChunkReload(forgetful, 'build')).toBe(false);
  });
});

describe('installStaleChunkReload and reloadForStaleChunk', () => {
  afterEach(() => {
    resetStaleChunkReloadForTests();
    vi.restoreAllMocks();
  });

  it('only records preload errors: a failed prefetch alone never reloads the page', () => {
    const target = new EventTarget() as unknown as Window;
    const storage = memoryStorage();
    const reload = vi.fn();
    const uninstall = installStaleChunkReload({ target, storage, buildId: 'build-a', reload });

    // Hovering header links whose chunks are gone: every prefetch fails, nothing happens.
    const first = preloadError(target, '/assets/FeaturesPage-old.js');
    const second = preloadError(target, '/assets/DownloadPage-old.js');
    expect(reload).not.toHaveBeenCalled();
    expect(isStaleChunkReloadPending()).toBe(false);
    expect(storage.data.size).toBe(0);
    expect(isChunkLoadError(first)).toBe(true);
    expect(isChunkLoadError(second)).toBe(true);
    uninstall();
  });

  it('reloads once when a recorded chunk error keeps a page from rendering', () => {
    const target = new EventTarget() as unknown as Window;
    const storage = memoryStorage();
    const reload = vi.fn();
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const uninstall = installStaleChunkReload({ target, storage, buildId: 'build-a', reload });

    const error = preloadError(target);
    expect(reloadForStaleChunk(error)).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
    expect(isStaleChunkReloadPending()).toBe(true);
    expect(storage.data.has(`${STALE_CHUNK_RELOAD_KEY}build-a`)).toBe(true);

    // Further failures while the reload is under way keep the loading state but do not reload again.
    expect(reloadForStaleChunk(preloadError(target))).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);

    // After the reload (fresh module state, same session and build): no second automatic reload.
    resetStaleChunkReloadForTests();
    expect(reloadForStaleChunk(preloadError(target))).toBe(false);
    expect(reload).toHaveBeenCalledTimes(1);
    expect(isStaleChunkReloadPending()).toBe(false);
    uninstall();
  });

  it('leaves every other render error to the error boundary', () => {
    const target = new EventTarget() as unknown as Window;
    const reload = vi.fn();
    const uninstall = installStaleChunkReload({
      target,
      storage: memoryStorage(),
      buildId: 'b',
      reload,
    });
    preloadError(target);
    // A different error object, even with the same message, is not a chunk Vite reported.
    const lookalike = new TypeError('Failed to fetch dynamically imported module: /assets/x.js');
    expect(reloadForStaleChunk(lookalike)).toBe(false);
    expect(reloadForStaleChunk(new Error('render bug'))).toBe(false);
    expect(reloadForStaleChunk('not an error')).toBe(false);
    expect(reloadForStaleChunk(undefined)).toBe(false);
    expect(isChunkLoadError(null)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    uninstall();
  });

  it('does not reload while the browser is offline, and still can once it is back online', () => {
    const target = new EventTarget() as unknown as Window;
    const storage = memoryStorage();
    const reload = vi.fn();
    let offline = true;
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const uninstall = installStaleChunkReload({
      target,
      storage,
      buildId: 'b',
      reload,
      isOffline: () => offline,
    });
    expect(reloadForStaleChunk(preloadError(target))).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    // The offline attempt did not use up the one reload of this session.
    expect(storage.data.size).toBe(0);

    offline = false;
    expect(reloadForStaleChunk(preloadError(target))).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
    uninstall();
  });

  it('does nothing without sessionStorage, and nothing once uninstalled', () => {
    const target = new EventTarget() as unknown as Window;
    const reload = vi.fn();
    const uninstall = installStaleChunkReload({
      target,
      storage: undefined,
      buildId: 'b',
      reload,
    });
    expect(reloadForStaleChunk(preloadError(target))).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    uninstall();

    const storage = memoryStorage();
    const off = installStaleChunkReload({ target, storage, buildId: 'b', reload });
    const recorded = preloadError(target);
    off();
    // Not recorded after uninstalling, and no configuration left to reload with.
    expect(isChunkLoadError(preloadError(target))).toBe(false);
    expect(reloadForStaleChunk(recorded)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    expect(storage.data.size).toBe(0);
  });

  it('uses the real window, navigator and sessionStorage by default', () => {
    const reload = vi.fn();
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    window.sessionStorage.clear();
    const uninstall = installStaleChunkReload({ reload });

    const online = vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false);
    expect(reloadForStaleChunk(preloadError(window))).toBe(false);
    online.mockReturnValue(true);
    expect(reloadForStaleChunk(preloadError(window))).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
    expect(window.sessionStorage.getItem(`${STALE_CHUNK_RELOAD_KEY}${currentBuildId()}`)).not.toBe(
      null,
    );
    uninstall();
    window.sessionStorage.clear();
  });
});

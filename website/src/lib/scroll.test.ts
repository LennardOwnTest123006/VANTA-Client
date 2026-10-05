import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { scrollToAndHold } from './scroll';

/** ResizeObserver double: the test triggers size changes by hand. */
class FakeResizeObserver {
  static instances: FakeResizeObserver[] = [];
  readonly observed: Element[] = [];
  disconnected = false;
  private readonly callback: ResizeObserverCallback;
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    FakeResizeObserver.instances.push(this);
  }
  observe(element: Element) {
    this.observed.push(element);
  }
  unobserve() {
    /* not used */
  }
  disconnect() {
    this.disconnected = true;
  }
  resize() {
    if (!this.disconnected) this.callback([], this as unknown as ResizeObserver);
  }
}

/** A hash target in the page with a spy for `scrollIntoView` (jsdom does not implement it). */
function target() {
  const element = document.createElement('article');
  element.id = 'launcher-1.0.0';
  document.body.appendChild(element);
  const scrollIntoView = vi.fn();
  Object.defineProperty(element, 'scrollIntoView', { value: scrollIntoView });
  return { element, scrollIntoView };
}

describe('scrollToAndHold', () => {
  beforeEach(() => {
    FakeResizeObserver.instances = [];
    vi.stubGlobal('ResizeObserver', FakeResizeObserver);
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('re-aligns the target while content above it grows, until the settle time ends', () => {
    const { element, scrollIntoView } = target();
    scrollToAndHold(element, 1000);
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(scrollIntoView).toHaveBeenLastCalledWith({ block: 'start' });
    const observer = FakeResizeObserver.instances[0];
    expect(observer?.observed).toEqual([document.body]);

    observer?.resize();
    observer?.resize();
    expect(scrollIntoView).toHaveBeenCalledTimes(3);

    vi.advanceTimersByTime(1000);
    expect(observer?.disconnected).toBe(true);
    observer?.resize();
    expect(scrollIntoView).toHaveBeenCalledTimes(3);
  });

  it.each(['wheel', 'touchstart', 'keydown', 'pointerdown'])(
    'lets go as soon as the user interacts (%s)',
    (type) => {
      const { element, scrollIntoView } = target();
      scrollToAndHold(element);
      const observer = FakeResizeObserver.instances[0];
      window.dispatchEvent(new Event(type));
      expect(observer?.disconnected).toBe(true);
      observer?.resize();
      expect(scrollIntoView).toHaveBeenCalledTimes(1);
    },
  );

  it('can be released early and ignores a target that left the page', () => {
    const { element, scrollIntoView } = target();
    const release = scrollToAndHold(element);
    const observer = FakeResizeObserver.instances[0];
    element.remove();
    observer?.resize();
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    release();
    expect(observer?.disconnected).toBe(true);
  });

  it('only scrolls once where ResizeObserver does not exist', () => {
    vi.stubGlobal('ResizeObserver', undefined);
    const { element, scrollIntoView } = target();
    const release = scrollToAndHold(element);
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    release();
  });
});

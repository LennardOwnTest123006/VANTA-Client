import { Component, type ErrorInfo, type ReactNode, Suspense, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import { scrollToAndHold } from '../../lib/scroll';
import { reloadForStaleChunk } from '../../lib/stale-chunks';
import { Button } from '../ui/Button';
import { Container } from '../ui/Container';
import { SkipLink } from '../ui/SkipLink';
import { ThemeMeta } from '../ui/ThemeMeta';
import { Footer } from './Footer';
import { Header } from './Header';

/** How long a hash target may take to appear (lazy route chunks and markdown render asynchronously). */
const HASH_TARGET_TIMEOUT_MS = 4000;

/**
 * Scrolls to the top on navigation, or to the hash target when present. Content such as rendered
 * markdown arrives after the route commits, so a missing target is waited for with a
 * MutationObserver instead of silently scrolling to the top, and a found target is kept in place
 * while content above it is still rendering (see {@link scrollToAndHold}).
 */
function ScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    const id = hash.startsWith('#') ? decodeURIComponent(hash.slice(1)) : '';
    if (!id) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
      return undefined;
    }
    let release: (() => void) | undefined;
    const existing = document.getElementById(id);
    if (existing) {
      release = scrollToAndHold(existing);
      return release;
    }
    // Start at the top of the new page while the content loads, then jump once the anchor exists.
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    const observer = new MutationObserver(() => {
      const target = document.getElementById(id);
      if (!target) return;
      observer.disconnect();
      window.clearTimeout(timer);
      release = scrollToAndHold(target);
    });
    observer.observe(document.body, { childList: true, subtree: true });
    const timer = window.setTimeout(() => {
      observer.disconnect();
    }, HASH_TARGET_TIMEOUT_MS);
    return () => {
      observer.disconnect();
      window.clearTimeout(timer);
      release?.();
    };
  }, [pathname, hash]);
  return null;
}

/** Minimal skeleton shown while a lazy route chunk loads. */
export function PageFallback() {
  return (
    <Container className="py-24" aria-busy="true">
      <div role="status" className="flex flex-col gap-4">
        <span className="sr-only">Loading page…</span>
        <div className="h-4 w-32 animate-pulse rounded-sm bg-surface-2" />
        <div className="h-10 w-2/3 max-w-xl animate-pulse rounded-sm bg-surface-2" />
        <div className="h-4 w-full max-w-2xl animate-pulse rounded-sm bg-surface-1" />
        <div className="h-4 w-5/6 max-w-2xl animate-pulse rounded-sm bg-surface-1" />
      </div>
    </Container>
  );
}

interface ErrorBoundaryProps {
  readonly children: ReactNode;
  /**
   * Identifies the current page (the pathname). When it changes, a caught error is cleared, so one
   * page that failed — for example a lazy chunk that could not be loaded — does not keep every other
   * page broken until a reload. The children are not remounted on a change.
   */
  readonly resetKey?: string;
}

interface ErrorBoundaryState {
  readonly error: Error | undefined;
  /** The `resetKey` the current state belongs to. */
  readonly resetKey: string | undefined;
  /** The caught error was a stale chunk and the page is reloading (see lib/stale-chunks.ts). */
  readonly reloading: boolean;
}

/**
 * Catches render errors of a page and offers a reload instead of a blank screen. The error is
 * cleared as soon as `resetKey` changes (navigation to another page).
 *
 * A page or content chunk that could not be loaded (typically a tab left open across a redeploy) is
 * the one case that reloads automatically, once per session and build: {@link reloadForStaleChunk}
 * decides, and the loading skeleton is shown until the browser navigates. Only an error that reaches
 * this boundary does that; a failed prefetch while hovering a link never reloads the page.
 */
export class PageErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = {
    error: undefined,
    resetKey: this.props.resetKey,
    reloading: false,
  };

  static getDerivedStateFromProps(
    props: ErrorBoundaryProps,
    state: ErrorBoundaryState,
  ): Partial<ErrorBoundaryState> | null {
    if (props.resetKey === state.resetKey) return null;
    return { error: undefined, resetKey: props.resetKey, reloading: false };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('VANTA website: page failed to render', error, info.componentStack);
    // Runs in the commit phase, so this state update is applied before the browser paints: the
    // error message is never visible while the automatic reload is under way.
    if (reloadForStaleChunk(error)) this.setState({ reloading: true });
  }

  override render(): ReactNode {
    if (!this.state.error) return this.props.children;
    if (this.state.reloading) return <PageFallback />;
    return (
      <Container className="py-24">
        <div role="alert" className="surface-card max-w-xl p-8">
          <p className="eyebrow mb-3">Something went wrong</p>
          <h1 className="font-display text-2xl font-semibold text-text-primary">
            This page could not be displayed.
          </h1>
          <p className="mt-3 text-sm text-text-secondary">
            The error has been written to the browser console. Reloading usually fixes a stale
            deployment; if it keeps happening, please report it on GitHub.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button
              onClick={() => {
                window.location.reload();
              }}
            >
              Reload page
            </Button>
            <Button href="/" variant="secondary" external={false}>
              Go to the home page
            </Button>
          </div>
        </div>
      </Container>
    );
  }
}

/** Application shell: skip link, header, main landmark with the routed page, footer. */
export function Layout() {
  const { pathname } = useLocation();
  return (
    <div className="flex min-h-dvh flex-col">
      <ThemeMeta />
      <ScrollManager />
      <SkipLink />
      <Header />
      <main id="main" tabIndex={-1} className="flex-1 outline-none">
        <PageErrorBoundary resetKey={pathname}>
          <Suspense fallback={<PageFallback />}>
            <Outlet />
          </Suspense>
        </PageErrorBoundary>
      </main>
      <Footer />
    </div>
  );
}

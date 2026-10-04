import { Component, type ErrorInfo, type ReactNode, Suspense, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import { Button } from '../ui/Button';
import { Container } from '../ui/Container';
import { SkipLink } from '../ui/SkipLink';
import { ThemeMeta } from '../ui/ThemeMeta';
import { Footer } from './Footer';
import { Header } from './Header';

/** Scrolls to the top on navigation (or to the hash target when present). */
function ScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (hash) {
      const target = document.getElementById(hash.slice(1));
      if (target) {
        target.scrollIntoView({ block: 'start' });
        return;
      }
    }
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
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

interface ErrorBoundaryState {
  readonly error: Error | undefined;
}

/** Catches render errors of a page and offers a reload instead of a blank screen. */
export class PageErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: undefined };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('VANTA website: page failed to render', error, info.componentStack);
  }

  override render(): ReactNode {
    if (!this.state.error) return this.props.children;
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
  return (
    <div className="flex min-h-dvh flex-col">
      <ThemeMeta />
      <ScrollManager />
      <SkipLink />
      <Header />
      <main id="main" tabIndex={-1} className="flex-1 outline-none">
        <PageErrorBoundary>
          <Suspense fallback={<PageFallback />}>
            <Outlet />
          </Suspense>
        </PageErrorBoundary>
      </main>
      <Footer />
    </div>
  );
}

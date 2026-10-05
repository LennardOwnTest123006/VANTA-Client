import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { createMemoryRouter, RouterProvider, useParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installStaleChunkReload, resetStaleChunkReloadForTests } from '../../lib/stale-chunks';
import { Layout, PageErrorBoundary } from './Layout';

/** A page that fails like a lazy route whose chunk could not be loaded. */
function BrokenPage(): never {
  throw new Error('Failed to fetch dynamically imported module: /assets/BrokenPage-old.js');
}

/** Throws the given value while rendering, like a lazy route whose import rejected with it. */
function Throws({ error }: { readonly error: unknown }): never {
  throw error;
}

/** A healthy page with local state, to show the boundary does not remount pages. */
function CounterPage() {
  const { slug } = useParams();
  const [count, setCount] = useState(0);
  return (
    <div>
      <h1>Page {slug}</h1>
      <button
        type="button"
        onClick={() => {
          setCount((value) => value + 1);
        }}
      >
        Clicked {count}
      </button>
    </div>
  );
}

function renderApp(initialPath: string) {
  const router = createMemoryRouter(
    [
      {
        element: <Layout />,
        children: [
          { path: '/', element: <h1>Home page</h1> },
          { path: '/broken', element: <BrokenPage /> },
          { path: '/docs/:slug', element: <CounterPage /> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

describe('Layout error boundary', () => {
  beforeEach(() => {
    // React reports the caught error; the boundary logs it too. Both are expected here.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    resetStaleChunkReloadForTests();
  });

  it('shows the error for the failed page and recovers when navigating to another page', async () => {
    const router = renderApp('/broken');
    expect(screen.getByRole('alert')).toHaveTextContent('This page could not be displayed.');

    await act(async () => {
      await router.navigate('/');
    });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('heading', { level: 1, name: 'Home page' })).toBeInTheDocument();

    // Going back to the broken page shows the error again, and leaving it recovers again.
    await act(async () => {
      await router.navigate('/broken');
    });
    expect(screen.getByRole('alert')).toBeInTheDocument();
    await act(async () => {
      await router.navigate('/docs/hud');
    });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('heading', { level: 1, name: 'Page hud' })).toBeInTheDocument();
  });

  it('does not remount a healthy page when only the path changes', async () => {
    const router = renderApp('/docs/hud');
    await userEvent.click(screen.getByRole('button', { name: 'Clicked 0' }));
    await act(async () => {
      await router.navigate('/docs/settings');
    });
    expect(screen.getByRole('heading', { level: 1, name: 'Page settings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Clicked 1' })).toBeInTheDocument();
  });
});

describe('PageErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    resetStaleChunkReloadForTests();
  });

  it('clears the error only when the reset key changes', () => {
    const { rerender } = render(
      <PageErrorBoundary resetKey="/a">
        <BrokenPage />
      </PageErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    // Same key, healthy children: the error stays until the user navigates.
    rerender(
      <PageErrorBoundary resetKey="/a">
        <p>Recovered</p>
      </PageErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    rerender(
      <PageErrorBoundary resetKey="/b">
        <p>Recovered</p>
      </PageErrorBoundary>,
    );
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByText('Recovered')).toBeInTheDocument();
  });

  /** Installs the stale-chunk handling with test doubles; returns the reload spy. */
  function installWithDoubles(options: { offline?: boolean } = {}) {
    const reload = vi.fn();
    const storage = new Map<string, string>();
    const uninstall = installStaleChunkReload({
      buildId: 'test-build',
      reload,
      isOffline: () => options.offline ?? false,
      storage: {
        getItem: (key) => storage.get(key) ?? null,
        setItem: (key, value) => {
          storage.set(key, value);
        },
      },
    });
    return { reload, uninstall };
  }

  /** Reports a failed chunk the way Vite does and returns the error the import rejects with. */
  function reportChunkFailure(): Error {
    const error = new TypeError(
      'Failed to fetch dynamically imported module: /assets/FeaturesPage-old.js',
    );
    window.dispatchEvent(
      Object.assign(new Event('vite:preloadError', { cancelable: true }), { payload: error }),
    );
    return error;
  }

  it('reloads once and shows the loading state when a failed chunk keeps the page from rendering', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const { reload, uninstall } = installWithDoubles();
    const error = reportChunkFailure();
    // Reporting the failure alone (a prefetch) does not reload.
    expect(reload).not.toHaveBeenCalled();

    render(
      <PageErrorBoundary resetKey="/features">
        <Throws error={error} />
      </PageErrorBoundary>,
    );
    expect(reload).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent('Loading page…');
    uninstall();
  });

  it('shows the error without reloading for any other render error, even after a failed prefetch', () => {
    const { reload, uninstall } = installWithDoubles();
    reportChunkFailure();
    render(
      <PageErrorBoundary resetKey="/broken">
        <BrokenPage />
      </PageErrorBoundary>,
    );
    expect(reload).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('This page could not be displayed.');
    uninstall();
  });

  it('shows the error with its reload button instead of reloading while the browser is offline', () => {
    const { reload, uninstall } = installWithDoubles({ offline: true });
    const error = reportChunkFailure();
    render(
      <PageErrorBoundary resetKey="/features">
        <Throws error={error} />
      </PageErrorBoundary>,
    );
    expect(reload).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload page' })).toBeInTheDocument();
    uninstall();
  });
});

import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type * as EnvModule from '../lib/env';
import { renderWithRouter } from '../test/render';

const launcherUrl =
  'https://github.com/example/vanta/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi';

async function loadPage() {
  const module = await import('./DownloadPage');
  return module.default;
}

describe('DownloadPage', () => {
  afterEach(() => {
    vi.doUnmock('../lib/env');
    vi.resetModules();
  });

  it('renders both products in the "not published" state from the repository manifests', async () => {
    vi.doMock('../lib/env', async (importOriginal) => {
      const original = await importOriginal<typeof EnvModule>();
      return {
        ...original,
        env: {
          ...original.readEnv({}),
          downloadLauncherUrl: undefined,
          downloadClientUrl: undefined,
        },
      };
    });
    const DownloadPage = await loadPage();
    renderWithRouter(<DownloadPage />, '/download');

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'VANTA Client for Minecraft 1.21.11',
    );
    const launcher = screen.getByRole('article', { name: 'VANTA Launcher' });
    const client = screen.getByRole('article', { name: 'VANTA Client (jar)' });
    expect(within(launcher).getByRole('button', { name: 'Download launcher' })).toBeDisabled();
    expect(within(client).getByRole('button', { name: 'Download client jar' })).toBeDisabled();
    expect(screen.getAllByText('Not published yet — release pending')).toHaveLength(2);
    // Facts stay visible.
    expect(within(launcher).getByText('VANTA-Launcher-1.0.0.msi')).toBeInTheDocument();
    expect(within(client).getByText('vanta-client-1.0.0.jar')).toBeInTheDocument();
    // Verification instructions reference the real versions.
    expect(
      screen.getByText(/certutil -hashfile "VANTA-Launcher-1\.0\.0\.msi" SHA256/),
    ).toBeInTheDocument();
    expect(screen.getByText(/shasum -a 256 vanta-client-1\.0\.0\.jar/)).toBeInTheDocument();
    // Next steps link into the documentation and the changelog.
    expect(screen.getByRole('link', { name: 'Installation guide' })).toHaveAttribute(
      'href',
      '/documentation/installation',
    );
    expect(screen.getAllByRole('link', { name: 'Full release notes' })[0]).toHaveAttribute(
      'href',
      '/changelog#launcher-1.0.0',
    );
    expect(screen.getByRole('link', { name: 'support page' })).toHaveAttribute('href', '/support');
    expect(document.title).toBe('Download — VANTA Client');
  });

  it('turns a card into a live download when the environment provides a URL', async () => {
    vi.doMock('../lib/env', async (importOriginal) => {
      const original = await importOriginal<typeof EnvModule>();
      return {
        ...original,
        env: { ...original.readEnv({}), downloadLauncherUrl: launcherUrl },
      };
    });
    const DownloadPage = await loadPage();
    renderWithRouter(<DownloadPage />, '/download');

    const launcher = screen.getByRole('article', { name: 'VANTA Launcher' });
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      launcherUrl,
    );
    expect(within(launcher).queryByText(/Not published yet/)).toBeNull();
    const client = screen.getByRole('article', { name: 'VANTA Client (jar)' });
    expect(within(client).getByRole('button', { name: 'Download client jar' })).toBeDisabled();
  });
});

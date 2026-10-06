import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type * as EnvModule from '../lib/env';
import type * as ReleasesModule from '../lib/releases';
import type { ReleaseManifest } from '../lib/releases';
import {
  CLIENT_FILE_NAMES,
  LAUNCHER_FILE_NAMES,
  clientFixture,
  launcherFixture,
} from '../test/fixtures/releases';
import { renderWithRouter } from '../test/render';

const launcherUrl =
  'https://github.com/example/vanta/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi';
const RELEASE_ASSET =
  /^https:\/\/github\.com\/LennardOwnTest123006\/VANTA-Client\/releases\/download\/(client|launcher)-v1\.0\.0\/[^/]+$/;
/** Any version: used for the repository manifests, which the release workflow fills. */
const ANY_RELEASE_ASSET =
  /^https:\/\/github\.com\/LennardOwnTest123006\/VANTA-Client\/releases\/download\/(client|launcher)-v[^/]+\/[^/]+$/;

/**
 * Renders the page against the given manifests instead of `shared/releases/`, so the tests do not
 * depend on whether the repository manifests have been filled by the release workflow yet.
 */
async function renderPage(
  manifests: readonly ReleaseManifest[],
  envOverrides: Partial<EnvModule.SiteEnv> = {},
) {
  vi.doMock('../lib/releases', async (importOriginal) => {
    const original = await importOriginal<typeof ReleasesModule>();
    return { ...original, releases: manifests };
  });
  vi.doMock('../lib/env', async (importOriginal) => {
    const original = await importOriginal<typeof EnvModule>();
    return {
      ...original,
      env: {
        ...original.readEnv({}),
        downloadLauncherUrl: undefined,
        downloadClientUrl: undefined,
        ...envOverrides,
      },
    };
  });
  const module = await import('./DownloadPage');
  const DownloadPage = module.default;
  renderWithRouter(<DownloadPage />, '/download');
  return {
    launcher: screen.getByRole('article', { name: 'VANTA Launcher' }),
    client: screen.getByRole('article', { name: 'VANTA Client (jar)' }),
  };
}

const unpublished = [clientFixture({ published: false }), launcherFixture({ published: false })];
const published = [clientFixture({ published: true }), launcherFixture({ published: true })];

describe('DownloadPage', () => {
  afterEach(() => {
    vi.doUnmock('../lib/releases');
    vi.doUnmock('../lib/env');
    vi.resetModules();
  });

  it('renders both products in the honest "not published" state', async () => {
    const { launcher, client } = await renderPage(unpublished);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'VANTA Client for Minecraft 1.21.11',
    );
    expect(within(launcher).getByRole('button', { name: 'Download launcher' })).toBeDisabled();
    expect(within(client).getByRole('button', { name: 'Download client jar' })).toBeDisabled();
    expect(screen.getAllByText('Not published yet — release pending')).toHaveLength(2);
    // No file list, no mods bundle link and no release page while nothing is published.
    expect(screen.queryByRole('list', { name: 'All files in this release' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Download mods bundle' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Release page on GitHub' })).toBeNull();
    expect(
      screen
        .queryAllByRole('link')
        .filter((link) => link.getAttribute('href')?.includes('/releases/download/')),
    ).toHaveLength(0);
    // Facts stay visible.
    expect(within(launcher).getByText('VANTA-Launcher-1.0.0.msi')).toBeInTheDocument();
    expect(within(client).getByText('vanta-client-1.0.0.jar')).toBeInTheDocument();
    // Honest footnotes: per-platform launcher files, Fabric API required.
    expect(launcher).toHaveTextContent(/Each jar runs only on the system it was built for/);
    expect(launcher).not.toHaveTextContent(/portable \.jar/);
    expect(client).toHaveTextContent('Requires Fabric API 0.141.6+1.21.11');
    expect(client).toHaveTextContent(/included in the mods bundle/);
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

  it('explains the three ways to install the client in both states', async () => {
    const { client } = await renderPage(unpublished);
    const howTo = within(client).getByRole('region', { name: 'How to install' });
    const ways = within(howTo).getAllByRole('listitem');
    expect(ways).toHaveLength(3);
    // The way that works with the published builds comes first: the launcher's main button sets up
    // the official Minecraft Launcher profile (launcher 1.1.0), called "Use with Minecraft Launcher" in 1.0.x.
    expect(ways[0]).toHaveTextContent('PLAY via Minecraft Launcher');
    expect(ways[0]).toHaveTextContent(/Microsoft application id that the project does not ship/);
    expect(ways[0]).toHaveTextContent('VANTA 1.21.11');
    expect(ways[0]).toHaveTextContent(/Close the Minecraft Launcher first/);
    expect(ways[0]).toHaveTextContent(/performance pack .* is installed with it by default/);
    expect(ways[0]).toHaveTextContent('Use with Minecraft Launcher');
    // The Microsoft Store / Xbox app launcher case is written but untested: never claimed to work.
    expect(ways[0]).toHaveTextContent(/Microsoft Store or the Xbox app it writes the profile/);
    expect(ways[0]).toHaveTextContent(/has not been tested on Windows yet/);
    expect(ways[1]).toHaveTextContent('VANTA Launcher → PLAY');
    expect(ways[1]).toHaveTextContent(/needs Microsoft sign-in inside\s+VANTA/);
    // Step 0 of INSTALL.txt and the README: without a first start of the official launcher the Fabric installer stops.
    expect(ways[2]).toHaveTextContent(
      /Manual\s*Start the official Minecraft Launcher once, then install Fabric/,
    );
    expect(ways[2]).toHaveTextContent(/Fabric installer/);
    expect(ways[2]).toHaveTextContent('keep “Create profile” checked');
    expect(ways[2]).toHaveTextContent('.minecraft/mods');
    expect(ways[2]).toHaveTextContent(/copy all jars from the mods bundle/);
    expect(ways[2]).toHaveTextContent(
      /From client 1\.2\.0 on the bundle holds the VANTA jar, Fabric API and the Performance pack/,
    );
    expect(ways[2]).toHaveTextContent(/EntityCulling is not in the zip/);
    expect(
      within(howTo).getByRole('link', { name: 'Step-by-step installation guide' }),
    ).toHaveAttribute('href', '/documentation/installation');
  });

  it('lists every published file with a GitHub release download link', async () => {
    const { launcher, client } = await renderPage(published);

    expect(screen.queryByText(/Not published yet/)).toBeNull();
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi',
    );
    expect(within(client).getByRole('link', { name: 'Download client jar' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
    );
    expect(within(client).getByRole('link', { name: 'Download mods bundle' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0-mods.zip',
    );

    const launcherFiles = within(launcher).getByRole('list', { name: 'All files in this release' });
    const clientFiles = within(client).getByRole('list', { name: 'All files in this release' });
    expect(within(launcherFiles).getAllByRole('listitem')).toHaveLength(LAUNCHER_FILE_NAMES.length);
    expect(within(clientFiles).getAllByRole('listitem')).toHaveLength(CLIENT_FILE_NAMES.length);
    for (const name of LAUNCHER_FILE_NAMES) {
      expect(within(launcherFiles).getByRole('link', { name: `Download ${name}` })).toHaveAttribute(
        'href',
        expect.stringMatching(RELEASE_ASSET),
      );
    }
    for (const name of CLIENT_FILE_NAMES) {
      expect(within(clientFiles).getByRole('link', { name: `Download ${name}` })).toHaveAttribute(
        'href',
        expect.stringMatching(RELEASE_ASSET),
      );
    }
    expect(within(launcher).getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0',
    );
    expect(within(client).getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0',
    );
    expect(within(clientFiles).getByText('Fabric API (required dependency)')).toBeInTheDocument();
    expect(
      within(launcherFiles).getByText('macOS Apple Silicon jar (needs Java 21)'),
    ).toBeInTheDocument();
    // The how-to stays next to the published files.
    expect(within(client).getByRole('region', { name: 'How to install' })).toBeInTheDocument();
  });

  it('keeps offering the published 1.0.0 while 1.0.1 is committed but not published', async () => {
    const { launcher, client } = await renderPage([
      clientFixture({ published: false, version: '1.0.1' }),
      clientFixture({ published: true }),
      launcherFixture({ published: false, version: '1.0.1' }),
      launcherFixture({ published: true }),
    ]);

    expect(screen.queryByText('Not published yet — release pending')).toBeNull();
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi',
    );
    expect(within(client).getByRole('link', { name: 'Download client jar' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
    );
    for (const card of [launcher, client]) {
      const files = within(card).getByRole('list', { name: 'All files in this release' });
      const hrefs = within(files)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href') ?? '');
      expect(hrefs.length).toBeGreaterThan(0);
      // Only files of the published 1.0.0 release are linked, never a 1.0.1 placeholder.
      for (const href of hrefs) expect(href).toMatch(RELEASE_ASSET);
      expect(within(card).getByText('Published')).toBeInTheDocument();
    }
    expect(
      within(launcher).getByRole('note', { name: 'Upcoming version 1.0.1' }),
    ).toHaveTextContent('VANTA Launcher 1.0.1 is not published yet');
    expect(within(client).getByRole('note', { name: 'Upcoming version 1.0.1' })).toHaveTextContent(
      'until then 1.0.0 is the current release',
    );
    expect(
      screen.getByText(/certutil -hashfile "VANTA-Launcher-1\.0\.0\.msi" SHA256/),
    ).toBeInTheDocument();
    expect(screen.getByText(/shasum -a 256 vanta-client-1\.0\.0\.jar/)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Full release notes' })[0]).toHaveAttribute(
      'href',
      '/changelog#launcher-1.0.0',
    );
  });

  it('offers 1.0.1 and no upcoming note once both versions are published', async () => {
    const { launcher, client } = await renderPage([
      clientFixture({ published: true, version: '1.0.1' }),
      clientFixture({ published: true }),
      launcherFixture({ published: true, version: '1.0.1' }),
      launcherFixture({ published: true }),
    ]);

    expect(screen.queryByText(/Not published yet/)).toBeNull();
    expect(screen.queryByRole('note', { name: /^Upcoming version/ })).toBeNull();
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/launcher-v1.0.1/VANTA-Launcher-1.0.1.msi',
    );
    expect(within(client).getByRole('link', { name: 'Download mods bundle' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.1/vanta-client-1.0.1-mods.zip',
    );
    expect(within(client).getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.1',
    );
    expect(
      screen.getByText(/certutil -hashfile "VANTA-Launcher-1\.0\.1\.msi" SHA256/),
    ).toBeInTheDocument();
    expect(screen.getByText(/shasum -a 256 vanta-client-1\.0\.1\.jar/)).toBeInTheDocument();
  });

  it('turns a card into a live download when the environment provides a URL', async () => {
    const { launcher, client } = await renderPage(unpublished, {
      downloadLauncherUrl: launcherUrl,
    });

    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      launcherUrl,
    );
    expect(within(launcher).queryByText(/Not published yet/)).toBeNull();
    // The manifest is still unpublished: no per-file links.
    expect(within(launcher).queryByRole('list', { name: 'All files in this release' })).toBeNull();
    expect(within(client).getByRole('button', { name: 'Download client jar' })).toBeDisabled();
  });

  it('keeps working with the repository manifests, whatever their state', async () => {
    const actual = await vi.importActual<typeof ReleasesModule>('../lib/releases');
    const { launcher, client } = await renderPage(actual.releases);
    for (const [card, product] of [
      [launcher, 'launcher'],
      [client, 'client'],
    ] as const) {
      const anyPublished = actual.releasesOf(actual.releases, product).some(actual.isPublished);
      const pending = within(card).queryByText('Not published yet — release pending');
      // A published release of the product is always offered, even when a newer one is pending.
      expect(pending === null).toBe(anyPublished);
      if (pending) {
        expect(within(card).getByRole('button', { name: /^Download/ })).toBeDisabled();
      } else {
        const links = within(card)
          .getAllByRole('link')
          .map((link) => link.getAttribute('href') ?? '')
          .filter((href) => href.includes('/releases/download/'));
        expect(links.length).toBeGreaterThan(0);
        for (const href of links) expect(href).toMatch(ANY_RELEASE_ASSET);
      }
    }
  });
});

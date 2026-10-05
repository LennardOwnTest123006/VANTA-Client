import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Monitor, Package } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { modsBundleFile, resolveDownload } from '../../lib/downloads';
import { parseReleaseManifest } from '../../lib/releases';
import {
  CLIENT_FILE_NAMES,
  LAUNCHER_FILE_NAMES,
  clientFixture,
  fakeSha,
  launcherFixture,
} from '../../test/fixtures/releases';
import { renderWithRouter } from '../../test/render';
import { DownloadCard } from './DownloadCard';

const sha = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08';

const pending = parseReleaseManifest({
  schemaVersion: 1,
  product: 'client',
  version: '1.0.0',
  minecraftVersion: '1.21.11',
  fabricVersion: '0.19.5',
  fabricApiVersion: '0.141.6+1.21.11',
  javaVersion: 21,
  releaseDate: '2026-10-04',
  channel: 'stable',
  files: [{ name: 'vanta-client-1.0.0.jar', downloadUrl: '', size: 0, sha256: '' }],
});

const published = parseReleaseManifest({
  schemaVersion: 1,
  product: 'client',
  version: '1.0.0',
  minecraftVersion: '1.21.11',
  fabricVersion: '0.19.5',
  fabricApiVersion: '0.141.6+1.21.11',
  javaVersion: 21,
  releaseDate: '2026-10-04',
  channel: 'stable',
  files: [
    {
      name: 'vanta-client-1.0.0.jar',
      downloadUrl:
        'https://github.com/example/vanta/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
      size: 2_480_000,
      sha256: sha,
    },
  ],
});

const props = {
  icon: Package,
  eyebrow: 'Client',
  title: 'VANTA Client (jar)',
  description: 'The Fabric mod on its own.',
  cta: 'Download client jar',
} as const;

const launcherProps = {
  icon: Monitor,
  eyebrow: 'Launcher',
  title: 'VANTA Launcher',
  description: 'Installs everything.',
  cta: 'Download launcher',
  primary: true,
} as const;

function mockClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
  return writeText;
}

describe('DownloadCard', () => {
  it('shows a disabled button and the pending notice when nothing is published', () => {
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={pending}
        resolution={resolveDownload(pending, undefined)}
      />,
    );
    const button = screen.getByRole('button', { name: 'Download client jar' });
    expect(button).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent('Not published yet — release pending');
    expect(screen.getByText('Release pending')).toBeInTheDocument();
    expect(screen.getAllByText('Published with the release')).toHaveLength(2);
    expect(screen.getByText('1.0.0')).toBeInTheDocument();
    expect(screen.getByText('October 4, 2026')).toBeInTheDocument();
    expect(screen.getByText('vanta-client-1.0.0.jar')).toBeInTheDocument();
    // Changelog excerpt comes from website/content/changelog/client-1.0.0.md
    expect(screen.getByText('In this release')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Download client jar/ })).toBeNull();
    expect(screen.queryByText('All files in this release')).toBeNull();
  });

  it('renders a real download link with size and checksum when published', async () => {
    const writeText = mockClipboard();

    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={published}
        resolution={resolveDownload(published, undefined)}
        primary
      />,
    );
    const link = screen.getByRole('link', { name: 'Download client jar' });
    expect(link).toHaveAttribute('href', published.files[0]?.downloadUrl);
    // A direct download in the same tab, not a new window.
    expect(link).not.toHaveAttribute('target');
    expect(link).toHaveAttribute('rel', 'noopener');
    expect(link).toHaveAttribute('download');
    expect(screen.getByText('Published')).toBeInTheDocument();
    expect(screen.getAllByText('2.5 MB').length).toBeGreaterThan(0);
    expect(screen.getByText(/9f86d081 884c7d65/)).toBeInTheDocument();
    expect(screen.queryByText(/Not published yet/)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: /Copy SHA-256/ }));
    expect(writeText).toHaveBeenCalledWith(sha);
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('prefers an environment URL while keeping the manifest facts', () => {
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={pending}
        resolution={resolveDownload(pending, 'https://mirror.example/vanta-client-1.0.0.jar')}
      />,
    );
    expect(screen.getByRole('link', { name: 'Download client jar' })).toHaveAttribute(
      'href',
      'https://mirror.example/vanta-client-1.0.0.jar',
    );
    expect(screen.getByText('Published')).toBeInTheDocument();
    // The manifest itself is unpublished, so there is no file list with links.
    expect(screen.queryByText('All files in this release')).toBeNull();
  });

  it('explains when no manifest exists at all', () => {
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={undefined}
        resolution={resolveDownload(undefined, undefined)}
      />,
    );
    expect(screen.getByText('No release manifest found for this product.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download client jar' })).toBeDisabled();
  });
});

describe('DownloadCard file list (published release)', () => {
  it('lists every launcher file with label, size, checksum and its own link', async () => {
    const writeText = mockClipboard();
    const manifest = launcherFixture({ published: true });
    renderWithRouter(
      <DownloadCard
        {...launcherProps}
        manifest={manifest}
        resolution={resolveDownload(manifest, undefined)}
      />,
    );

    // The primary button stays and offers the .msi.
    expect(screen.getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      manifest.files[0]?.downloadUrl,
    );

    const list = screen.getByRole('list', { name: 'All files in this release' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(LAUNCHER_FILE_NAMES.length);
    items.forEach((item, index) => {
      const file = manifest.files[index]!;
      expect(item).toHaveTextContent(file.name);
      const link = within(item).getByRole('link', { name: `Download ${file.name}` });
      expect(link).toHaveAttribute('href', file.downloadUrl);
      expect(link).not.toHaveAttribute('target');
      expect(link).toHaveAttribute('rel', 'noopener');
      expect(
        within(item).getByRole('button', { name: `Copy SHA-256 checksum of ${file.name}` }),
      ).toBeInTheDocument();
    });

    // Labels derived from the file names, in manifest order.
    expect(items.map((item) => item.querySelector('span')?.textContent)).toEqual([
      'Windows installer',
      'Windows installer',
      'Windows portable app with Java',
      'Windows jar (needs Java 21)',
      'Linux app with Java',
      'Linux jar (needs Java 21)',
      'macOS Apple Silicon jar (needs Java 21)',
    ]);
    expect(within(items[0]!).getByText('Recommended')).toBeInTheDocument();
    expect(within(items[1]!).queryByText('Recommended')).toBeNull();
    expect(within(items[0]!).getByText('1.2 MB')).toBeInTheDocument();

    expect(screen.getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0',
    );
    expect(screen.getByText(/SHA256SUMS\.txt/)).toBeInTheDocument();
    expect(screen.getByText('Published')).toBeInTheDocument();
    expect(screen.queryByText(/Not published yet/)).toBeNull();

    const linuxItem = items[4]!;
    await userEvent.click(within(linuxItem).getByRole('button', { name: /Copy SHA-256/ }));
    expect(writeText).toHaveBeenCalledWith(fakeSha(4));
  });

  it('offers the mods bundle and labels Fabric API as a required dependency', () => {
    const manifest = clientFixture({ published: true });
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={manifest}
        resolution={resolveDownload(manifest, undefined)}
        secondaryDownload={{ file: modsBundleFile(manifest), label: 'Download mods bundle' }}
      />,
    );
    expect(screen.getByRole('link', { name: 'Download client jar' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
    );
    expect(screen.getByRole('link', { name: 'Download mods bundle' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0-mods.zip',
    );
    const list = screen.getByRole('list', { name: 'All files in this release' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(CLIENT_FILE_NAMES.length);
    expect(items[0]).toHaveTextContent('VANTA Client mod');
    expect(items[0]).toHaveTextContent('Recommended');
    expect(items[1]).toHaveTextContent('Mods folder bundle (VANTA + Fabric API)');
    expect(items[2]).toHaveTextContent('Fabric API (required dependency)');
    expect(screen.getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0',
    );
  });

  it('never links a file without a download URL', () => {
    const manifest = launcherFixture({
      published: true,
      unpublished: ['vanta-launcher-1.0.0-macos-aarch64-all.jar'],
    });
    renderWithRouter(
      <DownloadCard
        {...launcherProps}
        manifest={manifest}
        resolution={resolveDownload(manifest, undefined)}
      />,
    );
    const items = within(
      screen.getByRole('list', { name: 'All files in this release' }),
    ).getAllByRole('listitem');
    const mac = items[6]!;
    expect(mac).toHaveTextContent('vanta-launcher-1.0.0-macos-aarch64-all.jar');
    expect(within(mac).queryByRole('link')).toBeNull();
    expect(within(mac).getByText('Not published yet')).toBeInTheDocument();
    expect(within(mac).getByText('Published with the release')).toBeInTheDocument();
    expect(within(mac).queryByRole('button')).toBeNull();
    for (const item of items.slice(0, 6)) {
      expect(within(item).getByRole('link')).toHaveAttribute(
        'href',
        expect.stringMatching(/^https:\/\/github\.com\//),
      );
    }
  });

  it('shows no release page link when the URLs are not GitHub release assets', () => {
    const manifest = clientFixture({
      published: true,
      url: (tag, name) => `https://mirror.example/${tag}/${name}`,
    });
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={manifest}
        resolution={resolveDownload(manifest, undefined)}
      />,
    );
    expect(screen.getByRole('list', { name: 'All files in this release' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Release page on GitHub' })).toBeNull();
    expect(screen.queryByText(/SHA256SUMS\.txt/)).toBeNull();
  });

  it('hides the mods bundle button while the bundle is unpublished', () => {
    const manifest = clientFixture({ published: false });
    renderWithRouter(
      <DownloadCard
        {...props}
        manifest={manifest}
        resolution={resolveDownload(manifest, undefined)}
        secondaryDownload={{ file: modsBundleFile(manifest), label: 'Download mods bundle' }}
      >
        <p>Extra content</p>
      </DownloadCard>,
    );
    expect(screen.queryByRole('link', { name: 'Download mods bundle' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Download mods bundle' })).toBeNull();
    expect(screen.getByText('Extra content')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'All files in this release' })).toBeNull();
  });
});

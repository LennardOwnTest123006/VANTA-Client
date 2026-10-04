import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Package } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { resolveDownload } from '../../lib/downloads';
import { parseReleaseManifest } from '../../lib/releases';
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
  });

  it('renders a real download link with size and checksum when published', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });

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
    expect(screen.getByText('Published')).toBeInTheDocument();
    expect(screen.getByText('2.5 MB')).toBeInTheDocument();
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

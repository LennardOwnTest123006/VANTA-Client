import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type * as EnvModule from '../lib/env';
import { renderWithRouter } from '../test/render';

async function loadPage(overrides: Partial<EnvModule.SiteEnv>) {
  vi.doMock('../lib/env', async (importOriginal) => {
    const original = await importOriginal<typeof EnvModule>();
    return { ...original, env: { ...original.readEnv({}), ...overrides } };
  });
  const module = await import('./SupportPage');
  return module.default;
}

describe('SupportPage', () => {
  afterEach(() => {
    vi.doUnmock('../lib/env');
    vi.resetModules();
  });

  it('lists every category and announces missing channels while GitHub Issues stays available', async () => {
    const SupportPage = await loadPage({});
    renderWithRouter(<SupportPage />, '/support');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Help, organised by problem.',
    );
    const topics = screen.getByRole('list', { name: 'Support topics' });
    for (const title of [
      'Installation',
      'Launcher',
      'Minecraft',
      'Fabric',
      'Performance',
      'Account',
      'Website',
    ]) {
      expect(within(topics).getByRole('heading', { level: 2, name: title })).toBeInTheDocument();
    }
    expect(screen.getByRole('link', { name: /Where do I download VANTA\?/ })).toHaveAttribute(
      'href',
      '/documentation/faq#where-do-i-download-vanta',
    );
    expect(screen.queryByText(/Why is there no download yet/)).toBeNull();
    expect(screen.getByRole('link', { name: /Open an issue/ })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/issues',
    );
    expect(screen.getByText('Support channels will be announced')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Write an e-mail/ })).toBeNull();
    expect(screen.queryByRole('link', { name: /Join the Discord/ })).toBeNull();
    expect(document.title).toBe('Support — VANTA Client');
  });

  it('shows e-mail and Discord only when configured', async () => {
    const SupportPage = await loadPage({
      supportEmail: 'help@example.org',
      discordUrl: 'https://discord.example/invite',
    });
    renderWithRouter(<SupportPage />, '/support');
    expect(screen.getByRole('link', { name: /Write an e-mail/ })).toHaveAttribute(
      'href',
      'mailto:help@example.org',
    );
    expect(screen.getByRole('link', { name: /Join the Discord/ })).toHaveAttribute(
      'href',
      'https://discord.example/invite',
    );
    expect(screen.queryByText(/will be announced/)).toBeNull();
  });

  it('falls back to the empty state when no channel at all is configured', async () => {
    const SupportPage = await loadPage({ githubUrl: undefined });
    renderWithRouter(<SupportPage />, '/support');
    expect(
      screen.getByRole('heading', { name: 'Support channels will be announced.' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Open an issue/ })).toBeNull();
  });
});

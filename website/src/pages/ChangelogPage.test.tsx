import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type * as ContentModule from '../lib/content';
import type * as ReleasesModule from '../lib/releases';
import type { ReleaseManifest } from '../lib/releases';
import { clientFixture, launcherFixture } from '../test/fixtures/releases';
import { renderWithRouter } from '../test/render';

/** Minimal release notes in the format of `content/changelog/<product>-<version>.md`. */
function notes(product: 'client' | 'launcher', version: string, date: string): string {
  return [
    '---',
    `product: ${product}`,
    `version: ${version}`,
    `date: ${date}`,
    'minecraftVersion: 1.21.11',
    '---',
    '',
    '## Fixed',
    '',
    `- Something in ${product} ${version}`,
    '',
  ].join('\n');
}

/** Renders the page against fixture manifests and fixture release notes (not the repository's). */
async function renderPage(manifests: readonly ReleaseManifest[]) {
  vi.doMock('../lib/releases', async (importOriginal) => {
    const original = await importOriginal<typeof ReleasesModule>();
    return { ...original, releases: manifests };
  });
  vi.doMock('../lib/content', async (importOriginal) => {
    const original = await importOriginal<typeof ContentModule>();
    return {
      ...original,
      changelog: original.loadChangelog({
        'content/changelog/client-1.0.1.md': notes('client', '1.0.1', '2026-10-05'),
        'content/changelog/client-1.0.0.md': notes('client', '1.0.0', '2026-10-04'),
        'content/changelog/launcher-1.0.1.md': notes('launcher', '1.0.1', '2026-10-05'),
        'content/changelog/launcher-1.0.0.md': notes('launcher', '1.0.0', '2026-10-04'),
      }),
    };
  });
  const module = await import('./ChangelogPage');
  const ChangelogPage = module.default;
  renderWithRouter(<ChangelogPage />, '/changelog');
}

const article = (id: string) => {
  const element = document.getElementById(id);
  if (!element) throw new Error(`#${id} is missing`);
  return within(element);
};

describe('ChangelogPage', () => {
  afterEach(() => {
    vi.doUnmock('../lib/releases');
    vi.doUnmock('../lib/content');
    vi.resetModules();
  });

  it('marks 1.0.1 as pending and keeps 1.0.0 as the latest release until 1.0.1 is published', async () => {
    await renderPage([
      clientFixture({ published: false, version: '1.0.1' }),
      clientFixture({ published: true }),
      launcherFixture({ published: false, version: '1.0.1' }),
      launcherFixture({ published: true }),
    ]);
    const latest = screen.getByLabelText('Latest versions');
    expect(within(latest).getByText('Latest client').nextElementSibling).toHaveTextContent(
      'v1.0.0',
    );
    expect(within(latest).getByText('Latest launcher').nextElementSibling).toHaveTextContent(
      'v1.0.0',
    );
    const list = screen.getByRole('list', { name: 'Releases' });
    expect(within(list).getAllByRole('article')).toHaveLength(4);
    for (const id of ['client-1.0.1', 'launcher-1.0.1']) {
      expect(article(id).getByText('Release pending')).toBeInTheDocument();
      expect(article(id).getByRole('link', { name: 'Download page' })).toHaveAttribute(
        'href',
        '/download',
      );
      // 1.0.0 is published, so the note names it as the version on offer meanwhile.
      expect(document.getElementById(id)).toHaveTextContent(
        'Not published yet: the files of this version appear on the Download page once the release workflow has published them. Until then version 1.0.0 stays available there.',
      );
    }
    for (const id of ['client-1.0.0', 'launcher-1.0.0']) {
      expect(article(id).queryByText('Release pending')).toBeNull();
    }
  });

  it('shows 1.0.1 as the latest release without a pending badge once it is published', async () => {
    await renderPage([
      clientFixture({ published: true, version: '1.0.1' }),
      clientFixture({ published: true }),
      launcherFixture({ published: true, version: '1.0.1' }),
      launcherFixture({ published: true }),
    ]);
    const latest = screen.getByLabelText('Latest versions');
    expect(within(latest).getByText('Latest client').nextElementSibling).toHaveTextContent(
      'v1.0.1',
    );
    expect(within(latest).getByText('Latest launcher').nextElementSibling).toHaveTextContent(
      'v1.0.1',
    );
    expect(screen.queryByText('Release pending')).toBeNull();
  });

  it('shows the newest version as pending while nothing of the product is published yet', async () => {
    await renderPage([
      clientFixture({ published: false, version: '1.0.1' }),
      clientFixture({ published: false }),
      launcherFixture({ published: true }),
    ]);
    const latest = screen.getByLabelText('Latest versions');
    const client = within(latest).getByText('Latest client').parentElement;
    expect(client).toHaveTextContent('v1.0.1');
    expect(client).toHaveTextContent('Release pending');
    // Nothing of the client is published: the note does not promise a previous version.
    for (const id of ['client-1.0.1', 'client-1.0.0']) {
      expect(article(id).getByText('Release pending')).toBeInTheDocument();
      const card = document.getElementById(id);
      expect(card).toHaveTextContent(
        'Not published yet: the files of this version appear on the Download page once the release workflow has published them.',
      );
      expect(card).not.toHaveTextContent('stays available');
      expect(card).not.toHaveTextContent('Until then');
    }
    // No launcher 1.0.1 manifest: its notes are not tied to a pending release.
    expect(article('launcher-1.0.1').queryByText('Release pending')).toBeNull();
    expect(document.getElementById('launcher-1.0.1')).not.toHaveTextContent('Not published yet');
  });
});

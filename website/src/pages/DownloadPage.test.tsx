import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type * as BundlesModule from '../lib/bundles';
import type { BundleManifest } from '../lib/bundles';
import { findChangelog, releaseHighlights } from '../lib/content';
import { launcherCrossPlatformFiles, launcherSetupFiles } from '../lib/downloads';
import type * as EnvModule from '../lib/env';
import { formatBytes } from '../lib/format';
import type * as LocalAiModule from '../lib/localAi';
import {
  type LocalAiManifest,
  localAiDownloadHosts,
  localAiPlatformLabel,
  localAiRuntimeSizeRange,
  parseLocalAiManifest,
} from '../lib/localAi';
import type * as ReleasesModule from '../lib/releases';
import type { ReleaseManifest } from '../lib/releases';
import { bundleFixture } from '../test/fixtures/bundles';
import {
  CLIENT_FILE_NAMES,
  LAUNCHER_FILE_NAMES,
  clientFixture,
  fakeSha,
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

/** The full release zip of version 1.0.0: the only asset of the GitHub Release `v1.0.0`. */
const BUNDLE_ASSET =
  'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/v1.0.0/VantaClient-1.0.0-Release.zip';

/** The launcher files listed under WINDOWS and under CROSS-PLATFORM for the 1.0.0 fixture. */
const SETUP_FILE_NAMES = launcherSetupFiles(launcherFixture({ published: false })).map(
  (f) => f.name,
);
const JAR_FILE_NAMES = launcherCrossPlatformFiles(launcherFixture({ published: false })).map(
  (f) => f.name,
);

/** A small, resolved Local AI manifest with fake sizes and digests (shape of shared/local-ai/local-ai.json). */
function localAiFixture(overrides: { resolved?: boolean } = {}): LocalAiManifest {
  const resolved = overrides.resolved ?? true;
  const platform = (file: string, size: number, serverPath: string) => ({
    file,
    url: `https://github.com/ggml-org/llama.cpp/releases/download/b11429/${file}`,
    size: resolved ? size : 0,
    sha256: resolved ? 'a'.repeat(64) : '',
    serverPath,
  });
  return parseLocalAiManifest(
    {
      schemaVersion: 1,
      resolvedAt: resolved ? '2026-10-08T12:00:00Z' : '',
      runtime: {
        name: 'llama.cpp',
        component: 'llama-server',
        tag: 'b11429',
        license: 'MIT',
        sourceUrl: 'https://github.com/ggml-org/llama.cpp',
        releaseUrl: 'https://github.com/ggml-org/llama.cpp/releases/tag/b11429',
        platforms: {
          'windows-x64': platform(
            'llama-b11429-bin-win-cpu-x64.zip',
            19_000_000,
            'llama-server.exe',
          ),
          'linux-x64': platform(
            'llama-b11429-bin-ubuntu-x64.tar.gz',
            17_000_000,
            'llama-b11429/llama-server',
          ),
          'macos-x64': platform(
            'llama-b11429-bin-macos-x64.tar.gz',
            11_000_000,
            'llama-b11429/llama-server',
          ),
        },
      },
      model: {
        name: 'Qwen3-1.7B',
        quantization: 'Q8_0',
        file: 'Qwen3-1.7B-Q8_0.gguf',
        url: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf',
        size: resolved ? 1_800_000_000 : 0,
        sha256: resolved ? 'b'.repeat(64) : '',
        license: 'Apache-2.0',
        licenseUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE',
        sourceUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF',
        contextSize: 4096,
      },
      requirements: { diskMb: 2200, ramMb: 3072 },
    },
    'fixture local-ai.json',
  );
}

interface RenderOptions {
  readonly env?: Partial<EnvModule.SiteEnv>;
  readonly bundles?: readonly BundleManifest[];
  /** The Local AI manifest to render with; `null` renders without one, `undefined` uses the fixture. */
  readonly localAi?: LocalAiManifest | null | undefined;
}

/**
 * Renders the page against the given manifests instead of `shared/releases/` (and the given bundle
 * manifests instead of `shared/releases/bundles/`, none by default, and a fixture Local AI manifest
 * instead of `shared/local-ai/`), so the tests do not depend on whether the repository manifests have
 * been filled by the release workflow yet or a bundle exists.
 */
async function renderPage(
  manifests: readonly ReleaseManifest[],
  { env: envOverrides = {}, bundles: bundleManifests = [], localAi }: RenderOptions = {},
) {
  vi.doMock('../lib/releases', async (importOriginal) => {
    const original = await importOriginal<typeof ReleasesModule>();
    return { ...original, releases: manifests };
  });
  vi.doMock('../lib/bundles', async (importOriginal) => {
    const original = await importOriginal<typeof BundlesModule>();
    return { ...original, bundles: bundleManifests };
  });
  vi.doMock('../lib/localAi', async (importOriginal) => {
    const original = await importOriginal<typeof LocalAiModule>();
    return { ...original, localAi: localAi === null ? undefined : (localAi ?? localAiFixture()) };
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
  // The news link of "What's new" reads the lazily loaded news chunk through `use()`; load it first
  // so the page renders synchronously inside Testing Library's act scope (see DocumentationPage.test).
  const news = await import('../lib/news');
  const posts = await news.loadNews();
  const module = await import('./DownloadPage');
  const DownloadPage = module.default;
  renderWithRouter(<DownloadPage />, '/download');
  return {
    launcher: screen.getByRole('article', { name: 'VANTA Launcher' }),
    client: screen.getByRole('article', { name: 'VANTA Client (jar)' }),
    jars: screen.getByRole('article', { name: 'VANTA Launcher jars and Linux app' }),
    bundle: screen.queryByRole('article', { name: 'Full release (zip)' }),
    posts,
  };
}

/** Undoes `renderPage` so the next render in the same test starts from fresh modules. */
function resetPage() {
  vi.doUnmock('../lib/releases');
  vi.doUnmock('../lib/bundles');
  vi.doUnmock('../lib/localAi');
  vi.doUnmock('../lib/env');
  vi.resetModules();
  document.body.innerHTML = '';
}

function mockClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
  return writeText;
}

const section = (id: string) => {
  const element = document.getElementById(id);
  if (!element) throw new Error(`#${id} is missing`);
  return element;
};

const unpublished = [clientFixture({ published: false }), launcherFixture({ published: false })];
const published = [clientFixture({ published: true }), launcherFixture({ published: true })];

describe('DownloadPage sections', () => {
  afterEach(resetPage);

  it('has the sections WINDOWS, CROSS-PLATFORM and COMPLETE RELEASE in this order with their cards', async () => {
    const { launcher, client, jars } = await renderPage(published, {
      bundles: [bundleFixture({ published: true })],
    });
    const sections = Array.from(document.querySelectorAll('#downloads > section'));
    expect(sections.map((element) => element.id)).toEqual([
      'windows',
      'cross-platform',
      'complete-release',
    ]);
    // Labels of the three sections (the eyebrow line) and their headings.
    expect(within(section('windows')).getByText('Windows')).toBeInTheDocument();
    expect(
      within(section('windows')).getByRole('heading', { level: 2, name: /Launcher Setup/ }),
    ).toHaveTextContent('Launcher Setup for Windows.');
    expect(within(section('cross-platform')).getByText('Cross-platform')).toBeInTheDocument();
    expect(
      within(section('cross-platform')).getByRole('heading', { level: 2, name: /client jar/ }),
    ).toHaveTextContent('The client jar for Windows, Linux and macOS, and the launcher jars.');
    expect(within(section('complete-release')).getByText('Complete release')).toBeInTheDocument();
    expect(
      within(section('complete-release')).getByRole('heading', { level: 2, name: /one zip/ }),
    ).toHaveTextContent('Everything in one zip.');
    // Which card lives where.
    expect(section('windows').contains(launcher)).toBe(true);
    expect(within(section('windows')).getAllByRole('article')).toHaveLength(1);
    expect(section('cross-platform').contains(client)).toBe(true);
    expect(section('cross-platform').contains(jars)).toBe(true);
    expect(within(section('cross-platform')).getAllByRole('article')).toHaveLength(2);
    expect(
      within(section('complete-release')).getByRole('article', { name: 'Full release (zip)' }),
    ).toBeInTheDocument();
    // The in-page navigation in the hero points at the sections.
    const toc = screen.getByRole('navigation', { name: 'On this page' });
    for (const id of [
      'windows',
      'cross-platform',
      'complete-release',
      'local-ai',
      'requirements',
    ]) {
      expect(
        within(toc).getByRole('link', { name: new RegExp(id.replace('-', '[ -]'), 'i') }),
      ).toHaveAttribute('href', `#${id}`);
      expect(document.getElementById(id)).not.toBeNull();
    }
    expect(document.title).toBe('Download — VANTA Client');
  });

  it('lists the Windows setup files on the launcher card and the jars and the Linux app on their own card', async () => {
    const { launcher, jars, client } = await renderPage(published);
    const setup = within(launcher).getByRole('list', { name: 'Windows files in this release' });
    expect(within(setup).getAllByRole('listitem')).toHaveLength(SETUP_FILE_NAMES.length);
    for (const name of SETUP_FILE_NAMES) {
      expect(within(setup).getByRole('link', { name: `Download ${name}` })).toHaveAttribute(
        'href',
        expect.stringMatching(RELEASE_ASSET),
      );
    }
    expect(within(setup).getByText('Recommended')).toBeInTheDocument();
    expect(within(launcher).queryByText('vanta-launcher-1.0.0-linux-all.jar')).toBeNull();
    // The .exe of the 1.0.0 fixture is named in the footnote as a thing of releases before 1.4.0.
    expect(launcher).toHaveTextContent(
      /the same installer as \.exe \(releases before 1\.4\.0 only\)/,
    );
    expect(launcher).toHaveTextContent(/listed under Cross-platform below/);

    const list = within(jars).getByRole('list', {
      name: 'Launcher files for Linux, macOS and Java in this release',
    });
    expect(within(list).getAllByRole('listitem')).toHaveLength(JAR_FILE_NAMES.length);
    for (const name of JAR_FILE_NAMES) {
      expect(within(list).getByRole('link', { name: `Download ${name}` })).toHaveAttribute(
        'href',
        expect.stringMatching(RELEASE_ASSET),
      );
    }
    expect(within(list).getByText('Linux app with Java')).toBeInTheDocument();
    expect(within(list).getByText('macOS Apple Silicon jar (needs Java 21)')).toBeInTheDocument();
    expect(within(jars).queryByText('VANTA-Launcher-1.0.0.msi')).toBeNull();
    expect(within(jars).queryByText('Recommended')).toBeNull();
    expect(jars).toHaveTextContent(/Each jar runs only on the system it was built for/);
    expect(within(jars).getByText('Published')).toBeInTheDocument();
    expect(within(jars).getByText('Release date').nextElementSibling).toHaveTextContent(
      'October 4, 2026',
    );
    expect(within(jars).getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0',
    );
    // Together the two cards list every launcher file exactly once; the client card lists all of its own.
    expect(SETUP_FILE_NAMES.length + JAR_FILE_NAMES.length).toBe(LAUNCHER_FILE_NAMES.length);
    const clientFiles = within(client).getByRole('list', { name: 'All files in this release' });
    expect(within(clientFiles).getAllByRole('listitem')).toHaveLength(CLIENT_FILE_NAMES.length);
  });

  it('shows the jars card in the pending state without any link while the launcher is unpublished', async () => {
    const { jars, launcher } = await renderPage(unpublished);
    expect(within(jars).getByText('Release pending')).toBeInTheDocument();
    expect(within(jars).getByRole('status')).toHaveTextContent('Not published yet');
    expect(within(jars).queryByRole('list')).toBeNull();
    expect(within(jars).queryAllByRole('link')).toHaveLength(0);
    expect(jars).toHaveTextContent('1.0.0');
    // The launcher card omits the .exe sentence only when its manifest lists no .exe.
    expect(launcher).toHaveTextContent(/the same installer as \.exe/);
    resetPage();
    const noExe = launcherFixture({ published: true }).files.filter(
      (file) => !file.name.endsWith('.exe'),
    );
    const { launcher: withoutExe } = await renderPage([
      clientFixture({ published: true }),
      { ...launcherFixture({ published: true }), files: noExe },
    ]);
    expect(withoutExe).not.toHaveTextContent(/the same installer as \.exe/);
    expect(withoutExe).toHaveTextContent(/Also published: a portable Windows app/);
    expect(
      within(
        within(withoutExe).getByRole('list', { name: 'Windows files in this release' }),
      ).getAllByRole('listitem'),
    ).toHaveLength(2);
  });

  it('says so under COMPLETE RELEASE while no bundle is published, without a dead link', async () => {
    for (const bundleManifests of [
      [],
      [bundleFixture({ published: false })],
      [bundleFixture({ published: true, channel: 'beta' })],
    ]) {
      const { bundle } = await renderPage(published, { bundles: bundleManifests });
      expect(bundle).toBeNull();
      const complete = section('complete-release');
      expect(within(complete).getByRole('status')).toHaveTextContent(
        'No full release zip is published at the moment.',
      );
      expect(within(complete).queryByRole('article')).toBeNull();
      expect(screen.queryByRole('link', { name: 'Download full release (.zip)' })).toBeNull();
      expect(screen.queryByText('Full release (zip)')).toBeNull();
      expect(
        screen
          .queryAllByRole('link')
          .filter((link) => /\/releases\/download\/v\d/.test(link.getAttribute('href') ?? '')),
      ).toHaveLength(0);
      // The other sections keep their three cards.
      expect(within(section('downloads')).getAllByRole('article')).toHaveLength(3);
      // The line about older versions keeps its GitHub links under COMPLETE RELEASE.
      expect(
        within(complete).getByText('Looking for older versions or checksum files?'),
      ).toBeInTheDocument();
      resetPage();
    }
  });
});

describe('DownloadPage Local AI note', () => {
  afterEach(resetPage);

  it('names the runtime, the model, the hosts, the sizes per system and the requirements from the manifest', async () => {
    const manifest = localAiFixture();
    await renderPage(published, { localAi: manifest });
    const note = section('local-ai');
    expect(within(note).getByText('Local AI')).toBeInTheDocument();
    expect(within(note).getByRole('heading', { level: 2 })).toHaveTextContent(
      'Optional, and it asks first.',
    );
    expect(note).toHaveTextContent(/From VANTA Client 1\.4\.0 and VANTA Launcher 1\.4\.0 on/);
    // Runtime and model facts.
    expect(note).toHaveTextContent('Runtime: llama.cpp b11429 (llama-server)');
    expect(note).toHaveTextContent('Model: Qwen3-1.7B Q8_0 (Qwen3-1.7B-Q8_0.gguf)');
    expect(note).toHaveTextContent(/licence MIT/);
    expect(note).toHaveTextContent(/licence Apache-2\.0/);
    const range = localAiRuntimeSizeRange(manifest)!;
    expect(note).toHaveTextContent(
      `${formatBytes(range.smallest.size)} (${localAiPlatformLabel(range.smallest.key)}) to ${formatBytes(range.largest.size)} (${localAiPlatformLabel(range.largest.key)})`,
    );
    expect(note).toHaveTextContent(`${formatBytes(manifest.model.size)} (1,800,000,000 bytes)`);
    // Hosts: the only two the client ever contacts besides Modrinth.
    for (const host of localAiDownloadHosts(manifest)) expect(note).toHaveTextContent(host);
    expect(note).toHaveTextContent(
      /github\.com and huggingface\.co, are contacted for this download only/,
    );
    expect(note).toHaveTextContent(/Modrinth while you use Mods & Shaders, and nothing else, ever/);
    expect(note).toHaveTextContent(/127\.0\.0\.1/);
    expect(note).toHaveTextContent(/no cloud AI, no API key, no account,\s*no telemetry/);
    // Optional, and it asks first.
    expect(note).toHaveTextContent(/Nothing downloads on its own/);
    expect(note).toHaveTextContent(/Install Local AI/);
    expect(note).toHaveTextContent(/"Not now" keeps the automatic install off/);
    // One row per platform archive with its size.
    const archives = within(note).getByRole('list', { name: 'Runtime archive per system' });
    const rows = within(archives).getAllByRole('listitem');
    expect(rows).toHaveLength(manifest.runtime.platforms.length);
    expect(rows[0]).toHaveTextContent('Windows x64');
    expect(rows[0]).toHaveTextContent('llama-b11429-bin-win-cpu-x64.zip');
    expect(rows[0]).toHaveTextContent('19.0 MB');
    expect(rows[2]).toHaveTextContent('macOS Intel');
    // Requirements and folders.
    expect(note).toHaveTextContent(/About 2200 MB of disk space/);
    expect(note).toHaveTextContent(/3072 MB of RAM/);
    expect(note).toHaveTextContent('config/vanta/local-ai');
    // Links: the release, the model page, the manifest, the Features section and the privacy page.
    expect(within(note).getByRole('link', { name: /Release b11429 on GitHub/ })).toHaveAttribute(
      'href',
      manifest.runtime.releaseUrl,
    );
    expect(within(note).getByRole('link', { name: /Model page/ })).toHaveAttribute(
      'href',
      manifest.model.sourceUrl,
    );
    expect(within(note).getByRole('link', { name: /The Local AI manifest/ })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/blob/HEAD/shared/local-ai/local-ai.json',
    );
    expect(
      within(note).getByRole('link', { name: /What the Local AI is and is not/ }),
    ).toHaveAttribute('href', '/features#local-ai');
    expect(within(note).getByRole('link', { name: 'Privacy' })).toHaveAttribute('href', '/privacy');
    // The note never offers a download: the Local AI is not a release file.
    expect(
      within(note)
        .getAllByRole('link')
        .filter((link) => link.getAttribute('href')?.includes('/releases/download/')),
    ).toHaveLength(0);
    // The requirements table has the Local AI row.
    const requirements = section('requirements');
    expect(
      within(requirements).getByText('Local AI (optional)').nextElementSibling,
    ).toHaveTextContent('2200 MB disk, 3072 MB RAM');
  });

  it('claims no size while the manifest is the unresolved template, and leaves the note out without a manifest', async () => {
    await renderPage(published, { localAi: localAiFixture({ resolved: false }) });
    const note = section('local-ai');
    expect(note).toHaveTextContent(
      /Its size is recorded in the manifest once the resolve workflow has run/,
    );
    expect(note).not.toHaveTextContent(/\d MB \(/);
    expect(within(note).getAllByText('size not resolved yet')).toHaveLength(3);
    resetPage();

    await renderPage(published, { localAi: null });
    expect(document.getElementById('local-ai')).toBeNull();
    expect(screen.queryByText('Local AI (optional)')).toBeNull();
    expect(
      within(screen.getByRole('navigation', { name: 'On this page' })).queryByRole('link', {
        name: 'Local AI',
      }),
    ).toBeNull();
  });
});

describe('DownloadPage', () => {
  afterEach(resetPage);

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
    expect(screen.queryByRole('list', { name: 'Windows files in this release' })).toBeNull();
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
    // Honest footnotes: Fabric API required; the Local AI asks first.
    expect(launcher).toHaveTextContent(/downloads it only after you agree/);
    expect(launcher).not.toHaveTextContent(/portable \.jar/);
    expect(client).toHaveTextContent('Requires Fabric API 0.141.6+1.21.11');
    expect(client).toHaveTextContent(/included in the mods bundle/);
    expect(client).toHaveTextContent(/for Windows, Linux and macOS alike/);
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
    expect(ways[0]).toHaveTextContent(/offers the optional Local AI for Vanta Nexus once/);
    expect(ways[0]).toHaveTextContent(/nothing of it downloads before you\s+agree/);
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
    const { launcher, client, jars } = await renderPage(published);

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

    const clientFiles = within(client).getByRole('list', { name: 'All files in this release' });
    expect(within(clientFiles).getAllByRole('listitem')).toHaveLength(CLIENT_FILE_NAMES.length);
    for (const name of CLIENT_FILE_NAMES) {
      expect(within(clientFiles).getByRole('link', { name: `Download ${name}` })).toHaveAttribute(
        'href',
        expect.stringMatching(RELEASE_ASSET),
      );
    }
    // Every launcher file has its link on exactly one of the two launcher cards.
    for (const name of LAUNCHER_FILE_NAMES) {
      const links = screen.getAllByRole('link', { name: `Download ${name}` });
      expect(links).toHaveLength(1);
      expect(links[0]).toHaveAttribute('href', expect.stringMatching(RELEASE_ASSET));
      const card = SETUP_FILE_NAMES.includes(name) ? launcher : jars;
      expect(card.contains(links[0]!)).toBe(true);
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
    // The how-to stays next to the published files.
    expect(within(client).getByRole('region', { name: 'How to install' })).toBeInTheDocument();
  });

  it('keeps offering the published 1.0.0 while 1.0.1 is committed but not published', async () => {
    const { launcher, client, jars } = await renderPage([
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
    for (const card of [launcher, client, jars]) {
      const hrefs = within(card)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href') ?? '')
        .filter((href) => href.includes('/releases/download/'));
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
    expect(jars).toHaveTextContent('1.0.0');
    expect(jars).not.toHaveTextContent('1.0.1');
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
      env: { downloadLauncherUrl: launcherUrl },
    });

    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      launcherUrl,
    );
    expect(within(launcher).queryByText(/Not published yet/)).toBeNull();
    // The manifest is still unpublished: no per-file links.
    expect(
      within(launcher).queryByRole('list', { name: 'Windows files in this release' }),
    ).toBeNull();
    expect(within(client).getByRole('button', { name: 'Download client jar' })).toBeDisabled();
  });

  it('keeps working with the repository manifests, whatever their state', async () => {
    const actual = await vi.importActual<typeof ReleasesModule>('../lib/releases');
    const actualBundles = await vi.importActual<typeof BundlesModule>('../lib/bundles');
    const actualLocalAi = await vi.importActual<typeof LocalAiModule>('../lib/localAi');
    const { launcher, client, jars, bundle } = await renderPage(actual.releases, {
      bundles: actualBundles.bundles,
      localAi: actualLocalAi.localAi ?? null,
    });
    expect(screen.getByRole('group', { name: 'Latest version' })).toBeInTheDocument();
    // A bundle card exactly when shared/releases/bundles has a published stable manifest.
    const offeredBundle = actualBundles.latestBundle(actualBundles.bundles);
    expect(bundle !== null).toBe(offeredBundle !== undefined);
    if (bundle && offeredBundle) {
      expect(
        within(bundle).getByRole('link', { name: 'Download full release (.zip)' }),
      ).toHaveAttribute('href', offeredBundle.file.downloadUrl);
    } else {
      expect(screen.queryByRole('link', { name: 'Download full release (.zip)' })).toBeNull();
    }
    // The Local AI note exactly when shared/local-ai/local-ai.json exists.
    expect(document.getElementById('local-ai') !== null).toBe(actualLocalAi.localAi !== undefined);
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
    // The jars card follows the launcher's state and lists the rest of the offered launcher release.
    const offeredLauncher = actual.latestRelease(actual.releases, 'launcher');
    if (offeredLauncher && actual.isPublished(offeredLauncher)) {
      const list = within(jars).getByRole('list', {
        name: 'Launcher files for Linux, macOS and Java in this release',
      });
      expect(within(list).getAllByRole('listitem')).toHaveLength(
        launcherCrossPlatformFiles(offeredLauncher).length,
      );
      for (const href of within(list)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href') ?? '')) {
        expect(href).toMatch(ANY_RELEASE_ASSET);
        expect(href).not.toMatch(/\.(msi|exe)$/);
      }
    } else {
      expect(within(jars).queryByRole('list')).toBeNull();
    }
  });

  it('shows the latest versions in the hero with their release date', async () => {
    await renderPage(published);
    const latest = screen.getByRole('group', { name: 'Latest version' });
    expect(latest).toHaveTextContent('Latest version');
    expect(latest).toHaveTextContent('VANTA Client 1.0.0 and VANTA Launcher 1.0.0');
    expect(latest).toHaveTextContent('Released October 4, 2026');
    expect(latest).not.toHaveTextContent('not published yet');
  });

  it('marks an unpublished offered version in the hero and lists different release dates', async () => {
    await renderPage([
      clientFixture({ published: false }),
      launcherFixture({ published: true, version: '1.0.1', releaseDate: '2026-10-05' }),
    ]);
    const latest = screen.getByRole('group', { name: 'Latest version' });
    expect(latest).toHaveTextContent(
      'VANTA Client 1.0.0 (not published yet) and VANTA Launcher 1.0.1',
    );
    expect(latest).toHaveTextContent(
      'VANTA Client released October 4, 2026, VANTA Launcher released October 5, 2026',
    );
  });
});

describe('DownloadPage full release zip', () => {
  afterEach(resetPage);

  it('offers a published bundle under COMPLETE RELEASE with its facts, checksum and contents', async () => {
    const writeText = mockClipboard();
    const fixture = bundleFixture({ published: true });
    const { launcher, client, bundle } = await renderPage(published, { bundles: [fixture] });
    expect(bundle).not.toBeNull();
    if (!bundle) return;

    // Third section, after the launcher and the client.
    const cards = screen.getAllByRole('article');
    expect(cards.indexOf(bundle)).toBeGreaterThan(cards.indexOf(launcher));
    expect(cards.indexOf(bundle)).toBeGreaterThan(cards.indexOf(client));
    expect(section('complete-release').contains(bundle)).toBe(true);
    const link = within(bundle).getByRole('link', { name: 'Download full release (.zip)' });
    expect(link).toHaveAttribute('href', BUNDLE_ASSET);
    expect(link).not.toHaveAttribute('target');
    expect(link).toHaveAttribute('rel', 'noopener');
    expect(link).toHaveAttribute('download');
    expect(within(bundle).getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.0.0',
    );
    expect(within(bundle).getByText('Published')).toBeInTheDocument();
    expect(within(bundle).getByText('VantaClient-1.0.0-Release.zip')).toBeInTheDocument();
    expect(within(bundle).getByText('240.0 MB')).toBeInTheDocument();
    expect(within(bundle).getByText('Client version').nextElementSibling).toHaveTextContent(
      '1.0.0',
    );
    expect(within(bundle).getByText('Launcher version').nextElementSibling).toHaveTextContent(
      '1.0.0',
    );
    expect(within(bundle).getByText('October 4, 2026')).toBeInTheDocument();
    expect(bundle).toHaveTextContent(/README\.txt/);
    expect(bundle).toHaveTextContent(/SHA256SUMS\.txt/);
    // Same versions as the other cards: no note about different ones.
    expect(within(bundle).queryByRole('note', { name: 'Versions in this zip' })).toBeNull();
    expect(bundle).toHaveTextContent('The files are the ones the two cards above offer');

    // The zip's checksum, grouped, with a copy button.
    const zipSha = fixture.file.sha256;
    expect(
      within(bundle).getByText(new RegExp(`${zipSha.slice(0, 8)} ${zipSha.slice(8, 16)}`)),
    ).toBeInTheDocument();
    await userEvent.click(
      within(bundle).getByRole('button', {
        name: 'Copy SHA-256 checksum of VantaClient-1.0.0-Release.zip',
      }),
    );
    expect(writeText).toHaveBeenCalledWith(zipSha);

    // Every file inside the zip, in manifest order, with size and checksum.
    const contents = within(bundle).getByRole('list', {
      name: `Files in the zip (${fixture.contents.length})`,
    });
    const items = within(contents).getAllByRole('listitem');
    expect(items).toHaveLength(fixture.contents.length);
    items.forEach((item, index) => {
      const entry = fixture.contents[index]!;
      expect(item).toHaveTextContent(entry.path);
      expect(
        within(item).getByRole('button', { name: `Copy SHA-256 checksum of ${entry.path}` }),
      ).toBeInTheDocument();
    });
    expect(items[0]).toHaveTextContent('README.txt');
    expect(items[0]).toHaveTextContent('1.2 MB');
    await userEvent.click(within(items[0]!).getByRole('button', { name: /Copy SHA-256/ }));
    expect(writeText).toHaveBeenLastCalledWith(fakeSha(0));
    // No link inside the list: the files are inside the archive, not separate downloads.
    expect(within(contents).queryByRole('link')).toBeNull();

    // The other cards are untouched.
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toBeInTheDocument();
    expect(within(client).getByRole('link', { name: 'Download client jar' })).toBeInTheDocument();
    // Every release download on the page is an asset of this repository, for one of the three tags.
    const hrefs = screen
      .getAllByRole('link')
      .map((a) => a.getAttribute('href') ?? '')
      .filter((href) => href.includes('/releases/download/'));
    expect(hrefs).toContain(BUNDLE_ASSET);
    for (const href of hrefs) {
      expect(href).toMatch(
        /^https:\/\/github\.com\/LennardOwnTest123006\/VANTA-Client\/releases\/download\/(client-v1\.0\.0|launcher-v1\.0\.0|v1\.0\.0)\/[^/]+$/,
      );
    }
    expect(document.title).toBe('Download — VANTA Client');
  });

  it('says so when the zip holds other versions than the cards offer', async () => {
    const { bundle } = await renderPage(
      [
        clientFixture({ published: true, version: '1.0.1' }),
        clientFixture({ published: true }),
        launcherFixture({ published: true }),
      ],
      { bundles: [bundleFixture({ published: true })] },
    );
    expect(bundle).not.toBeNull();
    if (!bundle) return;
    const note = within(bundle).getByRole('note', { name: 'Versions in this zip' });
    expect(note).toHaveTextContent('This zip holds client 1.0.0 and launcher 1.0.0');
    expect(note).toHaveTextContent('the cards above offer client 1.0.1 and launcher 1.0.0');
    expect(bundle).not.toHaveTextContent('The files are the ones the two cards above offer');
  });

  it('prefers the newest published stable bundle', async () => {
    const { bundle } = await renderPage(published, {
      bundles: [
        bundleFixture({ published: true }),
        bundleFixture({ published: true, version: '1.1.0', clientVersion: '1.0.0' }),
        bundleFixture({ published: false, version: '1.2.0' }),
      ],
    });
    expect(bundle).not.toBeNull();
    if (!bundle) return;
    expect(
      within(bundle).getByRole('link', { name: 'Download full release (.zip)' }),
    ).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/v1.1.0/VantaClient-1.1.0-Release.zip',
    );
    expect(within(bundle).getByText('Version').nextElementSibling).toHaveTextContent('1.1.0');
    // The zip holds launcher 1.1.0 while the card offers 1.0.0: said on the card.
    expect(within(bundle).getByRole('note', { name: 'Versions in this zip' })).toHaveTextContent(
      'This zip holds client 1.0.0 and launcher 1.1.0',
    );
  });
});

describe("DownloadPage What's new", () => {
  afterEach(resetPage);

  it('quotes up to five highlights per product from the release notes and links the full notes', async () => {
    const { posts } = await renderPage(published);
    const whatsNew = section('whats-new');
    expect(within(whatsNew).getByText("What's new")).toBeInTheDocument();
    expect(within(whatsNew).getByRole('heading', { level: 2 })).toHaveTextContent(
      "What's new in 1.0.0",
    );
    for (const [product, label] of [
      ['client', 'VANTA Client'],
      ['launcher', 'VANTA Launcher'],
    ] as const) {
      const notes = findChangelog(product, '1.0.0');
      expect(notes).toBeDefined();
      const column = within(whatsNew).getByRole('article', { name: notes!.title });
      const expected = releaseHighlights(notes!, 5);
      expect(expected.length).toBeGreaterThan(0);
      expect(expected.length).toBeLessThanOrEqual(5);
      expect(
        within(column)
          .getAllByRole('listitem')
          .map((item) => item.textContent),
      ).toEqual(expected);
      expect(column).toHaveTextContent('Released October 4, 2026');
      expect(
        within(column).getByRole('link', { name: `Full release notes for ${label} 1.0.0` }),
      ).toHaveAttribute('href', `/changelog#${product}-1.0.0`);
    }
    // The cards' own "Full release notes" links are unchanged and come first in the document.
    expect(screen.getAllByRole('link', { name: 'Full release notes' })).toHaveLength(2);
    // The newest news post is linked.
    const newest = posts[0];
    expect(newest).toBeDefined();
    const newsLink = await within(whatsNew).findByRole('link', { name: newest!.title });
    expect(newsLink).toHaveAttribute('href', `/news/${newest!.slug}`);
    expect(whatsNew).toHaveTextContent('Latest news post:');
  });

  it('leaves out a product without release notes, and the whole section without any', async () => {
    await renderPage([
      clientFixture({ published: true, version: '9.9.9' }),
      launcherFixture({ published: true }),
    ]);
    const whatsNew = section('whats-new');
    expect(within(whatsNew).getByRole('heading', { level: 2 })).toHaveTextContent(
      "What's new in 1.0.0",
    );
    expect(within(whatsNew).getAllByRole('article')).toHaveLength(1);
    expect(
      within(whatsNew).getByRole('article', { name: 'VANTA Launcher 1.0.0' }),
    ).toBeInTheDocument();
    expect(within(whatsNew).queryByText(/9\.9\.9/)).toBeNull();

    resetPage();
    await renderPage([
      clientFixture({ published: true, version: '9.9.9' }),
      launcherFixture({ published: true, version: '9.9.8' }),
    ]);
    expect(document.getElementById('whats-new')).toBeNull();
    expect(screen.queryByText("What's new")).toBeNull();
  });

  it('names both versions when the client and the launcher differ', async () => {
    await renderPage([
      clientFixture({ published: true, version: '1.0.1' }),
      launcherFixture({ published: true }),
    ]);
    expect(within(section('whats-new')).getByRole('heading', { level: 2 })).toHaveTextContent(
      "What's new in client 1.0.1 and launcher 1.0.0",
    );
  });
});

describe('DownloadPage older versions', () => {
  afterEach(resetPage);

  const older = () => document.getElementById('older-versions');

  it('lists every older published release with its date, file, checksum and links', async () => {
    const writeText = mockClipboard();
    const { launcher, client } = await renderPage([
      clientFixture({ published: true, version: '1.0.1', releaseDate: '2026-10-05' }),
      clientFixture({ published: true }),
      launcherFixture({ published: true, version: '1.0.2', releaseDate: '2026-10-06' }),
      launcherFixture({ published: true, version: '1.0.1', releaseDate: '2026-10-05' }),
      launcherFixture({ published: true }),
    ]);
    const list = older();
    expect(list).not.toBeNull();
    if (!list) return;
    expect(within(list).getByRole('heading', { level: 2 })).toHaveTextContent(
      'Every earlier release stays available.',
    );
    expect(list).toHaveTextContent('The cards above always offer the newest published release');
    // The cards keep offering the newest versions; the line under them points at this section.
    expect(within(launcher).getByRole('link', { name: 'Download launcher' })).toHaveAttribute(
      'href',
      expect.stringContaining('/launcher-v1.0.2/'),
    );
    expect(within(client).getByRole('link', { name: 'Download client jar' })).toHaveAttribute(
      'href',
      expect.stringContaining('/client-v1.0.1/'),
    );
    const jump = screen.getByRole('link', { name: 'Older versions on this page' });
    expect(jump).toHaveAttribute('href', '#older-versions');
    expect(jump).not.toHaveAttribute('target');
    expect(screen.getByText('Looking for older versions or checksum files?')).toBeInTheDocument();
    // The in-page navigation gets the entry too.
    expect(
      within(screen.getByRole('navigation', { name: 'On this page' })).getByRole('link', {
        name: 'Older versions',
      }),
    ).toHaveAttribute('href', '#older-versions');

    // Launcher first, then the client, like the cards; nothing for zips without a second bundle.
    const lists = within(list).getAllByRole('list');
    expect(lists.map((element) => element.getAttribute('aria-labelledby'))).toEqual([
      'older-versions-launcher',
      'older-versions-client',
    ]);
    expect(within(list).queryByRole('list', { name: 'Full release zip' })).toBeNull();

    const launchers = within(list).getByRole('list', { name: 'VANTA Launcher' });
    const launcherRows = within(launchers).getAllByRole('listitem');
    expect(launcherRows).toHaveLength(2);
    expect(list).toHaveTextContent('2 earlier versions');
    // Newest first, with the release date of each version.
    expect(launcherRows[0]).toHaveTextContent('1.0.1');
    expect(launcherRows[0]).toHaveTextContent('October 5, 2026');
    expect(launcherRows[1]).toHaveTextContent('1.0.0');
    expect(launcherRows[1]).toHaveTextContent('October 4, 2026');
    expect(within(launcherRows[0]!).getByText('VANTA-Launcher-1.0.1.msi')).toBeInTheDocument();
    expect(launcherRows[0]).toHaveTextContent('1.2 MB');
    const download = within(launcherRows[0]!).getByRole('link', {
      name: 'Download VANTA Launcher 1.0.1',
    });
    expect(download).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/launcher-v1.0.1/VANTA-Launcher-1.0.1.msi',
    );
    expect(download).toHaveAttribute('download');
    expect(download).toHaveAttribute('rel', 'noopener');
    expect(download).not.toHaveAttribute('target');
    expect(
      within(launcherRows[0]!).getByRole('link', {
        name: 'Release page on GitHub for VANTA Launcher 1.0.1',
      }),
    ).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.1',
    );
    expect(
      within(launcherRows[0]!).getByRole('link', {
        name: 'Release notes for VANTA Launcher 1.0.1',
      }),
    ).toHaveAttribute('href', '/changelog#launcher-1.0.1');
    expect(
      within(launcherRows[1]!).getByRole('link', { name: 'Download VANTA Launcher 1.0.0' }),
    ).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi',
    );
    expect(
      within(launcherRows[1]!).getByRole('link', {
        name: 'Release notes for VANTA Launcher 1.0.0',
      }),
    ).toHaveAttribute('href', '/changelog#launcher-1.0.0');

    const clients = within(list).getByRole('list', { name: 'VANTA Client' });
    const clientRows = within(clients).getAllByRole('listitem');
    expect(clientRows).toHaveLength(1);
    expect(list).toHaveTextContent('1 earlier version');
    expect(clientRows[0]).toHaveTextContent('1.0.0');
    expect(
      within(clientRows[0]!).getByRole('link', { name: 'Download VANTA Client 1.0.0' }),
    ).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
    );
    expect(
      within(clientRows[0]!).getByRole('link', { name: 'Release notes for VANTA Client 1.0.0' }),
    ).toHaveAttribute('href', '/changelog#client-1.0.0');
    // The checksum of the primary file, grouped, with a copy button.
    const sha = fakeSha(0);
    expect(
      within(clientRows[0]!).getByText(new RegExp(`${sha.slice(0, 8)} ${sha.slice(8, 16)}`)),
    ).toBeInTheDocument();
    await userEvent.click(
      within(clientRows[0]!).getByRole('button', {
        name: 'Copy SHA-256 checksum of vanta-client-1.0.0.jar',
      }),
    );
    expect(writeText).toHaveBeenCalledWith(sha);

    // Every link of a row points at an asset or page of the version the row names.
    for (const row of [...launcherRows, ...clientRows]) {
      const version = /\d+\.\d+\.\d+/.exec(row.textContent ?? '')?.[0] ?? '';
      expect(version).not.toBe('');
      for (const href of within(row)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href') ?? '')) {
        expect(href).toContain(version);
      }
    }
  });

  it('renders nothing when only one release of each product is published', async () => {
    await renderPage(published);
    expect(older()).toBeNull();
    expect(screen.queryByRole('link', { name: 'Older versions on this page' })).toBeNull();
    expect(screen.queryByText('Older versions')).toBeNull();
    // The line about older versions keeps its GitHub links.
    expect(screen.getByText('Looking for older versions or checksum files?')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'All releases on GitHub' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases',
    );
    expect(screen.queryByRole('link', { name: /^Download VANTA (Client|Launcher) \d/ })).toBeNull();
  });

  it('skips unpublished older manifests, the upcoming version and a product without older releases', async () => {
    await renderPage([
      clientFixture({ published: true, version: '1.0.2', releaseDate: '2026-10-06' }),
      clientFixture({ published: false, version: '1.0.1', releaseDate: '2026-10-05' }),
      clientFixture({ published: true }),
      launcherFixture({ published: false, version: '1.0.1', releaseDate: '2026-10-05' }),
      launcherFixture({ published: true }),
    ]);
    const list = older();
    expect(list).not.toBeNull();
    if (!list) return;
    // Only the client has an older published release: one list, one row.
    expect(within(list).getAllByRole('list')).toHaveLength(1);
    expect(within(list).queryByRole('list', { name: 'VANTA Launcher' })).toBeNull();
    const clients = within(list).getByRole('list', { name: 'VANTA Client' });
    expect(within(clients).getAllByRole('listitem')).toHaveLength(1);
    expect(clients).toHaveTextContent('1.0.0');
    expect(clients).not.toHaveTextContent('1.0.1');
    expect(clients).not.toHaveTextContent('1.0.2');
    // The unpublished launcher 1.0.1 is still the upcoming version on its card, not an older one.
    expect(screen.getByRole('note', { name: 'Upcoming version 1.0.1' })).toBeInTheDocument();
    // No link of the section leads to a 1.0.1 or 1.0.2 asset.
    for (const href of within(list)
      .getAllByRole('link')
      .map((link) => link.getAttribute('href') ?? '')) {
      expect(href).not.toMatch(/1\.0\.[12]/);
    }
  });

  it('offers the download of a version without release notes, just without the notes link', async () => {
    await renderPage([
      clientFixture({ published: true, version: '9.9.9' }),
      clientFixture({ published: true, version: '9.9.8' }),
      launcherFixture({ published: true }),
    ]);
    const list = older();
    expect(list).not.toBeNull();
    if (!list) return;
    const row = within(list).getByRole('listitem');
    expect(within(row).getByRole('link', { name: 'Download VANTA Client 9.9.8' })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v9.9.8/vanta-client-9.9.8.jar',
    );
    expect(
      within(row).getByRole('link', { name: 'Release page on GitHub for VANTA Client 9.9.8' }),
    ).toBeInTheDocument();
    expect(within(row).queryByRole('link', { name: /^Release notes/ })).toBeNull();
  });

  it('lists older published full release zips once there is more than one bundle', async () => {
    const { bundle } = await renderPage(published, {
      bundles: [
        bundleFixture({ published: true }),
        bundleFixture({ published: true, version: '1.1.0', clientVersion: '1.0.0' }),
        bundleFixture({ published: false, version: '1.2.0' }),
      ],
    });
    expect(bundle).not.toBeNull();
    const list = older();
    expect(list).not.toBeNull();
    if (!list) return;
    // Only the zips have an older version here: the products are at 1.0.0 only.
    expect(within(list).getAllByRole('list')).toHaveLength(1);
    const zips = within(list).getByRole('list', { name: 'Full release zip' });
    const rows = within(zips).getAllByRole('listitem');
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent('1.0.0');
    expect(rows[0]).toHaveTextContent('October 4, 2026');
    expect(rows[0]).toHaveTextContent('VantaClient-1.0.0-Release.zip');
    expect(rows[0]).toHaveTextContent('240.0 MB');
    expect(rows[0]).toHaveTextContent('Holds client 1.0.0 and launcher 1.0.0.');
    expect(
      within(rows[0]!).getByRole('link', { name: 'Download Full release zip 1.0.0' }),
    ).toHaveAttribute('href', BUNDLE_ASSET);
    expect(
      within(rows[0]!).getByRole('link', {
        name: 'Release page on GitHub for Full release zip 1.0.0',
      }),
    ).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.0.0',
    );
    expect(within(rows[0]!).queryByRole('link', { name: /^Release notes/ })).toBeNull();
    expect(
      within(rows[0]!).getByRole('button', {
        name: 'Copy SHA-256 checksum of VantaClient-1.0.0-Release.zip',
      }),
    ).toBeInTheDocument();
    // The unpublished 1.2.0 is nowhere, and the offered 1.1.0 stays on its card only.
    expect(list).not.toHaveTextContent('1.2.0');
    expect(within(list).queryByText('VantaClient-1.1.0-Release.zip')).toBeNull();

    resetPage();
    // One published bundle: nothing older to list.
    await renderPage(published, { bundles: [bundleFixture({ published: true })] });
    expect(older()).toBeNull();
  });
});

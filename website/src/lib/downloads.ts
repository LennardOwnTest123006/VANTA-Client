import { type ReleaseFile, type ReleaseManifest, pickFile } from './releases';

/**
 * Resolution of what the download page can honestly offer for a product.
 *
 * A download is `available` only when a real URL exists — either from the release manifest (filled
 * by the release workflow) or from an environment override (`VITE_DOWNLOAD_*_URL`). Otherwise it is
 * `pending` and the UI shows a disabled button with the facts that are known.
 */
export type DownloadResolution =
  | {
      readonly state: 'available';
      readonly url: string;
      readonly file: ReleaseFile;
      /** `manifest` when the URL comes from the release manifest, `env` for an environment override. */
      readonly source: 'manifest' | 'env';
    }
  | {
      readonly state: 'pending';
      readonly file: ReleaseFile | undefined;
    }
  | { readonly state: 'missing' };

/** File extensions preferred per product, in order (the launcher's primary file is the .msi). */
export const PREFERRED_EXTENSIONS = {
  launcher: ['.msi', '.exe', '.jar'],
  client: ['.jar'],
} as const satisfies Record<ReleaseManifest['product'], readonly string[]>;

const lower = (name: string) => name.toLowerCase();
const isFabricApiJar = (name: string) => /^fabric-api-.+\.jar$/.test(lower(name));
const isSourcesJar = (name: string) => lower(name).endsWith('-sources.jar');

/**
 * The file a product's primary download button offers.
 *
 * - client: exactly `vanta-client-<version>.jar`, the same rule the launcher applies. A manifest that
 *   does not list that name has no primary client file (the button stays disabled); Fabric API, a
 *   `-sources` jar, the mods bundle or a jar of another version are never offered in its place.
 * - launcher: the first `.msi`, then `.exe`, then `.jar` (see {@link PREFERRED_EXTENSIONS}).
 */
export function primaryFile(manifest: ReleaseManifest): ReleaseFile | undefined {
  if (manifest.product === 'client') {
    const exact = `vanta-client-${manifest.version}.jar`;
    return manifest.files.find((file) => file.name === exact);
  }
  return pickFile(manifest, PREFERRED_EXTENSIONS.launcher);
}

/** The client's mods folder bundle (`vanta-client-<version>-mods.zip`), when the manifest lists one. */
export function modsBundleFile(manifest: ReleaseManifest | undefined): ReleaseFile | undefined {
  return manifest?.files.find((file) => lower(file.name).endsWith('-mods.zip'));
}

/** Short, human description of one release file, derived from its name only. */
export interface ReleaseFileInfo {
  /** Platform and kind, e.g. "Windows installer" or "Fabric API (required dependency)". */
  readonly label: string;
  /** One sentence on what the file contains or how to start it; `undefined` for unknown files. */
  readonly note: string | undefined;
}

interface FileRule {
  readonly test: (name: string) => boolean;
  readonly info: ReleaseFileInfo;
}

/**
 * Rules in priority order; the first match wins. The wording mirrors the asset descriptions of
 * `scripts/release/release-assets.mjs`, which the release workflow puts into the GitHub Release.
 */
const FILE_RULES: readonly FileRule[] = [
  {
    test: (name) => name.endsWith('-mods.zip'),
    info: {
      label: 'Mods folder bundle (VANTA + Fabric API)',
      note: 'Both mods in a mods/ folder, plus INSTALL.txt and SHA256SUMS. Copy the two jars into .minecraft/mods.',
    },
  },
  {
    test: isFabricApiJar,
    info: {
      label: 'Fabric API (required dependency)',
      note: 'The unmodified FabricMC release (Apache-2.0). VANTA Client does not start without it.',
    },
  },
  {
    test: isSourcesJar,
    info: { label: 'Source code jar', note: 'Sources for developers; not needed to play.' },
  },
  {
    test: (name) => /^vanta-client-.+\.jar$/.test(name),
    info: {
      label: 'VANTA Client mod',
      note: 'The Fabric mod itself. It goes into your mods folder next to Fabric API.',
    },
  },
  {
    test: (name) => name.endsWith('.msi'),
    info: {
      label: 'Windows installer',
      note: 'Recommended for 64-bit Windows 10 and 11. Installs per user, no admin rights; Java 21 included.',
    },
  },
  {
    test: (name) => name.endsWith('.exe'),
    info: {
      label: 'Windows installer',
      note: 'The same installer as an .exe for 64-bit Windows 10 and 11; Java 21 included.',
    },
  },
  {
    test: (name) => name.endsWith('-windows-portable.zip'),
    info: {
      label: 'Windows portable app with Java',
      note: 'No installation: unzip and run "VANTA Launcher/VANTA Launcher.exe". Java 21 included.',
    },
  },
  {
    test: (name) => /-windows(?:-x64)?-all\.jar$/.test(name),
    info: {
      label: 'Windows jar (needs Java 21)',
      note: 'Windows x64 only. Start it with java -jar and an installed Java 21.',
    },
  },
  {
    test: (name) => name.endsWith('-linux-x64.tar.gz'),
    info: {
      label: 'Linux app with Java',
      note: 'Linux x64: extract and run "VANTA Launcher/bin/VANTA Launcher". Java 21 included.',
    },
  },
  {
    test: (name) => /-linux(?:-x64)?-all\.jar$/.test(name),
    info: {
      label: 'Linux jar (needs Java 21)',
      note: 'Linux x64 only. Start it with java -jar and an installed Java 21.',
    },
  },
  {
    test: (name) => name.endsWith('-macos-aarch64-all.jar'),
    info: {
      label: 'macOS Apple Silicon jar (needs Java 21)',
      note: 'Apple Silicon Macs only, unsigned. Built and command-line tested on macOS; the window is not tested yet.',
    },
  },
];

const UNKNOWN_FILE: ReleaseFileInfo = { label: 'Release file', note: undefined };

/** Describes a release file by its name (case-insensitive). Unknown names get a neutral label. */
export function describeReleaseFile(name: string): ReleaseFileInfo {
  const normalized = lower(name.trim());
  return FILE_RULES.find((rule) => rule.test(normalized))?.info ?? UNKNOWN_FILE;
}

/**
 * Resolves the download for a manifest. The environment override wins over the manifest so a
 * mirror can be configured without editing release data; the manifest's file facts (size, checksum)
 * are still shown alongside.
 */
export function resolveDownload(
  manifest: ReleaseManifest | undefined,
  envUrl: string | undefined,
): DownloadResolution {
  if (!manifest) return { state: 'missing' };
  const file = primaryFile(manifest);
  if (!file) return { state: 'pending', file: undefined };
  if (envUrl) return { state: 'available', url: envUrl, file, source: 'env' };
  if (file.downloadUrl !== '') {
    return { state: 'available', url: file.downloadUrl, file, source: 'manifest' };
  }
  return { state: 'pending', file };
}

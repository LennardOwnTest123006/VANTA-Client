import { env } from '../lib/env';
import { latestRelease, releases } from '../lib/releases';

/**
 * Product facts shown across the website. Versions and toolchain values come from the release
 * manifests in `shared/releases/` (the single source of truth); the constants below are the
 * fallbacks for a checkout without manifests and must match `core/.../VantaVersion.java`.
 */
const clientRelease = latestRelease(releases, 'client');
const launcherRelease = latestRelease(releases, 'launcher');

export const site = {
  name: 'VANTA Client',
  shortName: 'VANTA',
  tagline: 'Your Minecraft. Refined.',
  description:
    'A modern Fabric client focused on performance, customization and a clean Minecraft experience.',
  /** Appended to every page title. */
  titleSuffix: 'VANTA Client',

  clientVersion: clientRelease?.version ?? '1.0.0',
  launcherVersion: launcherRelease?.version ?? '1.0.0',
  minecraft: clientRelease?.minecraftVersion ?? '1.21.11',
  fabricLoader: clientRelease?.fabricVersion ?? '0.19.5',
  fabricApi: clientRelease?.fabricApiVersion ?? '0.141.6+1.21.11',
  java: clientRelease?.javaVersion ?? 21,
  platform: 'Windows 10/11',
  /**
   * Other platforms with launcher builds; not the primary target. Each launcher jar contains JavaFX
   * for the one platform it was built on, so there is no single jar for every system.
   */
  secondaryPlatforms:
    'Linux x64 and Apple Silicon macOS (per-platform builds, no support guarantees)',

  license: 'MIT',
  githubUrl: env.githubUrl,
  discordUrl: env.discordUrl,
  supportEmail: env.supportEmail,
  releasesBaseUrl: env.releasesBaseUrl,
} as const;

/**
 * Name of the profile that "Use with Minecraft Launcher" adds to the official Minecraft Launcher
 * (profile key `vanta-1.21.11` in launcher_profiles.json).
 */
export const officialLauncherProfileName = `VANTA ${site.minecraft}`;

/** "Minecraft 1.21.11 · Fabric Loader 0.19.5 · Java 21" */
export const toolchainLine = `Minecraft ${site.minecraft} · Fabric Loader ${site.fabricLoader} · Java ${site.java}`;

/** Spec strip facts used by the hero and the download page. */
export const specFacts = [
  { label: 'Minecraft', value: site.minecraft },
  { label: 'Fabric Loader', value: site.fabricLoader },
  { label: 'Java', value: String(site.java) },
  { label: 'Platform', value: site.platform },
] as const;

/** GitHub deep links derived from the repository URL (undefined when GitHub is not configured). */
export const githubLinks = site.githubUrl
  ? {
      repository: site.githubUrl,
      issues: `${site.githubUrl}/issues`,
      releases: `${site.githubUrl}/releases`,
      license: `${site.githubUrl}/blob/HEAD/LICENSE`,
      contributing: `${site.githubUrl}/blob/HEAD/CONTRIBUTING.md`,
    }
  : undefined;

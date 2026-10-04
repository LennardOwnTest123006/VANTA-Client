/**
 * Typed, validated access to the build-time environment (`import.meta.env`).
 *
 * Every variable is optional. URLs are only accepted when they parse as `http(s)` URLs, e-mail
 * addresses only when they contain a single `@`; everything else is treated as "not configured" so the
 * UI can render honest fallbacks instead of broken links.
 */
export interface SiteEnv {
  /** Direct download URL for the launcher installer. Overrides the release manifest when set. */
  readonly downloadLauncherUrl: string | undefined;
  /** Direct download URL for the client jar. Overrides the release manifest when set. */
  readonly downloadClientUrl: string | undefined;
  /** Base URL where release manifests are published (no trailing slash). */
  readonly releasesBaseUrl: string | undefined;
  /** Support e-mail address shown in the footer and the support page. */
  readonly supportEmail: string | undefined;
  /** Community Discord invite URL. */
  readonly discordUrl: string | undefined;
  /** Source repository URL. Defaults to the public GitHub repository. */
  readonly githubUrl: string | undefined;
  /** Public origin of the deployed website (for canonical URLs, Open Graph and the sitemap). */
  readonly siteUrl: string | undefined;
}

/** The default source repository used when `VITE_GITHUB_URL` is not set. */
export const DEFAULT_GITHUB_URL = 'https://github.com/LennardOwnTest123006/VANTA-Client';

/** Raw environment shape as provided by Vite (values can be strings, booleans or undefined). */
export type RawEnv = Readonly<Record<string, string | boolean | undefined>>;

/** Returns the trimmed string when it is a non-empty `http(s)` URL, otherwise `undefined`. */
export function sanitizeHttpUrl(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;
  try {
    const url = new URL(trimmed);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return undefined;
    return url.toString().replace(/\/$/, trimmed.endsWith('/') ? '/' : '');
  } catch {
    return undefined;
  }
}

/** Returns the trimmed string when it looks like a single e-mail address, otherwise `undefined`. */
export function sanitizeEmail(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return undefined;
  return trimmed;
}

/** Builds a {@link SiteEnv} from a raw environment object. Exposed for tests. */
export function readEnv(source: RawEnv): SiteEnv {
  const releasesBase = sanitizeHttpUrl(source.VITE_RELEASES_BASE_URL);
  return {
    downloadLauncherUrl: sanitizeHttpUrl(source.VITE_DOWNLOAD_LAUNCHER_URL),
    downloadClientUrl: sanitizeHttpUrl(source.VITE_DOWNLOAD_CLIENT_URL),
    releasesBaseUrl: releasesBase?.replace(/\/+$/, ''),
    supportEmail: sanitizeEmail(source.VITE_SUPPORT_EMAIL),
    discordUrl: sanitizeHttpUrl(source.VITE_DISCORD_URL),
    githubUrl:
      source.VITE_GITHUB_URL === undefined
        ? DEFAULT_GITHUB_URL
        : sanitizeHttpUrl(source.VITE_GITHUB_URL),
    siteUrl: sanitizeHttpUrl(source.VITE_SITE_URL)?.replace(/\/+$/, ''),
  };
}

/** The environment of the current build. */
export const env: SiteEnv = readEnv(import.meta.env);

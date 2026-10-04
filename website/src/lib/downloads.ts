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

/** File extensions preferred per product, in order. */
export const PREFERRED_EXTENSIONS = {
  launcher: ['.msi', '.exe', '.jar'],
  client: ['.jar'],
} as const satisfies Record<ReleaseManifest['product'], readonly string[]>;

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
  const file = pickFile(manifest, PREFERRED_EXTENSIONS[manifest.product]);
  if (!file) return { state: 'missing' };
  if (envUrl) return { state: 'available', url: envUrl, file, source: 'env' };
  if (file.downloadUrl !== '') {
    return { state: 'available', url: file.downloadUrl, file, source: 'manifest' };
  }
  return { state: 'pending', file };
}

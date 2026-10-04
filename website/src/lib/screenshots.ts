/**
 * Loader for the real screenshots in `assets/screenshots/*.png` and their captions.
 *
 * The folder is empty until a maintainer promotes captures from the automated game test (see
 * `assets/screenshots/README.md`). The website lists exactly what is there — nothing is faked —
 * and `captions.json` (optional) supplies caption, alt text and capture information per file.
 */

export interface ScreenshotCaption {
  readonly caption?: string;
  readonly alt?: string;
  readonly capturedWith?: string;
}

export interface Screenshot {
  /** File name, e.g. `01_main_menu.png`. */
  readonly file: string;
  /** Resolved asset URL. */
  readonly src: string;
  readonly caption: string;
  readonly alt: string;
  readonly capturedWith: string | undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Turns `01_main_menu.png` into "Main menu" for files without a caption entry. */
export function captionFromFileName(file: string): string {
  const base = file
    .replace(/\.[a-z0-9]+$/i, '')
    .replace(/^\d+[_-]?/, '')
    .replace(/[_-]+/g, ' ')
    .trim();
  return base === '' ? file : base.charAt(0).toUpperCase() + base.slice(1);
}

/** Reads `captions.json` defensively: unknown shapes yield no captions. */
export function parseCaptions(input: unknown): Readonly<Record<string, ScreenshotCaption>> {
  if (!isRecord(input)) return {};
  const captions: Record<string, ScreenshotCaption> = {};
  for (const [file, value] of Object.entries(input)) {
    if (!isRecord(value)) continue;
    const entry: { caption?: string; alt?: string; capturedWith?: string } = {};
    if (typeof value.caption === 'string') entry.caption = value.caption;
    if (typeof value.alt === 'string') entry.alt = value.alt;
    if (typeof value.capturedWith === 'string') entry.capturedWith = value.capturedWith;
    captions[file] = entry;
  }
  return captions;
}

/** Combines image URLs (path → url) with captions, sorted by file name. */
export function loadScreenshots(
  images: Readonly<Record<string, string>>,
  captions: Readonly<Record<string, ScreenshotCaption>>,
): Screenshot[] {
  return Object.entries(images)
    .map(([path, src]) => {
      const file = path.split('/').pop() ?? path;
      const caption = captions[file];
      const text = caption?.caption ?? captionFromFileName(file);
      return {
        file,
        src,
        caption: text,
        alt: caption?.alt ?? text,
        capturedWith: caption?.capturedWith,
      };
    })
    .sort((a, b) => a.file.localeCompare(b.file));
}

const imageModules = import.meta.glob<string>('../../../assets/screenshots/*.png', {
  eager: true,
  import: 'default',
});

const captionModules = import.meta.glob<unknown>('../../../assets/screenshots/captions.json', {
  eager: true,
  import: 'default',
});

/** Every real screenshot of the repository, in file order (empty before the first release). */
export const screenshots: readonly Screenshot[] = loadScreenshots(
  imageModules,
  parseCaptions(Object.values(captionModules)[0]),
);

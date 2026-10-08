import { Download, ExternalLink } from 'lucide-react';
import { describeReleaseFile } from '../../lib/downloads';
import { formatBytes, groupHash } from '../../lib/format';
import { type ReleaseFile, type ReleaseManifest, releasePageUrl } from '../../lib/releases';
import { Button } from '../ui/Button';
import { CopyButton } from '../ui/CopyButton';
import { Tag } from '../ui/Pill';

export interface ReleaseFileListProps {
  readonly manifest: ReleaseManifest;
  /** Prefix for element ids, unique per card (e.g. `download-launcher`). */
  readonly idPrefix: string;
  /** Name of the file the card's main button offers; marked as recommended in the list. */
  readonly primaryName?: string | undefined;
  /**
   * The files to list, in order; defaults to every file of the manifest. The download page splits a
   * launcher release into its Windows setup files and its cross-platform files, one list per card.
   */
  readonly files?: readonly ReleaseFile[] | undefined;
  /** Heading of the list; "All files in this release" when the whole manifest is listed. */
  readonly heading?: string | undefined;
}

function FileRow({ file, primary }: { file: ReleaseFile; primary: boolean }) {
  const info = describeReleaseFile(file.name);
  const size = formatBytes(file.size);
  const sha = file.sha256 !== '' ? file.sha256 : undefined;

  return (
    <li className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between sm:gap-5">
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3">
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="text-sm font-semibold text-text-primary">{info.label}</span>
            {primary ? <Tag className="text-[10px]">Recommended</Tag> : null}
          </p>
          <span className="shrink-0 pt-0.5 text-xs text-text-secondary tabular-nums">
            {size ?? <span className="text-text-muted">Size pending</span>}
          </span>
        </div>
        <p className="mt-1 font-mono text-[12px] leading-5 break-all text-text-secondary">
          {file.name}
        </p>
        {info.note ? (
          <p className="mt-1 text-xs leading-relaxed text-text-muted">{info.note}</p>
        ) : null}
        <div className="mt-2.5">
          <p className="text-[10px] font-semibold tracking-label text-text-muted uppercase">
            SHA-256
          </p>
          {sha ? (
            <div className="mt-1 flex items-start gap-2">
              <code className="min-w-0 flex-1 rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[11px] leading-[1.15rem] break-words text-text-primary">
                {groupHash(sha)}
              </code>
              <CopyButton
                value={sha}
                label={`Copy SHA-256 checksum of ${file.name}`}
                className="shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center"
              />
            </div>
          ) : (
            <p className="mt-1 text-xs text-text-muted">Published with the release</p>
          )}
        </div>
      </div>
      <div className="shrink-0 sm:pt-0.5">
        {file.downloadUrl !== '' ? (
          <Button
            href={file.downloadUrl}
            external={false}
            rel="noopener"
            download
            size="sm"
            variant="secondary"
            leadingIcon={<Download />}
            className="w-full sm:w-auto"
          >
            Download <span className="sr-only">{file.name}</span>
          </Button>
        ) : (
          <p className="text-xs font-medium text-warning">Not published yet</p>
        )}
      </div>
    </li>
  );
}

/**
 * The files of a release manifest (all of them, or the given subset) in manifest order: what each
 * one is (derived from the file name), size, SHA-256 with a copy button and a direct download link.
 * Files without a `downloadUrl` are listed but never linked. The GitHub release page is linked only
 * when it can be derived from a manifest URL.
 */
export function ReleaseFileList({
  manifest,
  idPrefix,
  primaryName,
  files = manifest.files,
  heading = 'All files in this release',
}: ReleaseFileListProps) {
  const headingId = `${idPrefix}-files`;
  const pageUrl = releasePageUrl(manifest);

  return (
    // A plain wrapper, not a region: several cards have this list, and duplicate landmarks confuse
    // screen reader navigation. The list itself is named by the heading.
    <div className="mt-6 border-t border-border-subtle pt-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <h3
          id={headingId}
          className="text-[11px] font-semibold tracking-label text-text-muted uppercase"
        >
          {heading}
        </h3>
        {pageUrl ? (
          <Button href={pageUrl} variant="link" trailingIcon={<ExternalLink />} className="text-sm">
            Release page on GitHub
          </Button>
        ) : null}
      </div>
      <ul
        aria-labelledby={headingId}
        className="mt-3 divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle bg-bg-void/40"
      >
        {files.map((file) => (
          <FileRow key={file.name} file={file} primary={file.name === primaryName} />
        ))}
      </ul>
      {pageUrl ? (
        <p className="mt-3 text-xs leading-relaxed text-text-muted">
          The release page also has SHA256SUMS.txt with every checksum of the release and the
          release manifest.
        </p>
      ) : null}
    </div>
  );
}

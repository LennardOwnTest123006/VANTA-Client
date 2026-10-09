import { ArrowRight, Cpu, ExternalLink, HardDrive, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router';
import { githubLinks } from '../../config/site';
import { formatBytes } from '../../lib/format';
import {
  type LocalAiManifest,
  isLocalAiResolved,
  localAiDownloadHosts,
  localAiModelLabel,
  localAiPlatformLabel,
  localAiRedirectTargets,
  localAiRuntimeLabel,
  localAiRuntimeSizeRange,
} from '../../lib/localAi';
import { Callout } from '../ui/Callout';
import { Section } from '../ui/Section';

export interface LocalAiNoteProps {
  /** The repository's Local AI manifest; the section is left out without one. */
  readonly manifest: LocalAiManifest | undefined;
}

const externalLink =
  'inline-flex items-center gap-1 text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 transition-colors hover:text-text-primary';

/** "1,834,426,016 bytes" */
function exactBytes(size: number): string {
  return `${new Intl.NumberFormat('en-US').format(size)} bytes`;
}

/**
 * The Local AI note of the download page: what Vanta Nexus downloads when the player installs the
 * Local AI, from which hosts, how big each file is, that nothing of it is in the release files above,
 * and that it is optional and asks first. Every name, size, host and licence comes from the Local AI
 * manifest the client and the launcher embed; while the manifest is unresolved the sizes are not
 * claimed.
 */
export function LocalAiNote({ manifest }: LocalAiNoteProps) {
  if (!manifest) return null;
  const resolved = isLocalAiResolved(manifest);
  const hosts = localAiDownloadHosts(manifest);
  const redirects = localAiRedirectTargets(manifest);
  const range = localAiRuntimeSizeRange(manifest);
  const modelSize = formatBytes(manifest.model.size);
  const manifestUrl = githubLinks
    ? `${githubLinks.repository}/blob/HEAD/shared/local-ai/local-ai.json`
    : undefined;
  const runtimeHosts = [...new Set(manifest.runtime.platforms.map((p) => new URL(p.url).hostname))];
  const modelHost = new URL(manifest.model.url).hostname;

  return (
    <Section
      id="local-ai"
      eyebrow="Local AI"
      title="Optional, and it asks first."
      lead="From VANTA Client 1.4.0 and VANTA Launcher 1.4.0 on, Vanta Nexus can run an assistant on your own PC. It needs two downloads that are in none of the files above. They happen once, only after you agree, and the assistant works offline afterwards."
      className="border-t border-border-subtle bg-bg-void/40"
      spacing="md"
    >
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="surface-card min-w-0 p-6 sm:p-7">
          <p className="eyebrow">What is downloaded</p>
          <dl className="mt-4 flex flex-col gap-4 text-sm">
            <div>
              <dt className="font-semibold text-text-primary">
                Runtime: {localAiRuntimeLabel(manifest)} ({manifest.runtime.component})
              </dt>
              <dd className="mt-1 leading-relaxed text-text-secondary">
                One archive for your system from {runtimeHosts.join(' and ')}, licence{' '}
                {manifest.runtime.license}
                {range && resolved ? (
                  <>
                    : {formatBytes(range.smallest.size)} ({localAiPlatformLabel(range.smallest.key)}
                    ) to {formatBytes(range.largest.size)} (
                    {localAiPlatformLabel(range.largest.key)}).
                  </>
                ) : (
                  <>. Its size is recorded in the manifest once the resolve workflow has run.</>
                )}{' '}
                <a
                  href={manifest.runtime.releaseUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={externalLink}
                >
                  Release {manifest.runtime.tag} on GitHub
                  <ExternalLink className="size-3.5" aria-hidden="true" />
                </a>
              </dd>
            </div>
            <div>
              <dt className="font-semibold text-text-primary">
                Model: {localAiModelLabel(manifest)} ({manifest.model.file})
              </dt>
              <dd className="mt-1 leading-relaxed text-text-secondary">
                From {modelHost}, licence {manifest.model.license}
                {modelSize && resolved ? (
                  <>
                    : {modelSize} ({exactBytes(manifest.model.size)}).
                  </>
                ) : (
                  <>. Its size is recorded in the manifest once the resolve workflow has run.</>
                )}{' '}
                <a
                  href={manifest.model.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={externalLink}
                >
                  Model page
                  <ExternalLink className="size-3.5" aria-hidden="true" />
                </a>
              </dd>
            </div>
          </dl>
          <ul
            className="mt-5 divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle bg-bg-void/40 text-sm"
            aria-label="Runtime archive per system"
          >
            {manifest.runtime.platforms.map((platform) => (
              <li
                key={platform.key}
                className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-4 py-2.5"
              >
                <span className="text-text-primary">{localAiPlatformLabel(platform.key)}</span>
                <span className="font-mono text-[12px] break-all text-text-secondary">
                  {platform.file}
                </span>
                <span className="text-xs text-text-muted tabular-nums">
                  {formatBytes(platform.size) ?? 'size not resolved yet'}
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs leading-relaxed text-text-muted">
            Other systems (for example 32-bit Windows) get an honest "Local AI is not available on
            this system" state; the rest of VANTA works as before.
          </p>
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <div className="surface-card p-6 sm:p-7">
            <p className="eyebrow">How you get it, and how you do not</p>
            <ul className="mt-4 flex flex-col gap-3 text-sm leading-relaxed text-text-secondary">
              <li className="flex gap-3">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                <span>
                  Nothing downloads on its own. The VANTA Launcher asks once at its first start
                  (what, from where, how big, licences); "Not now" keeps the automatic install off.
                  In the game, Vanta Nexus shows the same facts on an install card and downloads
                  only after you click Install Local AI.
                </span>
              </li>
              <li className="flex gap-3">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                <span>
                  Both files are checked against the size and SHA-256 in the Local AI manifest the
                  client and the launcher ship; a file that does not match is deleted, never run.
                  Verify files, Reinstall and Remove Local AI are in Nexus and in the launcher's
                  Settings. Remove deletes only the Local AI folder.
                </span>
              </li>
              <li className="flex gap-3">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                <span>
                  After the download the assistant works offline. The client starts{' '}
                  {manifest.runtime.component} on 127.0.0.1 and your questions go there only: no
                  cloud AI, no API key, no account, no telemetry. The hosts above,{' '}
                  {hosts.join(' and ')}, are contacted for this download only
                  {redirects.length > 0
                    ? `; each answers with a redirect to its own file host, which the client follows (${redirects.join(', ')})`
                    : ''}
                  . Besides those, the client contacts Modrinth while you use Mods &amp; Shaders,
                  and nothing else, ever.
                </span>
              </li>
            </ul>
          </div>
          <Callout tone="info" title="What it needs" icon={<HardDrive />}>
            <p>
              About {manifest.requirements.diskMb} MB of disk space for the files and{' '}
              {manifest.requirements.ramMb} MB of RAM while the assistant runs; it uses the CPU, not
              the graphics card, and stops itself after 10 minutes without a question (a setting).
              The launcher keeps it in its own folder (
              <code className="font-mono text-[12px]">local-ai</code> in the launcher data
              directory); a client without the launcher keeps it in{' '}
              <code className="font-mono text-[12px]">config/vanta/local-ai</code>.
            </p>
          </Callout>
          <p className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
            <Link to="/features#local-ai" className={externalLink}>
              What the Local AI is and is not <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
            <Link to="/privacy" className={externalLink}>
              Privacy <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
            {manifestUrl ? (
              <a
                href={manifestUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={externalLink}
              >
                <Cpu className="size-3.5" aria-hidden="true" /> The Local AI manifest
                <ExternalLink className="size-3.5" aria-hidden="true" />
              </a>
            ) : null}
          </p>
        </div>
      </div>
    </Section>
  );
}

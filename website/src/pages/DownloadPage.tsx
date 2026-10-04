import {
  BookOpen,
  ExternalLink,
  History,
  Monitor,
  Package,
  ShieldCheck,
  Terminal,
  UserRound,
  Coffee,
  Wrench,
} from 'lucide-react';
import { DownloadCard } from '../components/download/DownloadCard';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Callout } from '../components/ui/Callout';
import { Card } from '../components/ui/Card';
import { RouteLink } from '../components/ui/RouteLink';
import { Section } from '../components/ui/Section';
import { Stat, StatGroup } from '../components/ui/Stat';
import { githubLinks, site, specFacts } from '../config/site';
import { resolveDownload } from '../lib/downloads';
import { env } from '../lib/env';
import { latestRelease, releases } from '../lib/releases';

const requirements = [
  {
    label: 'Operating system',
    value: 'Windows 10 or 11 (64-bit)',
    note: 'Linux and macOS run the plain Java builds; installers are Windows only.',
  },
  {
    label: 'Java',
    value: `Java ${site.java}`,
    note: 'Detected automatically, or the launcher installs Eclipse Temurin 21.',
  },
  {
    label: 'Memory',
    value: '4 GB allocated',
    note: 'Vanilla 1.21.11 recommendation; 8 GB system memory or more.',
  },
  {
    label: 'Graphics',
    value: 'OpenGL 3.2',
    note: 'Any GPU that runs vanilla Minecraft 1.21.11 runs VANTA.',
  },
  {
    label: 'Disk space',
    value: 'About 1 GB',
    note: 'Minecraft, Fabric, libraries, assets and the Java runtime.',
  },
  {
    label: 'Account',
    value: 'Microsoft account owning Minecraft Java Edition',
    note: 'Required by Mojang; VANTA has no account of its own.',
  },
] as const;

const verification = [
  {
    os: 'Windows (PowerShell or Command Prompt)',
    command: 'certutil -hashfile "VANTA-Launcher-{version}.msi" SHA256',
  },
  {
    os: 'macOS / Linux',
    command: 'shasum -a 256 vanta-client-{version}.jar',
  },
] as const;

/** Download center: launcher and client cards from the release manifests, requirements, verification. */
export default function DownloadPage() {
  const launcher = latestRelease(releases, 'launcher');
  const client = latestRelease(releases, 'client');
  const launcherDownload = resolveDownload(launcher, env.downloadLauncherUrl);
  const clientDownload = resolveDownload(client, env.downloadClientUrl);

  return (
    <>
      <PageMeta
        title="Download"
        description={`Download VANTA Launcher and VANTA Client for Minecraft ${site.minecraft} (Fabric Loader ${site.fabricLoader}, Java ${site.java}, ${site.platform}). Every file comes with a SHA-256 checksum.`}
      />
      <PageHero
        eyebrow="Download"
        title={
          <>
            {site.name} for Minecraft <span className="text-gradient">{site.minecraft}</span>
          </>
        }
        lead="Install the launcher for the full experience, or add the client jar to a Fabric profile you already have. Every file is listed with its size and SHA-256 checksum from the release manifest."
      >
        <StatGroup
          className="grid-cols-2 gap-x-6 sm:grid-cols-4"
          aria-label="Technical specifications"
        >
          {specFacts.map((fact) => (
            <Stat key={fact.label} label={fact.label} value={fact.value} size="sm" />
          ))}
        </StatGroup>
      </PageHero>

      <Section id="downloads" spacing="sm" aria-label="Downloads">
        <div className="grid gap-6 lg:grid-cols-2">
          <DownloadCard
            manifest={launcher}
            resolution={launcherDownload}
            icon={Monitor}
            eyebrow="Launcher"
            title="VANTA Launcher"
            description={`Installs Minecraft ${site.minecraft}, Fabric Loader ${site.fabricLoader}, Fabric API and the VANTA Client with checksum verification, signs you in with Microsoft and starts the game. Windows installer (.msi).`}
            cta="Download launcher"
            primary
            footnote="Also available as .exe installer and portable .jar in the release assets."
          />
          <DownloadCard
            manifest={client}
            resolution={clientDownload}
            icon={Package}
            eyebrow="Client"
            title="VANTA Client (jar)"
            description={`The Fabric mod on its own, for players who already run Fabric Loader ${site.fabricLoader} on Minecraft ${site.minecraft}. Drop it into your mods folder next to Fabric API ${site.fabricApi}.`}
            cta="Download client jar"
            footnote={`Requires Fabric API ${site.fabricApi}. Other mods such as rendering optimisers can be installed alongside.`}
          />
        </div>
        {site.releasesBaseUrl || githubLinks ? (
          <div className="mt-6 flex flex-wrap items-center gap-3 text-sm text-text-secondary">
            <span>Looking for older versions or checksum files?</span>
            {githubLinks ? (
              <Button href={githubLinks.releases} variant="link" trailingIcon={<ExternalLink />}>
                All releases on GitHub
              </Button>
            ) : null}
            {site.releasesBaseUrl ? (
              <Button href={site.releasesBaseUrl} variant="link" trailingIcon={<ExternalLink />}>
                Release manifests
              </Button>
            ) : null}
          </div>
        ) : null}
      </Section>

      <Section
        id="what-you-need"
        eyebrow="What you need"
        title="Three things, two of which you probably have."
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        <div className="grid gap-4 lg:grid-cols-3">
          <Card
            icon={<UserRound />}
            title="A Microsoft account that owns Minecraft Java Edition"
            padding="sm"
          >
            <p>
              The launcher signs you in through Microsoft's device code flow in your browser and
              checks the game entitlement exactly like the official launcher. There is no VANTA
              account and no offline mode without a verified purchase.
            </p>
          </Card>
          <Card icon={<Coffee />} title={`Java ${site.java}`} padding="sm">
            <p>
              Minecraft {site.minecraft} requires Java {site.java}. The launcher detects an existing
              installation; if none is found it downloads Eclipse Temurin {site.java} from Adoptium,
              verifies the SHA-256 and installs it only for VANTA.
            </p>
          </Card>
          <Card icon={<Monitor />} title={site.platform} padding="sm">
            <p>
              The installer targets 64-bit Windows 10 and 11. The client jar and the launcher jar
              are plain Java and also run on Linux and macOS, without an installer or support
              guarantees.
            </p>
          </Card>
        </div>
      </Section>

      <Section
        id="requirements"
        eyebrow="System requirements"
        title="If it runs vanilla 1.21.11, it runs VANTA."
        lead="VANTA adds a UI layer on top of the game. It does not raise the hardware requirements of Minecraft itself."
        spacing="md"
      >
        <dl className="surface-card grid gap-px overflow-hidden bg-border-subtle sm:grid-cols-2 lg:grid-cols-3">
          {requirements.map((item) => (
            <div key={item.label} className="flex flex-col gap-1 bg-surface-1 p-5">
              <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
                {item.label}
              </dt>
              <dd className="font-display text-base font-semibold text-text-primary">
                {item.value}
              </dd>
              <dd className="text-xs leading-relaxed text-text-muted">{item.note}</dd>
            </div>
          ))}
        </dl>
      </Section>

      <Section
        id="verify"
        eyebrow="Verify your download"
        title="Check the SHA-256 before you install."
        lead="Compare the output of one command with the checksum shown on the card above. If they differ, delete the file and download it again — do not run it."
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        <div className="grid gap-4 lg:grid-cols-2">
          {verification.map((item) => (
            <div key={item.os} className="surface-card p-5 sm:p-6">
              <p className="flex items-center gap-2 text-[11px] font-semibold tracking-label text-text-muted uppercase">
                <Terminal className="size-3.5" aria-hidden="true" /> {item.os}
              </p>
              <pre
                tabIndex={0}
                className="mt-3 overflow-x-auto rounded-lg border border-border-subtle bg-bg-void p-4 text-[13px] leading-relaxed text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
              >
                <code>
                  {item.command.replace(
                    '{version}',
                    item.command.includes('Launcher') ? site.launcherVersion : site.clientVersion,
                  )}
                </code>
              </pre>
            </div>
          ))}
        </div>
        <Callout
          tone="success"
          title="The launcher verifies for you"
          icon={<ShieldCheck />}
          className="mt-6"
        >
          <p>
            Every file the launcher downloads — Minecraft, libraries, Fabric, Fabric API, the VANTA
            jar and the Java runtime — is checked against the SHA-1 or SHA-256 published by its
            source before it is used. Files that fail verification are deleted, never executed.
          </p>
        </Callout>
        <div className="mt-10 grid gap-4 lg:grid-cols-3" aria-label="After downloading">
          <Card
            icon={<BookOpen />}
            title="Installation guide"
            to="/documentation/installation"
            padding="sm"
          >
            <p>
              Verify the checksum, install the launcher, sign in and press PLAY — step by step, with
              the folders where everything ends up.
            </p>
          </Card>
          <Card icon={<History />} title="Release notes" to="/changelog" padding="sm">
            <p>
              What each version of the client and the launcher added, improved and fixed, with the
              Minecraft version it targets.
            </p>
          </Card>
          <Card
            icon={<Wrench />}
            title="Troubleshooting"
            to="/documentation/troubleshooting"
            padding="sm"
          >
            <p>
              Java not found, checksum mismatch, Microsoft sign-in errors, crashes on start and
              where the logs are.
            </p>
          </Card>
        </div>
        {githubLinks ? (
          <p className="mt-6 text-sm text-text-secondary">
            Still stuck? Read the{' '}
            <a
              href={githubLinks.issues}
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
            >
              open issues on GitHub
            </a>{' '}
            or visit the <RouteLink to="/support">support page</RouteLink>.
          </p>
        ) : null}
      </Section>
    </>
  );
}

import {
  ArrowDown,
  BookOpen,
  ExternalLink,
  FileArchive,
  History,
  Layers,
  Monitor,
  Package,
  ShieldCheck,
  Terminal,
  UserRound,
  Coffee,
  Wrench,
} from 'lucide-react';
import { BundleCard } from '../components/download/BundleCard';
import { DownloadCard } from '../components/download/DownloadCard';
import { InstallOptions } from '../components/download/InstallOptions';
import { LatestVersion } from '../components/download/LatestVersion';
import { LocalAiNote } from '../components/download/LocalAiNote';
import { OlderVersions } from '../components/download/OlderVersions';
import { ReleaseFilesCard } from '../components/download/ReleaseFilesCard';
import { WhatsNew } from '../components/download/WhatsNew';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { TocNav } from '../components/page/TocNav';
import { Button } from '../components/ui/Button';
import { Callout } from '../components/ui/Callout';
import { Card } from '../components/ui/Card';
import { RouteLink } from '../components/ui/RouteLink';
import { Section } from '../components/ui/Section';
import { Stat, StatGroup } from '../components/ui/Stat';
import { githubLinks, officialLauncherProfileName, site, specFacts } from '../config/site';
import { bundles, latestBundle, olderBundles } from '../lib/bundles';
import {
  launcherCrossPlatformFiles,
  launcherSetupFiles,
  modsBundleFile,
  resolveDownload,
} from '../lib/downloads';
import { env } from '../lib/env';
import { localAi } from '../lib/localAi';
import { latestRelease, olderReleases, releases, upcomingRelease } from '../lib/releases';

/** System requirements of the game; the Local AI row is added from the manifest when it exists. */
const baseRequirements = [
  {
    label: 'Operating system',
    value: 'Windows 10 or 11 (64-bit)',
    note: 'Installers are Windows only. Linux x64 and Apple Silicon macOS get their own launcher builds, without support guarantees.',
  },
  {
    label: 'Java',
    value: `Java ${site.java}`,
    note: `Included in the launcher installer and portable apps. For the game the launcher finds Java ${site.java} or installs Eclipse Temurin ${site.java}.`,
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

const requirements = localAi
  ? [
      ...baseRequirements,
      {
        label: 'Local AI (optional)',
        value: `${localAi.requirements.diskMb} MB disk, ${localAi.requirements.ramMb} MB RAM`,
        note: 'Only when you install the Local AI of Vanta Nexus (client and launcher 1.4.0 on): the runtime and the model on disk, and the RAM while the assistant runs. Values from the Local AI manifest.',
      },
    ]
  : [...baseRequirements];

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

/**
 * Download center in three clearly labelled sections: WINDOWS (the launcher setup, with the `.msi`
 * from the newest published launcher manifest), CROSS-PLATFORM (the client jar for Windows, Linux
 * and macOS plus the launcher jars and the Linux app) and COMPLETE RELEASE (the full release zip
 * from the newest published bundle manifest; no card at all while none is published). Then the
 * Local AI note, what is new in the offered versions, requirements, verification and, at the end,
 * every older published version. Each card offers the newest published release of its product; a
 * newer version that is committed but not published yet is mentioned on the card instead of
 * replacing the working downloads.
 */
export default function DownloadPage() {
  const launcher = latestRelease(releases, 'launcher');
  const client = latestRelease(releases, 'client');
  const launcherDownload = resolveDownload(launcher, env.downloadLauncherUrl);
  const clientDownload = resolveDownload(client, env.downloadClientUrl);
  const modsBundle = modsBundleFile(client);
  const bundle = latestBundle(bundles);
  const olderLaunchers = olderReleases(releases, 'launcher');
  const olderClients = olderReleases(releases, 'client');
  const olderZips = olderBundles(bundles);
  const hasOlderVersions =
    olderLaunchers.length > 0 || olderClients.length > 0 || olderZips.length > 0;
  const setupFiles = launcher ? launcherSetupFiles(launcher) : [];
  const crossPlatformFiles = launcher ? launcherCrossPlatformFiles(launcher) : [];
  const hasExe = setupFiles.some((file) => file.name.toLowerCase().endsWith('.exe'));
  const toc = [
    { id: 'windows', label: 'Windows' },
    { id: 'cross-platform', label: 'Cross-platform' },
    { id: 'complete-release', label: 'Complete release' },
    ...(localAi ? [{ id: 'local-ai', label: 'Local AI' }] : []),
    { id: 'requirements', label: 'Requirements' },
    { id: 'verify', label: 'Verify' },
    ...(hasOlderVersions ? [{ id: 'older-versions', label: 'Older versions' }] : []),
  ];

  return (
    <>
      <PageMeta
        title="Download"
        description={`Download VANTA Launcher and VANTA Client for Minecraft ${site.minecraft} (Fabric Loader ${site.fabricLoader}, Java ${site.java}): the Windows installer, the client jar and launcher jars for Windows, Linux and macOS${bundle ? ', or everything in one full release zip' : ''}. Every file comes with a SHA-256 checksum.`}
      />
      <PageHero
        eyebrow="Download"
        title={
          <>
            {site.name} for Minecraft <span className="text-gradient">{site.minecraft}</span>
          </>
        }
        lead="Install the launcher on Windows for the full experience, add the client jar to a Fabric profile on any system, or take the complete release in one zip. Every file is listed with its size and SHA-256 checksum from the release manifest."
      >
        <LatestVersion client={client} launcher={launcher} />
        <StatGroup
          className="grid-cols-2 gap-x-6 sm:grid-cols-4"
          aria-label="Technical specifications"
        >
          {specFacts.map((fact) => (
            <Stat key={fact.label} label={fact.label} value={fact.value} size="sm" />
          ))}
        </StatGroup>
        <TocNav items={toc} className="mt-8" />
      </PageHero>

      <div id="downloads">
        <Section
          id="windows"
          eyebrow="Windows"
          title="Launcher Setup for Windows."
          lead={`The VANTA Launcher installer for 64-bit Windows 10 and 11, with Java ${site.java} included. It installs Minecraft ${site.minecraft}, Fabric and the VANTA Client for you and keeps them up to date.`}
          className="scroll-mt-24"
          spacing="sm"
        >
          <DownloadCard
            manifest={launcher}
            resolution={launcherDownload}
            icon={Monitor}
            eyebrow="Launcher"
            title="VANTA Launcher"
            description={`Installs Minecraft ${site.minecraft}, Fabric Loader ${site.fabricLoader}, Fabric API and the VANTA Client with checksum verification. Until Microsoft sign-in is available inside VANTA, it adds the profile “${officialLauncherProfileName}” to the official Minecraft Launcher, which signs you in and starts the game. From launcher 1.1.0 on it also installs the Performance pack from Modrinth by default and has a Mods page; from launcher 1.4.0 on it offers the optional Local AI for Vanta Nexus once and downloads it only after you agree (see the Local AI note below). Windows installer (.msi) with Java ${site.java} included.`}
            cta="Download launcher"
            upcoming={upcomingRelease(releases, 'launcher')}
            primary
            files={setupFiles}
            filesHeading="Windows files in this release"
            footnote={`${hasExe ? 'Also published: the same installer as .exe (releases before 1.4.0 only) and a' : 'Also published: a'} portable Windows app that includes Java ${site.java}. The launcher jars for Windows x64, Linux x64 and Apple Silicon macOS and the Linux app are listed under Cross-platform below. Nothing is code-signed yet, so check the SHA-256 first.`}
          />
        </Section>

        <Section
          id="cross-platform"
          eyebrow="Cross-platform"
          title="The client jar for Windows, Linux and macOS, and the launcher jars."
          lead={`The VANTA Client is a Fabric mod: the same jar on every system, for Minecraft ${site.minecraft} with Fabric Loader ${site.fabricLoader} and Fabric API ${site.fabricApi}. The VANTA Launcher comes as one jar per system (each needs Java ${site.java} installed) and as a Linux app with Java included.`}
          className="scroll-mt-24 border-t border-border-subtle"
          spacing="sm"
        >
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <DownloadCard
              manifest={client}
              resolution={clientDownload}
              icon={Package}
              eyebrow="Client"
              title="VANTA Client (jar)"
              description={`The Fabric mod for Minecraft ${site.minecraft} with Fabric Loader ${site.fabricLoader}, for Windows, Linux and macOS alike. It needs Fabric API ${site.fabricApi}, which is included in the mods bundle and also published as its own file. From client 1.2.0 on the mods bundle also holds the Performance pack mods that may be redistributed (all but EntityCulling, which the game offers in one click).`}
              cta="Download client jar"
              upcoming={upcomingRelease(releases, 'client')}
              secondaryDownload={{ file: modsBundle, label: 'Download mods bundle' }}
              footnote={`Requires Fabric API ${site.fabricApi}. Other mods such as rendering optimisers can be installed alongside.`}
            >
              <InstallOptions idPrefix="download-client" />
            </DownloadCard>
            <ReleaseFilesCard
              manifest={launcher}
              files={crossPlatformFiles}
              icon={Layers}
              eyebrow="Launcher"
              title="VANTA Launcher jars and Linux app"
              idPrefix="download-launcher-jars"
              description={`The same launcher as above for Linux x64, Apple Silicon macOS and Windows x64 without the installer: one jar per system, started with java -jar and an installed Java ${site.java}, plus the Linux app that brings Java ${site.java} along. Pick the file with your system in its name.`}
              filesHeading="Launcher files for Linux, macOS and Java in this release"
              footnote="Each jar runs only on the system it was built for: it contains the JavaFX libraries of that one system. The macOS jar is unsigned and its window is not tested yet. No support guarantees outside Windows."
            />
          </div>
        </Section>

        <Section
          id="complete-release"
          eyebrow="Complete release"
          title="Everything in one zip."
          lead="One archive with every published file of a client release and a launcher release, the documentation, the release notes and the checksums. Built by the bundle workflow from the files already published, never rebuilt."
          className="scroll-mt-24 border-t border-border-subtle"
          spacing="sm"
        >
          {bundle ? (
            <BundleCard
              bundle={bundle}
              clientVersion={client?.version}
              launcherVersion={launcher?.version}
            />
          ) : (
            <p
              role="status"
              className="surface-card flex items-start gap-3 p-6 text-sm leading-relaxed text-text-secondary sm:p-7"
            >
              <FileArchive className="mt-0.5 size-4 shrink-0 text-text-muted" aria-hidden="true" />
              <span>
                No full release zip is published at the moment. The card appears here as soon as the
                bundle workflow has published one and recorded its size and SHA-256 in a bundle
                manifest; until then the single files above are the downloads.
              </span>
            </p>
          )}
          {hasOlderVersions || site.releasesBaseUrl || githubLinks ? (
            <div className="mt-6 flex flex-wrap items-center gap-3 text-sm text-text-secondary">
              <span>Looking for older versions or checksum files?</span>
              {hasOlderVersions ? (
                <Button
                  href="#older-versions"
                  variant="link"
                  trailingIcon={<ArrowDown />}
                  className="text-sm"
                >
                  Older versions on this page
                </Button>
              ) : null}
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
      </div>

      <LocalAiNote manifest={localAi} />

      <WhatsNew client={client} launcher={launcher} />

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
              Minecraft needs it whichever way you play. Signing in inside the VANTA Launcher needs
              a Microsoft application id approved by Mojang, which the project does not have yet, so
              “PLAY via Minecraft Launcher” (“Use with Minecraft Launcher” in launcher 1.0.x) adds a
              VANTA profile to the official Minecraft Launcher and you sign in there. There is no
              VANTA account.
            </p>
          </Card>
          <Card icon={<Coffee />} title={`Java ${site.java}`} padding="sm">
            <p>
              Minecraft {site.minecraft} requires Java {site.java}. The launcher installer and the
              portable apps bring their own. For the game the launcher detects an existing
              installation or downloads Eclipse Temurin {site.java} from Adoptium, verifies the
              SHA-256 and installs it only for VANTA; the Minecraft Launcher uses its own runtime.
            </p>
          </Card>
          <Card icon={<Monitor />} title={site.platform} padding="sm">
            <p>
              The installer targets 64-bit Windows 10 and 11. Linux x64 gets an app with Java
              included, Apple Silicon Macs a launcher jar; a launcher jar contains JavaFX for one
              system only, so pick the file for yours. No support guarantees outside Windows. The
              client jar is the same on every system.
            </p>
          </Card>
        </div>
      </Section>

      <Section
        id="requirements"
        eyebrow="System requirements"
        title="If it runs vanilla 1.21.11, it runs VANTA."
        lead="VANTA adds a UI layer on top of the game. It does not raise the hardware requirements of Minecraft itself; only the optional Local AI needs disk space and RAM of its own."
        className="scroll-mt-24"
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
        lead="Compare the output of one command with the checksum shown on the card above. If they differ, delete the file and download it again; do not run it."
        className="scroll-mt-24 border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {verification.map((item) => (
            <div key={item.os} className="surface-card min-w-0 p-5 sm:p-6">
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
            Every file the launcher downloads, from Minecraft, libraries, Fabric, Fabric API and the
            VANTA jar to the Java runtime, is checked against the SHA-1 or SHA-256 published by its
            source before it is used; from launcher 1.1.0 on, the Performance pack mods from
            Modrinth are checked against Modrinth’s SHA-512, and from 1.4.0 on the Local AI files
            against the SHA-256 of the Local AI manifest. Files that fail verification are deleted,
            never executed.
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
              Verify the checksum, install the launcher and play through VANTA or the Minecraft
              Launcher, step by step, with the folders where everything ends up.
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

      <OlderVersions launcher={olderLaunchers} client={olderClients} bundles={olderZips} />
    </>
  );
}

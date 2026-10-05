import { ArrowRight } from 'lucide-react';
import { type ReactNode } from 'react';
import { Link } from 'react-router';
import { officialLauncherProfileName, site } from '../../config/site';

export interface InstallOptionsProps {
  /** Prefix for element ids, unique per page (e.g. `download-client`). */
  readonly idPrefix: string;
}

const options: readonly { readonly title: string; readonly text: ReactNode }[] = [
  {
    title: 'VANTA Launcher',
    text: (
      <>
        Installs Minecraft, Fabric Loader, Fabric API and VANTA and verifies every file. Signing in
        inside VANTA needs a Microsoft application id that the project does not ship yet — until
        then, use option 2.
      </>
    ),
  },
  {
    title: 'VANTA Launcher → “Use with Minecraft Launcher”',
    text: (
      <>
        Adds the profile “{officialLauncherProfileName}” to the official Minecraft Launcher, which
        signs you in with Microsoft and starts the game with VANTA. For the Minecraft Launcher from
        the Microsoft Store or the Xbox app it writes the profile into that launcher’s profiles file
        too; whether the profile then shows up there has not been tested on Windows yet — if it does
        not, use option 3.
      </>
    ),
  },
  {
    title: 'Manual',
    text: (
      <>
        Start the official Minecraft Launcher once, then install Fabric Loader {site.fabricLoader}{' '}
        for Minecraft {site.minecraft} with the Fabric installer from fabricmc.net (keep “Create
        profile” checked) and copy the two jars from the mods bundle into{' '}
        <code className="font-mono text-[12px] text-text-primary">.minecraft/mods</code>.{' '}
        <code className="font-mono text-[12px] text-text-primary">INSTALL.txt</code> in the bundle
        lists these steps.
      </>
    ),
  },
];

/** The three ways to install the client, shortest first, with a link to the full guide. */
export function InstallOptions({ idPrefix }: InstallOptionsProps) {
  const headingId = `${idPrefix}-install`;
  return (
    <section aria-labelledby={headingId} className="mt-6 border-t border-border-subtle pt-6">
      <h3
        id={headingId}
        className="text-[11px] font-semibold tracking-label text-text-muted uppercase"
      >
        How to install
      </h3>
      <ol className="mt-3 flex flex-col gap-3.5">
        {options.map((option, index) => (
          <li key={option.title} className="flex gap-3">
            <span
              aria-hidden="true"
              className="mt-px inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-border-strong bg-surface-2 font-mono text-[11px] font-semibold text-accent-violet-hover"
            >
              {index + 1}
            </span>
            <div className="min-w-0 text-sm leading-relaxed text-text-secondary">
              <p className="font-semibold text-text-primary">{option.title}</p>
              <p className="mt-0.5">{option.text}</p>
            </div>
          </li>
        ))}
      </ol>
      <Link
        to="/documentation/installation"
        className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-accent-violet-hover transition-colors hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
      >
        Step-by-step installation guide <ArrowRight className="size-3.5" aria-hidden="true" />
      </Link>
    </section>
  );
}

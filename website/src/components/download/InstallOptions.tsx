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
    title: 'VANTA Launcher → “PLAY via Minecraft Launcher”',
    text: (
      <>
        Signing in inside VANTA needs a Microsoft application id that the project does not ship yet,
        so from launcher 1.1.0 on the main button adds the profile “{officialLauncherProfileName}”
        to the official Minecraft Launcher and opens it; you sign in there with Microsoft. Close the
        Minecraft Launcher first, also from the system tray: it reads new profiles only when it
        starts. The performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling,
        Iris from Modrinth) is installed with it by default. In launcher 1.0.x the same setup is the
        button “Use with Minecraft Launcher”. For the Minecraft Launcher from the Microsoft Store or
        the Xbox app it writes the profile into that launcher’s profiles file too; whether the
        profile then shows up there has not been tested on Windows yet — if it does not, use option
        3.
      </>
    ),
  },
  {
    title: 'VANTA Launcher → PLAY',
    text: (
      <>
        Installs Minecraft, Fabric Loader, Fabric API, VANTA and (from launcher 1.1.0 on) the
        performance pack, verifies every file and starts the game. It needs Microsoft sign-in inside
        VANTA, which requires a Microsoft application id that the project does not ship yet — until
        then, use option 1.
      </>
    ),
  },
  {
    title: 'Manual',
    text: (
      <>
        Start the official Minecraft Launcher once, then install Fabric Loader {site.fabricLoader}{' '}
        for Minecraft {site.minecraft} with the Fabric installer from fabricmc.net (keep “Create
        profile” checked) and copy all jars from the mods bundle into{' '}
        <code className="font-mono text-[12px] text-text-primary">.minecraft/mods</code>. From
        client 1.2.0 on the bundle holds the VANTA jar, Fabric API and the Performance pack mods
        whose licences allow redistribution (Sodium, Lithium, FerriteCore, ImmediatelyFast, Iris);
        delete older copies of those first. EntityCulling is not in the zip: the game offers it with
        one click. <code className="font-mono text-[12px] text-text-primary">INSTALL.txt</code> in
        the bundle lists these steps, and “Mods & Shaders” in the game installs more from Modrinth.
      </>
    ),
  },
];

/** The three ways to install the client, the one that works with the published builds first, with a link to the full guide. */
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

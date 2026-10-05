import { Download, LogIn, Play } from 'lucide-react';
import { officialLauncherProfileName, site } from '../../config/site';
import { Reveal } from '../layout/Reveal';
import { Section } from '../ui/Section';

const steps = [
  {
    icon: <Download />,
    title: 'Install the launcher',
    text: `Run the Windows installer — Java ${site.java} is included. Everything the launcher downloads, from Fabric Loader ${site.fabricLoader} and Fabric API to the VANTA Client jar, comes from the official sources and is verified before use.`,
  },
  {
    icon: <LogIn />,
    title: 'Add it to the Minecraft Launcher',
    text: `Signing in inside VANTA needs a Microsoft application id the project does not ship yet. Until then, “Use with Minecraft Launcher” adds the profile “${officialLauncherProfileName}” to the official Minecraft Launcher, where you sign in with Microsoft as usual.`,
  },
  {
    icon: <Play />,
    title: `Play ${site.minecraft}`,
    text: `Choose the profile “${officialLauncherProfileName}” in the Minecraft Launcher and press Play. The game starts with the VANTA main menu.`,
  },
] as const;

/** Three-step onboarding overview. */
export function HowItWorks() {
  return (
    <Section
      id="how-it-works"
      eyebrow="How it works"
      title="From download to the main menu in three steps."
      lead="Everything the launcher installs comes from Mojang, Fabric and Eclipse Temurin with checksum verification. You can also drop the client jar into an existing Fabric setup."
      className="border-t border-border-subtle bg-bg-void/40"
    >
      <ol className="grid gap-4 lg:grid-cols-3" aria-label="Steps">
        {steps.map((step, index) => (
          <Reveal as="li" key={step.title} delay={index * 80} className="flex">
            <div className="surface-card relative flex w-full flex-col p-6 sm:p-7">
              <div className="mb-6 flex items-center justify-between">
                <span
                  aria-hidden="true"
                  className="inline-flex size-11 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover [&>svg]:size-5"
                >
                  {step.icon}
                </span>
                <span className="font-display text-3xl font-semibold text-text-muted tabular-nums">
                  0{index + 1}
                </span>
              </div>
              <h3 className="font-display text-lg font-semibold text-text-primary">{step.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-text-secondary">{step.text}</p>
            </div>
          </Reveal>
        ))}
      </ol>
    </Section>
  );
}

import { Download, LogIn, Play } from 'lucide-react';
import { site } from '../../config/site';
import { Reveal } from '../layout/Reveal';
import { Section } from '../ui/Section';

const steps = [
  {
    icon: <Download />,
    title: 'Install the launcher',
    text: `Run the Windows installer. The launcher downloads Minecraft ${site.minecraft}, Fabric Loader ${site.fabricLoader}, Fabric API and the VANTA Client jar from the official sources and verifies every file.`,
  },
  {
    icon: <LogIn />,
    title: 'Sign in with Microsoft',
    text: 'Use the same Microsoft account you play with. The sign-in happens in your browser through the device code flow; VANTA never sees your password.',
  },
  {
    icon: <Play />,
    title: `Play ${site.minecraft}`,
    text: `Press PLAY. The launcher detects Java ${site.java} or installs a verified Temurin runtime, then starts the game with the VANTA main menu.`,
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
                <span className="font-display text-3xl font-semibold text-text-muted/70 tabular-nums">
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

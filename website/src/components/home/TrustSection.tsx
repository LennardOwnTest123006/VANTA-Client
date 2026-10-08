import { Code, FileCheck, Lock, ShieldCheck } from 'lucide-react';
import { githubLinks } from '../../config/site';
import { Reveal } from '../layout/Reveal';
import { Card } from '../ui/Card';
import { Section } from '../ui/Section';

const pillars = [
  {
    icon: <ShieldCheck />,
    title: 'No cheats. Ever.',
    text: 'No combat automation, packet manipulation, anti-cheat bypasses or player tracking. Every feature is visual, quality-of-life, performance or accessibility — on your side of the screen.',
  },
  {
    icon: <Lock />,
    title: 'Local only',
    text: 'Playtime and activity are written to config/vanta/stats.json on your computer, and from 1.4.0 the Vanta Nexus assistant runs on your PC too: its prompts go to 127.0.0.1 only. There is no account, no telemetry, no cloud AI and nothing to opt out of; you can pause or delete the data any time.',
  },
  {
    icon: <Code />,
    title: 'Open source',
    text: 'The client, the launcher and this website are MIT-licensed and developed in the open. Read the code, build it yourself, or open an issue.',
    link: githubLinks ? { href: githubLinks.repository, label: 'View the repository' } : undefined,
  },
  {
    icon: <FileCheck />,
    title: 'Verified downloads',
    text: 'Every release ships with SHA-256 checksums in its release manifest and in SHA256SUMS.txt. The launcher refuses files that do not match, and a downloaded launcher update is only opened after you confirm it.',
  },
] as const;

/** "Built on vanilla, not against it" — the trust pillars. */
export function TrustSection() {
  return (
    <Section
      id="trust"
      eyebrow="Principles"
      title="Built on vanilla, not against it."
      lead="VANTA changes how Minecraft 1.21.11 looks and feels on your machine. It never changes how the game is played for anyone else."
    >
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Principles">
        {pillars.map((pillar, index) => (
          <Reveal as="li" key={pillar.title} delay={index * 60} className="flex">
            <Card icon={pillar.icon} title={pillar.title} className="w-full" padding="sm">
              <p>{pillar.text}</p>
              {'link' in pillar && pillar.link ? (
                <a
                  href={pillar.link.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 inline-flex text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
                >
                  {pillar.link.label}
                </a>
              ) : null}
            </Card>
          </Reveal>
        ))}
      </ul>
    </Section>
  );
}

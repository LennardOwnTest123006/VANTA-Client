import {
  Accessibility,
  ArrowRight,
  Code,
  Download,
  Eye,
  Heart,
  Lock,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { PageMeta } from '../components/layout/PageMeta';
import { GitHubIcon } from '../components/layout/GitHubIcon';
import { Reveal } from '../components/layout/Reveal';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Callout } from '../components/ui/Callout';
import { Card } from '../components/ui/Card';
import { Section } from '../components/ui/Section';
import { Stat, StatGroup } from '../components/ui/Stat';
import { githubLinks, site, specFacts } from '../config/site';

const principles = [
  {
    icon: <ShieldCheck />,
    title: 'No cheats. Ever.',
    text: 'No combat automation, packet manipulation, anti-cheat bypasses or player tracking. Pull requests that cross that line are closed without discussion.',
  },
  {
    icon: <Eye />,
    title: 'Honest by default',
    text: 'No download link until the release workflow has published one. No screenshot that was not taken from the real game. No performance numbers that were not measured.',
  },
  {
    icon: <Lock />,
    title: 'Your data stays yours',
    text: 'Statistics live in a JSON file on your computer. The client opens no connections of its own except to Modrinth while you use Mods & Shaders, the website has no analytics, and account tokens are encrypted locally.',
  },
  {
    icon: <Code />,
    title: 'Open source',
    text: 'Client, launcher and website are MIT licensed and built in public. Read the code, build it yourself, or change it.',
  },
  {
    icon: <Heart />,
    title: 'Built on vanilla, not against it',
    text: 'Presets change vanilla options. HUD widgets show what the game already exposes. Every vanilla screen stays one click away.',
  },
  {
    icon: <Accessibility />,
    title: 'Accessible from the start',
    text: 'UI scale, reduced motion, high contrast, larger text and full keyboard navigation are part of the UI kit, so every screen has them.',
  },
] as const;

const parts = [
  {
    title: 'VANTA Client',
    text: `A Fabric mod for Minecraft Java Edition ${site.minecraft}. Its interface logic lives in a pure Java 21 library so it can be unit tested without the game; the Minecraft-specific layer stays thin.`,
  },
  {
    title: 'VANTA Launcher',
    text: 'A JavaFX desktop application that installs Minecraft, Fabric and the client from the official sources with checksum verification, and from version 1.1.0 on the Performance pack and other mods from Modrinth. Until Microsoft sign-in is available inside VANTA, it adds a VANTA profile to the official Minecraft Launcher, which signs you in and starts the game.',
  },
  {
    title: 'This website',
    text: 'A static site built with Vite, React and Tailwind, rendered from the same markdown files as the repository documentation and release notes.',
  },
] as const;

/** Who makes VANTA, why, and the lines it does not cross. */
export default function AboutPage() {
  return (
    <>
      <PageMeta
        title="About"
        description={`Why VANTA Client exists, the principles it follows — no cheats, honest by default, local-only data, open source — and who builds it: the VANTA Client contributors.`}
      />
      <PageHero
        eyebrow="About"
        title={
          <>
            A client that <span className="text-gradient">respects the game</span>.
          </>
        }
        lead={`${site.name} started from a simple wish: Minecraft ${site.minecraft} with a calmer interface, a HUD you can arrange yourself and honest performance tools — without any of the things that make client mods unwelcome on servers.`}
      >
        <StatGroup className="grid-cols-2 gap-x-6 sm:grid-cols-4" aria-label="Project facts">
          {specFacts.slice(0, 3).map((fact) => (
            <Stat key={fact.label} label={fact.label} value={fact.value} size="sm" />
          ))}
          <Stat label="License" value={site.license} size="sm" />
        </StatGroup>
      </PageHero>

      <Section
        id="mission"
        eyebrow="Mission"
        title="Refine what is on your side of the screen. Change nothing for anyone else."
        lead="Everything VANTA does is visual, quality-of-life, performance or accessibility, and all of it happens on your own machine. The game, the servers and the other players see vanilla Minecraft."
      >
        <div className="grid gap-4 lg:grid-cols-3">
          {parts.map((part, index) => (
            <Reveal key={part.title} delay={index * 60} className="flex">
              <Card title={part.title} className="w-full" padding="sm">
                <p>{part.text}</p>
              </Card>
            </Reveal>
          ))}
        </div>
      </Section>

      <Section
        id="principles"
        eyebrow="Principles"
        title="Six rules we do not bend."
        lead="They are written into the contributing guidelines and enforced in code review."
        className="border-t border-border-subtle bg-bg-void/40"
      >
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="Principles">
          {principles.map((principle, index) => (
            <Reveal as="li" key={principle.title} delay={(index % 3) * 60} className="flex">
              <Card icon={principle.icon} title={principle.title} className="w-full" padding="sm">
                <p>{principle.text}</p>
              </Card>
            </Reveal>
          ))}
        </ul>
      </Section>

      <Section
        id="contributors"
        eyebrow="Who builds VANTA"
        title="Built by the VANTA Client contributors."
        lead="There is no company behind VANTA, no paid tier and no sponsor. The people who open issues, write documentation and submit pull requests on GitHub are the team."
      >
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <div className="surface-card flex flex-col gap-5 p-6 sm:p-8">
            <span
              aria-hidden="true"
              className="inline-flex size-11 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover [&>svg]:size-5"
            >
              <Users />
            </span>
            <p className="text-sm leading-relaxed text-text-secondary">
              Bug reports, documentation fixes, translations and features that fit the no-cheats
              principle are welcome. Start with the contributing guide, pick an open issue, and keep
              changes small and tested — every piece of logic in the repository has a unit test.
            </p>
            {githubLinks ? (
              <div className="flex flex-wrap gap-3">
                <Button
                  href={`${githubLinks.repository}/graphs/contributors`}
                  variant="secondary"
                  size="sm"
                  leadingIcon={<GitHubIcon className="size-4" />}
                >
                  Contributors on GitHub
                </Button>
                <Button href={githubLinks.contributing} variant="ghost" size="sm">
                  Contributing guide
                </Button>
              </div>
            ) : null}
          </div>
          <Callout tone="info" title="Trademarks and affiliation">
            <p>
              Minecraft is a trademark of Mojang AB / Microsoft. {site.name} is an independent
              project, not affiliated with, endorsed by or supported by Mojang or Microsoft, and
              contains none of their assets. Fabric is developed by FabricMC. The VANTA name and
              logo identify this project and are not covered by the MIT License.
            </p>
          </Callout>
        </div>
      </Section>

      <Section
        id="next"
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="sm"
        aria-label="Next steps"
      >
        <div className="flex flex-col items-start justify-between gap-6 sm:flex-row sm:items-center">
          <div>
            <p className="eyebrow mb-2">Next</p>
            <p className="font-display text-2xl font-semibold tracking-display text-text-primary">
              See what is inside, or read how it works.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button to="/download" leadingIcon={<Download />}>
              Download
            </Button>
            <Button to="/documentation" variant="secondary" trailingIcon={<ArrowRight />}>
              Documentation
            </Button>
          </div>
        </div>
      </Section>
    </>
  );
}

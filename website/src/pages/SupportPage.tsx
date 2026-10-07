import {
  ArrowRight,
  Bug,
  CircleHelp,
  ExternalLink,
  Mail,
  MessageCircle,
  Wrench,
} from 'lucide-react';
import { type ReactNode } from 'react';
import { Link } from 'react-router';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Callout } from '../components/ui/Callout';
import { Container } from '../components/ui/Container';
import { EmptyState } from '../components/ui/EmptyState';
import { Section } from '../components/ui/Section';
import { githubLinks, site, toolchainLine } from '../config/site';
import { type SupportLink, supportCategories } from '../config/support';

interface Channel {
  readonly id: string;
  readonly icon: ReactNode;
  readonly title: string;
  readonly text: string;
  readonly href: string;
  readonly cta: string;
}

function LinkList({ title, links }: { title: string; links: readonly SupportLink[] }) {
  if (links.length === 0) return null;
  return (
    <div>
      <p className="mb-2 text-[11px] font-semibold tracking-label text-text-muted uppercase">
        {title}
      </p>
      <ul className="flex flex-col gap-2">
        {links.map((link) => (
          <li key={link.to}>
            <Link
              to={link.to}
              className="group block rounded-md outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
            >
              <span className="flex items-center gap-1.5 text-sm font-medium text-text-primary group-hover:text-accent-violet-hover">
                {link.label}
                <ArrowRight
                  className="size-3.5 text-text-muted transition-transform motion-safe:group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </span>
              <span className="block text-xs leading-relaxed text-text-muted">{link.note}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Support hub: categories pointing at the right guides, and only the contact channels that exist. */
export default function SupportPage() {
  const channels: Channel[] = [];
  if (githubLinks) {
    channels.push({
      id: 'github',
      icon: <Bug />,
      title: 'GitHub Issues',
      text: 'Bug reports and feature requests, read by the maintainers. Always open; please include the information from the troubleshooting guide.',
      href: githubLinks.issues,
      cta: 'Open an issue',
    });
  }
  if (site.supportEmail) {
    channels.push({
      id: 'email',
      icon: <Mail />,
      title: 'E-mail',
      text: `Questions that do not fit a public issue: ${site.supportEmail}.`,
      href: `mailto:${site.supportEmail}`,
      cta: 'Write an e-mail',
    });
  }
  if (site.discordUrl) {
    channels.push({
      id: 'discord',
      icon: <MessageCircle />,
      title: 'Discord',
      text: 'Community chat for quick questions and sharing HUD layouts and profiles.',
      href: site.discordUrl,
      cta: 'Join the Discord',
    });
  }
  const communityPending = !site.supportEmail && !site.discordUrl;

  return (
    <>
      <PageMeta
        title="Support"
        description={`Help with installing and using VANTA Client and the VANTA Launcher for Minecraft ${site.minecraft}: guides by topic, troubleshooting and the support channels that are available.`}
      />
      <PageHero
        eyebrow="Support"
        title={
          <>
            Help, <span className="text-gradient">organised by problem</span>.
          </>
        }
        lead="Pick the area you are stuck in. Each one lists the guide to read first and the troubleshooting section that matches the usual error messages."
      >
        <div className="flex flex-wrap gap-3">
          <Button to="/documentation/troubleshooting" leadingIcon={<Wrench />}>
            Troubleshooting guide
          </Button>
          <Button to="/faq" variant="secondary" leadingIcon={<CircleHelp />}>
            FAQ
          </Button>
        </div>
      </PageHero>

      <Container size="wide" className="py-10 sm:py-14">
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-4" aria-label="Support topics">
          {supportCategories.map((category) => {
            const Icon = category.icon;
            return (
              <li key={category.id} id={category.id} className="flex scroll-mt-24">
                <article
                  aria-labelledby={`support-${category.id}`}
                  className="surface-card flex w-full flex-col gap-5 p-5 sm:p-6"
                >
                  <div className="flex items-center gap-3">
                    <span
                      aria-hidden="true"
                      className="inline-flex size-10 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover [&>svg]:size-5"
                    >
                      <Icon />
                    </span>
                    <div>
                      <h2
                        id={`support-${category.id}`}
                        className="font-display text-lg font-semibold text-text-primary"
                      >
                        {category.title}
                      </h2>
                    </div>
                  </div>
                  <p className="text-sm leading-relaxed text-text-secondary">{category.summary}</p>
                  <LinkList title="Guides" links={category.guides} />
                  <LinkList title="Troubleshooting" links={category.troubleshooting} />
                </article>
              </li>
            );
          })}
        </ul>
      </Container>

      <Section
        id="contact"
        eyebrow="Contact"
        title="The channels that exist, and only those."
        lead="Support channels are configured by the project maintainers. This page shows a channel only when it is really available — no placeholder addresses."
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        {channels.length === 0 ? (
          <EmptyState
            icon={<MessageCircle />}
            eyebrow="Not configured"
            title="Support channels will be announced."
            description="No contact channel has been configured for this deployment yet. When the maintainers add one, it appears here."
          />
        ) : (
          <div className="grid gap-4 lg:grid-cols-3">
            <ul className="contents" aria-label="Support channels">
              {channels.map((channel) => (
                <li key={channel.id} className="flex">
                  <div className="surface-card flex w-full flex-col p-6">
                    <span
                      aria-hidden="true"
                      className="mb-5 inline-flex size-11 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover [&>svg]:size-5"
                    >
                      {channel.icon}
                    </span>
                    <h3 className="font-display text-lg font-semibold text-text-primary">
                      {channel.title}
                    </h3>
                    <p className="mt-2 flex-1 text-sm leading-relaxed text-text-secondary">
                      {channel.text}
                    </p>
                    <Button
                      href={channel.href}
                      variant="secondary"
                      size="sm"
                      className="mt-5 self-start"
                      trailingIcon={<ExternalLink />}
                    >
                      {channel.cta}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            {communityPending ? (
              <Callout
                tone="info"
                title="Support channels will be announced"
                className={channels.length === 1 ? 'lg:col-span-2' : 'lg:col-span-3'}
              >
                <p>
                  E-mail and Discord support have not been configured yet. They appear here as soon
                  as the maintainers set them up; until then, GitHub Issues is the place to ask.
                </p>
              </Callout>
            ) : null}
          </div>
        )}

        <div className="mt-10 surface-card p-6 sm:p-8">
          <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
            Before you report a problem
          </p>
          <ul className="mt-4 grid gap-3 text-sm text-text-secondary sm:grid-cols-2">
            {[
              `Check the versions: ${toolchainLine}. Other Minecraft versions are not supported.`,
              'Read the first error line of the log or crash report, not the last — it usually names the cause.',
              'Include the launcher log (launcher-0.log) and the game log (latest.log) with usernames removed if you prefer.',
              'List your other mods, including those from the Performance pack or Mods & Shaders (the list is in modrinth.json); other mod combinations are not tested with VANTA.',
            ].map((item) => (
              <li key={item} className="flex items-start gap-2.5">
                <span
                  aria-hidden="true"
                  className="mt-2 size-1.5 shrink-0 rounded-full bg-accent-violet-hover"
                />
                {item}
              </li>
            ))}
          </ul>
          <p className="mt-5 text-sm text-text-muted">
            Security problems (downloads, tokens, extraction, sign-in) belong in the private channel
            described in the repository&apos;s SECURITY.md, not in a public issue.
          </p>
        </div>
      </Section>
    </>
  );
}

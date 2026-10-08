import { ArrowRight, Check, ShieldOff } from 'lucide-react';
import { PageMeta } from '../components/layout/PageMeta';
import { Reveal } from '../components/layout/Reveal';
import { PageHero } from '../components/page/PageHero';
import { PresetTable } from '../components/page/PresetTable';
import { TocNav } from '../components/page/TocNav';
import { Button } from '../components/ui/Button';
import { Callout } from '../components/ui/Callout';
import { Container } from '../components/ui/Container';
import { Section } from '../components/ui/Section';
import { featureFamilies, neverList } from '../config/features';
import { site } from '../config/site';
import { cn } from '../lib/cn';

/** Detailed feature catalogue. Every section has a stable anchor used by the home page cards. */
export default function FeaturesPage() {
  const toc = [
    ...featureFamilies.map((family) => ({ id: family.id, label: family.title })),
    { id: 'never', label: 'What VANTA will never do' },
  ];
  return (
    <>
      <PageMeta
        title="Features"
        description={`Everything VANTA Client adds on top of vanilla Minecraft ${site.minecraft}: Vanta Nexus with its strictly local AI assistant, HUD Designer, waypoints, Vanta Lab, main menu, HUD widgets and editor, Performance Center, Performance pack, Mods & Shaders, settings search, keybind manager, crosshair, cosmetics, profiles, notifications, resource packs, statistics, accessibility and zoom.`}
      />
      <PageHero
        eyebrow="Features"
        title={
          <>
            Everything on your side of the screen, <span className="text-gradient">refined</span>.
          </>
        }
        lead={`A complete tour of what VANTA Client adds to Minecraft ${site.minecraft}, and the lines it never crosses. Features marked “New in 1.1.0”, “New in 1.2.0”, “New in 1.3.0” or “New in 1.4.0” need at least that VANTA Client version.`}
      >
        <TocNav items={toc} />
      </PageHero>

      <Container className="py-6 sm:py-10">
        <ol className="flex flex-col" aria-label="Feature families">
          {featureFamilies.map((family, index) => {
            const Icon = family.icon;
            const reversed = index % 2 === 1;
            const isList = family.id === 'hud';
            return (
              <li
                key={family.id}
                id={family.id}
                className="scroll-mt-24 border-b border-border-subtle py-12 last:border-0 sm:py-16"
              >
                <Reveal
                  className={cn('grid grid-cols-1 items-start gap-8 lg:grid-cols-12 lg:gap-12')}
                >
                  <div className={cn('min-w-0 lg:col-span-5', reversed && 'lg:order-2')}>
                    <div className="flex items-center gap-3">
                      <span
                        aria-hidden="true"
                        className="inline-flex size-11 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover [&>svg]:size-5"
                      >
                        <Icon />
                      </span>
                      <p className="eyebrow">{family.eyebrow}</p>
                    </div>
                    <h2 className="mt-5 font-display text-3xl leading-[1.08] font-semibold tracking-display text-text-primary sm:text-4xl">
                      {family.title}
                    </h2>
                    <p className="mt-4 text-base leading-relaxed text-text-secondary sm:text-lg">
                      {family.description}
                    </p>
                    {family.link ? (
                      <Button
                        to={family.link.to}
                        variant="secondary"
                        size="sm"
                        trailingIcon={<ArrowRight />}
                        className="mt-6"
                      >
                        {family.link.label}
                      </Button>
                    ) : null}
                  </div>

                  <div className={cn('min-w-0 lg:col-span-7', reversed && 'lg:order-1')}>
                    {isList ? (
                      <ul
                        className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4"
                        aria-label="HUD widgets"
                      >
                        {family.bullets.map((bullet) => (
                          <li
                            key={bullet}
                            className="surface-card flex items-center gap-2.5 rounded-lg px-3.5 py-3 text-sm font-medium text-text-primary"
                          >
                            <span
                              aria-hidden="true"
                              className="size-1.5 shrink-0 rounded-full bg-accent-violet-hover"
                            />
                            {bullet}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <ul className="surface-card divide-y divide-border-subtle rounded-xl">
                        {family.bullets.map((bullet) => (
                          <li
                            key={bullet}
                            className="flex items-start gap-3 px-5 py-3.5 text-sm leading-relaxed text-text-secondary sm:px-6"
                          >
                            <Check
                              className="mt-0.5 size-4 shrink-0 text-success"
                              aria-hidden="true"
                            />
                            <span>{bullet}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                    {family.id === 'performance' ? (
                      <PresetTable
                        className="mt-4"
                        caption="Vanilla video options set by each preset"
                      />
                    ) : null}
                  </div>
                </Reveal>
              </li>
            );
          })}
        </ol>
      </Container>

      <Section
        id="never"
        eyebrow="Non-negotiable"
        title="What VANTA will never do."
        lead="A client you can use on any server without a second thought. These rules are part of the contribution guidelines and are enforced in code review."
        className="scroll-mt-24 border-t border-border-subtle bg-bg-void/40"
      >
        <ul
          className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
          aria-label="Things VANTA never does"
        >
          {neverList.map((item, index) => (
            <Reveal as="li" key={item.title} delay={(index % 3) * 60}>
              <div className="surface-card flex h-full gap-4 p-5 sm:p-6">
                <span
                  aria-hidden="true"
                  className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-danger/30 bg-danger/10 text-danger [&>svg]:size-4"
                >
                  <ShieldOff />
                </span>
                <div>
                  <h3 className="font-display text-base font-semibold text-text-primary">
                    {item.title}
                  </h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-text-secondary">
                    {item.detail}
                  </p>
                </div>
              </div>
            </Reveal>
          ))}
        </ul>
        <Callout tone="info" title="Found something that crosses a line?" className="mt-8">
          Open an issue on GitHub. Reports about fairness and anti-cheat compatibility are treated
          as bugs of the highest priority.
        </Callout>
      </Section>
    </>
  );
}

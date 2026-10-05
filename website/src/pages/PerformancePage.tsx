import { Activity, Boxes, Cpu, Gauge, MemoryStick, Timer } from 'lucide-react';
import { PageMeta } from '../components/layout/PageMeta';
import { Reveal } from '../components/layout/Reveal';
import { PageHero } from '../components/page/PageHero';
import { PresetTable } from '../components/page/PresetTable';
import { Callout } from '../components/ui/Callout';
import { Card } from '../components/ui/Card';
import { Section } from '../components/ui/Section';
import { advisorRules, fpsLimitChoices, presets, untouchedOptions } from '../config/presets';
import { site } from '../config/site';

const measurements = [
  {
    icon: <Gauge />,
    title: 'Frames per second',
    text: "The game's own FPS counter — the same number the F3 overlay shows — with a rolling history and the 1% low FPS next to it.",
  },
  {
    icon: <Timer />,
    title: 'Frame time',
    text: 'Average, 1% low and worst frame time in milliseconds over the recent window. Stutter is visible even when the average looks fine.',
  },
  {
    icon: <MemoryStick />,
    title: 'Memory',
    text: 'Used, allocated and maximum Java heap, read from the running JVM. Nothing is estimated.',
  },
  {
    icon: <Boxes />,
    title: 'Render and simulation distance',
    text: 'The vanilla options currently in effect, so you can see at a glance what the advisor is reasoning about.',
  },
  {
    icon: <Activity />,
    title: 'Entity count',
    text: 'Entities in the loaded area, the value Minecraft itself reports — the usual suspect when a farm tanks your FPS.',
  },
  {
    icon: <Cpu />,
    title: 'CPU load',
    text: 'Process CPU load from the Java runtime. Shown as "n/a" on JVMs that do not expose it rather than guessed.',
  },
] as const;

/** Explains the Performance Center, the four presets and their limits — truthfully. */
export default function PerformancePage() {
  return (
    <>
      <PageMeta
        title="Performance"
        description={`How the VANTA Performance Center works: what it measures, which vanilla video options the LOW, BALANCED, HIGH and ULTRA presets set, and what it does not do. Minecraft ${site.minecraft}.`}
      />
      <PageHero
        eyebrow="Performance Center"
        title={
          <>
            Honest numbers. <span className="text-gradient">Vanilla options.</span>
          </>
        }
        lead="The Performance Center shows what your machine is doing and makes the video options that matter easy to reach. It is a control panel, not a magic renderer."
      />

      <Section
        id="measures"
        eyebrow="What it measures"
        title="Six readouts, all taken from the game itself."
        lead="Every value comes from Minecraft or the Java runtime. VANTA adds sampling and a readable screen, nothing else."
      >
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="Measurements">
          {measurements.map((item, index) => (
            <Reveal as="li" key={item.title} delay={(index % 3) * 60} className="flex">
              <Card icon={item.icon} title={item.title} className="w-full" padding="sm">
                <p>{item.text}</p>
              </Card>
            </Reveal>
          ))}
        </ul>
      </Section>

      <Section
        id="presets"
        eyebrow="Presets"
        title="Four presets, one table — this is exactly what they change."
        lead="A preset writes the vanilla video options below and nothing else. Apply one, then fine-tune any option in the normal Video Settings; VANTA never overrides a change you make afterwards."
        className="border-t border-border-subtle bg-bg-void/40"
      >
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {presets.map((preset, index) => (
            <Reveal key={preset.id} delay={index * 60}>
              <div className="surface-card h-full p-5">
                <p className="font-display text-xs font-semibold tracking-wordmark text-accent-violet-hover uppercase">
                  {preset.id}
                </p>
                <h3 className="mt-2 font-display text-xl font-semibold text-text-primary">
                  {preset.name}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-text-secondary">{preset.summary}</p>
                <p className="mt-3 text-xs leading-relaxed text-text-muted">{preset.audience}</p>
              </div>
            </Reveal>
          ))}
        </div>
        <PresetTable className="mt-6" showKeys />
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          <Callout tone="info" title="Options the presets never touch">
            <p>
              {untouchedOptions.join(', ')}. These are personal preferences and stay exactly as you
              set them.
            </p>
          </Callout>
          <Callout tone="success" title="Always visible, always reversible">
            <p>
              The Performance Center shows which preset matches your current options and marks them
              as "Custom" the moment you change anything. Every value is a vanilla option, so Video
              Settings can undo it at any time.
            </p>
          </Callout>
        </div>
        <div className="mt-6 surface-card p-5 sm:p-6">
          <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
            Frame-rate limit, separately
          </p>
          <p className="mt-2 text-sm leading-relaxed text-text-secondary">
            Presets turn VSync off so the limit is the only cap. A quick menu next to them sets the
            limit on its own — pick one and the preset stays otherwise untouched.
          </p>
          <ul className="mt-4 flex flex-wrap gap-2" aria-label="Frame-rate limit choices">
            {fpsLimitChoices.map((choice) => (
              <li
                key={choice.label}
                className="inline-flex h-8 items-center rounded-pill border border-border-strong bg-surface-2 px-3.5 font-mono text-xs text-text-primary"
                title={choice.detail}
              >
                {choice.label}
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-text-muted">
            Vanilla steps the limit in tens, so "144" is applied as 140.
          </p>
        </div>
      </Section>

      <Section
        id="suggestions"
        eyebrow="Render-distance advisor"
        title="Render-distance suggestions, with the reason attached."
        lead="The advisor watches your average frame rate against a target — your FPS limit, or 60 when the limit is unlimited — and suggests a change of two chunks with the numbers that led to it. It only applies the change itself if you switch on auto-apply."
      >
        <ul className="surface-card divide-y divide-border-subtle" aria-label="Advisor rules">
          {advisorRules.map((rule) => (
            <li
              key={rule.condition}
              className="grid gap-2 px-5 py-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] sm:gap-8 sm:px-6"
            >
              <div>
                <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
                  When
                </p>
                <p className="mt-1 text-sm leading-relaxed text-text-primary">{rule.condition}</p>
              </div>
              <div>
                <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
                  Suggestion
                </p>
                <p className="mt-1 text-sm leading-relaxed text-text-secondary">
                  {rule.suggestion}
                </p>
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <Section
        id="limits"
        eyebrow="What it is not"
        title="No renderer replacement. No unverified claims."
        lead="Being clear about limits is part of being a client you can trust."
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        <div className="grid gap-4 lg:grid-cols-3">
          <Callout tone="warning" title="VANTA does not replace Minecraft's renderer">
            <p>
              The frame rate you get is vanilla's frame rate with the video options you choose.
              Presets help you pick sensible values quickly; they cannot make the engine faster.
            </p>
          </Callout>
          <Callout tone="warning" title="No performance numbers are promised">
            <p>
              You will not find "+200% FPS" anywhere on this site or in the client. Measure on your
              own machine with the Performance Center and keep what works.
            </p>
          </Callout>
          <Callout tone="info" title="Rendering mods are optional, not bundled">
            <p>
              Sodium and the other mods of the Performance pack are not part of VANTA’s files. From
              client 1.1.0 on, Mods & Shaders installs them from Modrinth when you ask for it (the
              VANTA Launcher 1.1.0 does so by default); the presets keep changing vanilla options
              only.
            </p>
          </Callout>
        </div>
      </Section>
    </>
  );
}

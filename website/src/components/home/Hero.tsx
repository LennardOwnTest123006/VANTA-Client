import { ArrowRight, Download, Sparkles } from 'lucide-react';
import { site, specFacts } from '../../config/site';
import { Button } from '../ui/Button';
import { Container } from '../ui/Container';
import { Pill } from '../ui/Pill';
import { Stat, StatGroup } from '../ui/Stat';
import { IsoCube } from '../page/IsoCube';

/**
 * Home hero. The backdrop is CSS only: layered radial gradients, an isometric line grid, a few
 * isometric blocks and a violet horizon — no screenshots, no stock imagery.
 */
export function Hero() {
  return (
    <section
      aria-labelledby="hero-title"
      className="relative isolate overflow-hidden border-b border-border-subtle"
    >
      <div className="hero-ambience" aria-hidden="true" />
      <div className="absolute inset-0 bg-isogrid mask-fade-radial opacity-70" aria-hidden="true" />
      {/* Isometric blocks: only on wide screens, where they sit clear of the copy. */}
      <div className="absolute inset-0 hidden lg:block" aria-hidden="true">
        <IsoCube size={64} className="top-[14%] left-[6%] opacity-70" />
        <IsoCube size={36} className="top-[46%] left-[13%] opacity-60" accent />
        <IsoCube size={92} className="top-[52%] left-[-2%] opacity-50" />
        <IsoCube size={54} className="top-[12%] right-[8%] opacity-70" accent />
        <IsoCube size={30} className="top-[40%] right-[15%] opacity-55" />
        <IsoCube size={110} className="top-[48%] right-[-3%] opacity-45" />
      </div>
      <div className="hero-horizon" aria-hidden="true" />

      <Container className="relative pt-20 pb-20 sm:pt-28 sm:pb-24 lg:pt-32 lg:pb-28">
        <div className="mx-auto flex max-w-4xl flex-col items-center text-center">
          <Pill icon={<Sparkles />} tone="violet">
            Fabric client for Minecraft {site.minecraft}
          </Pill>

          <h1
            id="hero-title"
            className="mt-7 font-display text-[clamp(2.75rem,10vw,6.5rem)] leading-[0.95] font-bold tracking-[0.04em] text-text-primary"
          >
            VANTA <span className="text-gradient">CLIENT</span>
          </h1>

          <p className="mt-6 font-display text-2xl font-medium tracking-display text-text-primary sm:text-3xl">
            {site.tagline}
          </p>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-text-secondary sm:text-lg">
            {site.description}
          </p>

          <div className="mt-9 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row">
            <Button
              to="/download"
              size="lg"
              leadingIcon={<Download />}
              className="w-full sm:w-auto"
            >
              Download
            </Button>
            <Button
              to="/features"
              size="lg"
              variant="secondary"
              trailingIcon={<ArrowRight />}
              className="w-full sm:w-auto"
            >
              Explore features
            </Button>
          </div>

          <StatGroup
            aria-label="Technical specifications"
            className="mt-14 w-full grid-cols-2 gap-x-6 gap-y-6 border-t border-border-subtle pt-8 text-left sm:grid-cols-4 sm:gap-x-8"
          >
            {specFacts.map((fact) => (
              <Stat key={fact.label} label={fact.label} value={fact.value} size="sm" />
            ))}
          </StatGroup>
        </div>
      </Container>
    </section>
  );
}

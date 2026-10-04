import { ArrowRight, Download } from 'lucide-react';
import { githubLinks, site, toolchainLine } from '../../config/site';
import { Button } from '../ui/Button';
import { Container } from '../ui/Container';
import { GitHubIcon } from '../layout/GitHubIcon';

/** Closing call to action. */
export function FinalCta() {
  return (
    <section
      aria-labelledby="cta-title"
      className="relative overflow-hidden border-t border-border-subtle"
    >
      <div className="hero-ambience rotate-180" aria-hidden="true" />
      <div className="absolute inset-0 bg-isogrid mask-fade-radial opacity-40" aria-hidden="true" />
      <Container className="relative py-20 text-center sm:py-28">
        <p className="eyebrow mb-4">Ready when you are</p>
        <h2
          id="cta-title"
          className="mx-auto max-w-3xl font-display text-4xl leading-[1.05] font-semibold tracking-display text-text-primary sm:text-5xl"
        >
          Your Minecraft. <span className="text-gradient">Refined.</span>
        </h2>
        <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-text-secondary sm:text-lg">
          {site.name} is free, open source and built for Minecraft {site.minecraft}. Download the
          launcher or grab the client jar for your existing Fabric profile.
        </p>
        <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Button to="/download" size="lg" leadingIcon={<Download />} className="w-full sm:w-auto">
            Download
          </Button>
          {githubLinks ? (
            <Button
              href={githubLinks.repository}
              size="lg"
              variant="secondary"
              leadingIcon={<GitHubIcon className="size-4" />}
              trailingIcon={<ArrowRight />}
              className="w-full sm:w-auto"
            >
              Source on GitHub
            </Button>
          ) : null}
        </div>
        <p className="mt-8 font-mono text-xs tracking-wide text-text-muted">{toolchainLine}</p>
      </Container>
    </section>
  );
}

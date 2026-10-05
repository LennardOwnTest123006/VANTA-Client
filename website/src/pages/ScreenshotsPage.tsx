import { ArrowRight, Camera, FlaskConical, Images, ShieldCheck, Sparkles } from 'lucide-react';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { ScreenshotGrid } from '../components/screenshots/ScreenshotGrid';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Container } from '../components/ui/Container';
import { EmptyState } from '../components/ui/EmptyState';
import { Section } from '../components/ui/Section';
import { githubLinks, site } from '../config/site';
import { screenshots } from '../lib/screenshots';

const captured = [
  'VANTA main menu replacing the title screen',
  'Settings with categories and search',
  'HUD editor',
  'Performance Center',
  'Profiles',
  'Keybind manager',
  'Crosshair customizer',
  'Cosmetics',
  'Statistics',
  'Resource packs',
  'Accessibility',
  'Global search and About',
  'In-world HUD with several widgets',
  'VANTA menu opened while in a world',
] as const;

/** Real screenshots only. Shows the captures in `assets/screenshots/` or an honest empty state. */
export default function ScreenshotsPage() {
  const count = screenshots.length;
  return (
    <>
      <PageMeta
        title="Screenshots"
        description={
          count > 0
            ? `${count} real screenshots of VANTA Client ${site.clientVersion} for Minecraft ${site.minecraft}, captured by the automated game test.`
            : `Screenshots of VANTA Client for Minecraft ${site.minecraft} will be published with the first release — real captures from the automated game test only, never mock-ups.`
        }
      />
      <PageHero
        eyebrow="Screenshots"
        title={
          <>
            Real captures. <span className="text-gradient">Nothing staged.</span>
          </>
        }
        lead={
          count > 0
            ? `Every image below was taken by the automated game test from a running Minecraft ${site.minecraft} with the released VANTA build. Captions describe what you see; nothing is retouched.`
            : `Every image on this page is taken from the real game by the automated test suite. Until the first release is published there is nothing to show — and this site never fakes a screenshot.`
        }
      />

      <Container size="wide" className="py-10 sm:py-14">
        {count > 0 ? (
          <ScreenshotGrid items={screenshots} />
        ) : (
          <EmptyState
            icon={<Images />}
            eyebrow="Not yet"
            title="Screenshots will be published with the first release."
            description={
              <p>
                The screenshots shown here are produced by the client&apos;s automated game test,
                which launches Minecraft {site.minecraft} with Fabric Loader {site.fabricLoader} and
                the built VANTA jar in CI and photographs every screen. A maintainer reviews the run
                and promotes the images to the repository; this page lists exactly those files and
                nothing else.
              </p>
            }
          >
            <Button to="/features" trailingIcon={<ArrowRight />}>
              Explore the features
            </Button>
            <Button to="/changelog" variant="secondary">
              Read the changelog
            </Button>
          </EmptyState>
        )}
      </Container>

      <Section
        id="how"
        eyebrow="How screenshots are made"
        title="From a headless game session to this page."
        lead="A screenshot you can trust is one you could reproduce. These are the rules the project follows."
        className="border-t border-border-subtle bg-bg-void/40"
        spacing="md"
      >
        <div className="grid gap-4 lg:grid-cols-3">
          <Card icon={<FlaskConical />} title="Captured by the game test" padding="sm">
            <p>
              The Fabric client game test starts the real game in GitHub Actions, opens each VANTA
              screen and saves a PNG. The files are attached to the workflow run for review.
            </p>
          </Card>
          <Card icon={<ShieldCheck />} title="Reviewed, never retouched" padding="sm">
            <p>
              A maintainer copies the approved captures into the repository. Cropping to the window
              and lossless PNG optimisation are the only edits allowed; no renders, no mock-ups, no
              composites.
            </p>
          </Card>
          <Card icon={<Camera />} title="Described, not sold" padding="sm">
            <p>
              Each image carries a caption, an accessible description of what is visible and the
              client, Minecraft and run it was captured with. The lightbox shows all three.
            </p>
          </Card>
        </div>
        <div className="mt-8 surface-card p-6 sm:p-8">
          <p className="flex items-center gap-2 text-[11px] font-semibold tracking-label text-text-muted uppercase">
            <Sparkles className="size-3.5" aria-hidden="true" /> Screens the test captures
          </p>
          <ul className="mt-4 grid gap-2 text-sm text-text-secondary sm:grid-cols-2 lg:grid-cols-3">
            {captured.map((item) => (
              <li key={item} className="flex items-start gap-2.5">
                <span
                  aria-hidden="true"
                  className="mt-2 size-1.5 shrink-0 rounded-full bg-accent-violet-hover"
                />
                {item}
              </li>
            ))}
          </ul>
          {githubLinks ? (
            <p className="mt-6 text-sm text-text-muted">
              The exact procedure is documented in{' '}
              <a
                href={`${githubLinks.repository}/blob/HEAD/assets/screenshots/README.md`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
              >
                assets/screenshots/README.md
              </a>
              .
            </p>
          ) : null}
        </div>
      </Section>
    </>
  );
}

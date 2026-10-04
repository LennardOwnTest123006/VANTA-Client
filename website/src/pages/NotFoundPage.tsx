import { ArrowLeft, Download, Search } from 'lucide-react';
import { useLocation } from 'react-router';
import { PageMeta } from '../components/layout/PageMeta';
import { Button } from '../components/ui/Button';
import { Container } from '../components/ui/Container';
import { headerNavItems, siteNav } from '../config/siteNav';
import { Link } from 'react-router';

/** The real 404 page, registered on the `*` route. */
export default function NotFoundPage() {
  const { pathname } = useLocation();
  const suggestions = headerNavItems(siteNav);
  return (
    <>
      <PageMeta
        title="Page not found"
        description="The page you requested does not exist on the VANTA Client website."
        noindex
      />
      <section aria-labelledby="nf-title" className="relative overflow-hidden">
        <div className="hero-ambience" aria-hidden="true" />
        <div
          className="absolute inset-0 bg-isogrid mask-fade-radial opacity-50"
          aria-hidden="true"
        />
        <Container className="relative py-24 sm:py-32">
          <div className="mx-auto max-w-2xl text-center">
            <p className="eyebrow mb-4">Error 404</p>
            <p
              aria-hidden="true"
              className="font-display text-[clamp(5rem,20vw,9rem)] leading-none font-bold tracking-display text-gradient"
            >
              404
            </p>
            <h1
              id="nf-title"
              className="mt-2 font-display text-3xl font-semibold tracking-display text-text-primary sm:text-4xl"
            >
              This page does not exist.
            </h1>
            <p className="mt-4 text-base leading-relaxed text-text-secondary sm:text-lg">
              There is nothing at{' '}
              <code className="rounded-xs border border-border-subtle bg-surface-2 px-1.5 py-0.5 text-sm text-text-primary">
                {pathname}
              </code>
              . The link may be outdated, or the page has not been published yet.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Button to="/" leadingIcon={<ArrowLeft />} className="w-full sm:w-auto">
                Back to the home page
              </Button>
              <Button
                to="/download"
                variant="secondary"
                leadingIcon={<Download />}
                className="w-full sm:w-auto"
              >
                Download
              </Button>
            </div>

            <nav aria-label="Suggested pages" className="mt-14 text-left">
              <p className="mb-3 flex items-center justify-center gap-2 text-xs font-semibold tracking-label text-text-muted uppercase">
                <Search className="size-3.5" aria-hidden="true" /> Looking for one of these?
              </p>
              <ul className="grid gap-3 sm:grid-cols-3">
                {suggestions.map((item) => (
                  <li key={item.to}>
                    <Link
                      to={item.to}
                      className="surface-card surface-card-interactive block h-full p-4 text-sm"
                    >
                      <span className="block font-semibold text-text-primary">{item.label}</span>
                      <span className="mt-1 block text-text-secondary">{item.description}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          </div>
        </Container>
      </section>
    </>
  );
}

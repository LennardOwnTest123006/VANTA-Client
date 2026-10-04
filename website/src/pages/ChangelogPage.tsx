import { ListFilter } from 'lucide-react';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { ChangelogEntryCard } from '../components/changelog/ChangelogEntryCard';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Container } from '../components/ui/Container';
import { Stat, StatGroup } from '../components/ui/Stat';
import { site } from '../config/site';
import {
  CHANGELOG_PRODUCTS,
  changelog,
  type ChangelogProduct,
  changelogProducts,
  productLabel,
} from '../lib/content';
import { cn } from '../lib/cn';
import { formatDate } from '../lib/format';

function isProduct(value: string | null): value is ChangelogProduct {
  return value !== null && (CHANGELOG_PRODUCTS as readonly string[]).includes(value);
}

/** Release notes for every product version, newest first, filterable by product. */
export default function ChangelogPage() {
  const [params, setParams] = useSearchParams();
  const productParam = params.get('product');
  const filter = isProduct(productParam) ? productParam : null;
  const products = useMemo(() => changelogProducts(changelog), []);
  const entries = filter ? changelog.filter((entry) => entry.product === filter) : changelog;
  const latestClient = changelog.find((entry) => entry.product === 'client');
  const latestLauncher = changelog.find((entry) => entry.product === 'launcher');

  const setFilter = (product: ChangelogProduct | null) => {
    const next = new URLSearchParams(params);
    if (product) next.set('product', product);
    else next.delete('product');
    setParams(next, { replace: true, preventScrollReset: true });
  };

  const chipClass = (active: boolean) =>
    cn(
      'inline-flex h-9 items-center rounded-pill border px-4 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
      active
        ? 'border-accent-violet/60 bg-accent-violet/15 text-text-primary'
        : 'border-border-strong bg-surface-1/80 text-text-secondary hover:border-accent-violet/50 hover:text-text-primary',
    );

  return (
    <>
      <PageMeta
        title="Changelog"
        description={`Release notes for every VANTA Client, VANTA Launcher and website version — what was added, improved and fixed, with the Minecraft version each release targets (currently ${site.minecraft}).`}
      />
      <PageHero
        eyebrow="Changelog"
        title={
          <>
            Every release, <span className="text-gradient">in detail</span>.
          </>
        }
        lead="What changed in each version of the client, the launcher and this website. Entries come straight from the release notes that ship with every build; nothing is summarised after the fact."
      >
        <StatGroup className="grid-cols-2 gap-x-6 sm:grid-cols-4" aria-label="Latest versions">
          <Stat
            label="Latest client"
            value={latestClient ? `v${latestClient.version}` : '—'}
            size="sm"
            {...(latestClient ? { hint: formatDate(latestClient.date) ?? latestClient.date } : {})}
          />
          <Stat
            label="Latest launcher"
            value={latestLauncher ? `v${latestLauncher.version}` : '—'}
            size="sm"
            {...(latestLauncher
              ? { hint: formatDate(latestLauncher.date) ?? latestLauncher.date }
              : {})}
          />
          <Stat label="Minecraft" value={site.minecraft} size="sm" />
          <Stat label="Releases" value={String(changelog.length)} size="sm" />
        </StatGroup>
      </PageHero>

      <Container size="wide" className="py-10 sm:py-14">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_16rem] lg:gap-14">
          <div className="min-w-0">
            <div
              role="group"
              aria-label="Filter by product"
              className="mb-8 flex flex-wrap items-center gap-2"
            >
              <span className="mr-1 inline-flex items-center gap-1.5 text-[11px] font-semibold tracking-label text-text-muted uppercase">
                <ListFilter className="size-3.5" aria-hidden="true" /> Product
              </span>
              <button
                type="button"
                aria-pressed={filter === null}
                onClick={() => {
                  setFilter(null);
                }}
                className={chipClass(filter === null)}
              >
                All
              </button>
              {products.map((product) => (
                <button
                  key={product}
                  type="button"
                  aria-pressed={filter === product}
                  onClick={() => {
                    setFilter(product);
                  }}
                  className={chipClass(filter === product)}
                >
                  {productLabel(product)}
                </button>
              ))}
            </div>
            <p role="status" aria-live="polite" className="sr-only">
              {entries.length} {entries.length === 1 ? 'release' : 'releases'}
              {filter ? ` for ${productLabel(filter)}` : ''}
            </p>
            {entries.length === 0 ? (
              <p className="surface-card p-6 text-sm text-text-secondary">
                No releases for this product yet.
              </p>
            ) : (
              <ol className="flex flex-col gap-6" aria-label="Releases">
                {entries.map((entry) => (
                  <li key={entry.id}>
                    <ChangelogEntryCard entry={entry} />
                  </li>
                ))}
              </ol>
            )}
          </div>

          <aside className="hidden lg:block" aria-label="Release index">
            <div className="sticky top-24 flex flex-col gap-6">
              <nav aria-label="Releases on this page">
                <p className="mb-3 text-[11px] font-semibold tracking-label text-text-muted uppercase">
                  Jump to
                </p>
                <ul className="flex flex-col gap-4">
                  {products.map((product) => {
                    const ofProduct = changelog.filter((entry) => entry.product === product);
                    return (
                      <li key={product}>
                        <p className="mb-1 text-xs font-medium text-text-primary">
                          {productLabel(product)}
                        </p>
                        <ul className="flex flex-col gap-0.5 border-l border-border-subtle">
                          {ofProduct.map((entry) => (
                            <li key={entry.id} className="-ml-px">
                              <a
                                href={`#${entry.id}`}
                                onClick={() => {
                                  if (filter && filter !== product) setFilter(null);
                                }}
                                className="block border-l border-transparent py-1 pl-3.5 font-mono text-xs text-text-secondary transition-colors hover:border-border-strong hover:text-text-primary"
                              >
                                v{entry.version}{' '}
                                <span className="text-text-muted">· {entry.date}</span>
                              </a>
                            </li>
                          ))}
                        </ul>
                      </li>
                    );
                  })}
                </ul>
              </nav>
              <div className="surface-card p-4 text-xs leading-relaxed text-text-secondary">
                Release files appear on the Download page once the release workflow has published
                them with SHA-256 checksums.
                <Button to="/download" variant="link" className="mt-2 block text-xs">
                  Go to downloads
                </Button>
              </div>
            </div>
          </aside>
        </div>
      </Container>
    </>
  );
}

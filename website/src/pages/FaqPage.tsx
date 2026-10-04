import { ArrowRight, Search } from 'lucide-react';
import { useId, useMemo, useState } from 'react';
import { FaqAccordion } from '../components/faq/FaqAccordion';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Container } from '../components/ui/Container';
import { docLinkResolver } from '../lib/docs';
import { parseFaq } from '../lib/faq';
import { useDocs } from '../lib/use-content';
import NotFoundPage from './NotFoundPage';

/** Frequently asked questions, built from `docs/faq.md` (every `## Question` becomes an item). */
export default function FaqPage() {
  const pages = useDocs();
  const page = pages.find((candidate) => candidate.slug === 'faq');
  const faq = useMemo(() => (page ? parseFaq(page.body) : undefined), [page]);
  const [filter, setFilter] = useState('');
  const filterId = useId();
  if (!page || !faq) return <NotFoundPage />;

  return (
    <>
      <PageMeta title="FAQ" description={page.description} />
      <PageHero
        eyebrow="FAQ"
        title={
          <>
            Questions, <span className="text-gradient">answered plainly</span>.
          </>
        }
        lead={`${faq.items.length} questions about servers, performance mods, supported versions, price, data, accounts and more — the same answers as in the documentation, in one place.`}
      >
        <div className="relative max-w-md">
          <label htmlFor={filterId} className="sr-only">
            Filter questions
          </label>
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <input
            id={filterId}
            type="search"
            value={filter}
            onChange={(event) => {
              setFilter(event.target.value);
            }}
            placeholder="Filter questions, e.g. servers, Java, offline"
            autoComplete="off"
            className="h-11 w-full rounded-md border border-border-strong bg-surface-1 pr-4 pl-9 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-violet/70 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus [&::-webkit-search-cancel-button]:appearance-none"
          />
        </div>
      </PageHero>
      <Container size="narrow" className="py-10 sm:py-14">
        {faq.intro ? <p className="mb-6 text-text-secondary">{faq.intro}</p> : null}
        <FaqAccordion items={faq.items} filter={filter} resolveLink={docLinkResolver} />
        <div className="mt-10 flex flex-col gap-4 rounded-xl border border-border-subtle bg-surface-1/60 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-display text-base font-semibold text-text-primary">
              Did not find your question?
            </p>
            <p className="mt-1 text-sm text-text-secondary">
              The troubleshooting guide covers error messages in detail, and the support page lists
              every channel that exists.
            </p>
          </div>
          <div className="flex shrink-0 flex-wrap gap-3">
            <Button to="/documentation/troubleshooting" variant="secondary" size="sm">
              Troubleshooting
            </Button>
            <Button to="/support" size="sm" trailingIcon={<ArrowRight />}>
              Support
            </Button>
          </div>
        </div>
      </Container>
    </>
  );
}

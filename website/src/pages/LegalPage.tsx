import { ExternalLink, FileText } from 'lucide-react';
import { Link } from 'react-router';
import { DocToc } from '../components/docs/DocToc';
import { PageMeta } from '../components/layout/PageMeta';
import { PageHero } from '../components/page/PageHero';
import { Container } from '../components/ui/Container';
import { githubLinks, site } from '../config/site';
import { docEditUrl, docLinkResolver } from '../lib/docs';
import { MarkdownBlock } from '../lib/markdown';
import { useDocs } from '../lib/use-content';
import NotFoundPage from './NotFoundPage';

export interface LegalPageProps {
  /** Which document to render: `docs/privacy.md` or `docs/terms.md`. */
  readonly document: 'privacy' | 'terms';
}

/** Privacy and Terms pages, rendered from the documentation sources so there is one truth. */
export default function LegalPage({ document }: LegalPageProps) {
  const pages = useDocs();
  const page = pages.find((candidate) => candidate.slug === document);
  if (!page) return <NotFoundPage />;
  const other =
    document === 'privacy'
      ? pages.find((p) => p.slug === 'terms')
      : pages.find((p) => p.slug === 'privacy');
  const sourceUrl = githubLinks ? docEditUrl(githubLinks.repository, page.file) : undefined;

  return (
    <>
      <PageMeta title={page.title} description={page.description} />
      <PageHero eyebrow="Legal" title={page.title} lead={page.description}>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-text-muted">
          <span className="inline-flex items-center gap-1.5">
            <FileText className="size-4" aria-hidden="true" />
            Source: <code className="font-mono text-text-secondary">docs/{page.file}</code>
          </span>
          {sourceUrl ? (
            <a
              href={sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-text-secondary transition-colors hover:text-text-primary"
            >
              View history on GitHub <ExternalLink className="size-3.5" aria-hidden="true" />
            </a>
          ) : null}
          {other ? (
            <Link
              to={other.route}
              className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 transition-colors hover:text-text-primary"
            >
              {other.title}
            </Link>
          ) : null}
        </div>
      </PageHero>
      <Container className="py-10 sm:py-14">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_14rem] lg:gap-16">
          <article aria-label={page.title} className="min-w-0 max-w-3xl">
            <MarkdownBlock source={page.body} resolveLink={docLinkResolver} />
            <p className="mt-12 border-t border-border-subtle pt-6 text-xs leading-relaxed text-text-muted">
              Minecraft is a trademark of Mojang AB / Microsoft. {site.name} is an independent,
              open-source project and is not affiliated with, endorsed by or associated with Mojang
              or Microsoft.
            </p>
          </article>
          <aside className="hidden lg:block" aria-label="Page tools">
            <div className="sticky top-24">
              <DocToc headings={page.headings} />
            </div>
          </aside>
        </div>
      </Container>
    </>
  );
}

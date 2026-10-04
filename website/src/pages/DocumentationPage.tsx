import { Bug, Clock, ExternalLink, PanelLeft, Pencil } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { DocsSearch } from '../components/docs/DocsSearch';
import { DocsSidebar } from '../components/docs/DocsSidebar';
import { DocToc } from '../components/docs/DocToc';
import { PageMeta } from '../components/layout/PageMeta';
import { PrevNext } from '../components/page/PrevNext';
import { Button } from '../components/ui/Button';
import { Container } from '../components/ui/Container';
import { Drawer } from '../components/ui/Drawer';
import { Kbd } from '../components/ui/Kbd';
import { githubLinks } from '../config/site';
import { docEditUrl, docLinkResolver, docNeighbours, groupDocs } from '../lib/docs';
import { readingTime } from '../lib/format';
import { MarkdownBlock } from '../lib/markdown';
import { useDocs } from '../lib/use-content';
import NotFoundPage from './NotFoundPage';

const DESKTOP = '(min-width: 1024px)';

/** The drawer opens with the search box focused; focus returns to the opener on close. */
const focusSearch = (root: HTMLElement) => root.querySelector<HTMLElement>('input[type="search"]');

/**
 * Documentation: `/documentation` renders `docs/index.md`, `/documentation/:slug` any other page.
 * Desktop shows a sticky sidebar (search + navigation) and an on-this-page rail; small screens get
 * a toolbar that opens the navigation in a drawer.
 */
export default function DocumentationPage() {
  const { slug } = useParams();
  const { pathname } = useLocation();
  const pages = useDocs();
  const currentSlug = slug ?? 'index';
  const current = pages.find((page) => page.slug === currentSlug);
  const groups = useMemo(() => groupDocs(pages), [pages]);
  // The drawer remembers the path it was opened on, so navigating closes it without an effect.
  const [drawerPath, setDrawerPath] = useState<string | undefined>(undefined);
  const drawerOpen = drawerPath === pathname;
  const closeDrawer = useCallback(() => {
    setDrawerPath(undefined);
  }, []);
  const openDrawer = useCallback(() => {
    setDrawerPath(pathname);
  }, [pathname]);

  // "/" on small screens opens the drawer (the desktop search handles the shortcut itself).
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) return;
      if (typeof window.matchMedia === 'function' && window.matchMedia(DESKTOP).matches) return;
      const target = event.target;
      if (
        target instanceof HTMLElement &&
        (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))
      ) {
        return;
      }
      event.preventDefault();
      openDrawer();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [openDrawer]);

  if (!current) return <NotFoundPage />;

  const isIndex = current.slug === 'index';
  const { previous, next } = docNeighbours(pages, current.slug);
  const minutes = readingTime(current.body);
  const editUrl = githubLinks ? docEditUrl(githubLinks.repository, current.file) : undefined;

  return (
    <>
      <PageMeta
        title={isIndex ? 'Documentation' : `${current.title} · Documentation`}
        description={current.description}
      />

      {/* Small-screen toolbar */}
      <div className="sticky top-16 z-30 border-b border-border-subtle bg-bg-base/85 backdrop-blur-xl lg:hidden">
        <Container size="wide" className="flex h-12 items-center justify-between gap-3">
          <button
            type="button"
            onClick={openDrawer}
            aria-haspopup="dialog"
            aria-expanded={drawerOpen}
            className="inline-flex h-9 items-center gap-2 rounded-md border border-border-strong bg-surface-1 px-3 text-sm font-medium text-text-primary transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            <PanelLeft className="size-4" aria-hidden="true" />
            Browse documentation
          </button>
          <p className="truncate text-xs text-text-muted">
            {current.category}
            {isIndex ? '' : ` · ${current.title}`}
          </p>
        </Container>
      </div>

      <Container size="wide" className="py-8 lg:py-12">
        <div className="grid gap-10 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-12 xl:grid-cols-[15rem_minmax(0,1fr)_13rem] xl:gap-14">
          <aside className="hidden lg:block" aria-label="Documentation sidebar">
            <div className="sticky top-24 flex max-h-[calc(100dvh-7rem)] flex-col gap-6 overflow-y-auto pr-2 pb-4 [scrollbar-width:thin]">
              <DocsSearch pages={pages} shortcut />
              <DocsSidebar groups={groups} currentSlug={current.slug} />
            </div>
          </aside>

          <article className="min-w-0" aria-labelledby="doc-title">
            <header className="border-b border-border-subtle pb-6">
              <p className="eyebrow mb-3">{isIndex ? 'Documentation' : current.category}</p>
              <h1
                id="doc-title"
                className="font-display text-3xl leading-[1.08] font-semibold tracking-display text-text-primary sm:text-4xl lg:text-5xl"
              >
                {current.title}
              </h1>
              <p className="mt-4 text-base leading-relaxed text-text-secondary sm:text-lg">
                {current.description}
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-text-muted">
                <span className="inline-flex items-center gap-1.5">
                  <Clock className="size-4" aria-hidden="true" />
                  {minutes} min read
                </span>
                {editUrl ? (
                  <a
                    href={editUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 text-text-secondary transition-colors hover:text-text-primary"
                  >
                    <Pencil className="size-4" aria-hidden="true" />
                    Edit on GitHub
                    <ExternalLink className="size-3.5" aria-hidden="true" />
                  </a>
                ) : null}
                {isIndex ? (
                  <span className="hidden items-center gap-1.5 lg:inline-flex">
                    Press <Kbd>/</Kbd> to search
                  </span>
                ) : null}
              </div>
            </header>

            {current.headings.length > 1 ? (
              <details className="mt-6 rounded-lg border border-border-subtle bg-surface-1 px-4 py-3 xl:hidden">
                <summary className="cursor-pointer text-sm font-medium text-text-primary">
                  On this page
                </summary>
                <ul className="mt-3 flex flex-col gap-1.5 text-sm">
                  {current.headings.map((heading) => (
                    <li key={heading.id} className={heading.level >= 3 ? 'pl-4' : ''}>
                      <a
                        href={`#${heading.id}`}
                        className="text-text-secondary transition-colors hover:text-text-primary"
                      >
                        {heading.text}
                      </a>
                    </li>
                  ))}
                </ul>
              </details>
            ) : null}

            <MarkdownBlock
              key={current.slug}
              source={current.body}
              resolveLink={docLinkResolver}
              className="mt-2"
            />

            <footer className="mt-12 border-t border-border-subtle pt-8">
              <PrevNext
                previous={
                  previous
                    ? { to: previous.route, title: previous.title, hint: previous.category }
                    : undefined
                }
                next={next ? { to: next.route, title: next.title, hint: next.category } : undefined}
              />
              {githubLinks ? (
                <p className="mt-8 text-sm text-text-muted">
                  Something wrong or missing on this page?{' '}
                  <a
                    href={githubLinks.issues}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
                  >
                    Open an issue
                  </a>
                  {editUrl ? (
                    <>
                      {' '}
                      or{' '}
                      <a
                        href={editUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
                      >
                        edit the markdown
                      </a>
                    </>
                  ) : null}
                  .
                </p>
              ) : null}
            </footer>
          </article>

          <aside className="hidden xl:block" aria-label="Page tools">
            <div className="sticky top-24 flex flex-col gap-8">
              <DocToc key={current.slug} headings={current.headings} />
              {githubLinks ? (
                <div className="flex flex-col gap-2 border-t border-border-subtle pt-6">
                  <Button
                    href={githubLinks.issues}
                    variant="ghost"
                    size="sm"
                    leadingIcon={<Bug />}
                    className="justify-start px-2"
                  >
                    Report a problem
                  </Button>
                  {editUrl ? (
                    <Button
                      href={editUrl}
                      variant="ghost"
                      size="sm"
                      leadingIcon={<Pencil />}
                      className="justify-start px-2"
                    >
                      Edit this page
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>
          </aside>
        </div>
      </Container>

      <Drawer
        open={drawerOpen}
        onClose={closeDrawer}
        title="Documentation"
        initialFocus={focusSearch}
      >
        <div className="flex flex-col gap-6">
          <DocsSearch pages={pages} onNavigate={closeDrawer} />
          <DocsSidebar groups={groups} currentSlug={current.slug} onNavigate={closeDrawer} />
        </div>
      </Drawer>
    </>
  );
}

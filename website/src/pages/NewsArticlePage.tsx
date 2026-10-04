import { ArrowLeft, CalendarDays, Clock, UserRound } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { PageMeta } from '../components/layout/PageMeta';
import { PrevNext } from '../components/page/PrevNext';
import { Container } from '../components/ui/Container';
import { CopyLinkButton } from '../components/ui/CopyLinkButton';
import { Tag } from '../components/ui/Pill';
import { docLinkResolver } from '../lib/docs';
import { formatDate } from '../lib/format';
import { MarkdownBlock } from '../lib/markdown';
import { newsNeighbours } from '../lib/news';
import { useNews } from '../lib/use-content';
import NotFoundPage from './NotFoundPage';

/** One news post: header with metadata, markdown body, share link and newer/older navigation. */
export default function NewsArticlePage() {
  const { slug = '' } = useParams();
  const posts = useNews();
  const post = posts.find((candidate) => candidate.slug === slug);
  if (!post) return <NotFoundPage />;
  const { newer, older } = newsNeighbours(posts, slug);
  const date = formatDate(post.date) ?? post.date;

  return (
    <>
      <PageMeta
        title={post.title}
        description={post.summary}
        type="article"
        publishedTime={post.date}
      />
      <article aria-labelledby="post-title">
        <header className="relative overflow-hidden border-b border-border-subtle">
          <div className="hero-ambience" aria-hidden="true" />
          <div className="absolute inset-0 bg-isogrid mask-fade-b opacity-40" aria-hidden="true" />
          <Container size="narrow" className="relative py-14 sm:py-20">
            <Link
              to="/news"
              className="inline-flex items-center gap-1.5 text-sm text-text-secondary transition-colors hover:text-text-primary"
            >
              <ArrowLeft className="size-4" aria-hidden="true" /> All news
            </Link>
            <p className="eyebrow mt-8 mb-4">News</p>
            <h1
              id="post-title"
              className="font-display text-4xl leading-[1.05] font-semibold tracking-display text-text-primary sm:text-5xl"
            >
              {post.title}
            </h1>
            <p className="mt-5 text-lg leading-relaxed text-text-secondary">{post.summary}</p>
            <dl className="mt-6 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-text-secondary">
              <div className="inline-flex items-center gap-2">
                <dt className="sr-only">Published</dt>
                <CalendarDays className="size-4 text-text-muted" aria-hidden="true" />
                <dd>
                  <time dateTime={post.date}>{date}</time>
                </dd>
              </div>
              <div className="inline-flex items-center gap-2">
                <dt className="sr-only">Author</dt>
                <UserRound className="size-4 text-text-muted" aria-hidden="true" />
                <dd>{post.author}</dd>
              </div>
              <div className="inline-flex items-center gap-2">
                <dt className="sr-only">Reading time</dt>
                <Clock className="size-4 text-text-muted" aria-hidden="true" />
                <dd>{post.minutes} min read</dd>
              </div>
            </dl>
            {post.tags.length > 0 ? (
              <ul className="mt-5 flex flex-wrap gap-1.5" aria-label="Tags">
                {post.tags.map((tag) => (
                  <li key={tag}>
                    <Tag>{tag}</Tag>
                  </li>
                ))}
              </ul>
            ) : null}
          </Container>
        </header>

        <Container size="narrow" className="py-10 sm:py-14">
          <MarkdownBlock source={post.body} resolveLink={docLinkResolver} />
          <div className="mt-12 flex flex-wrap items-center justify-between gap-4 border-t border-border-subtle pt-6">
            <p className="text-sm text-text-muted">
              Published {date} by {post.author}.
            </p>
            <CopyLinkButton label="Copy link to this post" />
          </div>
          <PrevNext
            className="mt-8"
            previousLabel="Newer"
            nextLabel="Older"
            previous={
              newer
                ? { to: `/news/${newer.slug}`, title: newer.title, hint: newer.date }
                : undefined
            }
            next={
              older
                ? { to: `/news/${older.slug}`, title: older.title, hint: older.date }
                : undefined
            }
          />
        </Container>
      </article>
    </>
  );
}

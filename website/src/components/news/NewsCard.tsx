import { ArrowRight, CalendarDays, Clock } from 'lucide-react';
import { Link } from 'react-router';
import { formatDate } from '../../lib/format';
import { type NewsPost } from '../../lib/news';
import { cn } from '../../lib/cn';
import { Tag } from '../ui/Pill';

export interface NewsCardProps {
  readonly post: NewsPost;
  /** Larger typography for the newest post. */
  readonly featured?: boolean;
  readonly headingLevel?: 2 | 3;
}

/** News post teaser: date and reading time, title, summary and tags. The whole card is clickable. */
export function NewsCard({ post, featured = false, headingLevel = 2 }: NewsCardProps) {
  const Heading = `h${headingLevel}` as const;
  const date = formatDate(post.date) ?? post.date;
  return (
    <article
      className={cn(
        'surface-card surface-card-interactive flex flex-col',
        featured ? 'p-7 sm:p-10' : 'p-6',
      )}
      aria-labelledby={`news-${post.slug}`}
    >
      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-text-muted">
        <span className="inline-flex items-center gap-1.5">
          <CalendarDays className="size-3.5" aria-hidden="true" />
          <time dateTime={post.date}>{date}</time>
        </span>
        <span className="inline-flex items-center gap-1.5">
          <Clock className="size-3.5" aria-hidden="true" />
          {post.minutes} min read
        </span>
      </p>
      <Heading
        id={`news-${post.slug}`}
        className={cn(
          'mt-3 font-display font-semibold tracking-display text-text-primary',
          featured ? 'text-2xl sm:text-4xl' : 'text-xl',
        )}
      >
        <Link
          to={`/news/${post.slug}`}
          className="rounded-xs outline-none after:absolute after:inset-0 after:rounded-xl after:content-[''] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-border-focus"
        >
          {post.title}
        </Link>
      </Heading>
      <p
        className={cn(
          'mt-3 leading-relaxed text-text-secondary',
          featured ? 'text-base sm:text-lg' : 'text-sm',
        )}
      >
        {post.summary}
      </p>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        {post.tags.length > 0 ? (
          <ul className="flex flex-wrap gap-1.5" aria-label="Tags">
            {post.tags.map((tag) => (
              <li key={tag}>
                <Tag>{tag}</Tag>
              </li>
            ))}
          </ul>
        ) : (
          <span />
        )}
        <span
          aria-hidden="true"
          className="inline-flex items-center gap-1 text-sm font-medium text-accent-violet-hover"
        >
          Read <ArrowRight className="size-4" />
        </span>
      </div>
    </article>
  );
}

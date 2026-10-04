import { type Components } from 'react-markdown';
import { type AnchorHTMLAttributes, type ImgHTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from './cn';

/**
 * Sanitised component map shared by every markdown surface of the website.
 *
 * - Links are restricted to `http(s)`, `mailto:` and relative URLs; external links open in a new tab
 *   with `rel="noopener noreferrer"`, site-relative links (`/documentation/...`) navigate through
 *   the router without a full page load.
 * - Images are only rendered from the same origin or `https:`; everything else falls back to the
 *   alt text.
 */

const SAFE_LINK = /^(https?:|mailto:|\/|#|\.\/|\.\.\/)/i;
const SAFE_IMAGE = /^(https:|\/(?!\/)|\.\/)/i;

type AnchorProps = AnchorHTMLAttributes<HTMLAnchorElement> & { node?: unknown };

function isExternal(href: string): boolean {
  return /^https?:/i.test(href);
}

const LINK_CLASS =
  'text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 transition-colors hover:text-text-primary hover:decoration-accent-violet-hover';

function SafeLink({ href, children, node: _node, ...rest }: AnchorProps) {
  if (!href || !SAFE_LINK.test(href)) {
    return <span className="text-text-secondary">{children}</span>;
  }
  if (href.startsWith('/') && !href.startsWith('//')) {
    return (
      <Link to={href} className={LINK_CLASS} {...rest}>
        {children}
      </Link>
    );
  }
  const external = isExternal(href);
  return (
    <a
      {...rest}
      href={href}
      className={LINK_CLASS}
      {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
    >
      {children}
    </a>
  );
}

function SafeImage({ src, alt }: ImgHTMLAttributes<HTMLImageElement>) {
  if (typeof src !== 'string' || !SAFE_IMAGE.test(src)) {
    return <span className="text-text-muted italic">{alt ?? ''}</span>;
  }
  return (
    <img
      src={src}
      alt={alt ?? ''}
      loading="lazy"
      decoding="async"
      className="my-6 rounded-lg border border-border-subtle"
    />
  );
}

function heading(level: 1 | 2 | 3 | 4 | 5 | 6, className: string) {
  const Tag = `h${level}` as const;
  return function Heading({
    children,
    id,
  }: {
    children?: ReactNode | undefined;
    id?: string | undefined;
  }) {
    return (
      <Tag id={id} className={cn('scroll-mt-24 font-display text-text-primary', className)}>
        {children}
      </Tag>
    );
  };
}

/** Component map shared by every markdown surface of the website. */
export const markdownComponents: Components = {
  h1: heading(1, 'mt-10 mb-4 text-3xl font-semibold tracking-display'),
  h2: heading(2, 'mt-10 mb-3 text-2xl font-semibold tracking-display'),
  h3: heading(3, 'mt-8 mb-2 text-xl font-semibold'),
  h4: heading(4, 'mt-6 mb-2 text-lg font-semibold'),
  h5: heading(5, 'mt-6 mb-2 text-base font-semibold'),
  h6: heading(6, 'mt-6 mb-2 text-sm font-semibold uppercase tracking-label'),
  p: ({ children }) => <p className="my-4 leading-relaxed text-text-secondary">{children}</p>,
  a: SafeLink,
  img: SafeImage,
  ul: ({ children }) => (
    <ul className="my-4 list-disc space-y-1.5 pl-6 text-text-secondary marker:text-accent-violet">
      {children}
    </ul>
  ),
  ol: ({ children }) => (
    <ol className="my-4 list-decimal space-y-1.5 pl-6 text-text-secondary marker:text-text-muted">
      {children}
    </ol>
  ),
  li: ({ children }) => <li className="pl-1">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold text-text-primary">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  blockquote: ({ children }) => (
    <blockquote className="my-6 border-l-2 border-accent-violet/60 pl-4 text-text-secondary italic">
      {children}
    </blockquote>
  ),
  hr: () => <hr className="my-10 border-0 hairline" />,
  code: ({ children, className }) => {
    const block = typeof className === 'string' && className.includes('language-');
    return (
      <code
        className={cn(
          'font-mono text-[0.9em]',
          block
            ? 'block whitespace-pre text-text-primary'
            : 'rounded-xs border border-border-subtle bg-surface-2 px-1.5 py-0.5 text-text-primary',
        )}
      >
        {children}
      </code>
    );
  },
  // Scrollable regions are focusable so keyboard users can scroll wide code and tables.
  pre: ({ children }) => (
    <pre
      tabIndex={0}
      className="my-6 overflow-x-auto rounded-lg border border-border-subtle bg-surface-1 p-4 text-sm leading-relaxed focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
    >
      {children}
    </pre>
  ),
  table: ({ children }) => (
    <div
      tabIndex={0}
      className="my-6 overflow-x-auto rounded-lg border border-border-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
    >
      <table className="w-full border-collapse text-left text-sm">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="bg-surface-2 text-text-primary">{children}</thead>,
  th: ({ children }) => <th className="px-4 py-2.5 font-semibold">{children}</th>,
  td: ({ children }) => (
    <td className="border-t border-border-subtle px-4 py-2.5 text-text-secondary">{children}</td>
  ),
  input: ({ checked }) => (
    <input
      type="checkbox"
      checked={Boolean(checked)}
      readOnly
      className="mr-2 accent-accent-violet"
    />
  ),
};

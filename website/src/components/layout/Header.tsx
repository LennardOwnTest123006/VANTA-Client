import { Download, Menu, X } from 'lucide-react';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router';
import { headerNavItems, type NavItem, siteNav } from '../../config/siteNav';
import { site } from '../../config/site';
import { cn } from '../../lib/cn';
import { useBodyScrollLock, useFocusTrap, useScrolled } from '../../lib/hooks';
import { prefetchRoute } from '../../lib/prefetch';
import { Button } from '../ui/Button';
import { Container } from '../ui/Container';
import { Logo } from '../ui/Logo';
import { GitHubIcon } from './GitHubIcon';

export interface HeaderProps {
  /** Navigation items; defaults to the ready header items from `siteNav`. */
  readonly items?: readonly NavItem[];
}

const desktopLink = (isActive: boolean) =>
  cn(
    'relative inline-flex h-9 items-center rounded-md px-3 text-sm font-medium transition-colors',
    'after:absolute after:inset-x-3 after:-bottom-[13px] after:h-px after:rounded-full after:bg-accent-violet-hover after:opacity-0 after:transition-opacity',
    isActive
      ? 'text-text-primary after:opacity-100'
      : 'text-text-secondary hover:bg-surface-2/80 hover:text-text-primary',
  );

const sheetLink = (isActive: boolean) =>
  cn(
    'flex items-center justify-between rounded-lg px-4 py-3.5 text-base font-medium transition-colors',
    isActive
      ? 'bg-surface-2 text-text-primary'
      : 'text-text-secondary hover:bg-surface-2/70 hover:text-text-primary',
  );

/**
 * Sticky translucent header with the mark, primary navigation, repository link and the Download
 * call-to-action. On small screens the navigation lives in a sheet with a focus trap; it closes on
 * Escape, on backdrop click and on route change.
 */
export function Header({ items }: HeaderProps) {
  const navItems = items ?? headerNavItems(siteNav);
  const downloadItem = siteNav.find((item) => item.to === '/download');
  const [open, setOpen] = useState(false);
  const scrolled = useScrolled(8);
  const sheetRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const sheetId = useId();

  const close = useCallback(() => {
    setOpen(false);
  }, []);

  useFocusTrap(sheetRef, open, close);
  useBodyScrollLock(open);

  // Close when the viewport grows past the mobile breakpoint.
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const media = window.matchMedia('(min-width: 1024px)');
    const onChange = (event: MediaQueryListEvent) => {
      if (event.matches) setOpen(false);
    };
    media.addEventListener('change', onChange);
    return () => {
      media.removeEventListener('change', onChange);
    };
  }, []);

  return (
    <header
      className={cn(
        'sticky top-0 z-50 border-b transition-[background-color,border-color,box-shadow] duration-(--vanta-duration-base)',
        scrolled || open
          ? 'border-border-subtle bg-bg-base/80 shadow-[0_1px_0_rgba(255,255,255,0.02)] backdrop-blur-xl supports-[backdrop-filter]:bg-bg-base/70'
          : 'border-transparent bg-transparent',
      )}
    >
      <Container size="wide" className="flex h-16 items-center justify-between gap-6">
        <Link
          to="/"
          className="group inline-flex items-center gap-3 rounded-md py-1 pr-1 text-text-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-border-focus"
          aria-label={`${site.name} — home`}
        >
          <Logo
            variant="mark"
            size={28}
            className="transition-transform duration-(--vanta-duration-base) motion-safe:group-hover:scale-105"
          />
          <Logo variant="wordmark" size={13} className="text-text-primary" />
        </Link>

        <nav aria-label="Primary" className="hidden lg:block">
          <ul className="flex items-center gap-1">
            {navItems.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) => desktopLink(isActive)}
                  onMouseEnter={() => {
                    prefetchRoute(item.to);
                  }}
                  onFocus={() => {
                    prefetchRoute(item.to);
                  }}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex items-center gap-2">
          {site.githubUrl ? (
            <a
              href={site.githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="VANTA on GitHub (opens in a new tab)"
              className="hidden size-9 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-surface-2 hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus sm:inline-flex"
            >
              <GitHubIcon className="size-[18px]" />
            </a>
          ) : null}
          {downloadItem ? (
            <Button
              to={downloadItem.to}
              size="sm"
              leadingIcon={<Download />}
              aria-label={downloadItem.label}
              className="max-xs:px-2.5"
              onMouseEnter={() => {
                prefetchRoute(downloadItem.to);
              }}
              onFocus={() => {
                prefetchRoute(downloadItem.to);
              }}
            >
              <span className="max-xs:sr-only">{downloadItem.label}</span>
            </Button>
          ) : null}
          <button
            ref={toggleRef}
            type="button"
            className="inline-flex size-10 items-center justify-center rounded-md text-text-primary transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus lg:hidden"
            aria-expanded={open}
            aria-controls={sheetId}
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => {
              setOpen((value) => !value);
            }}
          >
            {open ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </Container>

      {/* Mobile sheet */}
      <div
        className={cn(
          'fixed inset-0 top-16 z-40 lg:hidden',
          open ? 'pointer-events-auto' : 'pointer-events-none',
        )}
        aria-hidden={!open}
      >
        <div
          className={cn(
            'absolute inset-0 bg-bg-void/70 transition-opacity duration-(--vanta-duration-base)',
            open ? 'opacity-100' : 'opacity-0',
          )}
          onClick={close}
        />
        <div
          ref={sheetRef}
          id={sheetId}
          role="dialog"
          aria-modal="true"
          aria-label="Site navigation"
          tabIndex={-1}
          hidden={!open}
          className={cn(
            'absolute inset-x-0 top-0 max-h-[calc(100dvh-4rem)] overflow-y-auto border-b border-border-subtle bg-bg-base shadow-lg transition-[transform,opacity] duration-(--vanta-duration-base) ease-standard',
            open ? 'translate-y-0 opacity-100' : '-translate-y-2 opacity-0',
          )}
        >
          <Container size="wide" className="py-4">
            <nav aria-label="Primary">
              <ul className="flex flex-col gap-1">
                <li>
                  <NavLink
                    to="/"
                    end
                    className={({ isActive }) => sheetLink(isActive)}
                    onClick={close}
                  >
                    Home
                  </NavLink>
                </li>
                {navItems.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      className={({ isActive }) => sheetLink(isActive)}
                      onClick={close}
                      onTouchStart={() => {
                        prefetchRoute(item.to);
                      }}
                    >
                      {item.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
            <div className="mt-4 flex flex-col gap-3 border-t border-border-subtle pt-4">
              {downloadItem ? (
                <Button to={downloadItem.to} fullWidth leadingIcon={<Download />} onClick={close}>
                  {downloadItem.label}
                </Button>
              ) : null}
              {site.githubUrl ? (
                <Button
                  href={site.githubUrl}
                  variant="secondary"
                  fullWidth
                  leadingIcon={<GitHubIcon className="size-4" />}
                >
                  Source on GitHub
                </Button>
              ) : null}
            </div>
          </Container>
        </div>
      </div>
    </header>
  );
}

/**
 * Single source of truth for site navigation.
 *
 * Every page of the website is listed here. `ready: true` marks routes that are not registered
 * yet; the header, footer and `RouteLink` hide or de-link them automatically, so a page becomes
 * reachable by registering its route in `src/routes.tsx` and flipping `ready` to `true`.
 */

export type NavGroup = 'product' | 'resources' | 'legal';

export interface NavItem {
  /** Visible label. */
  readonly label: string;
  /** Route path, always absolute. */
  readonly to: string;
  /** Whether the route is registered in `routes.tsx`. */
  readonly ready: boolean;
  /** Footer column. */
  readonly group: NavGroup;
  /** Show in the sticky header (desktop + mobile sheet). */
  readonly inHeader: boolean;
  /** One-line description for menus and the 404 page. */
  readonly description: string;
}

export const siteNav: readonly NavItem[] = [
  {
    label: 'Download',
    to: '/download',
    ready: true,
    group: 'product',
    inHeader: true,
    description: 'Launcher installer and client jar with checksums.',
  },
  {
    label: 'Features',
    to: '/features',
    ready: true,
    group: 'product',
    inHeader: true,
    description: 'Everything VANTA adds on top of vanilla Minecraft 1.21.11.',
  },
  {
    label: 'Performance',
    to: '/performance',
    ready: true,
    group: 'product',
    inHeader: true,
    description: 'The Performance Center, its presets and what they really change.',
  },
  {
    label: 'Screenshots',
    to: '/screenshots',
    ready: true,
    group: 'product',
    inHeader: true,
    description: 'Real screenshots captured by the automated game tests.',
  },
  {
    label: 'Changelog',
    to: '/changelog',
    ready: true,
    group: 'product',
    inHeader: true,
    description: 'Release notes for every client and launcher version.',
  },
  {
    label: 'News',
    to: '/news',
    ready: true,
    group: 'resources',
    inHeader: false,
    description: 'Project updates and release announcements.',
  },
  {
    label: 'Documentation',
    to: '/documentation',
    ready: true,
    group: 'resources',
    inHeader: true,
    description: 'Installation, launcher, settings, HUD, profiles and troubleshooting guides.',
  },
  {
    label: 'Support',
    to: '/support',
    ready: true,
    group: 'resources',
    inHeader: false,
    description: 'Get help and report problems.',
  },
  {
    label: 'FAQ',
    to: '/faq',
    ready: true,
    group: 'resources',
    inHeader: false,
    description: 'Frequently asked questions.',
  },
  {
    label: 'About',
    to: '/about',
    ready: true,
    group: 'resources',
    inHeader: false,
    description: 'Who builds VANTA and why.',
  },
  {
    label: 'Privacy',
    to: '/privacy',
    ready: true,
    group: 'legal',
    inHeader: false,
    description: 'What data the client, the launcher and the website process.',
  },
  {
    label: 'Terms',
    to: '/terms',
    ready: true,
    group: 'legal',
    inHeader: false,
    description: 'Plain terms for a free, open-source client.',
  },
];

/** Items shown in the header, in order. The Download call-to-action is rendered separately. */
export function headerNavItems(items: readonly NavItem[] = siteNav): NavItem[] {
  return items.filter((item) => item.ready && item.inHeader && item.to !== '/download');
}

/** Ready items of a footer group. */
export function navGroupItems(group: NavGroup, items: readonly NavItem[] = siteNav): NavItem[] {
  return items.filter((item) => item.ready && item.group === group);
}

/** Finds a navigation item by path: query string and hash are ignored, nested paths match their root. */
export function findNavItem(to: string, items: readonly NavItem[] = siteNav): NavItem | undefined {
  const pathOnly = to.split(/[?#]/)[0] ?? '';
  const base = `/${pathOnly.split('/')[1] ?? ''}`;
  return items.find((item) => item.to === base);
}

/** True when the path is the home page or a registered, ready route. */
export function isRouteReady(to: string, items: readonly NavItem[] = siteNav): boolean {
  if (to === '/') return true;
  return findNavItem(to, items)?.ready ?? false;
}

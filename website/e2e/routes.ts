/** Every public route of the website with the title and h1 the e2e tests expect. */
export interface RouteExpectation {
  readonly path: string;
  readonly title: string;
  /** Substring of the h1 text. */
  readonly h1: string;
  /** Pages that should not be indexed (404). */
  readonly noindex?: boolean;
}

export const routes: readonly RouteExpectation[] = [
  { path: '/', title: 'VANTA Client — Your Minecraft. Refined.', h1: 'VANTA' },
  { path: '/download', title: 'Download — VANTA Client', h1: 'VANTA Client for Minecraft 1.21.11' },
  {
    path: '/features',
    title: 'Features — VANTA Client',
    h1: 'Everything on your side of the screen',
  },
  { path: '/performance', title: 'Performance — VANTA Client', h1: 'Honest numbers.' },
  { path: '/screenshots', title: 'Screenshots — VANTA Client', h1: 'Real captures.' },
  { path: '/changelog', title: 'Changelog — VANTA Client', h1: 'Every release' },
  { path: '/news', title: 'News — VANTA Client', h1: 'Project updates' },
  {
    path: '/news/introducing-vanta',
    title: 'Introducing VANTA Client — VANTA Client',
    h1: 'Introducing VANTA Client',
  },
  { path: '/documentation', title: 'Documentation — VANTA Client', h1: 'VANTA documentation' },
  {
    path: '/documentation/installation',
    title: 'Installation · Documentation — VANTA Client',
    h1: 'Installation',
  },
  {
    path: '/documentation/troubleshooting',
    title: 'Troubleshooting · Documentation — VANTA Client',
    h1: 'Troubleshooting',
  },
  { path: '/support', title: 'Support — VANTA Client', h1: 'Help,' },
  { path: '/faq', title: 'FAQ — VANTA Client', h1: 'Questions,' },
  { path: '/about', title: 'About — VANTA Client', h1: 'A client that' },
  { path: '/privacy', title: 'Privacy — VANTA Client', h1: 'Privacy' },
  { path: '/terms', title: 'Terms of use — VANTA Client', h1: 'Terms of use' },
  {
    path: '/this-page-does-not-exist',
    title: 'Page not found — VANTA Client',
    h1: 'This page does not exist.',
    noindex: true,
  },
];

import { githubLinks, site } from './site';
import { type NavGroup, navGroupItems, siteNav } from './siteNav';

/** A footer link: internal route (`to`) or external URL (`href`). */
export interface FooterLink {
  readonly label: string;
  readonly to?: string;
  readonly href?: string;
}

export interface FooterColumn {
  readonly title: string;
  readonly links: readonly FooterLink[];
}

function internal(group: NavGroup): FooterLink[] {
  return navGroupItems(group, siteNav).map((item) => ({ label: item.label, to: item.to }));
}

/**
 * Builds the footer columns from `siteNav` (ready routes only) and the configured external links.
 * Columns without links are dropped, so an unconfigured Discord or support address never leaves an
 * empty heading behind.
 */
export function footerColumns(): FooterColumn[] {
  const columns: FooterColumn[] = [
    { title: 'Product', links: [{ label: 'Home', to: '/' }, ...internal('product')] },
    {
      title: 'Resources',
      links: [
        ...internal('resources'),
        ...(githubLinks
          ? [
              { label: 'Source code', href: githubLinks.repository },
              { label: 'Report an issue', href: githubLinks.issues },
              { label: 'Contributing', href: githubLinks.contributing },
            ]
          : []),
      ],
    },
    {
      title: 'Community',
      links: [
        ...(site.discordUrl ? [{ label: 'Discord', href: site.discordUrl }] : []),
        ...(site.githubUrl ? [{ label: 'GitHub', href: site.githubUrl }] : []),
        ...(site.supportEmail
          ? [{ label: site.supportEmail, href: `mailto:${site.supportEmail}` }]
          : []),
      ],
    },
    {
      title: 'Legal',
      links: [
        ...internal('legal'),
        ...(githubLinks ? [{ label: 'MIT License', href: githubLinks.license }] : []),
      ],
    },
  ];
  return columns.filter((column) => column.links.length > 0);
}

import { Mail, MessageCircle } from 'lucide-react';
import { Link } from 'react-router';
import { type FooterLink, footerColumns } from '../../config/footer';
import { site, toolchainLine } from '../../config/site';
import { Container } from '../ui/Container';
import { Logo } from '../ui/Logo';
import { GitHubIcon } from './GitHubIcon';

function FooterAnchor({ link }: { link: FooterLink }) {
  const className =
    'inline-flex items-center gap-1.5 text-sm text-text-secondary transition-colors hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus rounded-xs';
  if (link.to !== undefined) {
    return (
      <Link to={link.to} className={className}>
        {link.label}
      </Link>
    );
  }
  const href = link.href ?? '#';
  const external = /^https?:/i.test(href);
  return (
    <a
      href={href}
      className={className}
      {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
    >
      {link.label}
    </a>
  );
}

/** Site footer: brand, link columns (only configured links), toolchain strip and legal notice. */
export function Footer() {
  const columns = footerColumns();
  const year = new Date().getUTCFullYear();
  return (
    <footer className="relative mt-auto border-t border-border-subtle bg-bg-void/60">
      <Container size="wide" className="py-14 sm:py-16">
        <div
          className="grid gap-12 lg:grid-cols-[1.4fr_repeat(var(--columns),1fr)]"
          style={{ '--columns': columns.length } as React.CSSProperties}
        >
          <div className="max-w-sm">
            <Link
              to="/"
              className="inline-flex items-center gap-3 rounded-md text-text-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-border-focus"
              aria-label={`${site.name} — home`}
            >
              <Logo variant="mark" size={26} />
              <Logo variant="wordmark" size={12} />
            </Link>
            <p className="mt-4 text-sm leading-relaxed text-text-secondary">
              {site.tagline} A modern Fabric client for Minecraft Java Edition {site.minecraft} —
              performance, customization and a clean experience, without cheats.
            </p>
            <ul className="mt-5 flex items-center gap-2" aria-label="Community links">
              {site.githubUrl ? (
                <li>
                  <a
                    href={site.githubUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="GitHub (opens in a new tab)"
                    className="inline-flex size-9 items-center justify-center rounded-md border border-border-subtle bg-surface-1 text-text-secondary transition-colors hover:border-border-strong hover:text-text-primary"
                  >
                    <GitHubIcon className="size-4" />
                  </a>
                </li>
              ) : null}
              {site.discordUrl ? (
                <li>
                  <a
                    href={site.discordUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="Discord (opens in a new tab)"
                    className="inline-flex size-9 items-center justify-center rounded-md border border-border-subtle bg-surface-1 text-text-secondary transition-colors hover:border-border-strong hover:text-text-primary"
                  >
                    <MessageCircle className="size-4" />
                  </a>
                </li>
              ) : null}
              {site.supportEmail ? (
                <li>
                  <a
                    href={`mailto:${site.supportEmail}`}
                    aria-label={`E-mail ${site.supportEmail}`}
                    className="inline-flex size-9 items-center justify-center rounded-md border border-border-subtle bg-surface-1 text-text-secondary transition-colors hover:border-border-strong hover:text-text-primary"
                  >
                    <Mail className="size-4" />
                  </a>
                </li>
              ) : null}
            </ul>
          </div>

          <nav aria-label="Footer" className="contents">
            {columns.map((column) => (
              <div key={column.title}>
                <h2 className="font-ui text-[11px] font-semibold tracking-label text-text-muted uppercase">
                  {column.title}
                </h2>
                <ul className="mt-4 flex flex-col gap-2.5">
                  {column.links.map((link) => (
                    <li key={`${column.title}-${link.label}`}>
                      <FooterAnchor link={link} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <div className="mt-14 flex flex-col gap-4 border-t border-border-subtle pt-6 text-xs text-text-muted sm:flex-row sm:items-center sm:justify-between">
          <p className="font-mono tracking-wide text-text-secondary">{toolchainLine}</p>
          <p>
            © {year} VANTA · Client {site.clientVersion} · Launcher {site.launcherVersion} · Code
            under the {site.license} License
          </p>
        </div>
        <p className="mt-3 max-w-3xl text-xs leading-relaxed text-text-muted">
          Minecraft is a trademark of Mojang AB / Microsoft. VANTA is an independent, community
          project and is not affiliated with, endorsed by or associated with Mojang or Microsoft.
        </p>
      </Container>
    </footer>
  );
}

import { Newspaper } from 'lucide-react';
import { PageMeta } from '../components/layout/PageMeta';
import { NewsCard } from '../components/news/NewsCard';
import { PageHero } from '../components/page/PageHero';
import { Button } from '../components/ui/Button';
import { Container } from '../components/ui/Container';
import { EmptyState } from '../components/ui/EmptyState';
import { site } from '../config/site';
import { useNews } from '../lib/use-content';

/** News list: the newest post featured, the rest in a grid. */
export default function NewsPage() {
  const posts = useNews();
  const [featured, ...rest] = posts;
  return (
    <>
      <PageMeta
        title="News"
        description={`Project updates and release announcements for VANTA Client, the Fabric client for Minecraft ${site.minecraft}.`}
      />
      <PageHero
        eyebrow="News"
        title={
          <>
            Project <span className="text-gradient">updates</span>.
          </>
        }
        lead="Announcements and release notes in plain language — what exists, what changed and how releases work. No marketing, no promises about unfinished work."
      />
      <Container className="py-10 sm:py-14">
        {featured ? (
          <div className="flex flex-col gap-6">
            <NewsCard post={featured} featured />
            {rest.length > 0 ? (
              <ul className="grid gap-4 sm:grid-cols-2" aria-label="Older posts">
                {rest.map((post) => (
                  <li key={post.slug} className="flex">
                    <NewsCard post={post} headingLevel={3} />
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : (
          <EmptyState
            icon={<Newspaper />}
            eyebrow="Nothing yet"
            title="No news has been published."
            description="Posts appear here when the project has something real to announce."
          >
            <Button to="/changelog" variant="secondary">
              Read the changelog
            </Button>
          </EmptyState>
        )}
      </Container>
    </>
  );
}

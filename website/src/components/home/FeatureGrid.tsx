import { ArrowRight } from 'lucide-react';
import { featureHighlights } from '../../config/features';
import { Reveal } from '../layout/Reveal';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Section } from '../ui/Section';

/** Twelve feature cards, each linking to its section of the features page. */
export function FeatureGrid() {
  return (
    <Section
      id="features"
      eyebrow="What's inside"
      title="Everything you reach for, nothing you have to hide."
      lead="Twelve modules that cover the local assistant, performance, mods, interface and workflow: all configurable, all client-side."
      className="border-t border-border-subtle bg-bg-void/40"
      aside={
        <Button to="/features" variant="secondary" trailingIcon={<ArrowRight />}>
          All features
        </Button>
      }
    >
      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Feature modules">
        {featureHighlights.map((feature, index) => {
          const Icon = feature.icon;
          return (
            <Reveal as="li" key={feature.id} delay={(index % 4) * 60} className="flex">
              <Card
                icon={<Icon />}
                title={<span className="tracking-label uppercase">{feature.title}</span>}
                to={`/features#${feature.id}`}
                className="w-full"
                padding="sm"
              >
                <p>{feature.summary}</p>
              </Card>
            </Reveal>
          );
        })}
      </ul>
    </Section>
  );
}

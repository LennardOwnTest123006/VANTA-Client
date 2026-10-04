import { PageMeta } from '../components/layout/PageMeta';
import { FeatureGrid } from '../components/home/FeatureGrid';
import { FinalCta } from '../components/home/FinalCta';
import { Hero } from '../components/home/Hero';
import { HowItWorks } from '../components/home/HowItWorks';
import { InterfacePreview } from '../components/home/InterfacePreview';
import { TrustSection } from '../components/home/TrustSection';
import { site } from '../config/site';

/** Landing page. Part of the initial bundle; everything else is lazy. */
export function HomePage() {
  return (
    <>
      <PageMeta
        description={`${site.description} Minecraft ${site.minecraft}, Fabric Loader ${site.fabricLoader}, Java ${site.java}, ${site.platform}.`}
      />
      <Hero />
      <TrustSection />
      <FeatureGrid />
      <InterfacePreview />
      <HowItWorks />
      <FinalCta />
    </>
  );
}

export default HomePage;

import AxeBuilder from '@axe-core/playwright';
import { type ConsoleMessage, type Page, expect } from '@playwright/test';

/** Collects console errors and page errors so tests can assert a clean console. */
export function trackConsoleErrors(page: Page): () => string[] {
  const errors: string[] = [];
  const onConsole = (message: ConsoleMessage) => {
    if (message.type() === 'error') errors.push(`console.error: ${message.text()}`);
  };
  page.on('console', onConsole);
  page.on('pageerror', (error) => {
    errors.push(`pageerror: ${error.message}`);
  });
  page.on('requestfailed', (request) => {
    // Aborted font/preload requests during navigation are not application errors.
    const failure = request.failure()?.errorText ?? '';
    if (failure.includes('ERR_ABORTED')) return;
    errors.push(`requestfailed: ${request.url()} (${failure})`);
  });
  return () => errors;
}

/** Waits until the React app has rendered the main landmark and lazy content settled. */
export async function waitForApp(page: Page): Promise<void> {
  await expect(page.getByRole('main')).toBeVisible();
  await page.waitForLoadState('networkidle');
  // Lazy routes and markdown chunks render asynchronously; wait for the h1 of the page.
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
}

/** Scrolls through the page so IntersectionObserver reveals fire, then returns to the top. */
export async function revealAll(page: Page): Promise<void> {
  await page.evaluate(async () => {
    const step = window.innerHeight / 2;
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo({ top: y, behavior: 'instant' });
      await new Promise((resolve) => setTimeout(resolve, 60));
    }
    window.scrollTo({ top: 0, behavior: 'instant' });
  });
  await page.waitForTimeout(450);
}

/**
 * Runs axe-core and returns the serious and critical violations with a readable summary.
 * Moderate and minor findings are reported in the test output but do not fail the suite.
 */
export async function axeViolations(page: Page): Promise<string[]> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
    .analyze();
  const summarise = (impact: string) =>
    results.violations
      .filter((violation) => violation.impact === impact)
      .map(
        (violation) =>
          `${violation.id} (${violation.impact}): ${violation.help} — ${violation.nodes
            .slice(0, 3)
            .map((node) => node.target.join(' '))
            .join(', ')}`,
      );
  const minor = [...summarise('moderate'), ...summarise('minor')];
  if (minor.length > 0) {
    console.warn(`axe (non-blocking) on ${page.url()}:\n  ${minor.join('\n  ')}`);
  }
  return [...summarise('critical'), ...summarise('serious')];
}

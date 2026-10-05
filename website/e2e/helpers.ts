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

/**
 * Asserts that the page fits the viewport width, i.e. a phone cannot pan it sideways. Content that
 * is wider on purpose (code blocks, tables, chip rows) must scroll inside its own box. On failure the
 * message names the outermost elements that stick out and are not clipped by a scrolling ancestor.
 */
export async function expectNoSidewaysScroll(page: Page): Promise<void> {
  const viewportWidth = page.viewportSize()?.width;
  if (!viewportWidth) throw new Error('expectNoSidewaysScroll needs a fixed viewport size');
  const result = await page.evaluate((width) => {
    const isClipped = (element: Element) => {
      for (
        let node = element.parentElement;
        node && node !== document.body;
        node = node.parentElement
      ) {
        const style = getComputedStyle(node);
        if (style.overflowX !== 'visible' || style.position === 'fixed') return true;
      }
      return false;
    };
    const offenders: string[] = [];
    for (const element of Array.from(document.querySelectorAll('body *'))) {
      if (getComputedStyle(element).position === 'fixed') continue;
      const rect = element.getBoundingClientRect();
      if (rect.width === 0 || rect.right <= width + 0.5 || isClipped(element)) continue;
      if (offenders.length >= 5) break;
      const text = (element.textContent ?? '').trim().replace(/\s+/g, ' ').slice(0, 40);
      offenders.push(
        `<${element.tagName.toLowerCase()} class="${(element.getAttribute('class') ?? '').slice(0, 60)}"> ${Math.round(rect.width)}px wide "${text}"`,
      );
    }
    return { scrollWidth: document.documentElement.scrollWidth, offenders };
  }, viewportWidth);
  expect(
    result.scrollWidth,
    `page is ${result.scrollWidth}px wide in a ${viewportWidth}px viewport:\n  ${result.offenders.join('\n  ')}`,
  ).toBeLessThanOrEqual(viewportWidth);
}

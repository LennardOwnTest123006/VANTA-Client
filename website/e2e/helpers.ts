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

/** Waits until the React app has rendered the main landmark. */
export async function waitForApp(page: Page): Promise<void> {
  await expect(page.getByRole('main')).toBeVisible();
  await page.waitForLoadState('networkidle');
}

import { expect, test } from '@playwright/test';
import { revealAll, waitForApp } from './helpers';
import { routes } from './routes';

/**
 * Captures full-page screenshots of every route for visual review. The files are written to
 * test-results/ (ignored by git) and attached to the report.
 */
for (const route of routes.filter((r) => !r.noindex)) {
  test(`screenshot ${route.path}`, async ({ page }, testInfo) => {
    await page.goto(route.path);
    await waitForApp(page);
    await revealAll(page);
    const name = `${route.path === '/' ? 'home' : route.path.slice(1).replace(/\//g, '-')}-${testInfo.project.name}`;
    const path = testInfo.outputPath(`${name}.png`);
    await page.screenshot({ path, fullPage: true });
    await testInfo.attach(name, { path, contentType: 'image/png' });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
}

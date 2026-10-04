import { expect, test } from '@playwright/test';
import { waitForApp } from './helpers';

/**
 * Captures full-page screenshots of the home page for visual review. The files are written to
 * test-results/screenshots/ (ignored by git) and attached to the report.
 */
test('home page screenshot', async ({ page }, testInfo) => {
  await page.goto('/');
  await waitForApp(page);
  // Reveal animations: scroll through the page so every section becomes visible, then return to top.
  await page.evaluate(async () => {
    const step = window.innerHeight / 2;
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo({ top: y, behavior: 'instant' });
      await new Promise((resolve) => setTimeout(resolve, 80));
    }
    window.scrollTo({ top: 0, behavior: 'instant' });
  });
  await page.waitForTimeout(600);
  const path = testInfo.outputPath(`home-${testInfo.project.name}.png`);
  await page.screenshot({ path, fullPage: true });
  await testInfo.attach(`home-${testInfo.project.name}`, { path, contentType: 'image/png' });
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});

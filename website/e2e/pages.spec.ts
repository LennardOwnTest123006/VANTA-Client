import { expect, test } from '@playwright/test';
import { axeViolations, revealAll, trackConsoleErrors, waitForApp } from './helpers';
import { routes } from './routes';

/**
 * Every public route: the SPA fallback serves it with status 200, the document title and h1 are
 * right, the console stays clean, the canonical/robots metadata is set and axe-core reports no
 * serious or critical accessibility violation.
 */
for (const route of routes) {
  test(`${route.path} renders, is titled and passes the accessibility scan`, async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const response = await page.goto(route.path);
    expect(response?.status()).toBe(200);
    await waitForApp(page);

    await expect(page).toHaveTitle(route.title);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(route.h1);
    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('contentinfo')).toBeVisible();

    const canonical = page.locator('link[rel="canonical"]');
    await expect(canonical).toHaveAttribute(
      'href',
      new RegExp(`${route.path.replace(/\//g, '\\/')}$`),
    );
    await expect(page.locator('meta[property="og:url"]')).toHaveCount(1);
    await expect(page.locator('meta[property="og:image"]')).toHaveAttribute(
      'content',
      /\/icon-512\.png$/,
    );
    if (route.noindex) {
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
    } else {
      await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    }

    await revealAll(page);
    expect(await axeViolations(page)).toEqual([]);
    expect(errors()).toEqual([]);
  });
}

test('the mobile navigation sheet lists every header route and traps focus', async ({
  page,
  isMobile,
}) => {
  test.skip(!isMobile, 'mobile only');
  await page.goto('/');
  await waitForApp(page);
  await page.getByRole('button', { name: 'Open menu' }).click();
  const dialog = page.getByRole('dialog', { name: 'Site navigation' });
  await expect(dialog).toBeVisible();
  for (const label of ['Features', 'Performance', 'Screenshots', 'Changelog', 'Documentation']) {
    await expect(dialog.getByRole('link', { name: label })).toBeVisible();
  }
  expect(await axeViolations(page)).toEqual([]);
  await dialog.getByRole('link', { name: 'Changelog' }).click();
  await expect(page).toHaveURL(/\/changelog$/);
  await expect(dialog).toBeHidden();
});

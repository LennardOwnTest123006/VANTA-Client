import { expect, test } from '@playwright/test';
import { axeViolations, trackConsoleErrors, waitForApp } from './helpers';

/**
 * Runs against a second build whose environment configures the support e-mail and Discord URL
 * (see `webServer` in playwright.config.ts). The values are test fixtures, not real channels.
 */
test('support page shows the configured e-mail and Discord channels', async ({ page }) => {
  const errors = trackConsoleErrors(page);
  await page.goto('/support');
  await waitForApp(page);
  await expect(page.getByRole('link', { name: /Write an e-mail/ })).toHaveAttribute(
    'href',
    'mailto:help@example.org',
  );
  await expect(page.getByRole('link', { name: /Join the Discord/ })).toHaveAttribute(
    'href',
    'https://discord.example/invite',
  );
  await expect(page.getByRole('link', { name: /Open an issue/ })).toBeVisible();
  await expect(page.getByText(/will be announced/)).toHaveCount(0);
  await expect(
    page.getByRole('contentinfo').locator('a[href="mailto:help@example.org"]').first(),
  ).toBeVisible();
  expect(await axeViolations(page)).toEqual([]);
  expect(errors()).toEqual([]);
});

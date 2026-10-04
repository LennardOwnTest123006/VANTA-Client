import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';

test.describe('navigation', () => {
  test('header links reach /features and /download and mark the active route', async ({
    page,
    isMobile,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/');
    await waitForApp(page);

    if (isMobile) {
      await page.getByRole('button', { name: 'Open menu' }).click();
      const dialog = page.getByRole('dialog', { name: 'Site navigation' });
      await expect(dialog).toBeVisible();
      await dialog.getByRole('link', { name: 'Features' }).click();
      await expect(dialog).toBeHidden();
    } else {
      await page
        .getByRole('navigation', { name: 'Primary' })
        .getByRole('link', { name: 'Features' })
        .click();
    }
    await expect(page).toHaveURL(/\/features$/);
    await expect(page).toHaveTitle('Features — VANTA Client');

    if (!isMobile) {
      await expect(
        page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Features' }),
      ).toHaveAttribute('aria-current', 'page');
    }

    await page.goto('/performance');
    await waitForApp(page);
    await expect(page).toHaveTitle('Performance — VANTA Client');
    await expect(page.getByRole('table').first()).toContainText('Render Distance');

    expect(errors()).toEqual([]);
  });

  test('mobile menu closes with Escape and restores focus', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'mobile only');
    await page.goto('/');
    await waitForApp(page);
    const toggle = page.getByRole('button', { name: 'Open menu' });
    await toggle.click();
    const dialog = page.getByRole('dialog', { name: 'Site navigation' });
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('button', { name: 'Open menu' })).toBeFocused();
  });

  test('unknown routes render the 404 page without console errors', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/this-page-does-not-exist');
    await waitForApp(page);
    await expect(page).toHaveTitle('Page not found — VANTA Client');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('This page does not exist.');
    await expect(page.getByText('/this-page-does-not-exist')).toBeVisible();
    await page.getByRole('link', { name: 'Back to the home page' }).click();
    await expect(page).toHaveURL(/\/$/);
    expect(errors()).toEqual([]);
  });

  test('download page shows the honest release state', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/download');
    await waitForApp(page);
    const launcher = page.getByRole('article', { name: 'VANTA Launcher' });
    await expect(launcher).toBeVisible();
    const button = launcher.getByRole('button', { name: 'Download launcher' });
    const link = launcher.getByRole('link', { name: 'Download launcher' });
    if (await button.count()) {
      await expect(button).toBeDisabled();
      await expect(launcher.getByText('Not published yet — release pending')).toBeVisible();
    } else {
      await expect(link).toHaveAttribute('href', /^https:\/\//);
    }
    await expect(page.getByText(/certutil -hashfile/)).toBeVisible();
    expect(errors()).toEqual([]);
  });
});

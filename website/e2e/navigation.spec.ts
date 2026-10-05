import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';
import { offeredRelease, upcomingRelease } from './repo-state';

/**
 * A release asset of this repository for one version: the only place the download page may link
 * files to.
 */
const releaseAsset = (product: string, version: string) =>
  new RegExp(
    `^https://github\\.com/LennardOwnTest123006/VANTA-Client/releases/download/${product}-v${version.replace(/\./g, '\\.')}/[^/]+$`,
  );

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

  test('download page offers the newest published release, or the honest empty state while nothing is published', async ({
    page,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/download');
    await waitForApp(page);
    const cards = [
      { name: 'VANTA Launcher', cta: 'Download launcher', product: 'launcher' },
      { name: 'VANTA Client (jar)', cta: 'Download client jar', product: 'client' },
    ] as const;
    for (const { name, cta, product } of cards) {
      const card = page.getByRole('article', { name });
      await expect(card).toBeVisible();
      // Expectations come from shared/releases: a committed but unpublished newer version (e.g.
      // 1.0.1 before the release workflow ran) must not hide the published one (1.0.0).
      const offered = offeredRelease(product);
      const upcoming = upcomingRelease(product);
      expect(offered, `a ${product} manifest in shared/releases`).toBeDefined();
      if (!offered) continue;
      await expect(card.getByText(offered.version, { exact: true }).first()).toBeVisible();
      const files = card.getByRole('list', { name: 'All files in this release' });
      if (!offered.published) {
        // Not published: a disabled button, the notice, and no download link anywhere on the card.
        await expect(files).toHaveCount(0);
        await expect(card.getByRole('button', { name: cta })).toBeDisabled();
        await expect(card.getByText('Not published yet — release pending')).toBeVisible();
        expect(await card.locator('a[href*="/releases/download/"]').count()).toBe(0);
        await expect(card.getByRole('link', { name: 'Release page on GitHub' })).toHaveCount(0);
      } else {
        // Published: the main button and every listed file point at an asset of that release.
        const asset = releaseAsset(product, offered.version);
        await expect(card.getByText('Not published yet — release pending')).toHaveCount(0);
        await expect(card.getByRole('link', { name: cta })).toHaveAttribute('href', asset);
        const links = files.getByRole('link');
        expect(await links.count()).toBeGreaterThan(0);
        for (const href of await links.evaluateAll((anchors) =>
          anchors.map((a) => a.getAttribute('href') ?? ''),
        )) {
          expect(href).toMatch(asset);
        }
        await expect(files.getByRole('listitem')).toHaveCount(offered.fileNames.length);
        await expect(card.getByRole('link', { name: 'Release page on GitHub' })).toHaveAttribute(
          'href',
          new RegExp(`/releases/tag/${product}-v${offered.version.replace(/\./g, '\\.')}$`),
        );
      }
      const note = card.getByRole('note', { name: /^Upcoming version/ });
      if (upcoming) {
        await expect(note).toHaveAccessibleName(`Upcoming version ${upcoming.version}`);
        await expect(note).toContainText('is not published yet');
        expect(await card.locator(`a[href*="-v${upcoming.version}/"]`).count()).toBe(0);
      } else {
        await expect(note).toHaveCount(0);
      }
    }
    await expect(page.getByRole('region', { name: 'How to install' })).toBeVisible();
    await expect(page.getByText(/certutil -hashfile/)).toBeVisible();
    expect(errors()).toEqual([]);
  });
});

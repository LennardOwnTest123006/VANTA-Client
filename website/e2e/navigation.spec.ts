import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';
import { changelogIds, offeredBundle, offeredRelease, upcomingRelease } from './repo-state';

/**
 * A release asset of this repository under one tag: the only place the download page may link files
 * to. Product releases use the tag `<product>-v<version>`, the full release zip the tag `v<version>`.
 */
const releaseAsset = (tag: string) =>
  new RegExp(
    `^https://github\\.com/LennardOwnTest123006/VANTA-Client/releases/download/${tag.replace(/\./g, '\\.')}/[^/]+$`,
  );

const escapeVersion = (version: string) => version.replace(/\./g, '\\.');

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
      const card = page.getByRole('article', { name, exact: true });
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
        const asset = releaseAsset(`${product}-v${offered.version}`);
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
          new RegExp(`/releases/tag/${product}-v${escapeVersion(offered.version)}$`),
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

    // The hero names the offered versions literally as the latest ones.
    const latest = page.getByRole('group', { name: 'Latest version' });
    await expect(latest).toBeVisible();
    await expect(latest.getByText('Latest version', { exact: true })).toBeVisible();
    for (const product of ['client', 'launcher'] as const) {
      const offered = offeredRelease(product);
      if (!offered) continue;
      const label = product === 'client' ? 'VANTA Client' : 'VANTA Launcher';
      await expect(latest).toContainText(`${label} ${offered.version}`);
    }

    // The full release zip: a third card exactly when shared/releases/bundles has a published
    // stable manifest, never a dead link otherwise.
    const bundle = offeredBundle();
    const bundleCard = page.getByRole('article', { name: 'Full release (zip)' });
    const zipButton = page.getByRole('link', { name: 'Download full release (.zip)' });
    if (bundle) {
      await expect(bundleCard).toBeVisible();
      const asset = releaseAsset(`v${bundle.version}`);
      await expect(
        bundleCard.getByRole('link', { name: 'Download full release (.zip)' }),
      ).toHaveAttribute('href', asset);
      await expect(
        bundleCard.getByRole('link', { name: 'Download full release (.zip)' }),
      ).toHaveAttribute('href', bundle.downloadUrl);
      await expect(bundleCard.getByText(bundle.fileName, { exact: true })).toBeVisible();
      await expect(bundleCard.getByText(bundle.version, { exact: true }).first()).toBeVisible();
      for (const href of await bundleCard
        .locator('a[href*="/releases/download/"]')
        .evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href') ?? ''))) {
        expect(href).toMatch(asset);
      }
      await expect(
        bundleCard.getByRole('link', { name: 'Release page on GitHub' }),
      ).toHaveAttribute('href', new RegExp(`/releases/tag/v${escapeVersion(bundle.version)}$`));
      // The card says so when the zip holds other versions than the cards offer.
      const same =
        offeredRelease('client')?.version === bundle.clientVersion &&
        offeredRelease('launcher')?.version === bundle.launcherVersion;
      await expect(bundleCard.getByRole('note', { name: 'Versions in this zip' })).toHaveCount(
        same ? 0 : 1,
      );
      // The contents fold open and list every file of the manifest, without links.
      const contents = bundleCard.getByRole('list', { name: /^Files in the zip/ });
      await bundleCard.getByText(/^Files in the zip/).click();
      await expect(contents).toBeVisible();
      await expect(contents.getByRole('listitem')).toHaveCount(bundle.contentPaths.length);
      await expect(contents.getByRole('link')).toHaveCount(0);
      for (const path of bundle.contentPaths.slice(0, 3)) {
        await expect(contents.getByText(path, { exact: true })).toBeVisible();
      }
    } else {
      await expect(bundleCard).toHaveCount(0);
      await expect(zipButton).toHaveCount(0);
      expect(await page.locator('a[href*="/releases/download/v"]').count()).toBe(0);
    }

    // "What's new": a column per offered version that has release notes, else no section.
    const ids = changelogIds();
    const withNotes = (['client', 'launcher'] as const).filter((product) => {
      const offered = offeredRelease(product);
      return offered !== undefined && ids.includes(`${product}-${offered.version}`);
    });
    const whatsNew = page.locator('#whats-new');
    if (withNotes.length === 0) {
      await expect(whatsNew).toHaveCount(0);
    } else {
      await expect(whatsNew).toBeVisible();
      await expect(whatsNew.getByRole('heading', { level: 2 })).toContainText("What's new in");
      await expect(whatsNew.getByRole('article')).toHaveCount(withNotes.length);
      for (const product of withNotes) {
        const offered = offeredRelease(product);
        if (!offered) continue;
        const label = product === 'client' ? 'VANTA Client' : 'VANTA Launcher';
        const column = whatsNew.getByRole('article', { name: `${label} ${offered.version}` });
        await expect(column).toBeVisible();
        const bullets = await column.getByRole('listitem').count();
        expect(bullets).toBeGreaterThan(0);
        expect(bullets).toBeLessThanOrEqual(5);
        await expect(
          column.getByRole('link', { name: `Full release notes for ${label} ${offered.version}` }),
        ).toHaveAttribute('href', `/changelog#${product}-${offered.version}`);
      }
    }

    await expect(page.getByRole('region', { name: 'How to install' })).toBeVisible();
    await expect(page.getByText(/certutil -hashfile/)).toBeVisible();
    expect(errors()).toEqual([]);
  });
});

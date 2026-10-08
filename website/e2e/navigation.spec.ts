import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';
import {
  changelogIds,
  launcherCrossPlatformFileNames,
  launcherSetupFileNames,
  localAiManifest,
  offeredBundle,
  offeredRelease,
  olderBundles,
  olderReleases,
  primaryFileName,
  upcomingRelease,
} from './repo-state';

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

    // The three labelled sections, in this order: WINDOWS, CROSS-PLATFORM, COMPLETE RELEASE.
    const sections = page.locator('#downloads > section');
    await expect(sections).toHaveCount(3);
    await expect(sections.nth(0)).toHaveAttribute('id', 'windows');
    await expect(sections.nth(1)).toHaveAttribute('id', 'cross-platform');
    await expect(sections.nth(2)).toHaveAttribute('id', 'complete-release');
    await expect(sections.nth(0).getByText('Windows', { exact: true }).first()).toBeVisible();
    await expect(
      sections.nth(1).getByText('Cross-platform', { exact: true }).first(),
    ).toBeVisible();
    await expect(
      sections.nth(2).getByText('Complete release', { exact: true }).first(),
    ).toBeVisible();
    // The launcher setup is the only card under WINDOWS; the client and the launcher jars are
    // under CROSS-PLATFORM.
    await expect(sections.nth(0).getByRole('article')).toHaveCount(1);
    await expect(
      sections.nth(0).getByRole('article', { name: 'VANTA Launcher', exact: true }),
    ).toBeVisible();
    await expect(sections.nth(1).getByRole('article')).toHaveCount(2);
    await expect(
      sections.nth(1).getByRole('article', { name: 'VANTA Client (jar)', exact: true }),
    ).toBeVisible();
    await expect(
      sections
        .nth(1)
        .getByRole('article', { name: 'VANTA Launcher jars and Linux app', exact: true }),
    ).toBeVisible();

    const cards = [
      {
        name: 'VANTA Launcher',
        cta: 'Download launcher',
        product: 'launcher',
        listName: 'Windows files in this release',
        listed: (offered: { fileNames: readonly string[] }) =>
          launcherSetupFileNames(offered as Parameters<typeof launcherSetupFileNames>[0]),
      },
      {
        name: 'VANTA Client (jar)',
        cta: 'Download client jar',
        product: 'client',
        listName: 'All files in this release',
        listed: (offered: { fileNames: readonly string[] }) => [...offered.fileNames],
      },
    ] as const;
    for (const { name, cta, product, listName, listed } of cards) {
      const card = page.getByRole('article', { name, exact: true });
      await expect(card).toBeVisible();
      // Expectations come from shared/releases: a committed but unpublished newer version (e.g.
      // 1.0.1 before the release workflow ran) must not hide the published one (1.0.0).
      const offered = offeredRelease(product);
      const upcoming = upcomingRelease(product);
      expect(offered, `a ${product} manifest in shared/releases`).toBeDefined();
      if (!offered) continue;
      await expect(card.getByText(offered.version, { exact: true }).first()).toBeVisible();
      const files = card.getByRole('list', { name: listName });
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
        const names = listed(offered);
        await expect(files.getByRole('listitem')).toHaveCount(names.length);
        for (const fileName of names) {
          // The name appears twice per row (the visible line and the link's hidden suffix).
          await expect(files.getByText(fileName, { exact: true }).first()).toBeVisible();
        }
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

    // The launcher jars and the Linux app: the rest of the offered launcher release, under
    // CROSS-PLATFORM, each file linked to an asset of that release; the pending state otherwise.
    const launcher = offeredRelease('launcher');
    const jars = page.getByRole('article', {
      name: 'VANTA Launcher jars and Linux app',
      exact: true,
    });
    if (launcher) {
      await expect(jars.getByText(launcher.version, { exact: true }).first()).toBeVisible();
      const jarList = jars.getByRole('list', {
        name: 'Launcher files for Linux, macOS and Java in this release',
      });
      const expected = launcherCrossPlatformFileNames(launcher);
      if (launcher.published) {
        await expect(jarList.getByRole('listitem')).toHaveCount(expected.length);
        for (const fileName of expected) {
          await expect(jarList.getByText(fileName, { exact: true }).first()).toBeVisible();
        }
        const asset = releaseAsset(`launcher-v${launcher.version}`);
        for (const href of await jarList
          .getByRole('link')
          .evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href') ?? ''))) {
          expect(href).toMatch(asset);
        }
        expect(await jarList.locator('a[href$=".msi"], a[href$=".exe"]').count()).toBe(0);
      } else {
        await expect(jarList).toHaveCount(0);
        await expect(jars.getByText(/Not published yet/)).toBeVisible();
        expect(await jars.locator('a[href*="/releases/download/"]').count()).toBe(0);
      }
      // Every launcher file is listed exactly once across the two cards.
      expect(launcherSetupFileNames(launcher).length + expected.length).toBe(
        launcher.fileNames.length,
      );
    }

    // The Local AI note: built from shared/local-ai/local-ai.json, present exactly when that file is.
    const localAi = localAiManifest();
    const note = page.locator('#local-ai');
    if (localAi) {
      await expect(note).toBeVisible();
      await expect(note.getByRole('heading', { level: 2 })).toHaveText(
        'Optional, and it asks first.',
      );
      await expect(note.getByText(localAi.runtimeTag, { exact: false }).first()).toBeVisible();
      await expect(note.getByText(localAi.modelFile, { exact: false }).first()).toBeVisible();
      for (const host of localAi.hosts) {
        await expect(note.getByText(host, { exact: false }).first()).toBeVisible();
      }
      await expect(note.getByText(`${localAi.diskMb} MB`, { exact: false })).toBeVisible();
      await expect(note.getByText(`${localAi.ramMb} MB`, { exact: false })).toBeVisible();
      const archives = note.getByRole('list', { name: 'Runtime archive per system' });
      await expect(archives.getByRole('listitem')).toHaveCount(localAi.archiveFiles.length);
      await expect(note.getByText('127.0.0.1', { exact: false }).first()).toBeVisible();
      // The note never links a download: the Local AI is not a release file.
      expect(await note.locator('a[href*="/releases/download/"]').count()).toBe(0);
    } else {
      await expect(note).toHaveCount(0);
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

    // "Older versions": every published release of shared/releases older than the offered one, per
    // product, and every older published full release zip; no section while nothing is older.
    const older = page.locator('#older-versions');
    const jump = page.getByRole('link', { name: 'Older versions on this page' });
    const olderZips = olderBundles();
    const olderByProduct = (['launcher', 'client'] as const).map((product) => ({
      product,
      label: product === 'client' ? 'VANTA Client' : 'VANTA Launcher',
      releases: olderReleases(product),
    }));
    if (olderZips.length === 0 && olderByProduct.every(({ releases }) => releases.length === 0)) {
      await expect(older).toHaveCount(0);
      await expect(jump).toHaveCount(0);
    } else {
      await expect(older).toBeVisible();
      await expect(jump).toHaveAttribute('href', '#older-versions');
      await expect(older.getByRole('heading', { level: 2 })).toHaveText(
        'Every earlier release stays available.',
      );
      for (const { product, label, releases } of olderByProduct) {
        const list = older.getByRole('list', { name: label });
        if (releases.length === 0) {
          await expect(list).toHaveCount(0);
          continue;
        }
        const rows = list.getByRole('listitem');
        await expect(rows).toHaveCount(releases.length);
        // Newest first, one row per older published version, never an unpublished one.
        for (const [index, release] of releases.entries()) {
          const row = rows.nth(index);
          await expect(row.getByText(release.version, { exact: true })).toBeVisible();
          const download = row.getByRole('link', { name: `Download ${label} ${release.version}` });
          await expect(download).toHaveAttribute(
            'href',
            releaseAsset(`${product}-v${release.version}`),
          );
          const file = primaryFileName(release);
          expect(file, `${product} ${release.version} has a primary file`).toBeDefined();
          if (file) {
            await expect(download).toHaveAttribute(
              'href',
              new RegExp(`/${file.replace(/[.+]/g, '\\$&')}$`),
            );
            await expect(row.getByText(file, { exact: true })).toBeVisible();
          }
          await expect(
            row.getByRole('link', {
              name: `Release page on GitHub for ${label} ${release.version}`,
            }),
          ).toHaveAttribute(
            'href',
            new RegExp(`/releases/tag/${product}-v${escapeVersion(release.version)}$`),
          );
          const notes = row.getByRole('link', {
            name: `Release notes for ${label} ${release.version}`,
          });
          if (ids.includes(`${product}-${release.version}`)) {
            await expect(notes).toHaveAttribute('href', `/changelog#${product}-${release.version}`);
          } else {
            await expect(notes).toHaveCount(0);
          }
        }
        const upcoming = upcomingRelease(product);
        if (upcoming) {
          expect(await list.locator(`a[href*="-v${upcoming.version}/"]`).count()).toBe(0);
        }
      }
      const zips = older.getByRole('list', { name: 'Full release zip' });
      if (olderZips.length === 0) {
        await expect(zips).toHaveCount(0);
      } else {
        const rows = zips.getByRole('listitem');
        await expect(rows).toHaveCount(olderZips.length);
        for (const [index, zip] of olderZips.entries()) {
          const row = rows.nth(index);
          await expect(
            row.getByRole('link', { name: `Download Full release zip ${zip.version}` }),
          ).toHaveAttribute('href', zip.downloadUrl);
          await expect(row.getByText(zip.fileName, { exact: true })).toBeVisible();
        }
      }
      // Every download in the section is an asset of this repository.
      for (const href of await older
        .locator('a[href*="/releases/download/"]')
        .evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href') ?? ''))) {
        expect(href).toMatch(
          /^https:\/\/github\.com\/LennardOwnTest123006\/VANTA-Client\/releases\/download\/[^/]+\/[^/]+$/,
        );
      }
    }

    await expect(page.getByRole('region', { name: 'How to install' })).toBeVisible();
    await expect(page.getByText(/certutil -hashfile/)).toBeVisible();
    expect(errors()).toEqual([]);
  });
});

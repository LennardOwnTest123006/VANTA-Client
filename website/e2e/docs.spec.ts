import { expect, test } from '@playwright/test';
import { axeViolations, trackConsoleErrors, waitForApp } from './helpers';

test.describe('documentation', () => {
  test('search returns results for "fps" and "profile" and navigates with the keyboard', async ({
    page,
    isMobile,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/documentation');
    await waitForApp(page);

    if (isMobile) {
      await page.getByRole('button', { name: 'Browse documentation' }).click();
      await expect(page.getByRole('dialog', { name: 'Documentation' })).toBeVisible();
    } else {
      // The "/" shortcut focuses the search box.
      await page.keyboard.press('/');
    }
    const input = page.getByRole('combobox', { name: 'Search the documentation' }).first();
    if (!isMobile) await expect(input).toBeFocused();

    await input.fill('fps');
    const list = page.getByRole('listbox', { name: 'Search results' }).first();
    await expect(list).toBeVisible();
    const options = list
      .getByRole('option')
      .filter({ hasNot: page.locator('[aria-hidden="true"]') });
    expect(await options.count()).toBeGreaterThan(1);
    await expect(list.locator('mark').first()).toBeVisible();
    expect(await axeViolations(page)).toEqual([]);

    await input.fill('profile');
    const results = list.getByRole('option');
    await expect(results.first()).toContainText('Profiles');
    // Arrow keys move the active option (checked at every step, so the test does not depend on how
    // other pages rank further down); Enter opens the active one, here the best match again.
    await expect(results.first()).toHaveAttribute('aria-selected', 'true');
    await input.press('ArrowDown');
    await expect(results.nth(1)).toHaveAttribute('aria-selected', 'true');
    await input.press('ArrowUp');
    await expect(results.first()).toHaveAttribute('aria-selected', 'true');
    await input.press('Enter');
    await expect(page).toHaveURL(/\/documentation\/profiles/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Profiles');
    expect(errors()).toEqual([]);
  });

  test('a page has heading anchors, a working table of contents, cross-links and prev/next', async ({
    page,
    isMobile,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/documentation/installation');
    await waitForApp(page);

    // Every TOC entry points at an existing heading id.
    const toc = isMobile
      ? page.locator('details').filter({ hasText: 'On this page' })
      : page.getByRole('navigation', { name: 'On this page' });
    if (isMobile) await toc.locator('summary').click();
    const anchors = await toc
      .locator('a[href^="#"]')
      .evaluateAll((links) => links.map((link) => link.getAttribute('href')!.slice(1)));
    expect(anchors.length).toBeGreaterThan(4);
    for (const id of anchors) {
      await expect(page.locator(`[id="${id}"]`), `missing #${id}`).toHaveCount(1);
    }
    await toc.locator('a[href="#2-verify-the-checksum"]').click();
    await expect(page).toHaveURL(/#2-verify-the-checksum$/);

    // Cross-links written as `fabric.md#...` were mapped to website routes.
    const article = page.getByRole('article');
    const crossLink = article.locator('a[href^="/documentation/fabric#"]').first();
    await expect(crossLink).toBeVisible();
    expect(await article.locator('.markdown a[href$=".md"]').count()).toBe(0);
    await crossLink.click();
    await expect(page).toHaveURL(/\/documentation\/fabric#manual-installation/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Fabric');

    // Prev/next and the edit link.
    await page
      .getByRole('navigation', { name: 'Pagination' })
      .getByRole('link', { name: /Previous/ })
      .click();
    await expect(page).toHaveURL(/\/documentation\/java-21$/);
    await expect(page.getByRole('link', { name: /Edit on GitHub/ })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/blob/HEAD/docs/java-21.md',
    );
    expect(errors()).toEqual([]);
  });

  test('legacy documentation URLs redirect to the legal pages and unknown slugs show the 404', async ({
    page,
  }) => {
    await page.goto('/documentation/privacy');
    await expect(page).toHaveURL(/\/privacy$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Privacy');
    await page.goto('/documentation/index');
    await expect(page).toHaveURL(/\/documentation$/);
    await page.goto('/documentation/nope');
    await waitForApp(page);
    await expect(page).toHaveTitle('Page not found — VANTA Client');
  });

  test('the sidebar drawer on small screens opens, traps focus and closes', async ({
    page,
    isMobile,
  }) => {
    test.skip(!isMobile, 'mobile only');
    await page.goto('/documentation/hud');
    await waitForApp(page);
    await page.getByRole('button', { name: 'Browse documentation' }).click();
    const dialog = page.getByRole('dialog', { name: 'Documentation' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('combobox')).toBeFocused();
    await expect(dialog.getByRole('link', { name: 'HUD and crosshair' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('button', { name: 'Browse documentation' })).toBeFocused();
  });
});

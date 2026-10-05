import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';
import { changelogIds, offeredRelease, repositoryManifests } from './repo-state';

test.describe('changelog', () => {
  test('groups releases, filters by product and links from the download page', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const ids = changelogIds();
    const launcherIds = ids.filter((id) => id.startsWith('launcher-'));
    await page.goto('/changelog');
    await waitForApp(page);
    const releases = page.getByRole('list', { name: 'Releases' });
    await expect(releases.getByRole('article')).toHaveCount(ids.length);
    await expect(page.locator('#client-1\\.0\\.0')).toContainText('VANTA Client 1.0.0');
    await expect(page.locator('#client-1\\.0\\.0')).toContainText('Minecraft 1.21.11');
    await expect(
      page.locator('#client-1\\.0\\.0').getByText('Added', { exact: true }),
    ).toBeVisible();

    // Notes of a version whose manifest is committed but not published carry a pending badge.
    const manifests = repositoryManifests();
    for (const id of ids) {
      const manifest = manifests.find((m) => `${m.product}-${m.version}` === id);
      const badge = page.locator(`[id="${id}"]`).getByText('Release pending', { exact: true });
      await expect(badge).toHaveCount(manifest && !manifest.published ? 1 : 0);
    }

    await page.getByRole('button', { name: 'VANTA Launcher' }).click();
    await expect(page).toHaveURL(/product=launcher/);
    await expect(releases.getByRole('article')).toHaveCount(launcherIds.length);
    await expect(page.getByRole('button', { name: 'VANTA Launcher' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await page.getByRole('button', { name: 'All' }).click();
    await expect(releases.getByRole('article')).toHaveCount(ids.length);

    // The download page links the notes of the release it offers.
    const launcher = offeredRelease('launcher')?.version ?? '';
    const anchor = `launcher-${launcher}`;
    await page.goto('/download');
    await waitForApp(page);
    await page.getByRole('link', { name: 'Full release notes' }).first().click();
    await expect(page).toHaveURL(new RegExp(`/changelog#${anchor.replace(/\./g, '\\.')}$`));
    await expect(page.locator(`[id="${anchor}"]`)).toBeInViewport();
    expect(errors()).toEqual([]);
  });
});

test.describe('news', () => {
  test('lists posts and renders an article with reading time, tags and a copy-link button', async ({
    page,
    context,
    browserName,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/news');
    await waitForApp(page);
    const card = page.getByRole('article', { name: 'Introducing VANTA Client' });
    await expect(card).toContainText('min read');
    await card.getByRole('link', { name: 'Introducing VANTA Client' }).click();
    await expect(page).toHaveURL(/\/news\/introducing-vanta$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Introducing VANTA Client');
    await expect(page.getByText('VANTA team').first()).toBeVisible();
    await expect(page.getByRole('list', { name: 'Tags' }).first()).toContainText('announcement');
    await expect(
      page.getByRole('heading', { level: 2, name: 'What VANTA is not' }),
    ).toHaveAttribute('id', 'what-vanta-is-not');
    await expect(page.locator('meta[property="og:type"]')).toHaveAttribute('content', 'article');

    if (browserName === 'chromium') {
      await context.grantPermissions(['clipboard-read', 'clipboard-write']);
      await page.getByRole('button', { name: 'Copy link to this post' }).click();
      await expect(page.getByRole('button', { name: 'Link copied' })).toBeVisible();
      const copied = await page.evaluate(() => navigator.clipboard.readText());
      expect(copied).toMatch(/\/news\/introducing-vanta$/);
    }
    expect(errors()).toEqual([]);
  });
});

test.describe('faq', () => {
  test('accordion works with the keyboard and deep links open a question', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/faq');
    await waitForApp(page);
    const first = page.getByRole('button', { name: 'Is VANTA allowed on servers?' });
    await expect(first).toHaveAttribute('aria-expanded', 'false');
    await first.focus();
    await page.keyboard.press('Enter');
    await expect(first).toHaveAttribute('aria-expanded', 'true');
    const region = page.getByRole('region', { name: 'Is VANTA allowed on servers?' });
    await expect(region).toBeVisible();
    await expect(region).toContainText('server rules vary');
    await page.keyboard.press('ArrowDown');
    await expect(page.getByRole('button', { name: /Sodium, OptiFine/ })).toBeFocused();
    await page.keyboard.press('Space');
    await expect(page.getByRole('button', { name: /Sodium, OptiFine/ })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Enter');
    await expect(first).toHaveAttribute('aria-expanded', 'false');
    await expect(region).toBeHidden();

    // Filtering and deep links.
    await page.getByRole('searchbox', { name: 'Filter questions' }).fill('offline');
    await expect(page.getByRole('button', { name: 'Can I play offline?' })).toBeVisible();
    await expect(first).toBeHidden();

    // A fresh load with a hash (not a same-document fragment change) opens the question.
    await page.goto('/about');
    await waitForApp(page);
    await page.goto('/faq#how-do-i-verify-a-download');
    await waitForApp(page);
    await expect(page.getByRole('button', { name: 'How do I verify a download?' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    const docLink = page
      .getByRole('region', { name: 'How do I verify a download?' })
      .locator('a[href^="/"]');
    if ((await docLink.count()) > 0) {
      await expect(docLink.first()).not.toHaveAttribute('href', /\.md/);
    }
    expect(errors()).toEqual([]);
  });
});

test.describe('screenshots', () => {
  test('shows the honest empty state without captures, otherwise the gallery and lightbox', async ({
    page,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/screenshots');
    await waitForApp(page);
    const tiles = page.getByRole('list', { name: 'Screenshots' });
    if ((await tiles.count()) === 0) {
      await expect(
        page.getByRole('heading', {
          name: 'Screenshots will be published with the first release.',
        }),
      ).toBeVisible();
      await expect(page.getByRole('dialog')).toHaveCount(0);
      expect(await page.locator('main img').count()).toBe(0);
    } else {
      await tiles.getByRole('button').first().click();
      const dialog = page.getByRole('dialog', { name: /Screenshot 1 of/ });
      await expect(dialog).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(dialog).toBeHidden();
    }
    expect(errors()).toEqual([]);
  });
});

test.describe('support (no channels configured)', () => {
  test('announces missing channels, keeps GitHub Issues and links every topic', async ({
    page,
  }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/support');
    await waitForApp(page);
    await expect(page.getByText('Support channels will be announced')).toBeVisible();
    await expect(page.getByRole('link', { name: /Open an issue/ })).toHaveAttribute(
      'href',
      /github\.com\/.*\/issues$/,
    );
    expect(await page.locator('a[href^="mailto:"]').count()).toBe(0);
    const topics = page.getByRole('list', { name: 'Support topics' });
    await expect(topics.getByRole('article')).toHaveCount(8);
    await topics.getByRole('link', { name: /^Checksum mismatch/ }).click();
    await expect(page).toHaveURL(/\/documentation\/troubleshooting#checksum-mismatch$/);
    await expect(page.locator('#checksum-mismatch')).toBeInViewport();
    expect(errors()).toEqual([]);
  });
});

test.describe('seo', () => {
  test('serves sitemap.xml and robots.txt that list the public routes', async ({ request }) => {
    const sitemap = await request.get('/sitemap.xml');
    expect(sitemap.status()).toBe(200);
    expect(sitemap.headers()['content-type']).toContain('xml');
    const xml = await sitemap.text();
    for (const path of [
      '/download',
      '/documentation/installation',
      '/news/introducing-vanta',
      '/privacy',
    ]) {
      expect(xml).toContain(`${path}</loc>`);
    }
    expect(xml).not.toContain('/documentation/privacy');
    const robots = await request.get('/robots.txt');
    expect(robots.status()).toBe(200);
    expect(await robots.text()).toContain('Allow: /');
  });
});

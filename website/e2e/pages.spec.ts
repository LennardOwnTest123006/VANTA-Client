import { expect, test } from '@playwright/test';
import {
  axeViolations,
  expectNoSidewaysScroll,
  revealAll,
  trackConsoleErrors,
  waitForApp,
} from './helpers';
import { routes } from './routes';

/**
 * Every public route: the SPA fallback serves it with status 200, the document title and h1 are
 * right, the console stays clean, the canonical/robots metadata is set and axe-core reports no
 * serious or critical accessibility violation. On the phone viewport the page must also fit the
 * screen width, so it cannot be panned sideways.
 */
for (const route of routes) {
  test(`${route.path} renders, is titled and passes the accessibility scan`, async ({
    page,
    isMobile,
  }) => {
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
    if (isMobile) await expectNoSidewaysScroll(page);
    expect(await axeViolations(page)).toEqual([]);
    expect(errors()).toEqual([]);
  });
}

test('the download page fits small phones without sideways scrolling', async ({
  page,
  isMobile,
}) => {
  test.skip(!isMobile, 'mobile only');
  // 390px is covered by the route test above; 320px is the narrowest common phone width. The
  // checksum commands and the release card headers must wrap or scroll inside their cards.
  for (const width of [390, 360, 320]) {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/download');
    await waitForApp(page);
    await revealAll(page);
    await expect(page.getByText(/certutil -hashfile/)).toBeVisible();
    await expectNoSidewaysScroll(page);
  }
});

test('the download buttons are on the first phone screen of their cards, before the excerpt', async ({
  page,
  isMobile,
}) => {
  test.skip(!isMobile, 'mobile only');
  // The mobile project runs at 390×844. With a card scrolled to the top of the content area (below
  // the sticky site header), its main button must be visible without scrolling further: the release
  // notes excerpt, the upcoming-version note and the file list come after it.
  await page.goto('/download');
  await waitForApp(page);
  await revealAll(page);
  const viewport = page.viewportSize();
  const siteHeader = await page.getByRole('banner').boundingBox();
  expect(viewport).toEqual({ width: 390, height: 844 });
  if (!viewport || !siteHeader) return;
  const available = viewport.height - siteHeader.height;
  for (const { name, cta } of [
    { name: 'VANTA Launcher', cta: 'Download launcher' },
    { name: 'VANTA Client (jar)', cta: 'Download client jar' },
  ]) {
    const card = page.getByRole('article', { name });
    const button = card
      .getByRole('link', { name: cta, exact: true })
      .or(card.getByRole('button', { name: cta, exact: true }));
    await expect(button).toBeVisible();
    const cardBox = await card.boundingBox();
    const buttonBox = await button.boundingBox();
    if (!cardBox || !buttonBox) throw new Error(`${name}: no layout boxes`);
    const buttonBottom = buttonBox.y + buttonBox.height;
    expect(buttonBottom - cardBox.y, `${cta} from the top of its card`).toBeLessThanOrEqual(
      available,
    );

    const excerpt = card.getByText('In this release', { exact: true });
    if ((await excerpt.count()) > 0) {
      const excerptBox = await excerpt.boundingBox();
      expect(excerptBox?.y ?? 0, `${name}: excerpt below the button`).toBeGreaterThan(buttonBottom);
      // At most three bullets, each cut to three lines; the full notes are one link away.
      const bullets = excerpt.locator('xpath=following-sibling::ul[1]/li');
      expect(await bullets.count()).toBeLessThanOrEqual(3);
      const lineCounts = await bullets.evaluateAll((items) =>
        items.map((item) => {
          const text = item.querySelector('.line-clamp-3');
          if (!text) return 99;
          const lineHeight = parseFloat(getComputedStyle(text).lineHeight);
          return text.getBoundingClientRect().height / lineHeight;
        }),
      );
      expect(lineCounts.length).toBeGreaterThan(0);
      for (const lines of lineCounts) expect(lines).toBeLessThanOrEqual(3.05);
      await expect(card.getByRole('link', { name: 'Full release notes' })).toBeVisible();
    }
    const note = card.getByRole('note', { name: /^Upcoming version/ });
    if ((await note.count()) > 0) {
      expect((await note.boundingBox())?.y ?? 0).toBeGreaterThan(buttonBottom);
    }
  }
});

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

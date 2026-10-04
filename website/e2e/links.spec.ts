import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';

/**
 * Small link crawler: starts at the home page, follows every same-origin link found on the pages it
 * visits (same-origin anchors only, hashes stripped) and asserts that none of them lands on the
 * 404 page or produces console errors.
 */
test('every internal link on the public pages resolves', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-chromium', 'crawl once, on desktop');
  test.setTimeout(120_000);

  const errors = trackConsoleErrors(page);
  const queue = ['/'];
  const visited = new Set<string>();
  const broken: string[] = [];

  while (queue.length > 0) {
    const path = queue.shift()!;
    if (visited.has(path)) continue;
    visited.add(path);

    await page.goto(path);
    await waitForApp(page);

    const title = await page.title();
    if (title.startsWith('Page not found')) {
      broken.push(path);
      continue;
    }

    const hrefs = await page
      .locator('a[href]')
      .evaluateAll((anchors) =>
        anchors.map((a) => (a as HTMLAnchorElement).getAttribute('href') ?? ''),
      );
    for (const href of hrefs) {
      if (!href.startsWith('/') || href.startsWith('//')) continue;
      const clean = href.split('#')[0]!.split('?')[0]!;
      if (clean === '' || /\.[a-z0-9]+$/i.test(clean)) continue; // files such as /fonts/*.txt
      if (!visited.has(clean)) queue.push(clean);
    }
  }

  expect(visited.size).toBeGreaterThanOrEqual(4);
  expect(broken).toEqual([]);
  expect(errors()).toEqual([]);
});

test('in-page anchors on the features page exist', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-chromium', 'desktop only');
  await page.goto('/features');
  await waitForApp(page);
  const targets = await page
    .getByRole('navigation', { name: 'On this page' })
    .locator('a[href^="#"]')
    .evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href')!.slice(1)));
  expect(targets.length).toBeGreaterThan(5);
  for (const id of targets) {
    await expect(page.locator(`#${id}`), `missing anchor #${id}`).toHaveCount(1);
  }
});

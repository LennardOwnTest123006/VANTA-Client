import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { type Locator, type Page, expect, test } from '@playwright/test';
import { waitForApp } from './helpers';

/** The hashed chunk of the Features page, which these tests make unavailable. */
const FEATURES_CHUNK = /\/assets\/FeaturesPage-[^/]+\.js$/;
const RELOAD_WARNING = 'reloading once for the new build';

/** Counts document loads (initial load and reloads) and the automatic-reload warnings of a page. */
function trackReloads(page: Page): { documents: () => number; warnings: () => number } {
  let documents = 0;
  let warnings = 0;
  page.on('request', (request) => {
    if (request.resourceType() === 'document') documents += 1;
  });
  page.on('console', (message) => {
    if (message.text().includes(RELOAD_WARNING)) warnings += 1;
  });
  return { documents: () => documents, warnings: () => warnings };
}

/** Session markers of the automatic reload (one per build that reloaded). */
async function reloadMarkers(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    Object.keys(window.sessionStorage).filter((key) => key.startsWith('vanta:stale-chunk-reload:')),
  );
}

/** Opens a long documentation page and scrolls down, like a reader in the middle of it. */
async function readDocsPage(page: Page): Promise<number> {
  await page.goto('/documentation/installation');
  await waitForApp(page);
  await page.evaluate(() => {
    window.scrollTo({ top: 2000, behavior: 'instant' });
  });
  const scrollY = await page.evaluate(() => window.scrollY);
  expect(scrollY).toBeGreaterThan(1000);
  return scrollY;
}

/**
 * Moves the mouse onto a link of the sticky header. `locator.hover()` would first scroll the element
 * into view, which moves the page by itself; a plain mouse move hovers it where it is on screen, like
 * a user does.
 */
async function hoverInPlace(page: Page, link: Locator): Promise<void> {
  const box = await link.boundingBox();
  if (!box) throw new Error('the link is not rendered');
  const viewport = page.viewportSize();
  expect(box.y + box.height).toBeLessThanOrEqual(viewport?.height ?? 0);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
}

const featuresLink = (page: Page) =>
  page.getByRole('banner').getByRole('link', { name: 'Features', exact: true });

/**
 * A tab opened before a redeploy asks for page chunks that no longer exist. When such a chunk keeps
 * a page from rendering, the site reloads once (per session and build) to pick up the new chunk
 * names; when that does not help, the error stays on the failed page only and every other page keeps
 * working. A failed prefetch (hovering a link) never reloads the page the user is reading.
 */
test('a missing page chunk reloads once, then fails only that page', async ({ page, isMobile }) => {
  test.skip(isMobile, 'the mechanism does not depend on the viewport');
  // The page and the error boundary report the failure; that is the point of this test.
  await page.route(FEATURES_CHUNK, (route) =>
    route.fulfill({ status: 404, contentType: 'text/plain', body: 'gone after a redeploy' }),
  );
  const reloads = trackReloads(page);

  await page.goto('/features');
  const alert = page.getByRole('alert');
  await expect(alert).toContainText('This page could not be displayed.');
  // Exactly one automatic reload: the first failure reloaded, the second one did not.
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  expect(reloads.documents()).toBe(2);
  expect(await reloadMarkers(page)).toHaveLength(1);

  // Navigating away clears the error; other pages are not affected by the failed chunk.
  await page.getByRole('banner').getByRole('link', { name: 'Download' }).click();
  await expect(page).toHaveURL(/\/download$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText(
    'VANTA Client for Minecraft 1.21.11',
  );
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(reloads.documents()).toBe(2);
});

test('hovering a link whose chunk is gone after a redeploy keeps the page and its scroll position', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, 'the header links are hover targets on the desktop layout only');
  // Netlify answers a removed chunk with the SPA fallback: index.html as text/html.
  await page.route(FEATURES_CHUNK, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: '<!doctype html><html><body><div id="root"></div></body></html>',
    }),
  );
  const reloads = trackReloads(page);
  const scrollY = await readDocsPage(page);
  const heading = await page.getByRole('heading', { level: 1 }).textContent();

  const prefetch = page.waitForRequest(FEATURES_CHUNK);
  await hoverInPlace(page, featuresLink(page));
  await prefetch;
  await page.waitForTimeout(750);

  expect(reloads.documents()).toBe(1);
  expect(reloads.warnings()).toBe(0);
  await expect(page).toHaveURL(/\/documentation\/installation$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading ?? '');
  expect(await page.evaluate(() => window.scrollY)).toBe(scrollY);
  expect(await reloadMarkers(page)).toHaveLength(0);

  // Opening the page is what reloads, once; the chunk is still gone, so the error is shown.
  await featuresLink(page).click();
  await expect(page).toHaveURL(/\/features$/);
  await expect(page.getByRole('alert')).toContainText('This page could not be displayed.');
  await page.waitForLoadState('networkidle');
  expect(reloads.documents()).toBe(2);
  expect(reloads.warnings()).toBe(1);
  expect(await reloadMarkers(page)).toHaveLength(1);
});

test('offline, a failed prefetch keeps the page and a failed page shows its error instead of reloading', async ({
  page,
  context,
  isMobile,
}) => {
  test.skip(isMobile, 'the header links are hover targets on the desktop layout only');
  const reloads = trackReloads(page);
  const scrollY = await readDocsPage(page);
  await context.setOffline(true);
  expect(await page.evaluate(() => navigator.onLine)).toBe(false);

  const failedPrefetch = page.waitForEvent('requestfailed', (request) =>
    FEATURES_CHUNK.test(request.url()),
  );
  await hoverInPlace(page, featuresLink(page));
  await failedPrefetch;
  await page.waitForTimeout(750);
  expect(reloads.documents()).toBe(1);
  await expect(page).toHaveURL(/\/documentation\/installation$/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(await page.evaluate(() => window.scrollY)).toBe(scrollY);

  // Opening the page cannot load it either: the error and its "Reload page" button, no reload into
  // the browser's offline page.
  await featuresLink(page).click();
  await expect(page).toHaveURL(/\/features$/);
  await expect(page.getByRole('alert')).toContainText('This page could not be displayed.');
  await expect(page.getByRole('button', { name: 'Reload page' })).toBeVisible();
  await page.waitForTimeout(750);
  expect(reloads.documents()).toBe(1);
  expect(reloads.warnings()).toBe(0);
  expect(await reloadMarkers(page)).toHaveLength(0);
  await context.setOffline(false);
});

test('the deployed files do not include the build manifest', async ({ request }) => {
  const dist = fileURLToPath(new URL('../dist/', import.meta.url));
  expect(existsSync(`${dist}index.html`)).toBe(true);
  expect(existsSync(`${dist}.vite`)).toBe(false);
  const response = await request.get('/.vite/manifest.json');
  expect(await response.text()).not.toContain('"isEntry"');
});

test('the HTML every route is served with carries no page-specific canonical or og:url', async ({
  request,
}) => {
  const content = (html: string, key: string) =>
    new RegExp(`<meta\\s+(?:property|name)="${key}"\\s+content="([^"]*)"`).exec(html)?.[1];
  // The SPA fallback serves the same index.html for deep routes; a canonical or og:url for "/" in it
  // would make every page a duplicate of the home page for crawlers that do not run JavaScript.
  for (const path of ['/', '/download', '/documentation/installation', '/changelog']) {
    const html = await (await request.get(path)).text();
    expect(html, path).toContain('<div id="root"></div>');
    expect(html, path).not.toContain('rel="canonical"');
    expect(content(html, 'og:url'), path).toBeUndefined();
    // Relative without VITE_SITE_URL at build time, absolute with it.
    const ogImage = content(html, 'og:image') ?? '';
    expect(ogImage, path).toMatch(/^(https?:\/\/[^/]+)?\/icon-512\.png$/);
    expect(content(html, 'twitter:image'), path).toBe(ogImage);
  }
});

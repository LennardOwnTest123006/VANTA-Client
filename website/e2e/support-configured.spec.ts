import { expect, test } from '@playwright/test';
import { axeViolations, trackConsoleErrors, waitForApp } from './helpers';

/**
 * Runs against a second build whose environment configures the support e-mail, the Discord URL and
 * the site URL (see `webServer` in playwright.config.ts). The values are test fixtures, not real
 * channels or hosts.
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

test('a build with VITE_SITE_URL serves absolute images, and no canonical for "/" on deep routes', async ({
  request,
  page,
}) => {
  // The same index.html answers every route (SPA fallback). It carries the absolute social images,
  // which are the same for every page, but no og:url or canonical: those would name the home page
  // for /download as well. PageMeta sets both for the current page at runtime.
  for (const path of ['/', '/download', '/documentation/installation']) {
    const html = await (await request.get(path)).text();
    expect(html, path).toContain(
      '<meta property="og:image" content="https://vanta.example/icon-512.png" />',
    );
    expect(html, path).toContain(
      '<meta name="twitter:image" content="https://vanta.example/icon-512.png" />',
    );
    expect(html, path).not.toContain('og:url');
    expect(html, path).not.toContain('rel="canonical"');
  }

  for (const path of ['/', '/download']) {
    await page.goto(path);
    await waitForApp(page);
    const url = `https://vanta.example${path}`;
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', url);
    await expect(page.locator('meta[property="og:url"]')).toHaveCount(1);
    await expect(page.locator('meta[property="og:url"]')).toHaveAttribute('content', url);
  }
});

test('canonical and og:url name the route as defined, without query, hash or trailing slash', async ({
  page,
}) => {
  // React Router renders these variants (case-insensitive matching, trailing slash ignored); the
  // canonical URL must still be the one of the route, so variants do not compete in search results.
  const cases = [
    ['/?ref=chat#top', '/'],
    ['/download/', '/download'],
    ['/Download/?utm_source=chat#verify', '/download'],
    ['/DOCUMENTATION/installation/', '/documentation/installation'],
  ] as const;
  for (const [path, expected] of cases) {
    await page.goto(path);
    await waitForApp(page);
    const url = `https://vanta.example${expected}`;
    await expect(page.locator('link[rel="canonical"]'), path).toHaveAttribute('href', url);
    await expect(page.locator('meta[property="og:url"]'), path).toHaveAttribute('content', url);
  }
});

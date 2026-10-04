import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright end-to-end configuration.
 *
 * Runs against the production build served by `vite preview`. Uses the preinstalled Chromium when
 * `PLAYWRIGHT_CHROMIUM_PATH` is set (CI / sandbox), otherwise the browser Playwright installed itself.
 *
 * A second build (`dist-e2e-support`) is produced with support channels configured through
 * `VITE_*` variables so the support page can be tested in both states; it is served on its own
 * port and used only by the `support-configured` project.
 */
const PORT = 4173;
const SUPPORT_PORT = 4174;
const BASE_URL = `http://127.0.0.1:${PORT}`;
const SUPPORT_BASE_URL = `http://127.0.0.1:${SUPPORT_PORT}`;
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_PATH;
const launchOptions = executablePath ? { executablePath } : {};

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  timeout: 45_000,
  expect: { timeout: 7_500 },
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    colorScheme: 'dark',
    launchOptions,
  },
  projects: [
    {
      name: 'desktop-chromium',
      testIgnore: /support-configured\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'mobile-chromium',
      testIgnore: /support-configured\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 2,
      },
    },
    {
      name: 'support-configured',
      testMatch: /support-configured\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        baseURL: SUPPORT_BASE_URL,
      },
    },
  ],
  webServer: [
    {
      command: `npm run preview -- --port ${PORT} --strictPort --host 127.0.0.1`,
      url: BASE_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
    {
      command: `npx vite build --outDir dist-e2e-support --emptyOutDir --logLevel warn && npx vite preview --outDir dist-e2e-support --port ${SUPPORT_PORT} --strictPort --host 127.0.0.1`,
      url: SUPPORT_BASE_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      env: {
        VITE_SUPPORT_EMAIL: 'help@example.org',
        VITE_DISCORD_URL: 'https://discord.example/invite',
      },
    },
  ],
});

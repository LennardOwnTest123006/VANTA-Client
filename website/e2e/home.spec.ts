import { expect, test } from '@playwright/test';
import { trackConsoleErrors, waitForApp } from './helpers';

test.describe('home page', () => {
  test('renders the hero, the primary navigation and the spec strip', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/');
    await waitForApp(page);

    await expect(page).toHaveTitle('VANTA Client — Your Minecraft. Refined.');
    const hero = page.getByRole('heading', { level: 1 });
    await expect(hero).toContainText('VANTA');
    await expect(hero).toContainText('CLIENT');
    await expect(page.getByText('Your Minecraft. Refined.').first()).toBeVisible();
    await expect(page.getByText('Fabric client for Minecraft 1.21.11')).toBeVisible();

    const specs = page.getByLabel('Technical specifications').first();
    await expect(specs).toContainText('1.21.11');
    await expect(specs).toContainText('0.19.5');
    await expect(specs).toContainText('21');
    await expect(specs).toContainText('Windows 10/11');

    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('contentinfo')).toContainText(
      'Minecraft 1.21.11 · Fabric Loader 0.19.5 · Java 21',
    );
    await expect(page.getByText('Interface preview').first()).toBeVisible();

    expect(errors()).toEqual([]);
  });

  test('has a working skip link and landmarks', async ({ page }) => {
    await page.goto('/');
    await waitForApp(page);
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: 'Skip to content' });
    await expect(skip).toBeFocused();
    await skip.press('Enter');
    await expect(page).toHaveURL(/#main$/);
    await expect(page.getByRole('main')).toHaveCount(1);
    await expect(page.getByRole('navigation', { name: 'Footer' })).toBeVisible();
  });

  test('download and features buttons navigate', async ({ page, isMobile }) => {
    await page.goto('/');
    await waitForApp(page);
    const main = page.getByRole('main');
    await main.getByRole('link', { name: 'Explore features' }).click();
    await expect(page).toHaveURL(/\/features$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Everything on your side of the screen',
    );

    if (isMobile) {
      await page.getByRole('button', { name: 'Open menu' }).click();
      await page
        .getByRole('dialog', { name: 'Site navigation' })
        .getByRole('link', { name: 'Download' })
        .click();
    } else {
      await page.getByRole('banner').getByRole('link', { name: 'Download' }).click();
    }
    await expect(page).toHaveURL(/\/download$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'VANTA Client for Minecraft 1.21.11',
    );
  });
});

import { test, expect } from '@playwright/test';

test('app root renders', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#app[data-v-app]')).toBeVisible();
});

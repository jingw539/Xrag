import { test, expect } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL;
const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;

const hasEnv = Boolean(baseURL && username && password);

test.describe('doctor flow (optional)', () => {
  test.skip(!hasEnv, 'E2E env not set');

  test('login and open workstation', async ({ page }) => {
    await page.goto(baseURL);
    await page.getByPlaceholder('请输入用户名').fill(username);
    await page.getByPlaceholder('请输入密码').fill(password);
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page.locator('.workstation')).toBeVisible();
    await expect(page.locator('.case-panel')).toBeVisible();
  });
});

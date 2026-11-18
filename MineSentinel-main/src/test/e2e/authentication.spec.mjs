import { expect, test } from '@playwright/test';

test('protected pages redirect an anonymous visitor to login', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: 'Ingresar' })).toBeVisible();
});

test('invalid credentials keep the visitor on login', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[name="usuario"]').fill('admin');
    await page.locator('input[name="password"]').fill('incorrecta');
    await page.getByRole('button', { name: 'Ingresar' }).click();

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('.error')).toHaveText('Usuario o contraseña incorrectos');
});

test('valid credentials open the dashboard in the authenticated session', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[name="usuario"]').fill('admin');
    await page.locator('input[name="password"]').fill('test-admin-password');
    await page.getByRole('button', { name: 'Ingresar' }).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(page).toHaveTitle(/Centro de Control/);
    await expect(page.getByText('Control de Aforo')).toBeVisible();
});

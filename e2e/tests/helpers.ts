import { Browser, Page, expect } from '@playwright/test';

/** Accounts of the test data set (docs/test-data/README.md); every account uses the same password. */
export const PASSWORD = 'Test1234!';
export const ACCOUNTS = {
  /** Member A: writes the post. */
  author: { email: 'sarra@tounsiguide.test', name: 'Sarra Ben Ali' },
  /** Member B: a brand new account that asks for the official page of the place. */
  owner: { email: 'lina@tounsiguide.test', name: 'Lina Chaabane' },
  admin: { email: 'admin@tounsiguide.test', name: 'Admin TounsiGuide' },
} as const;
export type Account = { readonly email: string; readonly name: string };

/** Logs in through the real form, in a fresh browser context (each account has its own session). */
export async function loginAs(browser: Browser, account: Account): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(account.email);
  await page.getByLabel('Mot de passe').fill(PASSWORD);
  await page.getByRole('button', { name: /se connecter/i }).click();
  await expect(page.getByRole('link', { name: new RegExp(account.name) })).toBeVisible();
  return page;
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page.getByRole('link', { name: 'Se connecter' })).toBeVisible();
}

/** Opens the « + » quick-create menu and follows one of its entries. */
export async function quickCreate(page: Page, entry: 'Nouvelle publication' | 'Demander une page' | 'Suggérer un module') {
  await page.getByRole('button', { name: 'Créer' }).hover();
  await page.getByRole('link', { name: new RegExp(entry) }).click();
}

/** Picks a place already known to TounsiGuide in a place picker (search by name, « Dans TounsiGuide » group). */
export async function pickKnownPlace(page: Page, openLabel: RegExp, placeName: string) {
  await page.getByRole('button', { name: openLabel }).click();
  await page.getByRole('searchbox', { name: 'Rechercher un lieu en Tunisie' }).fill(placeName);
  await page.getByRole('option', { name: new RegExp(placeName) }).first().click();
  await expect(page.locator('.place-chip')).toContainText(placeName);
}

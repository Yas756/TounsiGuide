import { test, expect } from '@playwright/test';

/** S3-US10: one search box in the header, results grouped by type (places, official pages, posts). */
test('a visitor searches "delices" (no accent) and gets places, the official page and posts, grouped', async ({ page }) => {
  await page.goto('/');
  const box = page.getByRole('searchbox', { name: 'Rechercher des lieux, des pages et des publications' });
  await box.fill('delices');
  await box.press('Enter');

  await expect(page).toHaveURL(/\/search\?q=delices$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Résultats pour « delices »');

  const places = page.locator('section', { has: page.getByRole('heading', { name: /^Lieux/ }) });
  await expect(places.locator('li', { hasText: 'Café des Délices' })).toBeVisible();
  await expect(places.getByRole('link', { name: /Voir la page officielle/ })).toBeVisible();

  const pages = page.locator('section', { has: page.getByRole('heading', { name: /^Pages officielles/ }) });
  await expect(pages.getByRole('link', { name: 'Café des Délices' })).toBeVisible();

  const posts = page.locator('section', { has: page.getByRole('heading', { name: /^Publications/ }) });
  await expect(posts.locator('li', { hasText: 'thé aux pignons' })).toBeVisible();
  await posts.locator('li', { hasText: 'thé aux pignons' }).getByRole('link').click();
  await expect(page).toHaveURL(/\/posts\/101$/);
});

test('a search with no match says so', async ({ page }) => {
  await page.goto('/search?q=zzqxw');
  await expect(page.getByText('Aucun résultat')).toBeVisible();
});

test("restricted posts are never found (seed: Walid's post restricted by a moderator)", async ({ page }) => {
  await page.goto('/search?q=arnaque');
  // Only the restricted post contains that word in the seed, so the search finds nothing at all.
  await expect(page.getByText('Aucun résultat')).toBeVisible();
  await expect(page.getByText(/ARNAQUE/)).toHaveCount(0);
});

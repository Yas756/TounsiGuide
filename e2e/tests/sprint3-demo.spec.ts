import { test, expect, Page } from '@playwright/test';
import { ACCOUNTS, loginAs, logout, pickKnownPlace, quickCreate } from './helpers';

/**
 * S3-T01: the Sprint 3 review demo (sprint3/Sprint3_demo_and_restart.md), end to end through the browser,
 * the Gateway and the four services. One story told in order by member A (author), member B (future
 * representative) and an administrator; each step depends on the previous one.
 */
test.describe.configure({ mode: 'serial' });

const MODULE = { name: 'Terrasses avec vue', icon: '🌅', description: 'Rooftops et terrasses où prendre un verre avec vue, de la Médina à Sidi Bou Saïd.' };
/** A seeded café with no official page and no pending request. */
const PLACE = 'Plan B Coworking Café';
const POST_TEXT = `Plan B aux Berges du Lac : wifi rapide, prises partout et un très bon café. Idéal pour travailler. [E2E ${Date.now()}]`;
const PAGE_DESCRIPTION = 'Café-coworking aux Berges du Lac : wifi fibre, salles de réunion, café de spécialité.';
const HIDE_REASON = 'Hors sujet pour notre page';
const RESTRICT_REASON = 'Contenu promotionnel signalé (test E2E)';

let postId: number;
let pageUrl: string;
let moduleUrl: string;

test('1. A suggests a topic module, the administrator approves it', async ({ browser }) => {
  const a = await loginAs(browser, ACCOUNTS.author);
  await quickCreate(a, 'Suggérer un module');
  await a.getByLabel('Nom *').fill(MODULE.name);
  await a.getByLabel('Icône').fill(MODULE.icon);
  await a.getByLabel('Description *').fill(MODULE.description);
  await a.getByRole('button', { name: 'Proposer le module' }).click();
  await expect(a.getByRole('status')).toContainText(`Module « ${MODULE.name} » proposé`);
  await expect(a.locator('li.module-proposal', { hasText: MODULE.name })).toBeVisible();

  const admin = await loginAs(browser, ACCOUNTS.admin);
  await admin.getByRole('link', { name: 'Administration' }).click();
  await admin.goto('/admin/modules');
  const row = admin.locator('li.admin-module', { hasText: MODULE.name });
  await row.getByRole('button', { name: 'Approuver' }).click();
  await expect(row).toHaveCount(0);

  await a.goto('/modules');
  await a.getByRole('link', { name: new RegExp(MODULE.name) }).first().click();
  await expect(a).toHaveURL(/\/modules\/[a-z0-9-]+$/);
  moduleUrl = new URL(a.url()).pathname;
  await a.context().close();
  await admin.context().close();
});

test('2-3. A publishes a post at a place with the new module; the place is the author\'s own (confirmed)', async ({ browser }) => {
  const a = await loginAs(browser, ACCOUNTS.author);
  await quickCreate(a, 'Nouvelle publication');
  await a.getByPlaceholder(/Partagez-le ici/).fill(POST_TEXT);
  await pickKnownPlace(a, /Ajouter un lieu|Choisir|lieu/i, PLACE);
  await a.getByLabel('Module thématique (facultatif)').selectOption({ label: `${MODULE.icon} ${MODULE.name}` });
  const created = a.waitForResponse(r => r.url().endsWith('/api/v1/posts') && r.request().method() === 'POST');
  await a.getByRole('button', { name: 'Publier dans le fil' }).click();
  const response = await created;
  expect(response.status()).toBe(201);
  postId = (await response.json()).id;

  // Back in « Fil général », with the module chip.
  await expect(a).toHaveURL(/\/feed$/);
  const card = a.locator('article', { hasText: POST_TEXT });
  await expect(card).toBeVisible();
  await expect(card).toContainText(MODULE.name);
  await a.goto(moduleUrl);
  await expect(a.locator('article', { hasText: POST_TEXT })).toBeVisible();

  // Lieu panel: the place chosen by the author; confirm it if it is still only proposed.
  await a.goto(`/posts/${postId}`);
  await expect(a.getByRole('heading', { name: 'Lieu et vérification' })).toBeVisible();
  const places = a.locator('app-post-places');
  await expect(places).toContainText(PLACE);
  const confirm = places.getByRole('button', { name: 'Confirmer' });
  if (await confirm.isVisible()) await confirm.click();
  await expect(places.getByRole('button', { name: 'Confirmer' })).toHaveCount(0);
  await a.context().close();
});

test('4. B asks for the official page of the place, the administrator approves, B becomes « Représentant »', async ({ browser }) => {
  const b = await loginAs(browser, ACCOUNTS.owner);
  await quickCreate(b, 'Demander une page');
  await pickKnownPlace(b, /Choisir le lieu/, PLACE);
  await b.getByLabel("Preuve d'autorité *").fill('Je suis la gérante du café depuis 2024 : registre de commerce B123456, e-mail contact@planb.test.');
  await b.getByRole('button', { name: 'Envoyer la demande' }).click();
  await expect(b.getByRole('status')).toContainText('Demande envoyée');

  const admin = await loginAs(browser, ACCOUNTS.admin);
  await admin.goto('/admin/business');
  const request = admin.locator('li.admin-business-request', { hasText: PLACE });
  await request.getByRole('button', { name: 'Approuver' }).click();
  await expect(request).toHaveCount(0);
  await admin.context().close();

  // The new role is in the next token: log out and back in.
  await logout(b);
  await b.goto('/login');
  await b.getByLabel('E-mail').fill(ACCOUNTS.owner.email);
  await b.getByLabel('Mot de passe').fill('Test1234!');
  await b.getByRole('button', { name: /se connecter/i }).click();
  await expect(b.locator('.session-name .role-badge')).toHaveText('Représentant');
  await b.context().close();
});

test('5. The official page is verified, its wall shows A\'s older post, the post links to the page', async ({ browser }) => {
  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/pages');
  await visitor.getByRole('link', { name: new RegExp(PLACE) }).first().click();
  await expect(visitor.getByRole('heading', { level: 1, name: new RegExp(PLACE) })).toBeVisible();
  await expect(visitor.getByText('Page vérifiée')).toBeVisible();
  pageUrl = visitor.url();
  await expect(wallPost(visitor)).toBeVisible();

  await visitor.goto(`/posts/${postId}`);
  await visitor.getByRole('link', { name: /Voir la page officielle/ }).click();
  await expect(visitor).toHaveURL(pageUrl);
  await visitor.context().close();
});

test('6. B edits the official info and curates the wall: hide (still in the feed), history, show again', async ({ browser }) => {
  const b = await loginAs(browser, ACCOUNTS.owner);
  await b.goto(pageUrl);
  await b.getByRole('button', { name: 'Modifier les informations' }).click();
  await b.getByLabel('Description').fill(PAGE_DESCRIPTION);
  await b.getByLabel('Horaires').fill('Tous les jours, 8h–22h');
  await b.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(b.locator('.business-official')).toContainText(PAGE_DESCRIPTION);

  // Hide A's post from this wall only.
  const post = wallPost(b);
  await post.getByRole('button', { name: 'Masquer de ce mur' }).click();
  await post.getByLabel(/Motif/).fill(HIDE_REASON);
  await post.getByRole('button', { name: 'Confirmer' }).click();
  await expect(wallPost(b)).toHaveCount(0);

  // ...but it stays in « Fil général ».
  await b.goto('/feed');
  await expect(b.locator('article', { hasText: POST_TEXT })).toBeVisible();

  // Hidden posts can be shown to the page members, the wall history records it, and it can be shown again.
  await b.goto(pageUrl);
  await b.getByLabel('Afficher les publications masquées').check();
  const hidden = wallPost(b);
  await expect(hidden).toContainText('Masquée sur ce mur');
  await expect(b.locator('section', { has: b.getByRole('heading', { name: 'Historique du mur' }) })).toContainText(HIDE_REASON);
  await hidden.getByRole('button', { name: 'Afficher à nouveau' }).click();
  await expect(wallPost(b)).not.toContainText('Masquée sur ce mur');
  await b.getByLabel('Afficher les publications masquées').uncheck();
  await expect(wallPost(b)).toBeVisible();
  await b.context().close();
});

test('7. B reports A\'s post as spam; it stays visible', async ({ browser }) => {
  const b = await loginAs(browser, ACCOUNTS.owner);
  await b.goto(`/posts/${postId}`);
  await b.getByRole('button', { name: 'Signaler cette publication' }).click();
  await b.getByLabel('Spam ou publicité').check();
  await b.locator('app-report-button').first().getByRole('button', { name: /Signaler|Envoyer/ }).last().click();
  await expect(b.locator('app-report-button').first().getByRole('status')).toBeVisible();
  await b.goto('/feed');
  await expect(b.locator('article', { hasText: POST_TEXT })).toBeVisible();
  await b.context().close();
});

test('8. The administrator restricts the post: gone from feed, module and wall; A sees why; then it is restored', async ({ browser }) => {
  const admin = await loginAs(browser, ACCOUNTS.admin);
  await admin.goto('/admin/reports');
  const item = admin.locator('li.report-item', { hasText: POST_TEXT.slice(0, 40) });
  await item.getByRole('button', { name: 'Restreindre' }).click();
  await item.getByLabel('Motif de la restriction *').fill(RESTRICT_REASON);
  await item.getByRole('button', { name: /Restreindre|Confirmer/ }).last().click();
  await expect(item).toHaveCount(0);

  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/feed');
  await expect(visitor.locator('article').first()).toBeVisible();
  await expect(visitor.locator('article', { hasText: POST_TEXT })).toHaveCount(0);
  await visitor.goto(moduleUrl);
  await expect(visitor.getByRole('heading', { level: 1, name: new RegExp(MODULE.name) })).toBeVisible();
  await expect(visitor.locator('article', { hasText: POST_TEXT })).toHaveCount(0);
  await visitor.goto(pageUrl);
  await expect(visitor.getByRole('heading', { name: 'Ce que dit la communauté' })).toBeVisible();
  await expect(wallPost(visitor)).toHaveCount(0);

  const a = await loginAs(browser, ACCOUNTS.author);
  await a.goto(`/posts/${postId}`);
  await expect(a.getByText('Publication restreinte par un modérateur')).toBeVisible();

  // « Contenus restreints » → « Rétablir » undoes it.
  await admin.getByRole('button', { name: 'Contenus restreints' }).click();
  const restricted = admin.locator('li.report-item', { hasText: POST_TEXT.slice(0, 40) });
  await expect(restricted).toContainText(RESTRICT_REASON);
  admin.once('dialog', dialog => dialog.accept()); // « Rétablir ce contenu ? »
  await restricted.getByRole('button', { name: 'Rétablir' }).click();
  await expect(restricted).toHaveCount(0);
  await visitor.goto('/feed');
  await expect(visitor.locator('article', { hasText: POST_TEXT })).toBeVisible();

  for (const p of [admin, visitor, a]) await p.context().close();
});

function wallPost(page: Page) {
  return page.locator('article.wall-post', { hasText: POST_TEXT });
}

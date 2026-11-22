import { test, expect } from '@playwright/test';
import { Account, loginAs } from './helpers';

/** S3-US11 likes on posts and comments, S3-US12 star ratings of business pages (seeded data set). */
const HATEM: Account = { email: 'hatem@tounsiguide.test', name: 'Hatem Bouzid' };
const MEHDI: Account = { email: 'mehdi@tounsiguide.test', name: 'Mehdi Ben Salah' };

test('a member likes a post and a comment; the like stays after a reload and visitors see the count', async ({ browser }) => {
  const member = await loginAs(browser, HATEM);
  await member.goto('/posts/109');
  const postLike = member.locator('.post-detail-footer app-like-button button');
  await expect(postLike).toHaveAttribute('aria-pressed', 'false');
  await postLike.click();
  await expect(postLike).toHaveAttribute('aria-pressed', 'true');
  await expect(postLike).toContainText('1');

  const commentLike = member.locator('.comment-actions app-like-button button').first();
  await commentLike.click();
  await expect(commentLike).toHaveAttribute('aria-pressed', 'true');

  await member.reload();
  await expect(member.locator('.post-detail-footer app-like-button button')).toHaveAttribute('aria-pressed', 'true');

  // Unlike, then like again: still exactly one like from this member.
  await member.locator('.post-detail-footer app-like-button button').click();
  await expect(member.locator('.post-detail-footer app-like-button button')).toContainText('0');
  await member.locator('.post-detail-footer app-like-button button').click();
  await expect(member.locator('.post-detail-footer app-like-button button')).toContainText('1');

  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/posts/109');
  const seen = visitor.locator('.post-detail-footer app-like-button button');
  await expect(seen).toContainText('1');
  await expect(seen).toHaveAttribute('aria-pressed', 'false');
  await seen.click();
  await expect(visitor).toHaveURL(/\/login/);
  await member.context().close();
  await visitor.context().close();
});

test('a member rates an official page; its owner cannot; the list shows the average', async ({ browser }) => {
  const member = await loginAs(browser, HATEM);
  await member.goto('/pages');
  await member.getByRole('link', { name: /Café des Délices/ }).first().click();
  const rating = member.locator('app-star-rating');
  await expect(rating).toContainText('Pas encore de note');
  await rating.getByRole('button', { name: '4 étoiles' }).click();
  await expect(rating.locator('.rating-average')).toHaveText('4,0');
  await expect(rating).toContainText('1 note');
  await expect(rating).toContainText('Votre note');
  // Changing the rating replaces it.
  await rating.getByRole('button', { name: '5 étoiles' }).click();
  await expect(rating.locator('.rating-average')).toHaveText('5,0');
  await expect(rating).toContainText('1 note');

  const owner = await loginAs(browser, MEHDI);
  await owner.goto(member.url());
  await expect(owner.locator('app-star-rating')).toContainText('ne peuvent pas la noter');
  await expect(owner.locator('app-star-rating .star-pick')).toHaveCount(0);

  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/pages');
  const row = visitor.locator('li.business-page-row', { hasText: 'Café des Délices' });
  await expect(row).toContainText('5,0 · 1 note');
  for (const p of [member, owner, visitor]) await p.context().close();
});

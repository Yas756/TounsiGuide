import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Fil général · TounsiGuide',
    loadComponent: () => import('./pages/feed/feed.page').then(m => m.FeedPage),
  },
  {
    path: 'posts/:id',
    title: 'Publication · TounsiGuide',
    loadComponent: () => import('./pages/post-detail/post-detail.page').then(m => m.PostDetailPage),
  },
  {
    path: '**',
    title: 'Page introuvable · TounsiGuide',
    loadComponent: () => import('./pages/not-found/not-found.page').then(m => m.NotFoundPage),
  },
];

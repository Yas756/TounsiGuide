import { Routes } from '@angular/router';
import { guestGuard } from './core/auth.guard';

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
    path: 'login',
    title: 'Connexion · TounsiGuide',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login.page').then(m => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Créer un compte · TounsiGuide',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register.page').then(m => m.RegisterPage),
  },
  {
    path: '**',
    title: 'Page introuvable · TounsiGuide',
    loadComponent: () => import('./pages/not-found/not-found.page').then(m => m.NotFoundPage),
  },
];

import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Découvrir · TounsiGuide',
    loadComponent: () => import('./pages/discover/discover.page').then(m => m.DiscoverPage),
  },
  {
    path: 'feed',
    title: 'Fil général · TounsiGuide',
    loadComponent: () => import('./pages/feed/feed.page').then(m => m.FeedPage),
  },
  {
    path: 'create-post',
    title: 'Nouvelle publication · TounsiGuide',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/create-post/create-post.page').then(m => m.CreatePostPage),
  },
  {
    path: 'posts/:id',
    title: 'Publication · TounsiGuide',
    loadComponent: () => import('./pages/post-detail/post-detail.page').then(m => m.PostDetailPage),
  },
  {
    path: 'pages',
    title: 'Pages officielles · TounsiGuide',
    loadComponent: () => import('./pages/business-pages/business-pages.page').then(m => m.BusinessPagesPage),
  },
  {
    path: 'pages/:id',
    title: 'Page officielle · TounsiGuide',
    loadComponent: () => import('./pages/business-page/business-page.page').then(m => m.BusinessPagePage),
  },
  {
    path: 'business/requests',
    title: 'Demander une page · TounsiGuide',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/business-requests/business-requests.page').then(m => m.BusinessRequestsPage),
  },
  {
    path: 'modules',
    title: 'Modules · TounsiGuide',
    loadComponent: () => import('./pages/modules/modules.page').then(m => m.ModulesPage),
  },
  {
    path: 'modules/:slug',
    title: 'Module · TounsiGuide',
    loadComponent: () => import('./pages/module-detail/module-detail.page').then(m => m.ModuleDetailPage),
  },
  {
    path: 'suggest-module',
    title: 'Suggérer un module · TounsiGuide',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/suggest-module/suggest-module.page').then(m => m.SuggestModulePage),
  },
  {
    path: 'search',
    title: 'Recherche · TounsiGuide',
    loadComponent: () => import('./pages/search/search.page').then(m => m.SearchPage),
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
    path: 'account',
    title: 'Mon compte · TounsiGuide',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/account/account.page').then(m => m.AccountPage),
  },
  {
    path: 'settings',
    title: 'Paramètres · TounsiGuide',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/settings/settings.page').then(m => m.SettingsPage),
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./pages/admin/admin-layout.page').then(m => m.AdminLayoutPage),
    children: [
      {
        path: '',
        title: 'Administration · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-overview.page').then(m => m.AdminOverviewPage),
      },
      {
        path: 'review',
        title: 'File de révision · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-review.page').then(m => m.AdminReviewPage),
      },
      {
        path: 'modules',
        title: 'Modules · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-modules.page').then(m => m.AdminModulesPage),
      },
      {
        path: 'reports',
        title: 'Signalements · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-reports.page').then(m => m.AdminReportsPage),
      },
      {
        path: 'business',
        title: 'Pages professionnelles · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-business.page').then(m => m.AdminBusinessPage),
      },
      {
        path: 'locations',
        title: 'Lieux · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-locations.page').then(m => m.AdminLocationsPage),
      },
      {
        path: 'users',
        title: 'Comptes · TounsiGuide',
        loadComponent: () => import('./pages/admin/admin-users.page').then(m => m.AdminUsersPage),
      },
    ],
  },
  {
    path: 'forbidden',
    title: 'Accès refusé · TounsiGuide',
    loadComponent: () => import('./pages/forbidden/forbidden.page').then(m => m.ForbiddenPage),
  },
  {
    path: '**',
    title: 'Page introuvable · TounsiGuide',
    loadComponent: () => import('./pages/not-found/not-found.page').then(m => m.NotFoundPage),
  },
];

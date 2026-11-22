import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Shell of the admin area: tabs on top, the chosen admin page below. Guarded by roleGuard('ADMIN'). */
@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="page-head">
      <h1>Administration</h1>
    </header>
    <nav class="tabs" aria-label="Sections d'administration">
      <a routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Vue d'ensemble</a>
      <a routerLink="/admin/review" routerLinkActive="active">File de révision</a>
      <a routerLink="/admin/reports" routerLinkActive="active">Signalements</a>
      <a routerLink="/admin/modules" routerLinkActive="active">Modules</a>
      <a routerLink="/admin/business" routerLinkActive="active">Pages pro</a>
      <a routerLink="/admin/locations" routerLinkActive="active">Lieux</a>
      <a routerLink="/admin/users" routerLinkActive="active">Comptes</a>
    </nav>
    <router-outlet />
  `,
})
export class AdminLayoutPage {}

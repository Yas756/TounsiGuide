import { Component, inject, OnInit, signal } from '@angular/core';
import { AdminUser, AdminUserApiService } from '../../core/admin-user-api.service';
import { AuthService } from '../../core/auth.service';
import { Role, ROLE_LABEL } from '../../core/auth.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { AvatarComponent } from '../../shared/avatar.component';
import { fullDate } from '../../shared/format';

const PAGE_SIZE = 20;

/** Adds or removes ADMIN and keeps every other role (MEMBER, BUSINESS_REP…): the endpoint replaces the whole set. */
export function withAdmin(roles: Role[], admin: boolean): Role[] {
  const others = roles.filter(r => r !== 'ADMIN' && r !== 'MEMBER');
  return admin ? ['MEMBER', 'ADMIN', ...others] : ['MEMBER', ...others];
}

/** Accounts list with the one admin action the Identity Service offers today: give or take the ADMIN role. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [ErrorBannerComponent, AvatarComponent],
  template: `
    <app-error-banner [error]="error()" [retryable]="true" (retry)="load(page()?.page ?? 0)" />

    @if (loading()) {
      <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
    } @else { @if (page(); as p) {
      <p class="muted small">{{ p.totalElements }} compte(s). Un changement de rôle prend effet à la prochaine connexion de la personne (15 min au plus).</p>
      <ul class="user-list">
        @for (u of p.items; track u.id) {
          <li class="card user-row">
            <app-avatar [name]="u.displayName" />
            <div class="user-main">
              <strong>{{ u.displayName }}</strong>
              @if (isSelf(u)) { <span class="muted small"> (vous)</span> }
              <div class="muted small">{{ u.email }}@if (u.createdAt) { · inscrit le {{ date(u.createdAt) }} }</div>
            </div>
            <div class="user-roles">
              @for (r of u.roles; track r) { <span class="pill" [class.pill-admin]="r === 'ADMIN'">{{ r === 'ADMIN' ? 'Admin' : roleLabel(r) }}</span> }
            </div>
            @if (isAdmin(u)) {
              <button type="button" class="btn btn-ghost btn-small" [disabled]="busyId() === u.id || isSelf(u)"
                      [title]="isSelf(u) ? 'Vous ne pouvez pas retirer votre propre rôle' : ''"
                      (click)="setAdmin(u, false)">Retirer admin</button>
            } @else {
              <button type="button" class="btn btn-ghost btn-small" [disabled]="busyId() === u.id"
                      (click)="setAdmin(u, true)">Nommer admin</button>
            }
          </li>
        } @empty {
          <li class="card empty">Aucun compte.</li>
        }
      </ul>
      @if (p.totalPages > 1) {
        <nav class="pager" aria-label="Pages">
          <button type="button" class="btn btn-ghost btn-small" [disabled]="p.page === 0" (click)="load(p.page - 1)">Précédent</button>
          <span class="muted small">Page {{ p.page + 1 }} / {{ p.totalPages }}</span>
          <button type="button" class="btn btn-ghost btn-small" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Suivant</button>
        </nav>
      }
    } }
  `,
})
export class AdminUsersPage implements OnInit {
  private readonly api = inject(AdminUserApiService);
  private readonly auth = inject(AuthService);

  readonly page = signal<Page<AdminUser> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<UiError | null>(null);
  readonly busyId = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(pageIndex, PAGE_SIZE).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(toUiError(err));
        this.loading.set(false);
      },
    });
  }

  isAdmin(u: AdminUser): boolean {
    return u.roles.includes('ADMIN');
  }

  isSelf(u: AdminUser): boolean {
    return this.auth.user()?.id === u.id;
  }

  setAdmin(u: AdminUser, admin: boolean): void {
    const roles = withAdmin(u.roles, admin);
    if (!admin && !confirm(`Retirer le rôle administrateur à ${u.displayName} ?`)) return;
    this.busyId.set(u.id);
    this.error.set(null);
    this.api.updateRoles(u.id, roles).subscribe({
      next: updated => {
        const newRoles = updated?.roles ?? roles;
        this.page.update(p => p && { ...p, items: p.items.map(x => (x.id === u.id ? { ...x, roles: newRoles } : x)) });
        this.busyId.set(null);
      },
      error: err => {
        this.error.set(toUiError(err));
        this.busyId.set(null);
      },
    });
  }

  roleLabel(role: string): string {
    return ROLE_LABEL[role] ?? role;
  }

  date(iso: string): string {
    return fullDate(iso);
  }
}

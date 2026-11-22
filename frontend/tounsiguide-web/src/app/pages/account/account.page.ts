import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ROLE_LABEL } from '../../core/auth.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { AvatarComponent } from '../../shared/avatar.component';
import { fullDate } from '../../shared/format';


@Component({
  selector: 'app-account-page',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, AvatarComponent],
  template: `
    <section class="account-hero">
      <div>
        <p class="eyebrow">Votre espace</p>
        <h1>Mon compte</h1>
        <p class="account-lead muted">Votre profil, vos accès et les prochaines façons de participer à TounsiGuide.</p>
      </div>
      <a class="btn btn-ghost" routerLink="/settings">Paramètres du compte</a>
    </section>

    <app-error-banner [error]="error()" [retryable]="true" (retry)="reload()" />

    @if (auth.user(); as u) {
      <div class="account-layout">
        <main>
          <section class="card account-profile-card">
            <div class="account-profile-head">
              <app-avatar [name]="u.displayName" />
              <div>
                <h2>{{ u.displayName }}</h2>
                <p class="muted">{{ u.email }}</p>
                <div class="account-roles">
                  @for (r of u.roles; track r) { <span class="status" [class.status-confirmed]="r === 'ADMIN'">{{ label(r) }}</span> }
                </div>
              </div>
            </div>
            <div class="account-meta">
              <span class="muted small">Membre depuis</span>
              <strong>{{ since() }}</strong>
            </div>
          </section>

          <section class="account-actions-grid" aria-label="Raccourcis du compte">
            <a class="account-action-card" routerLink="/create-post">
              <span class="action-card-icon" aria-hidden="true">+</span>
              <span><strong>Nouvelle publication</strong><small class="muted">Partager avec la communauté</small></span>
              <span class="link" aria-hidden="true">→</span>
            </a>
            <a class="account-action-card" routerLink="/feed">
              <span class="action-card-icon" aria-hidden="true">↗</span>
              <span><strong>Retourner au fil</strong><small class="muted">Lire les discussions récentes</small></span>
              <span class="link" aria-hidden="true">→</span>
            </a>
            <a class="account-action-card" routerLink="/settings">
              <span class="action-card-icon" aria-hidden="true">⚙</span>
              <span><strong>Paramètres</strong><small class="muted">Gérer les préférences du compte</small></span>
              <span class="link" aria-hidden="true">→</span>
            </a>
          </section>

          <section class="card capability-card">
            <div class="section-heading">
              <div>
                <p class="eyebrow">Participation</p>
                <h2>Ce que vous pouvez faire</h2>
              </div>
            </div>
            <ul class="can-list">
              <li>Publier dans le fil et choisir le lieu de votre publication.</li>
              <li>Commenter, modifier et supprimer vos propres publications et commentaires.</li>
              <li>Proposer un lieu pour la publication d'un autre membre ou contester un lieu.</li>
              @if (auth.isAdmin()) {
                <li><strong>Administration :</strong> traiter la file de révision et gérer les rôles des comptes.</li>
              }
            </ul>
          </section>
        </main>

        <aside class="account-side">
          <section class="account-side-card">
            <span class="card-kicker">Bientôt</span>
            <h2>Votre activité</h2>
            <p class="muted small">Vos publications, commentaires et suggestions seront regroupés ici.</p>
            <span class="status status-proposed">Sous développement</span>
          </section>
          @if (auth.isAdmin()) {
            <a class="account-admin-card" routerLink="/admin">
              <span class="card-kicker">Administration</span>
              <strong>Ouvrir le tableau de bord</strong>
              <span class="link">Accéder →</span>
            </a>
          }
        </aside>
      </div>
    }
  `,
})
export class AccountPage implements OnInit {
  readonly auth = inject(AuthService);
  readonly error = signal<UiError | null>(null);
  readonly since = computed(() => {
    const created = this.auth.user()?.createdAt;
    return created ? fullDate(created) : '—';
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.error.set(null);
    this.auth.loadProfile().subscribe({ error: err => this.error.set(toUiError(err)) });
  }

  label(role: string): string {
    return ROLE_LABEL[role] ?? role;
  }
}

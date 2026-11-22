import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ModuleApiService } from '../../core/module-api.service';
import { AuthService } from '../../core/auth.service';
import { moduleErrorMessage, TopicModule } from '../../core/module.model';
import { toUiError } from '../../core/api-error.model';

/** S3-F05: the approved topic modules, to browse posts by theme. */
@Component({
  selector: 'app-modules-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="page-head business-list-head">
      <div>
        <p class="eyebrow">Découverte</p>
        <h1>Modules thématiques</h1>
        <p class="muted">Parcourez les discussions par thème. Chaque module est proposé par un membre puis validé par l'équipe.</p>
      </div>
      <a class="btn btn-ghost" [routerLink]="auth.isLoggedIn() ? '/suggest-module' : '/login'"
         [queryParams]="auth.isLoggedIn() ? null : { returnUrl: '/suggest-module' }">Suggérer un module</a>
    </header>

    @if (loading()) {
      <div class="module-grid" aria-busy="true">
        @for (i of [1, 2, 3]; track i) { <div class="card skeleton" aria-hidden="true"><div></div><div></div></div> }
      </div>
    } @else if (error()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ error() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load()">Réessayer</button>
      </div>
    } @else {
      <ul class="module-grid">
        @for (m of modules(); track m.id) {
          <li>
            <a class="card module-card" [routerLink]="['/modules', m.slug]">
              <span class="module-icon" aria-hidden="true">{{ m.icon || '◎' }}</span>
              <strong>{{ m.name }}</strong>
              <span class="muted small module-card-description">{{ m.description }}</span>
              <span class="muted small">{{ m.postCount }} publication{{ m.postCount > 1 ? 's' : '' }}</span>
            </a>
          </li>
        } @empty {
          <li class="card empty module-empty">
            <p><strong>Aucun module pour le moment.</strong></p>
            <p class="muted">Proposez le premier thème : un administrateur le validera.</p>
          </li>
        }
      </ul>
    }
  `,
})
export class ModulesPage implements OnInit {
  private readonly api = inject(ModuleApiService);
  readonly auth = inject(AuthService);

  readonly modules = signal<TopicModule[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.approved().subscribe({
      next: list => {
        this.modules.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(moduleErrorMessage(toUiError(err)));
        this.loading.set(false);
      },
    });
  }
}

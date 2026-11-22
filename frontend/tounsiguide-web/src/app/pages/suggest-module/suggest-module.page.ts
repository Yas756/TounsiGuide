import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ModuleApiService } from '../../core/module-api.service';
import {
  MODULE_DESCRIPTION_MAX,
  MODULE_NAME_MAX,
  MODULE_STATUS_LABEL,
  moduleErrorMessage,
  TopicModule,
  validateModuleForm,
} from '../../core/module.model';
import { toUiError } from '../../core/api-error.model';
import { relativeTime } from '../../shared/format';

/** S3-F05: propose a topic module (logged in) and follow « Mes propositions ». */
@Component({
  selector: 'app-suggest-module-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a class="link back" routerLink="/modules">← Retour aux modules</a>
    <header class="page-head">
      <p class="eyebrow">Contribution</p>
      <h1>Suggérer un module</h1>
      <p class="muted">Proposez un nouvel espace thématique. Un administrateur le valide avant qu'il apparaisse dans les modules et dans le formulaire de publication.</p>
    </header>

    <section class="card" aria-labelledby="propose-title">
      <h2 id="propose-title">Nouveau module</h2>
      <form (ngSubmit)="submit()" novalidate>
        <div class="location-form-row module-form-row">
          <label class="field">
            <span class="label">Nom *</span>
            <input name="name" [maxlength]="nameMax" [ngModel]="name()" (ngModelChange)="name.set($event)"
                   placeholder="Ex. : Cafés pour travailler" [attr.aria-invalid]="!!errors()['name']" />
            @if (errors()['name']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
          <label class="field module-icon-field">
            <span class="label">Icône</span>
            <input name="icon" [ngModel]="icon()" (ngModelChange)="icon.set($event)" placeholder="☕" />
            @if (errors()['icon']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
        </div>
        <label class="field">
          <span class="label">Description *</span>
          <textarea name="description" rows="3" [ngModel]="description()" (ngModelChange)="description.set($event)"
                    [attr.aria-invalid]="!!errors()['description']" placeholder="De quoi parle-t-on dans ce module ?"></textarea>
          <span class="hint char-count" [class.near-limit]="description().length > descriptionMax - 50">
            {{ description().length }} / {{ descriptionMax }}
          </span>
          @if (errors()['description']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        @if (submitError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
        @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }
        <div class="actions">
          <button type="submit" class="btn btn-primary" [disabled]="sending()">{{ sending() ? 'Envoi…' : 'Proposer le module' }}</button>
        </div>
      </form>
    </section>

    <section aria-labelledby="mine-title">
      <h2 id="mine-title">Mes propositions</h2>
      @if (mineLoading()) {
        <div class="card skeleton" aria-busy="true"><div></div><div></div></div>
      } @else if (mineError()) {
        <div class="banner banner-error" role="alert">
          <strong>{{ mineError() }}</strong>
          <button type="button" class="btn btn-ghost" (click)="loadMine()">Réessayer</button>
        </div>
      } @else {
        <ul class="business-request-list">
          @for (m of mine(); track m.id) {
            <li class="card business-request module-proposal">
              <div class="business-request-head">
                <div>
                  <strong><span aria-hidden="true">{{ m.icon || '◎' }}</span> {{ m.name }}</strong>
                  <div class="muted small">proposé {{ ago(m.createdAt) }}</div>
                </div>
                <span class="status" [class.status-confirmed]="m.status === 'APPROVED'" [class.status-proposed]="m.status === 'PROPOSED'"
                      [class.status-disputed]="m.status === 'REJECTED'">{{ statusLabel[m.status] }}</span>
              </div>
              <p class="small">{{ m.description }}</p>
              @if (m.status === 'REJECTED' && m.decisionReason) { <p class="small"><strong>Motif du refus :</strong> {{ m.decisionReason }}</p> }
              @if (m.status === 'APPROVED') { <a class="link small" [routerLink]="['/modules', m.slug]">Voir le module</a> }
            </li>
          } @empty {
            <li class="card empty">Vous n'avez encore proposé aucun module.</li>
          }
        </ul>
      }
    </section>
  `,
})
export class SuggestModulePage implements OnInit {
  private readonly api = inject(ModuleApiService);

  readonly nameMax = MODULE_NAME_MAX;
  readonly descriptionMax = MODULE_DESCRIPTION_MAX;
  readonly statusLabel = MODULE_STATUS_LABEL;
  readonly ago = relativeTime;

  readonly name = signal('');
  readonly description = signal('');
  readonly icon = signal('');
  readonly errors = signal<Record<string, string>>({});
  readonly sending = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly mine = signal<TopicModule[]>([]);
  readonly mineLoading = signal(true);
  readonly mineError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMine();
  }

  loadMine(): void {
    this.mineLoading.set(true);
    this.mineError.set(null);
    this.api.mine().subscribe({
      next: list => {
        this.mine.set(list);
        this.mineLoading.set(false);
      },
      error: err => {
        this.mineError.set(moduleErrorMessage(toUiError(err)));
        this.mineLoading.set(false);
      },
    });
  }

  submit(): void {
    if (this.sending()) return;
    const form = { name: this.name(), description: this.description(), icon: this.icon() };
    const errors = validateModuleForm(form);
    this.errors.set(errors);
    this.submitError.set(null);
    this.notice.set(null);
    if (Object.keys(errors).length) return;
    this.sending.set(true);
    this.api.propose({ name: form.name.trim(), description: form.description.trim(), icon: form.icon.trim() || null }).subscribe({
      next: created => {
        this.sending.set(false);
        this.name.set('');
        this.description.set('');
        this.icon.set('');
        this.notice.set(`Module « ${created.name} » proposé. Un administrateur va l'examiner.`);
        this.mine.update(list => [created, ...list]);
      },
      error: err => {
        this.sending.set(false);
        const error = toUiError(err);
        if (error.code === 'VALIDATION_ERROR' && Object.keys(error.details).length) this.errors.set(error.details);
        this.submitError.set(error.status === 409
          ? 'Un module porte déjà ce nom (proposé ou approuvé). Choisissez un autre nom ou rejoignez le module existant.'
          : moduleErrorMessage(error));
      },
    });
  }
}

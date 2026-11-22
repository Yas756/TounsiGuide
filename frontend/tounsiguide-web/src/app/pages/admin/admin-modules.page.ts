import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ModuleApiService } from '../../core/module-api.service';
import { MODULE_STATUS_LABEL, moduleErrorMessage, ModuleStatus, TopicModule } from '../../core/module.model';
import { toUiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { relativeTime } from '../../shared/format';

/** S3-F05 (admin): module proposals to approve or reject (with a reason), approved modules to archive. */
@Component({
  selector: 'app-admin-modules',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <h2 class="sr-only">Modules thématiques</h2>
    <div class="feed-context-actions business-filter" role="group" aria-label="Filtrer les modules">
      @for (s of statuses; track s) {
        <button type="button" class="feed-filter" [class.active]="status() === s" (click)="setStatus(s)">{{ tabLabel[s] }}</button>
      }
    </div>

    @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }

    @if (loading()) {
      <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
    } @else if (error()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ error() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load(0)">Réessayer</button>
      </div>
    } @else { @if (page(); as p) {
      <ul class="business-request-list">
        @for (m of p.items; track m.id) {
          <li class="card business-request admin-module">
            <div class="business-request-head">
              <div>
                <strong><span aria-hidden="true">{{ m.icon || '◎' }}</span> {{ m.name }}</strong>
                <div class="muted small">/{{ m.slug }} · proposé par {{ m.proposedByName || 'Membre' }} · {{ ago(m.createdAt) }}
                  @if (m.status === 'APPROVED') { · {{ m.postCount }} publication(s) }</div>
              </div>
              <span class="status" [class.status-confirmed]="m.status === 'APPROVED'" [class.status-proposed]="m.status === 'PROPOSED'"
                    [class.status-disputed]="m.status === 'REJECTED'">{{ statusLabel[m.status] }}</span>
            </div>
            <p class="small">{{ m.description }}</p>
            @if (m.decisionReason) { <p class="muted small">Motif : {{ m.decisionReason }}</p> }
            @if (rowError()[m.id]; as e) { <div class="banner banner-error" role="alert">{{ e }}</div> }

            @if (rejectingId() === m.id) {
              <label class="field">
                <span class="label">Motif du refus * (envoyé au membre)</span>
                <textarea rows="2" maxlength="1000" [name]="'reason-' + m.id" [ngModel]="reason()" (ngModelChange)="reason.set($event)"></textarea>
                @if (reasonMissing()) { <span class="field-error">Le motif du refus est obligatoire.</span> }
              </label>
              <div class="actions">
                <button type="button" class="btn btn-ghost" (click)="rejectingId.set(null)" [disabled]="busyId() === m.id">Annuler</button>
                <button type="button" class="btn btn-danger" (click)="reject(m)" [disabled]="busyId() === m.id">Confirmer le refus</button>
              </div>
            } @else {
              <div class="actions">
                @if (m.status === 'PROPOSED') {
                  <button type="button" class="btn btn-ghost" (click)="startReject(m)" [disabled]="busyId() === m.id">Refuser</button>
                  <button type="button" class="btn btn-primary" (click)="approve(m)" [disabled]="busyId() === m.id">Approuver</button>
                } @else if (m.status === 'APPROVED') {
                  <a class="btn btn-ghost" [routerLink]="['/modules', m.slug]">Voir</a>
                  <button type="button" class="btn btn-danger" (click)="archive(m)" [disabled]="busyId() === m.id">Archiver</button>
                } @else if (m.status === 'ARCHIVED') {
                  <button type="button" class="btn btn-primary" (click)="approve(m)" [disabled]="busyId() === m.id">Réactiver</button>
                }
              </div>
            }
          </li>
        } @empty {
          <li class="card empty">{{ emptyLabel[status()] }}</li>
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
export class AdminModulesPage implements OnInit {
  private readonly api = inject(ModuleApiService);

  readonly statuses: ModuleStatus[] = ['PROPOSED', 'APPROVED', 'ARCHIVED', 'REJECTED'];
  readonly tabLabel: Record<ModuleStatus, string> = { PROPOSED: 'Proposés', APPROVED: 'Approuvés', ARCHIVED: 'Archivés', REJECTED: 'Refusés' };
  readonly emptyLabel: Record<ModuleStatus, string> = {
    PROPOSED: 'Aucune proposition en attente.',
    APPROVED: 'Aucun module approuvé.',
    ARCHIVED: 'Aucun module archivé.',
    REJECTED: 'Aucun module refusé.',
  };
  readonly statusLabel = MODULE_STATUS_LABEL;
  readonly ago = relativeTime;

  readonly status = signal<ModuleStatus>('PROPOSED');
  readonly page = signal<Page<TopicModule> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);
  readonly rowError = signal<Record<number, string>>({});
  readonly rejectingId = signal<number | null>(null);
  readonly reason = signal('');
  readonly reasonMissing = signal(false);

  ngOnInit(): void {
    this.load(0);
  }

  setStatus(status: ModuleStatus): void {
    this.status.set(status);
    this.notice.set(null);
    this.rejectingId.set(null);
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.queue(this.status(), pageIndex).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(moduleErrorMessage(toUiError(err)));
        this.loading.set(false);
      },
    });
  }

  approve(m: TopicModule): void {
    this.run(m, this.api.approve(m.id), `Module « ${m.name} » approuvé : il est visible et sélectionnable dans les publications.`);
  }

  startReject(m: TopicModule): void {
    this.rejectingId.set(m.id);
    this.reason.set('');
    this.reasonMissing.set(false);
  }

  reject(m: TopicModule): void {
    const reason = this.reason().trim();
    if (!reason) {
      this.reasonMissing.set(true);
      return;
    }
    this.run(m, this.api.reject(m.id, reason), `Module « ${m.name} » refusé. Le membre verra le motif.`);
  }

  archive(m: TopicModule): void {
    if (!confirm(`Archiver « ${m.name} » ? Il ne sera plus proposé ni affiché ; les publications restent.`)) return;
    this.run(m, this.api.archive(m.id), `Module « ${m.name} » archivé.`);
  }

  private run(m: TopicModule, call: ReturnType<ModuleApiService['approve']>, message: string): void {
    if (this.busyId() !== null) return;
    this.busyId.set(m.id);
    this.notice.set(null);
    this.reasonMissing.set(false);
    this.rowError.update(errors => {
      const { [m.id]: _, ...rest } = errors;
      return rest;
    });
    call.subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejectingId.set(null);
        this.notice.set(message);
        // The module changed status: it leaves this tab.
        this.page.update(p => p && { ...p, items: p.items.filter(x => x.id !== m.id), totalElements: p.totalElements - 1 });
      },
      error: err => {
        this.busyId.set(null);
        this.rowError.update(errors => ({ ...errors, [m.id]: moduleErrorMessage(toUiError(err)) }));
      },
    });
  }
}

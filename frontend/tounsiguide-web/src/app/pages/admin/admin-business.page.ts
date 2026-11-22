import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BUSINESS_PAGE_SIZE, BusinessApiService } from '../../core/business-api.service';
import {
  businessErrorMessage,
  PageRequest,
  PageRequestStatus,
  REJECT_REASON_MAX_LENGTH,
  REQUEST_STATUS_LABEL,
  requestPlaceLabel,
} from '../../core/business.model';
import { toUiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { fullDate, relativeTime } from '../../shared/format';

/**
 * S3-F01 (admin side): verification queue of business page requests. Approving creates the page on the
 * existing Location and makes the requester its owner; rejecting needs a reason the requester will read.
 */
@Component({
  selector: 'app-admin-business',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <h2 class="sr-only">Demandes de pages professionnelles</h2>
    <div class="feed-context-actions business-filter" role="group" aria-label="Filtrer les demandes">
      <button type="button" class="feed-filter" [class.active]="status() === 'PENDING'" (click)="setStatus('PENDING')">En attente</button>
      <button type="button" class="feed-filter" [class.active]="status() === null" (click)="setStatus(null)">Toutes</button>
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
      <p class="muted small">{{ p.totalElements }} demande(s){{ status() === 'PENDING' ? ' en attente, la plus ancienne en premier' : '' }}.</p>
      <ul class="business-request-list">
        @for (r of p.items; track r.id) {
          <li class="card business-request admin-business-request">
            <div class="business-request-head">
              <div>
                <strong>{{ r.proposedName || placeLabel(r) }}</strong>
                <div class="muted small">
                  Lieu : {{ placeLabel(r) }} · demandé par {{ r.requesterName || 'Membre' }} · {{ ago(r.createdAt) }}
                </div>
              </div>
              <span class="status" [class.status-confirmed]="r.status === 'APPROVED'"
                    [class.status-proposed]="r.status === 'PENDING'" [class.status-disputed]="r.status === 'REJECTED'">
                {{ statusLabel[r.status] }}
              </span>
            </div>
            <div class="business-evidence">
              <span class="label small">Preuve d'autorité</span>
              <p>{{ r.evidence }}</p>
              @if (r.contact) { <p class="small"><strong>Contact :</strong> {{ r.contact }}</p> }
            </div>

            @if (r.status === 'PENDING') {
              @if (rowError()[r.id]; as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
              @if (rejectingId() === r.id) {
                <label class="field">
                  <span class="label">Motif du refus * (envoyé au demandeur)</span>
                  <textarea rows="3" [maxlength]="reasonMax" [ngModel]="reason()" (ngModelChange)="reason.set($event)"
                            [name]="'reason-' + r.id"></textarea>
                  @if (reasonMissing()) { <span class="field-error">Le motif du refus est obligatoire.</span> }
                </label>
                <div class="actions">
                  <button type="button" class="btn btn-ghost" (click)="cancelReject()" [disabled]="busyId() === r.id">Annuler</button>
                  <button type="button" class="btn btn-danger" (click)="reject(r)" [disabled]="busyId() === r.id">
                    {{ busyId() === r.id ? 'Refus…' : 'Confirmer le refus' }}
                  </button>
                </div>
              } @else {
                <div class="actions">
                  <button type="button" class="btn btn-ghost" (click)="startReject(r)" [disabled]="busyId() === r.id">Refuser</button>
                  <button type="button" class="btn btn-primary" (click)="approve(r)" [disabled]="busyId() === r.id">
                    {{ busyId() === r.id ? 'Approbation…' : 'Approuver' }}
                  </button>
                </div>
              }
            } @else {
              <p class="muted small">
                {{ statusLabel[r.status] }} par {{ r.decidedByName || 'un administrateur' }} le {{ date(r.decidedAt) }}
                @if (r.decisionReason) { · motif : {{ r.decisionReason }} }
                @if (r.pageId !== null) { · <a class="link" [routerLink]="['/pages', r.pageId]">Voir la page</a> }
              </p>
            }
          </li>
        } @empty {
          <li class="card empty">{{ status() === 'PENDING' ? 'Aucune demande en attente.' : 'Aucune demande.' }}</li>
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
export class AdminBusinessPage implements OnInit {
  private readonly api = inject(BusinessApiService);

  readonly statusLabel = REQUEST_STATUS_LABEL;
  readonly placeLabel = requestPlaceLabel;
  readonly ago = relativeTime;
  readonly reasonMax = REJECT_REASON_MAX_LENGTH;

  readonly status = signal<PageRequestStatus | null>('PENDING');
  readonly page = signal<Page<PageRequest> | null>(null);
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

  setStatus(status: PageRequestStatus | null): void {
    this.status.set(status);
    this.notice.set(null);
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.requests(this.status(), pageIndex, BUSINESS_PAGE_SIZE).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(businessErrorMessage(toUiError(err)));
        this.page.set(null);
        this.loading.set(false);
      },
    });
  }

  approve(r: PageRequest): void {
    if (this.busyId() !== null) return;
    this.startAction(r);
    this.api.approve(r.id).subscribe({
      next: updated => {
        this.busyId.set(null);
        this.notice.set(
          `Demande approuvée : la page « ${updated.proposedName || requestPlaceLabel(updated)} » est créée et ${updated.requesterName || 'le demandeur'} en est propriétaire.`,
        );
        this.replace(updated);
      },
      error: err => this.fail(r, err),
    });
  }

  startReject(r: PageRequest): void {
    this.rejectingId.set(r.id);
    this.reason.set('');
    this.reasonMissing.set(false);
  }

  cancelReject(): void {
    this.rejectingId.set(null);
    this.reasonMissing.set(false);
  }

  reject(r: PageRequest): void {
    const reason = this.reason().trim();
    if (!reason) {
      this.reasonMissing.set(true);
      return;
    }
    if (this.busyId() !== null) return;
    this.startAction(r);
    this.api.reject(r.id, reason).subscribe({
      next: updated => {
        this.busyId.set(null);
        this.rejectingId.set(null);
        this.notice.set('Demande refusée. Le demandeur verra le motif.');
        this.replace(updated);
      },
      error: err => this.fail(r, err),
    });
  }

  date(iso: string | null): string {
    return iso ? fullDate(iso) : '';
  }

  private startAction(r: PageRequest): void {
    this.busyId.set(r.id);
    this.notice.set(null);
    this.reasonMissing.set(false);
    this.rowError.update(errors => {
      const { [r.id]: _, ...rest } = errors;
      return rest;
    });
  }

  /** A decided request leaves the pending list; with "Toutes" it stays, updated. */
  private replace(updated: PageRequest): void {
    this.page.update(p => {
      if (!p) return p;
      const items = this.status() === 'PENDING'
        ? p.items.filter(x => x.id !== updated.id)
        : p.items.map(x => (x.id === updated.id ? updated : x));
      const removed = p.items.length - items.length;
      return { ...p, items, totalElements: p.totalElements - removed };
    });
  }

  private fail(r: PageRequest, err: unknown): void {
    this.busyId.set(null);
    const error = toUiError(err);
    let message = businessErrorMessage(error);
    if (error.code === 'IDENTITY_SERVICE_UNAVAILABLE') {
      message += ' Rien n’a été enregistré : la demande reste en attente, réessayez plus tard.';
    }
    this.rowError.update(errors => ({ ...errors, [r.id]: message }));
  }
}

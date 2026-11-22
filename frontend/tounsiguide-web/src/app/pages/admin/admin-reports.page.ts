import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { REPORT_PAGE_SIZE, ReportApiService } from '../../core/report-api.service';
import {
  MODERATION_REASON_MAX_LENGTH,
  REPORT_REASON_LABEL,
  REPORT_REASONS,
  ReportedContent,
  reportErrorMessage,
  ReportReason,
  ReportTargetType,
  RestrictedContent,
  TARGET_LABEL,
} from '../../core/report.model';
import { toUiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { fullDate, relativeTime } from '../../shared/format';

type View = 'QUEUE' | 'RESTRICTED';
type Pending = { key: string; action: 'DISMISS' | 'RESTRICT' | 'RESTORE' };

export function targetKey(item: { targetType: ReportTargetType; targetId: number }): string {
  return `${item.targetType}:${item.targetId}`;
}

/**
 * Moderation queue (S3-US08, S3-F04): reported contents, most reported first, with their reasons. The moderator
 * dismisses the reports or restricts the content platform-wide (reason required); restricted contents can be restored.
 */
@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <h2 class="sr-only">Signalements</h2>
    <div class="feed-context-actions business-filter" role="group" aria-label="Vue">
      <button type="button" class="feed-filter" [class.active]="view() === 'QUEUE'" (click)="setView('QUEUE')">À examiner</button>
      <button type="button" class="feed-filter" [class.active]="view() === 'RESTRICTED'" (click)="setView('RESTRICTED')">Contenus restreints</button>
    </div>

    @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }

    @if (loading()) {
      <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
    } @else if (error()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ error() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load(0)">Réessayer</button>
      </div>
    } @else if (view() === 'QUEUE') { @if (queue(); as p) {
      <p class="muted small">{{ p.totalElements }} contenu(s) signalé(s), les plus signalés en premier. Un signalement ne retire rien tant que vous n'avez pas décidé.</p>
      <ul class="business-request-list">
        @for (item of p.items; track key(item)) {
          <li class="card report-item">
            <div class="business-request-head">
              <div>
                <span class="pill">{{ targetLabel[item.targetType] }}</span>
                <strong>{{ item.authorName || 'Auteur inconnu' }}</strong>
                <div class="muted small">dernier signalement {{ ago(item.lastReportedAt) }}</div>
              </div>
              <span class="status status-disputed" [attr.aria-label]="item.openReports + ' signalements'">
                {{ item.openReports }} signalement{{ item.openReports > 1 ? 's' : '' }}
              </span>
            </div>
            <blockquote class="report-preview">{{ item.preview ?? 'Contenu introuvable' }}</blockquote>
            <div class="report-reasons">
              @for (r of reasonsOf(item); track r.reason) {
                <span class="tag">{{ reasonLabel[r.reason] }} · {{ r.count }}</span>
              }
              <a class="link small" [routerLink]="['/posts', item.postId]"
                 [fragment]="item.targetType === 'COMMENT' ? 'comment-' + item.targetId : undefined">Voir dans son contexte</a>
            </div>
            <details class="report-details">
              <summary class="small">Détail des signalements</summary>
              <ul>
                @for (r of item.reports; track r.id) {
                  <li class="small">
                    <strong>{{ reasonLabel[r.reason] }}</strong> · {{ r.reporterName || 'Membre' }} · {{ ago(r.createdAt) }}
                    @if (r.detail) { <div>{{ r.detail }}</div> }
                  </li>
                }
              </ul>
            </details>

            @if (rowError()[key(item)]; as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
            @if (pending()?.key === key(item)) {
              <label class="field">
                <span class="label">{{ pending()?.action === 'RESTRICT' ? 'Motif de la restriction *' : 'Motif (facultatif)' }}</span>
                <textarea rows="2" [maxlength]="reasonMax" [ngModel]="reason()" (ngModelChange)="reason.set($event)"
                          [name]="'reason-' + key(item)"></textarea>
                @if (reasonMissing()) { <span class="field-error">Le motif de la restriction est obligatoire.</span> }
              </label>
              <div class="actions">
                <button type="button" class="btn btn-ghost" (click)="cancel()" [disabled]="busy()">Annuler</button>
                @if (pending()?.action === 'RESTRICT') {
                  <button type="button" class="btn btn-danger" (click)="confirm(item)" [disabled]="busy()">
                    {{ busy() ? 'Restriction…' : 'Confirmer la restriction' }}
                  </button>
                } @else {
                  <button type="button" class="btn btn-primary" (click)="confirm(item)" [disabled]="busy()">
                    {{ busy() ? 'Enregistrement…' : 'Confirmer le rejet' }}
                  </button>
                }
              </div>
            } @else {
              <div class="actions">
                <button type="button" class="btn btn-ghost" (click)="start(item, 'DISMISS')">Rejeter les signalements</button>
                <button type="button" class="btn btn-danger" (click)="start(item, 'RESTRICT')">Restreindre</button>
              </div>
            }
          </li>
        } @empty {
          <li class="card empty">Aucun signalement à examiner.</li>
        }
      </ul>
      @if (p.totalPages > 1) {
        <nav class="pager" aria-label="Pages">
          <button type="button" class="btn btn-ghost btn-small" [disabled]="p.page === 0" (click)="load(p.page - 1)">Précédent</button>
          <span class="muted small">Page {{ p.page + 1 }} / {{ p.totalPages }}</span>
          <button type="button" class="btn btn-ghost btn-small" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Suivant</button>
        </nav>
      }
    } } @else { @if (restricted(); as p) {
      <p class="muted small">Ces contenus sont masqués partout (fil, murs des pages, liens directs), sauf pour leur auteur et les administrateurs.</p>
      <ul class="business-request-list">
        @for (item of p.items; track key(item)) {
          <li class="card report-item">
            <div class="business-request-head">
              <div>
                <span class="pill">{{ targetLabel[item.targetType] }}</span>
                <strong>{{ item.authorName || 'Auteur inconnu' }}</strong>
                <div class="muted small">restreint le {{ date(item.restrictedAt) }}</div>
              </div>
              <span class="status status-disputed">Restreint</span>
            </div>
            <blockquote class="report-preview">{{ item.preview }}</blockquote>
            @if (item.restrictionReason) { <p class="small"><strong>Motif :</strong> {{ item.restrictionReason }}</p> }
            @if (rowError()[key(item)]; as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
            <div class="actions">
              <a class="btn btn-ghost" [routerLink]="['/posts', item.postId]">Voir</a>
              <button type="button" class="btn btn-primary" (click)="restore(item)" [disabled]="busy()">Rétablir</button>
            </div>
          </li>
        } @empty {
          <li class="card empty">Aucun contenu restreint.</li>
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
export class AdminReportsPage implements OnInit {
  private readonly api = inject(ReportApiService);

  readonly targetLabel = TARGET_LABEL;
  readonly reasonLabel = REPORT_REASON_LABEL;
  readonly reasonMax = MODERATION_REASON_MAX_LENGTH;
  readonly key = targetKey;
  readonly ago = relativeTime;

  readonly view = signal<View>('QUEUE');
  readonly queue = signal<Page<ReportedContent> | null>(null);
  readonly restricted = signal<Page<RestrictedContent> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly pending = signal<Pending | null>(null);
  readonly reason = signal('');
  readonly reasonMissing = signal(false);
  readonly busy = signal(false);
  readonly rowError = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.load(0);
  }

  setView(view: View): void {
    this.view.set(view);
    this.notice.set(null);
    this.cancel();
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    const done = () => this.loading.set(false);
    const fail = (err: unknown) => {
      this.error.set(reportErrorMessage(toUiError(err)));
      done();
    };
    if (this.view() === 'QUEUE') {
      this.api.queue(pageIndex, REPORT_PAGE_SIZE).subscribe({ next: p => { this.queue.set(p); done(); }, error: fail });
    } else {
      this.api.restricted(pageIndex, REPORT_PAGE_SIZE).subscribe({ next: p => { this.restricted.set(p); done(); }, error: fail });
    }
  }

  /** Reasons with their counts, in the usual order. */
  reasonsOf(item: ReportedContent): { reason: ReportReason; count: number }[] {
    return REPORT_REASONS.filter(r => (item.reasons[r] ?? 0) > 0).map(r => ({ reason: r, count: item.reasons[r]! }));
  }

  start(item: ReportedContent, action: 'DISMISS' | 'RESTRICT'): void {
    this.pending.set({ key: targetKey(item), action });
    this.reason.set('');
    this.reasonMissing.set(false);
    this.notice.set(null);
  }

  cancel(): void {
    this.pending.set(null);
    this.reasonMissing.set(false);
  }

  confirm(item: ReportedContent): void {
    const pending = this.pending();
    if (!pending || this.busy()) return;
    const reason = this.reason().trim();
    if (pending.action === 'RESTRICT' && !reason) {
      this.reasonMissing.set(true);
      return;
    }
    this.begin(item);
    const call = pending.action === 'RESTRICT'
      ? this.api.restrict(item.targetType, item.targetId, reason)
      : this.api.dismiss(item.targetType, item.targetId, reason || null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.pending.set(null);
        this.notice.set(pending.action === 'RESTRICT'
          ? `${TARGET_LABEL[item.targetType]} restreint(e) : masqué(e) partout sauf pour son auteur et les administrateurs.`
          : 'Signalements rejetés : le contenu reste publié.');
        this.queue.update(p => p && this.without(p, item));
      },
      error: err => this.fail(item, err),
    });
  }

  restore(item: RestrictedContent): void {
    if (this.busy()) return;
    if (!confirm(`Rétablir ce contenu ? Il redeviendra visible par tous.`)) return;
    this.begin(item);
    this.api.restore(item.targetType, item.targetId, null).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(`${TARGET_LABEL[item.targetType]} rétabli(e) : à nouveau visible par tous.`);
        this.restricted.update(p => p && this.without(p, item));
      },
      error: err => this.fail(item, err),
    });
  }

  date(iso: string | null): string {
    return iso ? fullDate(iso) : '';
  }

  private begin(item: { targetType: ReportTargetType; targetId: number }): void {
    this.busy.set(true);
    this.reasonMissing.set(false);
    this.rowError.update(errors => {
      const { [targetKey(item)]: _, ...rest } = errors;
      return rest;
    });
  }

  private fail(item: { targetType: ReportTargetType; targetId: number }, err: unknown): void {
    this.busy.set(false);
    this.rowError.update(errors => ({ ...errors, [targetKey(item)]: reportErrorMessage(toUiError(err)) }));
  }

  private without<T extends { targetType: ReportTargetType; targetId: number }>(page: Page<T>, item: T | ReportedContent | RestrictedContent): Page<T> {
    const key = targetKey(item);
    const items = page.items.filter(x => targetKey(x) !== key);
    return { ...page, items, totalElements: page.totalElements - (page.items.length - items.length) };
  }
}

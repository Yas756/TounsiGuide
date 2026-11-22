import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReportApiService } from '../core/report-api.service';
import { AuthService } from '../core/auth.service';
import {
  REPORT_DETAIL_MAX_LENGTH,
  REPORT_REASON_LABEL,
  REPORT_REASONS,
  ReportReason,
  reportErrorMessage,
  ReportTargetType,
} from '../core/report.model';
import { toUiError } from '../core/api-error.model';

/**
 * « Signaler » on a post or a comment (S3-US07): shown to logged-in members, never on their own content.
 * Opens a small form (reason + optional detail). The content is not removed: a moderator decides.
 */
@Component({
  selector: 'app-report-button',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (visible()) {
      @if (sent(); as message) {
        <span class="report-sent small" role="status">{{ message }}</span>
      } @else if (!open()) {
        <button type="button" class="btn-text report-trigger" (click)="open.set(true)"
                [attr.aria-label]="'Signaler ' + (targetType() === 'POST' ? 'cette publication' : 'ce commentaire')">Signaler</button>
      } @else {
        <form class="report-form card" (ngSubmit)="send()" novalidate>
          <fieldset>
            <legend class="label">Pourquoi signalez-vous {{ targetType() === 'POST' ? 'cette publication' : 'ce commentaire' }} ?</legend>
            @for (r of reasons; track r) {
              <label class="report-reason small">
                <input type="radio" [name]="'reason-' + targetType() + '-' + targetId()" [value]="r"
                       [checked]="reason() === r" (change)="reason.set(r)" />
                {{ reasonLabel[r] }}
              </label>
            }
          </fieldset>
          @if (reasonMissing()) { <span class="field-error">Choisissez un motif.</span> }
          <label class="field">
            <span class="label small">Précisions (facultatif)</span>
            <textarea rows="2" [name]="'detail-' + targetType() + '-' + targetId()" [ngModel]="detail()" (ngModelChange)="detail.set($event)"></textarea>
            <span class="hint char-count" [class.near-limit]="detail().length > detailMax - 100">{{ detail().length }} / {{ detailMax }}</span>
          </label>
          <p class="muted small">Le contenu reste visible : un modérateur examine le signalement.</p>
          @if (error(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
          <div class="actions">
            <button type="button" class="btn btn-ghost btn-small" (click)="cancel()" [disabled]="sending()">Annuler</button>
            <button type="submit" class="btn btn-danger btn-small" [disabled]="sending() || detail().length > detailMax">
              {{ sending() ? 'Envoi…' : 'Envoyer le signalement' }}
            </button>
          </div>
        </form>
      }
    }
  `,
})
export class ReportButtonComponent {
  private readonly api = inject(ReportApiService);
  private readonly auth = inject(AuthService);

  readonly targetType = input.required<ReportTargetType>();
  readonly targetId = input.required<number>();
  /** Author of the content: no button on one's own content (the backend answers 403 anyway). */
  readonly authorId = input<string | null>(null);

  readonly reasons = REPORT_REASONS;
  readonly reasonLabel = REPORT_REASON_LABEL;
  readonly detailMax = REPORT_DETAIL_MAX_LENGTH;

  readonly visible = computed(() => this.auth.isLoggedIn() && !this.auth.isOwner(this.authorId()));
  readonly open = signal(false);
  readonly reason = signal<ReportReason | null>(null);
  readonly detail = signal('');
  readonly reasonMissing = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  /** Confirmation shown instead of the button once sent (or already reported: 409). */
  readonly sent = signal<string | null>(null);

  cancel(): void {
    this.open.set(false);
    this.error.set(null);
    this.reasonMissing.set(false);
  }

  send(): void {
    const reason = this.reason();
    this.reasonMissing.set(!reason);
    if (!reason || this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    this.api
      .report({ targetType: this.targetType(), targetId: this.targetId(), reason, detail: this.detail().trim() || null })
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.open.set(false);
          this.sent.set("Signalement envoyé, un modérateur va l'examiner.");
        },
        error: err => {
          this.sending.set(false);
          const error = toUiError(err);
          if (error.status === 409) {
            this.open.set(false);
            this.sent.set("Vous avez déjà signalé ce contenu ; un modérateur va l'examiner.");
            return;
          }
          this.error.set(reportErrorMessage(error));
        },
      });
  }
}

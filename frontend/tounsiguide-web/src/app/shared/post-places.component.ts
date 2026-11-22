import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AssociationApiService } from '../core/association-api.service';
import {
  Association,
  AssociationAction,
  associationErrorMessage,
  AssociationEvidence,
  EVIDENCE_LABEL,
  ADMIN_DECISION_LABEL,
  adminDecision,
  displayText,
  originLabelOf,
  scoreClass,
  STATUS_LABEL,
} from '../core/association.model';
import { ApiComment } from '../core/api-comment.model';
import { toUiError, UiError } from '../core/api-error.model';
import { ErrorBannerComponent } from './error-banner.component';
import { relativeTime } from './format';

/** Which note form is open: a question from the author, or a dispute from a member. */
interface NoteForm {
  associationId: number;
  action: 'clarify' | 'dispute';
}

/**
 * "Lieu" panel of a post (S2-F03/F04): the confirmed place or "Lieu non confirmé", then every proposal
 * with its score and evidence. The post author confirms, rejects or asks a question about proposals;
 * other members dispute the confirmed place. Visitors only read.
 * After any action the parent reloads the associations (`changed`), because one action can change several
 * of them (confirming one rejects the previous one).
 */
@Component({
  selector: 'app-post-places',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent],
  template: `
    <section class="card places" aria-labelledby="places-title">
      <div class="places-head">
        <h2 id="places-title">Lieu</h2>
        @if (confirmed(); as c) {
          <span class="status status-confirmed">Confirmé</span>
        } @else if (disputed() && !loading() && !error()) {
          <span class="status status-disputed">Contesté</span>
        } @else if (!loading() && !error()) {
          <span class="status status-proposed">Non confirmé</span>
        }
      </div>

      @if (loading()) {
        <div class="skeleton" aria-busy="true"><div></div><div></div></div>
        <span class="sr-only">Chargement des lieux proposés…</span>
      } @else if (error()) {
        <app-error-banner [error]="error()" [retryable]="true" (retry)="retry.emit()" />
      } @else {
        @if (confirmed(); as c) {
          <p class="places-lead">
            <strong>{{ c.location.name }}</strong>@if (c.location.city) {<span class="muted">, {{ c.location.city }}</span>}
            @if (decisionOf(c); as d) {
              <span class="muted small"> · </span><span class="pill pill-admin">{{ decisionLabel[d] }}</span>
              <span class="muted small places-strength">Solidité des éléments : {{ c.score }} / 100</span>
            } @else {
              <span class="muted small"> · confiance {{ c.score }} / 100</span>
            }
          </p>
        } @else {
          @if (disputed(); as d) {
            <p class="places-lead muted">
              Lieu contesté : « {{ d.location.name }} » est en cours d'examen. Un modérateur va vérifier les éléments et décider.
            </p>
          } @else {
            <p class="places-lead muted">
              Lieu non confirmé.
              @if (open().length) { {{ open().length }} proposition{{ open().length > 1 ? 's' : '' }} en attente de l'auteur. }
              @else if (canSuggest()) { Vous le connaissez ? Mentionnez-le dans un commentaire. }
            </p>
          }
        }

        @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }
        @if (actionError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }

        @if (sorted().length) {
          <ul class="place-list">
            @for (a of sorted(); track a.id) {
              <li class="place-item" [class.rejected]="a.status === 'REJECTED'">
                <div class="place-item-head">
                  @if (decisionOf(a); as d) {
                    <span class="score score-admin" role="img"
                          [attr.aria-label]="decisionLabel[d] + ' · solidité des éléments ' + a.score + ' sur 100'"
                          [title]="decisionLabel[d] + ' · solidité des éléments ' + a.score + ' / 100'">✓</span>
                  } @else {
                    <span class="score" [class]="scoreClass(a.score)" [attr.aria-label]="'Confiance ' + a.score + ' sur 100'">{{ a.score }}</span>
                  }
                  <div class="review-main">
                    <strong>{{ a.location.name }}</strong>@if (a.location.city) {<span class="muted">, {{ a.location.city }}</span>}
                    <span class="muted small review-meta">{{ originLabelOf(a) }} · {{ ago(a.createdAt) }}</span>
                  </div>
                  <span class="status status-{{ a.status.toLowerCase() }}">{{ statusLabel[a.status] }}</span>
                </div>

                @if (visibleEvidence(a).length) {
                  <ul class="evidence">
                    @for (ev of visibleEvidence(a); track ev.id) {
                      <li [class.oppose]="ev.direction === 'OPPOSE'">
                        <span class="ev-sign" aria-hidden="true">{{ ev.direction === 'OPPOSE' ? '−' : '+' }}{{ ev.weight }}</span>
                        <div class="small">
                          <strong>{{ evidenceLabel(ev.type) }}</strong>
                          @if (ev.actorName) { <span class="muted"> · {{ ev.actorName }}</span> }
                          @if (ev.commentId !== null) {
                            <span class="muted"> · <a class="link" [href]="'#comment-' + ev.commentId">{{ commentLabel(ev.commentId) }}</a></span>
                          }
                          <span class="muted"> · {{ ago(ev.createdAt) }}</span>
                          @if (ev.note) { <p class="ev-note">{{ displayText(ev.note) }}</p> }
                        </div>
                      </li>
                    }
                  </ul>
                }

                @if (isAuthor() && a.status === 'PROPOSED') {
                  <div class="actions actions-small">
                    <button type="button" class="btn btn-primary btn-small" (click)="confirm(a)" [disabled]="busyId() === a.id">Confirmer</button>
                    <button type="button" class="btn btn-ghost btn-small" (click)="reject(a)" [disabled]="busyId() === a.id">Rejeter</button>
                    <button type="button" class="btn-text" (click)="openNote(a, 'clarify')" [disabled]="busyId() === a.id">Demander une précision</button>
                  </div>
                }
                @if (canDispute(a)) {
                  <div class="actions actions-small">
                    <button type="button" class="btn btn-danger btn-small" (click)="openNote(a, 'dispute')" [disabled]="busyId() === a.id">
                      {{ a.status === 'DISPUTED' ? 'Contester aussi' : 'Contester' }}
                    </button>
                  </div>
                }

                @if (noteForm()?.associationId === a.id) {
                  <form class="note-form" (ngSubmit)="sendNote(a)">
                    <label class="field">
                      <span class="label">{{ noteForm()!.action === 'dispute' ? 'Pourquoi ce lieu est-il faux ?' : 'Votre question aux membres' }}</span>
                      <textarea name="note" rows="2" maxlength="500" required
                                [placeholder]="noteForm()!.action === 'dispute' ? 'Ex. : la photo montre la terrasse du Café Rome, pas du Café Roma.' : 'Ex. : de quelle succursale parlez-vous ?'"
                                [ngModel]="note()" (ngModelChange)="note.set($event)"></textarea>
                      <span class="hint">{{ note().length }} / 500</span>
                    </label>
                    <div class="form-actions">
                      <button type="button" class="btn btn-ghost btn-small" (click)="closeNote()">Annuler</button>
                      <button type="submit" class="btn btn-small" [class.btn-danger]="noteForm()!.action === 'dispute'"
                              [class.btn-primary]="noteForm()!.action === 'clarify'"
                              [disabled]="!note().trim() || busyId() === a.id">
                        {{ noteForm()!.action === 'dispute' ? 'Envoyer la contestation' : 'Envoyer la question' }}
                      </button>
                    </div>
                  </form>
                }
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class PostPlacesComponent {
  private readonly api = inject(AssociationApiService);

  readonly associations = input<Association[]>([]);
  readonly loading = input(false);
  readonly error = input<UiError | null>(null);
  /** Comments of the post, to name the comment an evidence comes from. */
  readonly comments = input<ApiComment[]>([]);
  readonly isAuthor = input(false);
  /** Author of the post: tells an author's confirmation from an administrator's decision. */
  readonly postAuthorId = input<string | null>(null);
  readonly loggedIn = input(false);

  /** An action succeeded: the parent reloads the associations. */
  readonly changed = output<void>();
  readonly retry = output<void>();

  protected readonly busyId = signal<number | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly noteForm = signal<NoteForm | null>(null);
  protected readonly note = signal('');

  protected readonly statusLabel = STATUS_LABEL;
  protected readonly originLabelOf = originLabelOf;
  protected readonly displayText = displayText;
  protected readonly decisionLabel = ADMIN_DECISION_LABEL;
  protected decisionOf(a: Association) {
    return adminDecision(a, this.postAuthorId());
  }
  protected readonly scoreClass = scoreClass;
  protected readonly ago = relativeTime;

  /** Best first: confirmed, then disputed and proposed by score, rejected last. */
  protected readonly sorted = computed(() => {
    const rank = { CONFIRMED: 0, DISPUTED: 1, PROPOSED: 2, REJECTED: 3 } as const;
    return [...this.associations()].sort((a, b) => rank[a.status] - rank[b.status] || b.score - a.score);
  });
  protected readonly confirmed = computed(() => this.associations().find(a => a.status === 'CONFIRMED') ?? null);
  protected readonly open = computed(() => this.associations().filter(a => a.status === 'PROPOSED'));
  /** A disputed place waits for a moderator, not for the author. */
  protected readonly disputed = computed(() => this.associations().find(a => a.status === 'DISPUTED') ?? null);
  protected readonly canSuggest = computed(() => this.loggedIn() && !this.isAuthor());

  protected canDispute(a: Association): boolean {
    return this.loggedIn() && !this.isAuthor() && !a.forced && (a.status === 'CONFIRMED' || a.status === 'DISPUTED');
  }

  /** Competing claims are system bookkeeping: they are counted in the score, not listed one by one. */
  protected visibleEvidence(a: Association): AssociationEvidence[] {
    return (a.evidence ?? []).filter(ev => ev.type !== 'COMPETING_CLAIM');
  }

  protected evidenceLabel(type: string): string {
    return EVIDENCE_LABEL[type] ?? type;
  }

  protected commentLabel(commentId: number): string {
    const comment = this.comments().find(c => c.id === commentId);
    return comment ? `commentaire de ${comment.authorName}` : 'commentaire';
  }

  protected confirm(a: Association): void {
    this.run(a, 'confirm', this.api.confirm(a.id), `« ${a.location.name} » est maintenant le lieu confirmé.`);
  }

  protected reject(a: Association): void {
    if (!confirm(`Rejeter la proposition « ${a.location.name} » ?`)) return;
    this.run(a, 'reject', this.api.reject(a.id), 'Proposition rejetée.');
  }

  protected openNote(a: Association, action: 'clarify' | 'dispute'): void {
    this.noteForm.set({ associationId: a.id, action });
    this.note.set('');
    this.actionError.set(null);
  }

  protected closeNote(): void {
    this.noteForm.set(null);
  }

  protected sendNote(a: Association): void {
    const form = this.noteForm();
    const note = this.note().trim();
    if (!form || !note) return;
    const request = form.action === 'dispute' ? this.api.dispute(a.id, note) : this.api.clarify(a.id, note);
    const done = form.action === 'dispute'
      ? 'Contestation envoyée. Un administrateur examinera le lieu.'
      : 'Question publiée dans l’historique de la proposition.';
    this.run(a, form.action, request, done);
  }

  private run(a: Association, action: AssociationAction, request: Observable<Association>, done: string): void {
    this.busyId.set(a.id);
    this.actionError.set(null);
    this.notice.set(null);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.noteForm.set(null);
        this.notice.set(done);
        this.changed.emit();
      },
      error: err => {
        this.busyId.set(null);
        this.actionError.set(associationErrorMessage(toUiError(err), action));
      },
    });
  }
}

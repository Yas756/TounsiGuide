import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BUSINESS_PAGE_SIZE, BusinessApiService } from '../../core/business-api.service';
import { AuthService } from '../../core/auth.service';
import {
  AUDIT_ACTION_LABEL,
  BusinessAuditEntry,
  BusinessPage,
  businessErrorMessage,
  HIDE_REASON_MAX_LENGTH,
  PAGE_FIELD_LIMITS,
  PAGE_ROLE_LABEL,
  toUpdateRequest,
  UpdatePageRequest,
  validatePageForm,
  WallPost,
} from '../../core/business.model';
import { toUiError } from '../../core/api-error.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { MediaGalleryComponent } from '../../shared/media-gallery.component';
import { StarRatingComponent } from '../../shared/star-rating.component';
import { fullDate, relativeTime, wasEdited } from '../../shared/format';

/** Appends the wall posts not already shown (a hide/unhide between two pages can shift them by one). */
export function appendWall(shown: WallPost[], incoming: WallPost[]): WallPost[] {
  const ids = new Set(shown.map(p => p.id));
  return [...shown, ...incoming.filter(p => !ids.has(p.id))];
}

function formOf(page: BusinessPage): UpdatePageRequest {
  return {
    name: page.name,
    description: page.description ?? '',
    openingHours: page.openingHours ?? '',
    phone: page.phone ?? '',
    email: page.email ?? '',
    website: page.website ?? '',
    address: page.address ?? '',
  };
}

/**
 * S3-F02 / S3-F03: a verified business page. Everyone sees the official information and the wall (posts confirmed
 * for the page's Location, older ones included). Members of the page edit the information, hide posts from this
 * wall only (they stay in the general feed) and read the hide/unhide history.
 */
@Component({
  selector: 'app-business-page-page',
  standalone: true,
  imports: [FormsModule, RouterLink, AvatarComponent, MediaGalleryComponent, StarRatingComponent],
  template: `
    <a class="link back" routerLink="/pages">← Toutes les pages</a>

    @if (loading()) {
      <div class="card skeleton" aria-hidden="true"><div></div><div></div><div></div></div>
      <span class="sr-only">Chargement de la page…</span>
    } @else if (notFound()) {
      <div class="card empty">
        <p><strong>Cette page n'existe pas ou n'est plus active.</strong></p>
        <a class="btn btn-primary" routerLink="/pages">Voir les pages officielles</a>
      </div>
    } @else if (loadError()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ loadError() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load()">Réessayer</button>
      </div>
    } @else { @if (page(); as b) {
      <div [class.detail-layout]="b.member || auth.isAdmin()">
        <div class="detail-main">
          <section class="card business-official" aria-labelledby="business-title">
            <div class="business-title-row">
              <h1 id="business-title">{{ b.name }}</h1>
              <span class="status status-confirmed" [title]="b.verifiedAt ? 'Vérifiée le ' + date(b.verifiedAt) : ''">Page vérifiée</span>
            </div>
            <p class="muted small">
              @if (b.location; as l) {
                {{ l.name }}@if (l.city && l.city !== l.name) { · {{ l.city }} }
              } @else { Lieu n°{{ b.locationId }} }
              @if (b.member && b.memberRole) { · <span class="pill pill-admin">{{ roleLabel[b.memberRole] }}</span> }
            </p>

            @if (editing()) {
              <form class="business-edit" (ngSubmit)="save()" novalidate>
                <h2>Modifier les informations officielles</h2>
                <label class="field">
                  <span class="label">Nom *</span>
                  <input name="name" [maxlength]="limits.name" [ngModel]="form().name" (ngModelChange)="set('name', $event)"
                         [attr.aria-invalid]="!!errors()['name']" />
                  @if (errors()['name']; as m) { <span class="field-error">{{ m }}</span> }
                </label>
                <label class="field">
                  <span class="label">Description</span>
                  <textarea name="description" rows="4" [ngModel]="form().description" (ngModelChange)="set('description', $event)"></textarea>
                  <span class="hint char-count" [class.near-limit]="(form().description ?? '').length > limits.description - 100">
                    {{ (form().description ?? '').length }} / {{ limits.description }}
                  </span>
                  @if (errors()['description']; as m) { <span class="field-error">{{ m }}</span> }
                </label>
                <label class="field">
                  <span class="label">Horaires</span>
                  <textarea name="openingHours" rows="2" [maxlength]="limits.openingHours" [ngModel]="form().openingHours"
                            (ngModelChange)="set('openingHours', $event)" placeholder="Ex. : tous les jours, 8h–23h"></textarea>
                  @if (errors()['openingHours']; as m) { <span class="field-error">{{ m }}</span> }
                </label>
                <div class="location-form-row">
                  <label class="field">
                    <span class="label">Téléphone</span>
                    <input name="phone" type="tel" [maxlength]="limits.phone" [ngModel]="form().phone" (ngModelChange)="set('phone', $event)"
                           [attr.aria-invalid]="!!errors()['phone']" />
                    @if (errors()['phone']; as m) { <span class="field-error">{{ m }}</span> }
                  </label>
                  <label class="field">
                    <span class="label">E-mail</span>
                    <input name="email" type="email" [maxlength]="limits.email" [ngModel]="form().email" (ngModelChange)="set('email', $event)"
                           [attr.aria-invalid]="!!errors()['email']" />
                    @if (errors()['email']; as m) { <span class="field-error">{{ m }}</span> }
                  </label>
                </div>
                <label class="field">
                  <span class="label">Site web</span>
                  <input name="website" type="url" [maxlength]="limits.website" [ngModel]="form().website" (ngModelChange)="set('website', $event)"
                         placeholder="https://…" [attr.aria-invalid]="!!errors()['website']" />
                  @if (errors()['website']; as m) { <span class="field-error">{{ m }}</span> }
                </label>
                <label class="field">
                  <span class="label">Adresse</span>
                  <input name="address" [maxlength]="limits.address" [ngModel]="form().address" (ngModelChange)="set('address', $event)" />
                  @if (errors()['address']; as m) { <span class="field-error">{{ m }}</span> }
                </label>
                @if (saveError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
                <div class="actions">
                  <button type="button" class="btn btn-ghost" (click)="cancelEdit()" [disabled]="saving()">Annuler</button>
                  <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Enregistrement…' : 'Enregistrer' }}</button>
                </div>
              </form>
            } @else {
              @if (b.description) { <p class="business-description">{{ b.description }}</p> }
              <dl class="business-info">
                @if (b.openingHours) { <div><dt>Horaires</dt><dd>{{ b.openingHours }}</dd></div> }
                @if (b.address) { <div><dt>Adresse</dt><dd>{{ b.address }}</dd></div> }
                @if (b.phone) { <div><dt>Téléphone</dt><dd><a class="link" [href]="'tel:' + b.phone">{{ b.phone }}</a></dd></div> }
                @if (b.email) { <div><dt>E-mail</dt><dd><a class="link" [href]="'mailto:' + b.email">{{ b.email }}</a></dd></div> }
                @if (b.website) { <div><dt>Site web</dt><dd><a class="link" [href]="b.website" target="_blank" rel="noopener">{{ b.website }}</a></dd></div> }
              </dl>
              @if (!hasInfo()) { <p class="muted small">Aucune information officielle pour le moment.</p> }
              @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }
              @if (b.member) {
                <div class="actions">
                  <button type="button" class="btn btn-ghost" (click)="startEdit()">Modifier les informations</button>
                </div>
              }
            }
          </section>

          <app-star-rating [pageId]="b.id" />

          <section class="business-wall" aria-labelledby="wall-title">
            <div class="section-heading">
              <div>
                <p class="eyebrow">Mur</p>
                <h2 id="wall-title">Ce que dit la communauté</h2>
                <p class="muted small wall-explainer">
                  Les publications dont le lieu confirmé est cet établissement, y compris celles écrites avant la création de la page.
                  @if (b.member) {
                    Masquer une publication ne la retire que de ce mur : elle reste dans le fil général et sur le profil de son auteur.
                  }
                </p>
              </div>
              @if (b.member) {
                <label class="wall-toggle small">
                  <input type="checkbox" [checked]="showHidden()" (change)="toggleHidden()" />
                  Afficher les publications masquées
                </label>
              }
            </div>

            @if (wallError(); as m) {
              <div class="banner banner-error" role="alert">
                <strong>{{ m }}</strong>
                <button type="button" class="btn btn-ghost" (click)="loadWall(true)">Réessayer</button>
              </div>
            }
            @if (wallActionError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }

            @if (wallLoading() && !posts().length) {
              <div class="card skeleton" aria-hidden="true"><div></div><div></div><div></div></div>
            } @else if (!wallError()) {
              @for (p of posts(); track p.id) {
                <article class="card post post-card wall-post" [class.wall-post-hidden]="p.hidden">
                  <header class="post-head">
                    <app-avatar [name]="p.authorName" />
                    <div class="post-author">
                      <strong>{{ p.authorName }}</strong>
                      <span class="muted small" [title]="date(p.createdAt)">
                        {{ ago(p.createdAt) }}@if (wasEdited(p.createdAt, p.updatedAt)) { · modifié }
                      </span>
                    </div>
                    @if (p.hidden) {
                      <span class="pill pill-deleted" [title]="p.hiddenReason ? 'Motif : ' + p.hiddenReason : ''">Masquée sur ce mur</span>
                    }
                  </header>
                  <p class="post-content">{{ p.content }}</p>
                  <app-media-gallery [media]="p.media" />
                  @if (p.hidden && p.hiddenReason) { <p class="muted small">Motif : {{ p.hiddenReason }}</p> }

                  @if (hidingId() === p.id) {
                    <label class="field">
                      <span class="label">Motif (facultatif, visible des membres de la page)</span>
                      <input [name]="'hide-reason-' + p.id" [maxlength]="hideMax" [ngModel]="hideReason()" (ngModelChange)="hideReason.set($event)"
                             placeholder="Ex. : hors sujet" />
                    </label>
                  }
                  <footer class="post-foot">
                    <a class="post-discussion-link" [routerLink]="['/posts', p.id]">Voir la discussion <span aria-hidden="true">→</span></a>
                    @if (b.member) {
                      <div class="actions">
                        @if (p.hidden) {
                          <button type="button" class="btn btn-ghost btn-small" (click)="unhide(p)" [disabled]="busyPostId() === p.id">
                            {{ busyPostId() === p.id ? '…' : 'Afficher à nouveau' }}
                          </button>
                        } @else if (hidingId() === p.id) {
                          <button type="button" class="btn btn-ghost btn-small" (click)="hidingId.set(null)">Annuler</button>
                          <button type="button" class="btn btn-danger btn-small" (click)="hide(p)" [disabled]="busyPostId() === p.id">
                            {{ busyPostId() === p.id ? 'Masquage…' : 'Confirmer' }}
                          </button>
                        } @else {
                          <button type="button" class="btn btn-ghost btn-small" (click)="startHide(p)">Masquer de ce mur</button>
                        }
                      </div>
                    }
                  </footer>
                </article>
              } @empty {
                <div class="card empty">
                  <p><strong>Aucune publication sur ce mur pour l'instant.</strong></p>
                  <p class="muted small">Une publication y apparaît quand son lieu confirmé est cet établissement.</p>
                </div>
              }
              @if (wallHasMore()) {
                <div class="wall-more">
                  <button type="button" class="btn btn-ghost" (click)="loadWall(false)" [disabled]="wallLoading()">
                    {{ wallLoading() ? 'Chargement…' : 'Afficher plus' }}
                  </button>
                </div>
              }
            }
          </section>
        </div>

        @if (b.member || auth.isAdmin()) {
          <aside class="detail-side">
            <section class="card wall-history" aria-labelledby="history-title">
              <h2 id="history-title">Historique du mur</h2>
              @if (historyError(); as m) {
                <p class="field-error">{{ m }}</p>
              } @else {
                <ul class="wall-history-list">
                  @for (h of history(); track h.id) {
                    <li class="small">
                      <strong>{{ actionLabel[h.action] }}</strong> · publication
                      <a class="link" [routerLink]="['/posts', h.postId]">n°{{ h.postId }}</a>
                      <div class="muted">{{ h.actorName || 'Membre' }} · {{ ago(h.createdAt) }}</div>
                      @if (h.reason) { <div>Motif : {{ h.reason }}</div> }
                    </li>
                  } @empty {
                    <li class="muted small">Aucune publication masquée jusqu'ici.</li>
                  }
                </ul>
              }
            </section>
          </aside>
        }
      </div>
    } }
  `,
})
export class BusinessPagePage {
  private readonly api = inject(BusinessApiService);
  readonly auth = inject(AuthService);

  /** Route parameter :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  readonly limits = PAGE_FIELD_LIMITS;
  readonly hideMax = HIDE_REASON_MAX_LENGTH;
  readonly roleLabel = PAGE_ROLE_LABEL;
  readonly actionLabel = AUDIT_ACTION_LABEL;
  readonly ago = relativeTime;
  readonly wasEdited = wasEdited;

  readonly page = signal<BusinessPage | null>(null);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly hasInfo = computed(() => {
    const b = this.page();
    return !!b && !!(b.description || b.openingHours || b.address || b.phone || b.email || b.website);
  });

  readonly editing = signal(false);
  readonly form = signal<UpdatePageRequest>({ name: '', description: '', openingHours: '', phone: '', email: '', website: '', address: '' });
  readonly errors = signal<Record<string, string>>({});
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly posts = signal<WallPost[]>([]);
  readonly wallLoading = signal(false);
  readonly wallError = signal<string | null>(null);
  readonly wallHasMore = signal(false);
  readonly showHidden = signal(false);
  readonly wallActionError = signal<string | null>(null);
  readonly hidingId = signal<number | null>(null);
  readonly hideReason = signal('');
  readonly busyPostId = signal<number | null>(null);

  readonly history = signal<BusinessAuditEntry[]>([]);
  readonly historyError = signal<string | null>(null);

  private nextWallPage = 0;
  /** Bumped by a reload, so an answer to an older request is ignored. */
  private wallGeneration = 0;

  constructor() {
    effect(() => {
      this.id();
      untracked(() => this.load());
    });
  }

  private pageId(): number {
    return Number(this.id());
  }

  load(): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.loadError.set(null);
    this.editing.set(false);
    this.showHidden.set(false);
    const id = this.pageId();
    if (!Number.isInteger(id) || id <= 0) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.api.get(id).subscribe({
      next: page => {
        this.page.set(page);
        this.loading.set(false);
        this.loadWall(true);
        if (page.member || this.auth.isAdmin()) this.loadHistory();
      },
      error: err => {
        const error = toUiError(err);
        if ((error.status === 404 && error.code !== 'HTTP_404') || error.status === 400) this.notFound.set(true);
        else this.loadError.set(businessErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  // ----- official information

  startEdit(): void {
    const b = this.page();
    if (!b) return;
    this.form.set(formOf(b));
    this.errors.set({});
    this.saveError.set(null);
    this.notice.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  set(field: keyof UpdatePageRequest, value: string): void {
    this.form.update(f => ({ ...f, [field]: value ?? '' }));
  }

  save(): void {
    const b = this.page();
    if (!b || this.saving()) return;
    const errors = validatePageForm(this.form());
    this.errors.set(errors);
    this.saveError.set(null);
    if (Object.keys(errors).length) return;
    this.saving.set(true);
    this.api.update(b.id, toUpdateRequest(this.form())).subscribe({
      next: updated => {
        this.saving.set(false);
        this.page.set(updated);
        this.editing.set(false);
        this.notice.set('Informations officielles enregistrées.');
      },
      error: err => {
        this.saving.set(false);
        const error = toUiError(err);
        if (error.code === 'VALIDATION_ERROR' && Object.keys(error.details).length) this.errors.set(error.details);
        this.saveError.set(
          error.status === 403 ? 'Seuls les membres de cette page peuvent modifier ses informations.' : businessErrorMessage(error),
        );
      },
    });
  }

  // ----- wall

  toggleHidden(): void {
    this.showHidden.update(v => !v);
    this.loadWall(true);
  }

  /** `reset` reloads from the first page (after a hide/unhide or the toggle); otherwise loads the next page. */
  loadWall(reset: boolean): void {
    const b = this.page();
    if (!b) return;
    const generation = reset ? ++this.wallGeneration : this.wallGeneration;
    if (reset) {
      this.nextWallPage = 0;
      this.wallError.set(null);
    }
    this.wallLoading.set(true);
    this.api.wall(b.id, this.nextWallPage, BUSINESS_PAGE_SIZE, this.showHidden() && b.member).subscribe({
      next: p => {
        if (generation !== this.wallGeneration) return;
        this.posts.update(shown => (reset ? p.items : appendWall(shown, p.items)));
        this.nextWallPage = p.page + 1;
        this.wallHasMore.set(p.items.length > 0 && p.page + 1 < p.totalPages);
        this.wallLoading.set(false);
      },
      error: err => {
        if (generation !== this.wallGeneration) return;
        if (reset) this.posts.set([]);
        this.wallError.set(businessErrorMessage(toUiError(err)));
        this.wallLoading.set(false);
      },
    });
  }

  startHide(p: WallPost): void {
    this.hidingId.set(p.id);
    this.hideReason.set('');
    this.wallActionError.set(null);
  }

  hide(p: WallPost): void {
    const b = this.page();
    if (!b || this.busyPostId() !== null) return;
    this.busyPostId.set(p.id);
    this.wallActionError.set(null);
    this.api.hide(b.id, p.id, this.hideReason()).subscribe({
      next: () => this.afterWallAction(),
      error: err => this.wallActionFailed(err),
    });
  }

  unhide(p: WallPost): void {
    const b = this.page();
    if (!b || this.busyPostId() !== null) return;
    this.busyPostId.set(p.id);
    this.wallActionError.set(null);
    this.api.unhide(b.id, p.id).subscribe({
      next: () => this.afterWallAction(),
      error: err => this.wallActionFailed(err),
    });
  }

  private afterWallAction(): void {
    this.busyPostId.set(null);
    this.hidingId.set(null);
    this.loadWall(true);
    this.loadHistory();
  }

  private wallActionFailed(err: unknown): void {
    this.busyPostId.set(null);
    const error = toUiError(err);
    this.wallActionError.set(
      error.status === 403 ? 'Seuls les membres de cette page peuvent gérer son mur.' : businessErrorMessage(error),
    );
  }

  loadHistory(): void {
    const b = this.page();
    if (!b) return;
    this.historyError.set(null);
    this.api.history(b.id, 0, BUSINESS_PAGE_SIZE).subscribe({
      next: p => this.history.set(p.items),
      error: err => this.historyError.set(businessErrorMessage(toUiError(err))),
    });
  }

  date(iso: string | null): string {
    return iso ? fullDate(iso) : '';
  }
}

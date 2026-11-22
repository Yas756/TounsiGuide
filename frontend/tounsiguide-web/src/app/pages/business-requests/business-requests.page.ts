import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BUSINESS_PAGE_SIZE, BusinessApiService } from '../../core/business-api.service';
import { AuthService } from '../../core/auth.service';
import {
  businessErrorMessage,
  CONTACT_MAX_LENGTH,
  EVIDENCE_MAX_LENGTH,
  PageRequest,
  PROPOSED_NAME_MAX_LENGTH,
  REQUEST_STATUS_LABEL,
  requestPlaceLabel,
} from '../../core/business.model';
import { ApiLocation } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { PlacePickerComponent } from '../../shared/place-picker.component';
import { fullDate, relativeTime } from '../../shared/format';

export interface PageRequestForm {
  location: ApiLocation | null;
  proposedName: string;
  evidence: string;
  contact: string;
}

export const EMPTY_PAGE_REQUEST_FORM: PageRequestForm = { location: null, proposedName: '', evidence: '', contact: '' };

/** French messages per field, checked before sending (the backend checks the same rules again). */
export function validatePageRequestForm(form: PageRequestForm): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!form.location) errors['locationId'] = 'Choisissez le lieu de votre établissement.';
  const evidence = form.evidence.trim();
  if (!evidence) errors['evidence'] = 'Expliquez pourquoi vous représentez ce lieu.';
  else if (evidence.length > EVIDENCE_MAX_LENGTH) errors['evidence'] = `${EVIDENCE_MAX_LENGTH} caractères au maximum.`;
  if (form.proposedName.trim().length > PROPOSED_NAME_MAX_LENGTH) errors['proposedName'] = `${PROPOSED_NAME_MAX_LENGTH} caractères au maximum.`;
  if (form.contact.trim().length > CONTACT_MAX_LENGTH) errors['contact'] = `${CONTACT_MAX_LENGTH} caractères au maximum.`;
  return errors;
}

/**
 * S3-F01 (member side): request the official page of an existing Location with proof of authority, and follow
 * one's requests. After an approval the new role only reaches the session at the next login.
 */
@Component({
  selector: 'app-business-requests-page',
  standalone: true,
  imports: [FormsModule, RouterLink, PlacePickerComponent],
  template: `
    <header class="page-head">
      <p class="eyebrow">Pages professionnelles</p>
      <h1>Demander la page officielle d'un lieu</h1>
      <p class="muted">
        Vous représentez un café, un restaurant, un hôtel… ? Demandez sa page : un administrateur vérifie votre preuve,
        puis vous pourrez gérer ses informations officielles et son mur.
      </p>
    </header>

    <section class="card" aria-labelledby="request-form-title">
      <h2 id="request-form-title">Nouvelle demande</h2>
      <form (ngSubmit)="submit()" novalidate>
        <div class="field">
          <app-place-picker label="Lieu de votre établissement *" addLabel="Choisir le lieu"
                            [value]="form().location" (valueChange)="setLocation($event)" />
          @if (errors()['locationId']; as m) { <span class="field-error">{{ m }}</span> }
        </div>
        <label class="field">
          <span class="label">Nom de la page (facultatif)</span>
          <input name="proposedName" [maxlength]="nameMax" [ngModel]="form().proposedName" (ngModelChange)="set('proposedName', $event)"
                 placeholder="Par défaut : le nom du lieu" [attr.aria-invalid]="!!errors()['proposedName']" />
          @if (errors()['proposedName']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        <label class="field">
          <span class="label">Preuve d'autorité *</span>
          <textarea name="evidence" rows="5" [ngModel]="form().evidence" (ngModelChange)="set('evidence', $event)"
                    aria-describedby="evidence-count" [attr.aria-invalid]="!!errors()['evidence']"
                    placeholder="Ex. : je suis le gérant ; numéro du registre de commerce, lien vers le site officiel, e-mail professionnel…"></textarea>
          <span id="evidence-count" class="hint char-count" [class.near-limit]="form().evidence.length > evidenceMax - 100">
            {{ form().evidence.length }} / {{ evidenceMax }}
          </span>
          @if (errors()['evidence']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        <label class="field">
          <span class="label">Contact (facultatif)</span>
          <input name="contact" [maxlength]="contactMax" [ngModel]="form().contact" (ngModelChange)="set('contact', $event)"
                 placeholder="Téléphone ou e-mail où l'administrateur peut vous joindre" />
          @if (errors()['contact']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        @if (submitError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
        @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }
        <div class="actions">
          <button type="submit" class="btn btn-primary" [disabled]="submitting() || form().evidence.length > evidenceMax">
            {{ submitting() ? 'Envoi…' : 'Envoyer la demande' }}
          </button>
        </div>
      </form>
    </section>

    <section aria-labelledby="my-requests-title">
      <h2 id="my-requests-title">Mes demandes</h2>
      @if (loading()) {
        <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
      } @else if (listError()) {
        <div class="banner banner-error" role="alert">
          <strong>{{ listError() }}</strong>
          <button type="button" class="btn btn-ghost" (click)="load(0)">Réessayer</button>
        </div>
      } @else { @if (page(); as p) {
        <ul class="business-request-list">
          @for (r of p.items; track r.id) {
            <li class="card business-request">
              <div class="business-request-head">
                <div>
                  <strong>{{ r.proposedName || placeLabel(r) }}</strong>
                  <div class="muted small">{{ placeLabel(r) }} · envoyée {{ ago(r.createdAt) }}</div>
                </div>
                <span class="status" [class.status-confirmed]="r.status === 'APPROVED'"
                      [class.status-proposed]="r.status === 'PENDING'" [class.status-disputed]="r.status === 'REJECTED'">
                  {{ statusLabel[r.status] }}
                </span>
              </div>
              @if (r.status === 'REJECTED') {
                <p class="small"><strong>Motif du refus :</strong> {{ r.decisionReason }}</p>
              }
              @if (r.status === 'APPROVED') {
                <p class="small">
                  Approuvée le {{ date(r.decidedAt) }}.
                  @if (r.pageId !== null) { <a class="link" [routerLink]="['/pages', r.pageId]">Voir la page</a> }
                </p>
                @if (!auth.hasRole('BUSINESS_REP')) {
                  <div class="banner banner-info relogin-hint" role="status">
                    Reconnectez-vous pour gérer votre page : votre nouveau rôle de représentant sera pris en compte à la prochaine connexion.
                  </div>
                }
              }
            </li>
          } @empty {
            <li class="card empty">Vous n'avez encore envoyé aucune demande.</li>
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
    </section>
  `,
})
export class BusinessRequestsPage implements OnInit {
  private readonly api = inject(BusinessApiService);
  readonly auth = inject(AuthService);

  readonly evidenceMax = EVIDENCE_MAX_LENGTH;
  readonly nameMax = PROPOSED_NAME_MAX_LENGTH;
  readonly contactMax = CONTACT_MAX_LENGTH;
  readonly statusLabel = REQUEST_STATUS_LABEL;
  readonly placeLabel = requestPlaceLabel;
  readonly ago = relativeTime;

  readonly form = signal<PageRequestForm>({ ...EMPTY_PAGE_REQUEST_FORM });
  readonly errors = signal<Record<string, string>>({});
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly page = signal<Page<PageRequest> | null>(null);
  readonly loading = signal(true);
  readonly listError = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  set(field: 'proposedName' | 'evidence' | 'contact', value: string): void {
    this.form.update(f => ({ ...f, [field]: value ?? '' }));
    this.notice.set(null);
  }

  setLocation(location: ApiLocation | null): void {
    this.form.update(f => ({ ...f, location }));
    this.notice.set(null);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.listError.set(null);
    this.api.myRequests(pageIndex, BUSINESS_PAGE_SIZE).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.listError.set(businessErrorMessage(toUiError(err)));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (this.submitting()) return;
    const form = this.form();
    const errors = validatePageRequestForm(form);
    this.errors.set(errors);
    this.submitError.set(null);
    this.notice.set(null);
    if (Object.keys(errors).length || !form.location) return;
    this.submitting.set(true);
    this.api
      .submitRequest({
        locationId: form.location.id,
        proposedName: form.proposedName.trim() || null,
        evidence: form.evidence.trim(),
        contact: form.contact.trim() || null,
      })
      .subscribe({
        next: created => {
          this.submitting.set(false);
          this.form.set({ ...EMPTY_PAGE_REQUEST_FORM });
          this.notice.set(`Demande envoyée pour « ${requestPlaceLabel(created)} ». Un administrateur va l'examiner.`);
          this.load(0);
        },
        error: err => {
          this.submitting.set(false);
          const error: UiError = toUiError(err);
          if (error.code === 'VALIDATION_ERROR' && Object.keys(error.details).length) this.errors.set(error.details);
          this.submitError.set(businessErrorMessage(error));
        },
      });
  }

  date(iso: string | null): string {
    return iso ? fullDate(iso) : '';
  }
}

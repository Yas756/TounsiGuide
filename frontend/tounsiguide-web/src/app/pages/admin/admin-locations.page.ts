import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LocationApiService } from '../../core/location-api.service';
import { ApiLocation, CreateLocationRequest } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';

/** What the admin types; numbers stay text until the form is checked. */
export interface LocationForm {
  name: string;
  city: string;
  address: string;
  category: string;
  latitude: string;
  longitude: string;
  /** Comma separated. */
  aliases: string;
}

export const EMPTY_LOCATION_FORM: LocationForm = {
  name: '', city: '', address: '', category: '', latitude: '', longitude: '', aliases: '',
};

/** French messages per field, checked before sending (the backend checks the same rules again). */
export function validateLocationForm(form: LocationForm): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = form.name.trim();
  if (!name) errors['name'] = 'Le nom est obligatoire.';
  else if (name.length > 150) errors['name'] = '150 caractères au maximum.';
  if (form.city.trim().length > 100) errors['city'] = '100 caractères au maximum.';
  if (form.address.trim().length > 500) errors['address'] = '500 caractères au maximum.';
  if (form.category.trim().length > 60) errors['category'] = '60 caractères au maximum.';

  const lat = form.latitude.trim();
  const lng = form.longitude.trim();
  if (lat || lng) {
    if (!lat || !lng) {
      errors['coordinates'] = 'Donnez la latitude et la longitude, ou aucune des deux.';
    } else {
      const latitude = Number(lat.replace(',', '.'));
      const longitude = Number(lng.replace(',', '.'));
      if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) errors['latitude'] = 'Entre -90 et 90.';
      if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) errors['longitude'] = 'Entre -180 et 180.';
    }
  }
  const aliases = splitAliases(form.aliases);
  if (aliases.length > 20) errors['aliases'] = '20 autres noms au maximum.';
  else if (aliases.some(a => a.length > 150)) errors['aliases'] = 'Chaque autre nom : 150 caractères au maximum.';
  return errors;
}

/** "Bardo, Musée du Bardo" → ["Bardo", "Musée du Bardo"] (blanks and duplicates removed). */
export function splitAliases(text: string): string[] {
  const seen = new Set<string>();
  const aliases: string[] = [];
  for (const raw of text.split(',')) {
    const alias = raw.trim();
    if (alias && !seen.has(alias.toLowerCase())) {
      seen.add(alias.toLowerCase());
      aliases.push(alias);
    }
  }
  return aliases;
}

export function toCreateRequest(form: LocationForm): CreateLocationRequest {
  const text = (value: string) => value.trim() || null;
  const number = (value: string) => (value.trim() ? Number(value.trim().replace(',', '.')) : null);
  return {
    name: form.name.trim(),
    city: text(form.city),
    address: text(form.address),
    latitude: number(form.latitude),
    longitude: number(form.longitude),
    category: text(form.category),
    aliases: splitAliases(form.aliases),
  };
}

/** French message for a failed creation. */
export function createLocationErrorMessage(error: UiError): string {
  switch (error.status) {
    case 409:
      return 'Ce lieu existe déjà : un lieu porte déjà ce nom dans cette ville. Cherchez-le dans la liste ou ajoutez un autre nom.';
    case 403:
      return 'Seul un administrateur peut ajouter un lieu.';
    case 401:
      return 'Votre session a expiré. Reconnectez-vous pour continuer.';
    case 400:
      return 'Certains champs ne sont pas valides. Vérifiez le formulaire.';
    default:
      return error.status === 0 || error.status >= 500
        ? 'Le service des lieux ne répond pas pour le moment. Réessayez dans un instant.'
        : error.message;
  }
}

/** Directory of Locations (S2-US10): search the existing ones and add a new one. */
@Component({
  selector: 'app-admin-locations',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent],
  template: `
    <section class="card admin-location-form" aria-labelledby="new-location-title">
      <h2 id="new-location-title">Ajouter un lieu</h2>
      <p class="muted small">Le lieu entre dans l'annuaire : les membres peuvent ensuite le choisir pour leurs publications.</p>
      <form (ngSubmit)="submit()" novalidate>
        <label class="field">
          <span class="label">Nom *</span>
          <input name="name" maxlength="150" required [ngModel]="form().name" (ngModelChange)="set('name', $event)"
                 [attr.aria-invalid]="!!errors()['name']" placeholder="Ex. : Musée national du Bardo" />
          @if (errors()['name']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        <div class="location-form-row">
          <label class="field">
            <span class="label">Ville</span>
            <input name="city" maxlength="100" [ngModel]="form().city" (ngModelChange)="set('city', $event)" placeholder="Tunis" />
            @if (errors()['city']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
          <label class="field">
            <span class="label">Catégorie</span>
            <input name="category" maxlength="60" [ngModel]="form().category" (ngModelChange)="set('category', $event)"
                   placeholder="museum, cafe, beach…" />
            @if (errors()['category']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
        </div>
        <label class="field">
          <span class="label">Adresse</span>
          <input name="address" maxlength="500" [ngModel]="form().address" (ngModelChange)="set('address', $event)" />
          @if (errors()['address']; as m) { <span class="field-error">{{ m }}</span> }
        </label>
        <div class="location-form-row">
          <label class="field">
            <span class="label">Latitude</span>
            <input name="latitude" inputmode="decimal" [ngModel]="form().latitude" (ngModelChange)="set('latitude', $event)"
                   [attr.aria-invalid]="!!errors()['latitude'] || !!errors()['coordinates']" placeholder="36.8095" />
            @if (errors()['latitude']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
          <label class="field">
            <span class="label">Longitude</span>
            <input name="longitude" inputmode="decimal" [ngModel]="form().longitude" (ngModelChange)="set('longitude', $event)"
                   [attr.aria-invalid]="!!errors()['longitude'] || !!errors()['coordinates']" placeholder="10.1347" />
            @if (errors()['longitude']; as m) { <span class="field-error">{{ m }}</span> }
          </label>
        </div>
        @if (errors()['coordinates']; as m) { <p class="field-error">{{ m }}</p> }
        <label class="field">
          <span class="label">Autres noms (séparés par des virgules)</span>
          <input name="aliases" [ngModel]="form().aliases" (ngModelChange)="set('aliases', $event)" placeholder="Bardo, Musée du Bardo" />
          @if (errors()['aliases']; as m) { <span class="field-error">{{ m }}</span> }
        </label>

        @if (saveError(); as m) { <div class="banner banner-error" role="alert">{{ m }}</div> }
        @if (notice(); as n) { <div class="banner banner-info" role="status">{{ n }}</div> }
        <div class="actions">
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Ajout…' : 'Ajouter le lieu' }}</button>
        </div>
      </form>
    </section>

    <section aria-labelledby="directory-title">
      <h2 id="directory-title">Annuaire</h2>
      <form class="location-search" role="search" (ngSubmit)="search()">
        <label class="field field-inline">
          <span class="sr-only">Chercher un lieu</span>
          <input name="query" type="search" [ngModel]="query()" (ngModelChange)="query.set($event)"
                 placeholder="Nom, autre nom ou ville" />
        </label>
        <button type="submit" class="btn btn-ghost">Chercher</button>
      </form>
      <app-error-banner [error]="listError()" [retryable]="true" (retry)="search()" />
      @if (listLoading()) {
        <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
      } @else {
        <ul class="location-list">
          @for (l of locations(); track l.id) {
            <li class="card location-row" [class.just-added]="l.id === lastCreatedId()">
              <div>
                <strong>{{ l.name }}</strong>@if (l.city) { <span class="muted">, {{ l.city }}</span> }
                <div class="muted small">
                  n° {{ l.id }}@if (l.category) { · {{ l.category }} }@if (l.source === 'MAP') { · ajouté depuis la carte }
                  @if (l.latitude !== null && l.longitude !== null) { · {{ l.latitude }}, {{ l.longitude }} }
                </div>
                @if (l.aliases.length) { <div class="small">Aussi : {{ l.aliases.join(', ') }}</div> }
              </div>
              @if (l.id === lastCreatedId()) { <span class="pill pill-admin">Nouveau</span> }
            </li>
          } @empty {
            <li class="card empty">Aucun lieu trouvé.</li>
          }
        </ul>
      }
    </section>
  `,
})
export class AdminLocationsPage implements OnInit {
  private readonly api = inject(LocationApiService);

  readonly form = signal<LocationForm>({ ...EMPTY_LOCATION_FORM });
  readonly errors = signal<Record<string, string>>({});
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly lastCreatedId = signal<number | null>(null);

  readonly query = signal('');
  readonly locations = signal<ApiLocation[]>([]);
  readonly listLoading = signal(true);
  readonly listError = signal<UiError | null>(null);

  ngOnInit(): void {
    this.search();
  }

  set(field: keyof LocationForm, value: string): void {
    this.form.update(f => ({ ...f, [field]: value ?? '' }));
    this.notice.set(null);
  }

  search(): void {
    this.listLoading.set(true);
    this.listError.set(null);
    this.api.directory(this.query(), 50).subscribe({
      next: list => {
        this.locations.set(list);
        this.listLoading.set(false);
      },
      error: err => {
        this.listError.set(toUiError(err));
        this.listLoading.set(false);
      },
    });
  }

  submit(): void {
    if (this.saving()) return;
    const errors = validateLocationForm(this.form());
    this.errors.set(errors);
    this.saveError.set(null);
    this.notice.set(null);
    if (Object.keys(errors).length) return;
    this.saving.set(true);
    this.api.create(toCreateRequest(this.form())).subscribe({
      next: created => {
        this.saving.set(false);
        this.form.set({ ...EMPTY_LOCATION_FORM });
        this.lastCreatedId.set(created.id);
        this.locations.update(list => [created, ...list.filter(l => l.id !== created.id)]);
        this.notice.set(`« ${created.name} » a été ajouté à l'annuaire (n° ${created.id}).`);
      },
      error: err => {
        this.saving.set(false);
        const error = toUiError(err);
        this.saveError.set(createLocationErrorMessage(error));
        if (error.status === 409) this.errors.set({ name: 'Déjà dans l’annuaire pour cette ville.' });
      },
    });
  }
}

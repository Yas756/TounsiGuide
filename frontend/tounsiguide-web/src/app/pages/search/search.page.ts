import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { ApiLocation, categoryLabel, externalMapUrl, placeDetails } from '../../core/api-location.model';
import { ApiPost } from '../../core/api-post.model';
import { BusinessApiService } from '../../core/business-api.service';
import { BusinessPage } from '../../core/business.model';
import { LocationApiService } from '../../core/location-api.service';
import { PostApiService } from '../../core/post-api.service';
import { relativeTime } from '../../shared/format';

/** One group of results; `failed` when its service did not answer (the other groups are still shown). */
interface Group<T> {
  items: T[];
  total: number;
  failed: boolean;
}

interface Results {
  locations: Group<ApiLocation>;
  pages: Group<BusinessPage>;
  posts: Group<ApiPost>;
}

const PER_GROUP = 8;
const MIN_LENGTH = 2;

/**
 * Unified search (S3-US10, FR-14): one search box, results grouped by type. No new service: the page asks the
 * three owners of the data in parallel through the Gateway (Location directory, Business pages, Community posts),
 * and a service that is down only empties its own group.
 */
@Component({
  selector: 'app-search-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <header class="page-head">
      <p class="eyebrow">Recherche</p>
      <h1>{{ q()?.trim() ? 'Résultats pour « ' + q()!.trim() + ' »' : 'Rechercher' }}</h1>
      <p class="muted">Lieux, pages officielles et publications, en une seule recherche.</p>
    </header>

    <form class="location-search" role="search" (ngSubmit)="submit()">
      <label class="field field-inline">
        <span class="sr-only">Rechercher</span>
        <input type="search" name="q" [ngModel]="draft()" (ngModelChange)="draft.set($event)"
               placeholder="Café, plage, Médina, lablabi…" />
      </label>
      <button type="submit" class="btn btn-primary">Rechercher</button>
    </form>

    @if (tooShort()) {
      <p class="card empty">Tapez au moins {{ minLength }} caractères.</p>
    } @else if (loading()) {
      <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
    } @else { @if (results(); as r) {
      @if (!r.locations.total && !r.pages.total && !r.posts.total && !r.locations.failed && !r.pages.failed && !r.posts.failed) {
        <p class="card empty">Aucun résultat. Essayez un autre mot ou un nom de ville.</p>
      } @else {
        <section class="search-group" aria-labelledby="search-locations">
          <h2 id="search-locations">Lieux <span class="muted small">({{ r.locations.total }})</span></h2>
          @if (r.locations.failed) { <p class="banner banner-error" role="alert">La recherche de lieux ne répond pas pour le moment.</p> }
          <ul class="search-list">
            @for (l of r.locations.items; track l.id) {
              <li class="card">
                <strong>{{ l.name }}</strong>
                <div class="muted small">
                  @if (category(l.category); as c) { <span class="place-cat">{{ c }}</span> · }
                  {{ details(l) }}
                </div>
                @if (pageOf(r, l.id); as p) {
                  <a class="link small" [routerLink]="['/pages', p.id]">✓ Voir la page officielle</a>
                } @else if (mapUrl(l)) {
                  <a class="link small" [href]="mapUrl(l)" target="_blank" rel="noopener">Voir sur la carte</a>
                }
              </li>
            } @empty { @if (!r.locations.failed) { <li class="muted small">Aucun lieu.</li> } }
          </ul>
        </section>

        <section class="search-group" aria-labelledby="search-pages">
          <h2 id="search-pages">Pages officielles <span class="muted small">({{ r.pages.total }})</span></h2>
          @if (r.pages.failed) { <p class="banner banner-error" role="alert">Les pages officielles ne répondent pas pour le moment.</p> }
          <ul class="search-list">
            @for (b of r.pages.items; track b.id) {
              <li class="card">
                <a class="search-hit-title" [routerLink]="['/pages', b.id]">{{ b.name }}</a>
                <span class="status status-confirmed">Page vérifiée</span>
                @if (b.location; as loc) { <div class="muted small">{{ loc.city || loc.address }}</div> }
                @if (b.description) { <p class="small search-hit-text">{{ b.description }}</p> }
              </li>
            } @empty { @if (!r.pages.failed) { <li class="muted small">Aucune page officielle.</li> } }
          </ul>
        </section>

        <section class="search-group" aria-labelledby="search-posts">
          <h2 id="search-posts">Publications <span class="muted small">({{ r.posts.total }})</span></h2>
          @if (r.posts.failed) { <p class="banner banner-error" role="alert">Les publications ne répondent pas pour le moment.</p> }
          <ul class="search-list">
            @for (p of r.posts.items; track p.id) {
              <li class="card">
                <a class="search-hit-title" [routerLink]="['/posts', p.id]">{{ p.authorName }}</a>
                <span class="muted small"> · {{ ago(p.createdAt) }}</span>
                <p class="search-hit-text">{{ p.content }}</p>
              </li>
            } @empty { @if (!r.posts.failed) { <li class="muted small">Aucune publication.</li> } }
          </ul>
          @if (r.posts.total > r.posts.items.length) {
            <p class="muted small">Les {{ r.posts.items.length }} plus récentes sur {{ r.posts.total }}. Précisez la recherche pour affiner.</p>
          }
        </section>
      }
    } }
  `,
})
export class SearchPage {
  private readonly router = inject(Router);
  private readonly locationsApi = inject(LocationApiService);
  private readonly businessApi = inject(BusinessApiService);
  private readonly postsApi = inject(PostApiService);

  /** ?q=… , bound by withComponentInputBinding(). */
  readonly q = input<string | undefined>();

  readonly minLength = MIN_LENGTH;
  readonly draft = signal('');
  readonly loading = signal(false);
  readonly tooShort = signal(false);
  readonly results = signal<Results | null>(null);

  readonly category = categoryLabel;
  readonly details = placeDetails;
  readonly mapUrl = externalMapUrl;
  readonly ago = relativeTime;

  constructor() {
    effect(() => this.run(this.q() ?? ''), { allowSignalWrites: true });
  }

  submit(): void {
    const q = this.draft().trim();
    if (q) this.router.navigate(['/search'], { queryParams: { q } });
  }

  /** The verified page of a Location, when the pages group found one. */
  pageOf(r: Results, locationId: number): BusinessPage | undefined {
    return r.pages.items.find(p => p.locationId === locationId);
  }

  private run(raw: string): void {
    const q = raw.trim();
    this.draft.set(q);
    this.results.set(null);
    this.tooShort.set(q.length > 0 && q.length < MIN_LENGTH);
    if (q.length < MIN_LENGTH) return;
    this.loading.set(true);
    const failed = <T>(): Group<T> => ({ items: [], total: 0, failed: true });
    forkJoin({
      locations: this.locationsApi.directory(q, PER_GROUP).pipe(
        map(list => ({ items: list, total: list.length, failed: false })), catchError(() => of(failed<ApiLocation>()))),
      pages: this.businessApi.list(q, 0, PER_GROUP).pipe(
        map(p => ({ items: p.items, total: p.totalElements, failed: false })), catchError(() => of(failed<BusinessPage>()))),
      posts: this.postsApi.search(q, 0, PER_GROUP).pipe(
        map(p => ({ items: p.items, total: p.totalElements, failed: false })), catchError(() => of(failed<ApiPost>()))),
    }).subscribe(results => {
      // Ignore an answer that arrives after the visitor searched something else.
      if ((this.q() ?? '').trim() !== q) return;
      this.results.set(results);
      this.loading.set(false);
    });
  }
}

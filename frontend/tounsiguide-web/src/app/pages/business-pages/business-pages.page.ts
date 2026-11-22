import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BUSINESS_PAGE_SIZE, BusinessApiService } from '../../core/business-api.service';
import { AuthService } from '../../core/auth.service';
import { BusinessPage, businessErrorMessage } from '../../core/business.model';
import { toUiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { StarsComponent } from '../../shared/star-rating.component';

/** S3-F02: public list of verified business pages, with a search on the name. */
@Component({
  selector: 'app-business-pages-page',
  standalone: true,
  imports: [FormsModule, RouterLink, StarsComponent],
  template: `
    <header class="page-head business-list-head">
      <div>
        <p class="eyebrow">Pages professionnelles</p>
        <h1>Pages officielles</h1>
        <p class="muted">Les établissements vérifiés par un administrateur, avec leurs informations et les avis de la communauté.</p>
      </div>
      <a class="btn btn-ghost" [routerLink]="auth.isLoggedIn() ? '/business/requests' : '/login'"
         [queryParams]="auth.isLoggedIn() ? null : { returnUrl: '/business/requests' }">Vous gérez un lieu ? Demander sa page</a>
    </header>

    <form class="location-search" role="search" (ngSubmit)="search()">
      <label class="field field-inline">
        <span class="sr-only">Chercher une page</span>
        <input name="q" type="search" [ngModel]="query()" (ngModelChange)="query.set($event)" placeholder="Nom de l'établissement" />
      </label>
      <button type="submit" class="btn btn-ghost">Chercher</button>
    </form>

    @if (loading()) {
      <div class="card skeleton" aria-busy="true"><div></div><div></div><div></div></div>
    } @else if (error()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ error() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load(0)">Réessayer</button>
      </div>
    } @else { @if (page(); as p) {
      <ul class="business-page-list">
        @for (b of p.items; track b.id) {
          <li class="card business-page-row">
            <a class="business-page-link" [routerLink]="['/pages', b.id]">
              <strong>{{ b.name }}</strong>
              <span class="status status-confirmed">Page vérifiée</span>
            </a>
            @if (b.ratingCount) {
              <div class="small page-rating-line"><app-stars [value]="b.ratingAverage" /> {{ (b.ratingAverage ?? 0).toFixed(1).replace('.', ',') }} · {{ b.ratingCount }} note{{ b.ratingCount > 1 ? 's' : '' }}</div>
            }
            <div class="muted small">
              @if (b.location; as l) { {{ l.name }}@if (l.city && l.city !== l.name) { · {{ l.city }} } }
              @else { Lieu n°{{ b.locationId }} }
            </div>
            @if (b.description) { <p class="small business-page-excerpt">{{ b.description }}</p> }
          </li>
        } @empty {
          <li class="card empty">
            @if (searched()) { Aucune page ne correspond à « {{ searched() }} ». }
            @else { Aucune page officielle pour le moment. }
          </li>
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
export class BusinessPagesPage implements OnInit {
  private readonly api = inject(BusinessApiService);
  readonly auth = inject(AuthService);

  readonly query = signal('');
  readonly searched = signal('');
  readonly page = signal<Page<BusinessPage> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  search(): void {
    this.searched.set(this.query().trim());
    this.load(0);
  }

  load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.searched(), pageIndex, BUSINESS_PAGE_SIZE).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(businessErrorMessage(toUiError(err)));
        this.loading.set(false);
      },
    });
  }
}

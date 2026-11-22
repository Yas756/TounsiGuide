import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { BusinessApiService } from '../core/business-api.service';
import { RatingSummary } from '../core/business.model';
import { toUiError } from '../core/api-error.model';

/** Read-only stars for an average (0 to 5, halves shown as a partly filled star). */
@Component({
  selector: 'app-stars',
  standalone: true,
  template: `
    <span class="stars" role="img" [attr.aria-label]="label()">
      @for (i of [1, 2, 3, 4, 5]; track i) {
        <span class="star" [style.--fill]="fill(i)" aria-hidden="true">★</span>
      }
    </span>
  `,
})
export class StarsComponent {
  readonly value = input<number | null | undefined>(null);
  label(): string {
    const v = this.value();
    return v == null ? 'Pas encore noté' : `${v.toString().replace('.', ',')} sur 5`;
  }
  fill(i: number): string {
    const v = this.value() ?? 0;
    return `${Math.round(Math.max(0, Math.min(1, v - (i - 1))) * 100)}%`;
  }
}

/**
 * Star rating of a business page (S3-US12): the average and the number of ratings for everyone; members rate
 * from 1 to 5, change or remove their rating. Page members cannot rate their own page.
 */
@Component({
  selector: 'app-star-rating',
  standalone: true,
  imports: [StarsComponent, RouterLink],
  template: `
    <section class="rating card" aria-labelledby="rating-title">
      <h2 id="rating-title">Note de la communauté</h2>
      @if (summary(); as s) {
        <div class="rating-head">
          <strong class="rating-average">{{ s.average === null ? '–' : format(s.average) }}</strong>
          <div>
            <app-stars [value]="s.average" />
            <div class="muted small">{{ s.count === 0 ? 'Pas encore de note' : s.count + ' note' + (s.count > 1 ? 's' : '') }}</div>
          </div>
        </div>
        @if (s.count > 0) {
          <ul class="rating-bars" aria-label="Répartition des notes">
            @for (n of [5, 4, 3, 2, 1]; track n) {
              <li><span>{{ n }} ★</span><span class="rating-bar"><span [style.width.%]="share(s, n)"></span></span><span class="muted">{{ s.distribution[n - 1] }}</span></li>
            }
          </ul>
        }
        @if (s.canRate) {
          <div class="rating-mine" role="group" aria-label="Votre note">
            <span class="small">{{ s.myStars ? 'Votre note :' : 'Notez cette page :' }}</span>
            @for (n of [1, 2, 3, 4, 5]; track n) {
              <button type="button" class="star-pick" [class.on]="n <= (hover() || s.myStars || 0)" [disabled]="busy()"
                      [attr.aria-label]="n + ' étoile' + (n > 1 ? 's' : '')" [attr.aria-pressed]="s.myStars === n"
                      (mouseenter)="hover.set(n)" (mouseleave)="hover.set(0)" (click)="rate(n)">★</button>
            }
            @if (s.myStars) { <button type="button" class="btn-text small" (click)="remove()" [disabled]="busy()">Retirer ma note</button> }
          </div>
        } @else if (!auth.isLoggedIn()) {
          <p class="small muted"><a class="link" routerLink="/login">Connectez-vous</a> pour noter cette page.</p>
        } @else {
          <p class="small muted">Les membres d’une page ne peuvent pas la noter.</p>
        }
        @if (error(); as e) { <div class="banner banner-error" role="alert">{{ e }}</div> }
      } @else if (error()) {
        <p class="small muted">{{ error() }}</p>
      } @else {
        <div class="skeleton" aria-busy="true"><div></div></div>
      }
    </section>
  `,
})
export class StarRatingComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(BusinessApiService);

  readonly pageId = input.required<number>();
  readonly summary = signal<RatingSummary | null>(null);
  readonly busy = signal(false);
  readonly hover = signal(0);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => this.load(this.pageId()), { allowSignalWrites: true });
  }

  format(v: number): string {
    return v.toFixed(1).replace('.', ',');
  }

  share(s: RatingSummary, stars: number): number {
    return s.count ? Math.round((s.distribution[stars - 1] / s.count) * 100) : 0;
  }

  rate(stars: number): void {
    this.call(this.api.rate(this.pageId(), stars));
  }

  remove(): void {
    this.call(this.api.removeRating(this.pageId()));
  }

  private load(pageId: number): void {
    this.summary.set(null);
    this.error.set(null);
    this.api.rating(pageId).subscribe({
      next: s => this.summary.set(s),
      error: () => this.error.set('La note n’est pas disponible pour le moment.'),
    });
  }

  private call(request: ReturnType<BusinessApiService['rate']>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: s => {
        this.summary.set(s);
        this.busy.set(false);
      },
      error: err => {
        this.error.set(toUiError(err).message);
        this.busy.set(false);
      },
    });
  }
}

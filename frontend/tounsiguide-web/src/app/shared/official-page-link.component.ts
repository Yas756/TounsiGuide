import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BusinessApiService } from '../core/business-api.service';
import { BusinessPage } from '../core/business.model';

/**
 * « Voir la page officielle » next to a confirmed Location, when that Location has a verified business page.
 * No page (404) or a failed call shows nothing: the link is a bonus, never an error.
 */
@Component({
  selector: 'app-official-page-link',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (page(); as p) {
      <a class="official-page-link" [routerLink]="['/pages', p.id]" [title]="'Page officielle de ' + p.name">
        <span aria-hidden="true">✓</span> Voir la page officielle
      </a>
    }
  `,
})
export class OfficialPageLinkComponent {
  private readonly api = inject(BusinessApiService);

  readonly locationId = input<number | null>(null);
  readonly page = signal<BusinessPage | null>(null);

  constructor() {
    effect(onCleanup => {
      const id = this.locationId();
      untracked(() => this.page.set(null));
      if (id === null) return;
      const sub = this.api.byLocation(id).subscribe({
        next: page => this.page.set(page),
        error: () => this.page.set(null),
      });
      onCleanup(() => sub.unsubscribe());
    }, { allowSignalWrites: true }); // a cached answer arrives synchronously
  }
}

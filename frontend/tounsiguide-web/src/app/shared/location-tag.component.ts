import { Component, input } from '@angular/core';
import { ApiLocation } from '../core/api-location.model';
import { ApiPlace, googleMapsUrl } from '../core/api-place.model';

@Component({
  selector: 'app-location-tag',
  standalone: true,
  template: `
    @if (place(); as p) {
      <a class="tag tag-link" [href]="mapsUrl(p)" target="_blank" rel="noopener" title="Ouvrir dans Google Maps">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6-5.3-6-11a6 6 0 0112 0c0 5.7-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/></svg>
        {{ p.name }}
      </a>
    } @else {
      @if (location(); as l) {
        <span class="tag" title="Lieu indiqué par l'auteur">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6-5.3-6-11a6 6 0 0112 0c0 5.7-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/></svg>
          {{ l.name }}@if (l.city) {<span class="muted">&nbsp;· {{ l.city }}</span>}
        </span>
      } @else if (locationId() !== null) {
        <span class="tag">Lieu n°{{ locationId() }}</span>
      }
    }
  `,
})
export class LocationTagComponent {
  readonly locationId = input<number | null>(null);
  readonly location = input<ApiLocation | undefined>(undefined);
  /** Place picked on the map; takes precedence over the legacy catalog location. */
  readonly place = input<ApiPlace | null>(null);

  protected readonly mapsUrl = googleMapsUrl;
}

import { Component, input } from '@angular/core';
import { ApiLocation, externalMapUrl } from '../core/api-location.model';

/** The place of a post, as a small tag. Opens the place in a map app when it has coordinates. */
@Component({
  selector: 'app-location-tag',
  standalone: true,
  template: `
    @if (location(); as l) {
      @if (mapUrl(l); as url) {
        <a class="tag tag-link" [href]="url" target="_blank" rel="noopener" title="Ouvrir dans une application de cartes">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6-5.3-6-11a6 6 0 0112 0c0 5.7-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/></svg>
          {{ l.name }}@if (l.city && l.city !== l.name) {<span class="muted">&nbsp;· {{ l.city }}</span>}
        </a>
      } @else {
        <span class="tag">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6-5.3-6-11a6 6 0 0112 0c0 5.7-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/></svg>
          {{ l.name }}@if (l.city && l.city !== l.name) {<span class="muted">&nbsp;· {{ l.city }}</span>}
        </span>
      }
    } @else if (locationId() !== null) {
      <span class="tag muted" title="Le service des lieux ne répond pas">Lieu n°{{ locationId() }}</span>
    }
  `,
})
export class LocationTagComponent {
  readonly locationId = input<number | null>(null);
  /** The Location loaded from the Location service; undefined while loading or if it failed. */
  readonly location = input<ApiLocation | undefined>(undefined);

  protected readonly mapUrl = externalMapUrl;
}

import { Component, DestroyRef, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import type { Map as MapLibreMap, Marker } from 'maplibre-gl';
import { ApiLocation } from '../core/api-location.model';
import { createMap, createPin } from './map';

/** Small read-only map under a post, centered on its Location. */
@Component({
  selector: 'app-location-map',
  standalone: true,
  template: `
    @if (!failed()) {
      <div #host class="location-map" role="img" [attr.aria-label]="'Carte : ' + location().name"></div>
    }
  `,
})
export class LocationMapComponent {
  readonly location = input.required<ApiLocation>();

  protected readonly failed = signal(false);
  private readonly host = viewChild<ElementRef<HTMLElement>>('host');
  private map?: MapLibreMap;
  private marker?: Marker;
  private starting = false;
  private destroyed = false;

  constructor() {
    effect(() => {
      const host = this.host();
      const location = this.location();
      untracked(() => void this.show(host?.nativeElement, location));
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.map?.remove();
    });
  }

  private async show(host: HTMLElement | undefined, location: ApiLocation): Promise<void> {
    if (!host || location.latitude === null || location.longitude === null) return;
    const point: [number, number] = [location.longitude, location.latitude];
    if (this.map) {
      this.marker?.setLngLat(point);
      this.map.jumpTo({ center: point });
      return;
    }
    if (this.starting) return;
    this.starting = true;
    try {
      const map = await createMap(host, { center: point, zoom: 15, interactive: false });
      if (this.destroyed) {
        map.remove();
        return;
      }
      this.map = map;
      this.marker = (await createPin(point)).addTo(map);
    } catch {
      this.failed.set(true);
    } finally {
      this.starting = false;
    }
  }
}

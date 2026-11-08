import { Component, DestroyRef, effect, ElementRef, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import type { Map as MapLibreMap, Marker } from 'maplibre-gl';
import { ApiLocation, categoryLabel, PlaceSearchResponse, PlaceSuggestion, placeDetails } from '../core/api-location.model';
import { LocationApiService } from '../core/location-api.service';
import { toUiError, UiError } from '../core/api-error.model';
import { createMap, createPin, distanceMeters } from './map';

/** A map click this close to a named place picks that place (its name, position and OpenStreetMap id). */
const SNAP_METERS = 60;
const SEARCH_DELAY_MS = 300;
const MIN_QUERY_LENGTH = 2;

/**
 * Lets the author attach a place to a post: search by name (TounsiGuide directory first, then
 * OpenStreetMap), or click on the map and confirm. The chosen place always becomes a Location of the
 * Location & Matching Service, and the post only keeps its id.
 */
@Component({
  selector: 'app-place-picker',
  standalone: true,
  templateUrl: './place-picker.component.html',
})
export class PlacePickerComponent {
  private readonly locationApi = inject(LocationApiService);

  readonly value = input<ApiLocation | null>(null);
  readonly valueChange = output<ApiLocation | null>();

  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly results = signal<PlaceSearchResponse | null>(null);
  protected readonly searching = signal(false);
  protected readonly searchFailed = signal(false);
  /** A place clicked on the map, waiting for the author's confirmation. */
  protected readonly pending = signal<PlaceSuggestion | null>(null);
  protected readonly naming = signal(false);
  protected readonly resolving = signal(false);
  protected readonly resolveError = signal<UiError | null>(null);
  protected readonly mapFailed = signal(false);

  protected readonly details = placeDetails;
  protected readonly categoryLabel = categoryLabel;

  private readonly mapHost = viewChild<ElementRef<HTMLElement>>('mapHost');
  private map?: MapLibreMap;
  private marker?: Marker;
  private mapStarting = false;
  private destroyed = false;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private searchSub?: Subscription;
  private reverseSub?: Subscription;

  constructor() {
    // Build the map the first time the panel is shown.
    effect(() => {
      const host = this.mapHost();
      if (this.open() && host && !this.map && !this.mapStarting) {
        untracked(() => void this.initMap(host.nativeElement));
      }
    });
    // Keep the pin on the chosen place (also when the form is reset).
    effect(() => {
      const place = this.value();
      untracked(() => void this.showChosen(place));
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      clearTimeout(this.searchTimer);
      this.searchSub?.unsubscribe();
      this.reverseSub?.unsubscribe();
      this.map?.remove();
    });
  }

  protected toggle(): void {
    this.open.update(open => !open);
  }

  protected clear(): void {
    this.pending.set(null);
    this.valueChange.emit(null);
  }

  protected onQuery(text: string): void {
    this.query.set(text);
    clearTimeout(this.searchTimer);
    this.searchSub?.unsubscribe();
    this.searchFailed.set(false);
    if (text.trim().length < MIN_QUERY_LENGTH) {
      this.results.set(null);
      this.searching.set(false);
      return;
    }
    this.searching.set(true);
    // Wait for a pause in typing: fewer requests, and the results do not flicker.
    this.searchTimer = setTimeout(() => this.search(text.trim()), SEARCH_DELAY_MS);
  }

  protected chooseKnown(location: ApiLocation): void {
    this.closeSearch();
    this.pending.set(null);
    this.valueChange.emit(location);
  }

  /** A place from OpenStreetMap or a map click: stored (or found) in the Location service first. */
  protected chooseSuggestion(place: PlaceSuggestion): void {
    this.resolving.set(true);
    this.resolveError.set(null);
    this.locationApi.resolve(place).subscribe({
      next: location => {
        this.resolving.set(false);
        this.closeSearch();
        this.pending.set(null);
        this.valueChange.emit(location);
      },
      error: err => {
        this.resolving.set(false);
        this.resolveError.set(toUiError(err));
      },
    });
  }

  protected cancelPending(): void {
    this.pending.set(null);
    this.reverseSub?.unsubscribe();
    void this.showChosen(this.value());
  }

  private search(text: string): void {
    const center = this.map?.getCenter();
    const near = center ? { latitude: center.lat, longitude: center.lng } : undefined;
    this.searchSub = this.locationApi.search(text, near).subscribe({
      next: response => {
        this.results.set(response);
        this.searching.set(false);
      },
      error: () => {
        this.results.set(null);
        this.searchFailed.set(true);
        this.searching.set(false);
      },
    });
  }

  private closeSearch(): void {
    clearTimeout(this.searchTimer);
    this.searchSub?.unsubscribe();
    this.query.set('');
    this.results.set(null);
    this.searching.set(false);
  }

  private async initMap(host: HTMLElement): Promise<void> {
    this.mapStarting = true;
    const current = this.value();
    const hasPoint = current?.latitude != null && current?.longitude != null;
    try {
      const map = await createMap(host, {
        center: hasPoint ? [current!.longitude!, current!.latitude!] : undefined,
        zoom: hasPoint ? 15 : 11,
      });
      if (this.destroyed) {
        map.remove();
        return;
      }
      this.map = map;
      map.on('click', event => this.pickFromPoint(event.lngLat.lat, event.lngLat.lng));
      map.on('error', () => this.mapFailed.set(!map.isStyleLoaded()));
      await this.showChosen(current);
    } catch {
      this.mapFailed.set(true); // WebGL unavailable or the library could not load: search still works
    } finally {
      this.mapStarting = false;
    }
  }

  /** A click on the map: pin at once, then ask the Location service what is there. */
  private pickFromPoint(latitude: number, longitude: number): void {
    const clicked: [number, number] = [longitude, latitude];
    this.resolveError.set(null);
    this.pending.set({
      name: 'Lieu choisi sur la carte',
      address: null,
      city: null,
      latitude,
      longitude,
      category: null,
      externalId: null,
    });
    void this.movePin(clicked, false);
    this.naming.set(true);
    this.reverseSub?.unsubscribe();
    this.reverseSub = this.locationApi.reverse(latitude, longitude).subscribe({
      next: nearest => {
        this.naming.set(false);
        if (!nearest) return;
        const near = distanceMeters(clicked, [nearest.longitude, nearest.latitude]) <= SNAP_METERS;
        // Close to a named place: take it. Otherwise keep the clicked point and only borrow the address.
        const place: PlaceSuggestion = near
          ? nearest
          : { ...this.pending()!, address: [nearest.name, nearest.address].filter(Boolean).join(', '), city: nearest.city };
        this.pending.set(place);
        void this.movePin([place.longitude, place.latitude], false);
      },
      error: () => this.naming.set(false), // the coordinates are enough to confirm
    });
  }

  private async showChosen(place: ApiLocation | null): Promise<void> {
    if (!this.map) return;
    if (!place || place.latitude === null || place.longitude === null) {
      this.marker?.remove();
      this.marker = undefined;
      return;
    }
    await this.movePin([place.longitude, place.latitude], true);
  }

  private async movePin(lngLat: [number, number], fly: boolean): Promise<void> {
    if (!this.map) return;
    if (!this.marker) {
      this.marker = await createPin(lngLat);
      this.marker.addTo(this.map);
    } else {
      this.marker.setLngLat(lngLat);
    }
    if (fly) {
      this.map.flyTo({ center: lngLat, zoom: Math.max(this.map.getZoom(), 15), essential: true });
    }
  }
}

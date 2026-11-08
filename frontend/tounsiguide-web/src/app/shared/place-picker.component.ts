import { Component, DestroyRef, effect, ElementRef, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { ApiPlace } from '../core/api-place.model';

/** Center of the map until a place is chosen: Tunis. */
const TUNIS: L.LatLngTuple = [36.8065, 10.1815];
/** Free OpenStreetMap-based geocoder with search-as-you-type (fair use, no key). */
const PHOTON_URL = 'https://photon.komoot.io';
/** Search is limited to Tunisia: minLon,minLat,maxLon,maxLat. */
const TUNISIA_BBOX = '7.5,30.2,11.6,37.6';

const PIN = L.divIcon({
  className: 'place-pin',
  html: '<svg viewBox="0 0 28 36" aria-hidden="true"><path fill="#c8102e" d="M14 0C6.3 0 0 6.2 0 13.9 0 24.4 14 36 14 36s14-11.6 14-22.1C28 6.2 21.7 0 14 0z"/><circle cx="14" cy="14" r="5" fill="#fff"/></svg>',
  iconSize: [28, 36],
  iconAnchor: [14, 36],
});

interface PhotonFeature {
  geometry: { coordinates: [number, number] };
  properties: {
    name?: string;
    housenumber?: string;
    street?: string;
    district?: string;
    locality?: string;
    city?: string;
    county?: string;
    state?: string;
    country?: string;
  };
}

function toPlace(feature: PhotonFeature): ApiPlace {
  const p = feature.properties;
  const [longitude, latitude] = feature.geometry.coordinates;
  const street = [p.housenumber, p.street].filter(Boolean).join(' ');
  const city = p.city ?? p.locality ?? p.district ?? p.county ?? p.state;
  const name = p.name ?? (street || city) ?? `Lieu choisi (${latitude.toFixed(4)}, ${longitude.toFixed(4)})`;
  const address = [street, city, p.country].filter(part => part && part !== name).join(', ') || null;
  return { name, address, latitude, longitude, googlePlaceId: null };
}

/**
 * Lets the author attach a place to a post: search by name (suggestions appear while typing),
 * or click on the map. Map: OpenStreetMap through Leaflet. Search: Photon. No API key needed.
 */
@Component({
  selector: 'app-place-picker',
  standalone: true,
  templateUrl: './place-picker.component.html',
})
export class PlacePickerComponent {
  readonly value = input<ApiPlace | null>(null);
  readonly valueChange = output<ApiPlace | null>();

  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly results = signal<ApiPlace[]>([]);
  protected readonly searching = signal(false);
  protected readonly searchFailed = signal(false);

  private readonly mapHost = viewChild<ElementRef<HTMLElement>>('mapHost');
  private map?: L.Map;
  private marker?: L.Marker;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private searchAbort?: AbortController;

  constructor() {
    // Build the map the first time the panel is shown.
    effect(() => {
      const host = this.mapHost();
      if (this.open() && host && !this.map) {
        untracked(() => this.initMap(host.nativeElement));
      }
    });
    // Keep the marker in sync with the chosen place (also when the form is reset).
    effect(() => {
      const place = this.value();
      untracked(() => this.showOnMap(place));
    });
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.searchAbort?.abort();
      this.map?.remove();
    });
  }

  protected clear(): void {
    this.valueChange.emit(null);
  }

  protected onQuery(text: string): void {
    this.query.set(text);
    clearTimeout(this.searchTimer);
    this.searchAbort?.abort();
    this.searchFailed.set(false);
    if (text.trim().length < 3) {
      this.results.set([]);
      this.searching.set(false);
      return;
    }
    // Wait for a pause in typing: the free public server asks for fair use.
    this.searchTimer = setTimeout(() => void this.search(text.trim()), 350);
  }

  protected choose(place: ApiPlace): void {
    this.results.set([]);
    this.query.set('');
    this.valueChange.emit(place);
  }

  private async search(text: string): Promise<void> {
    this.searchAbort = new AbortController();
    this.searching.set(true);
    const params = new URLSearchParams({
      q: text,
      limit: '6',
      lang: 'fr',
      bbox: TUNISIA_BBOX,
      lat: String(TUNIS[0]),
      lon: String(TUNIS[1]),
    });
    try {
      const response = await fetch(`${PHOTON_URL}/api/?${params.toString()}`, { signal: this.searchAbort.signal });
      if (!response.ok) throw new Error(`Photon answered ${response.status}`);
      const data = (await response.json()) as { features: PhotonFeature[] };
      this.results.set(data.features.map(toPlace));
      this.searching.set(false);
    } catch (error) {
      if ((error as Error).name === 'AbortError') return; // replaced by a newer search
      this.results.set([]);
      this.searchFailed.set(true);
      this.searching.set(false);
    }
  }

  private initMap(host: HTMLElement): void {
    const current = this.value();
    this.map = L.map(host, {
      center: current ? [current.latitude, current.longitude] : TUNIS,
      zoom: current ? 15 : 11,
    });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(this.map);
    this.map.on('click', (event: L.LeafletMouseEvent) =>
      void this.pickFromPoint(event.latlng.lat, event.latlng.lng),
    );
    this.showOnMap(current);
  }

  /** A click on the map: the name and address come from reverse geocoding. */
  private async pickFromPoint(latitude: number, longitude: number): Promise<void> {
    // Show the pin at once; the name is filled in as soon as the server answers.
    const fallback = `Lieu choisi (${latitude.toFixed(4)}, ${longitude.toFixed(4)})`;
    let place: ApiPlace = { name: fallback, address: null, latitude, longitude, googlePlaceId: null };
    this.valueChange.emit(place);
    try {
      const params = new URLSearchParams({ lat: String(latitude), lon: String(longitude), lang: 'fr' });
      const response = await fetch(`${PHOTON_URL}/reverse?${params.toString()}`);
      if (!response.ok) return;
      const data = (await response.json()) as { features: PhotonFeature[] };
      if (!data.features.length) return;
      // Keep the clicked position, take only the name and the address of the nearest feature.
      place = { ...toPlace(data.features[0]), latitude, longitude };
      // Ignore the answer if the user has already picked something else meanwhile.
      const now = this.value();
      if (now && now.latitude === latitude && now.longitude === longitude) {
        this.valueChange.emit(place);
      }
    } catch {
      // Offline or server busy: the coordinates stay as the label.
    }
  }

  private showOnMap(place: ApiPlace | null): void {
    if (!this.map) return;
    if (!place) {
      this.marker?.remove();
      this.marker = undefined;
      return;
    }
    const position: L.LatLngTuple = [place.latitude, place.longitude];
    if (this.marker) {
      this.marker.setLatLng(position);
    } else {
      this.marker = L.marker(position, { icon: PIN, title: 'Lieu choisi' }).addTo(this.map);
    }
    this.map.panTo(position);
    if (this.map.getZoom() < 14) {
      this.map.setZoom(15);
    }
  }
}

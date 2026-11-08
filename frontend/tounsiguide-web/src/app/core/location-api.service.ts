import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiLocation, PlaceSearchResponse, PlaceSuggestion } from './api-location.model';

/**
 * Calls to the Location & Matching Service through the Gateway (/api/v1/locations).
 * The browser never calls a map search provider directly: the service does it.
 */
@Injectable({ providedIn: 'root' })
export class LocationApiService {
  private readonly http = inject(HttpClient);
  private readonly locationsUrl = `${environment.apiBaseUrl}/api/v1/locations`;
  /** Locations do not change often: each one is loaded once per visit. */
  private readonly cache = new Map<number, ApiLocation>();

  /** Place picker search; `near` ranks places close to the map center first. */
  search(query: string, near?: { latitude: number; longitude: number }): Observable<PlaceSearchResponse> {
    let params = new HttpParams().set('query', query).set('limit', 6);
    if (near) {
      params = params.set('lat', near.latitude.toFixed(5)).set('lng', near.longitude.toFixed(5));
    }
    return this.http
      .get<PlaceSearchResponse>(`${this.locationsUrl}/search`, { params })
      .pipe(tap(response => response.directory.forEach(l => this.remember(l))));
  }

  /** The named place under a map click, or null when there is nothing named nearby (204). */
  reverse(latitude: number, longitude: number): Observable<PlaceSuggestion | null> {
    const params = new HttpParams().set('lat', latitude.toFixed(6)).set('lng', longitude.toFixed(6));
    return this.http
      .get<PlaceSuggestion>(`${this.locationsUrl}/reverse`, { params, observe: 'response' })
      .pipe(map(response => (response.status === 204 ? null : response.body)));
  }

  /** Turns a place picked on the map into a Location (existing one reused, otherwise created). Needs a login. */
  resolve(place: PlaceSuggestion): Observable<ApiLocation> {
    return this.http.post<ApiLocation>(`${this.locationsUrl}/resolve`, place).pipe(tap(l => this.remember(l)));
  }

  /** Locations by id, from the cache when possible. Unknown ids are simply missing from the map. */
  byIds(ids: Iterable<number | null | undefined>): Observable<Map<number, ApiLocation>> {
    const wanted = [...new Set([...ids].filter((id): id is number => typeof id === 'number'))];
    const missing = wanted.filter(id => !this.cache.has(id));
    const fromCache = () => new Map(wanted.filter(id => this.cache.has(id)).map(id => [id, this.cache.get(id)!]));
    if (!missing.length) return of(fromCache());
    const params = new HttpParams().set('ids', missing.join(','));
    return this.http.get<ApiLocation[]>(this.locationsUrl, { params }).pipe(
      tap(locations => locations.forEach(l => this.remember(l))),
      map(fromCache),
    );
  }

  remember(location: ApiLocation): void {
    this.cache.set(location.id, location);
  }
}

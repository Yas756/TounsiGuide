import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiLocation } from './api-location.model';

@Injectable({ providedIn: 'root' })
export class LocationApiService {
  private readonly http = inject(HttpClient);
  private readonly locationsUrl = `${environment.apiBaseUrl}/api/v1/dummy-locations`;
  private cache$?: Observable<ApiLocation[]>;

  /** The catalog is static in Sprint 1, so it is loaded once and shared. */
  list(): Observable<ApiLocation[]> {
    if (!this.cache$) {
      this.cache$ = this.http.get<ApiLocation[]>(this.locationsUrl).pipe(
        catchError(err => {
          this.cache$ = undefined; // allow a retry after a failure
          return throwError(() => err);
        }),
        shareReplay(1),
      );
    }
    return this.cache$;
  }
}

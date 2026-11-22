import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  BusinessAuditEntry,
  BusinessPage,
  CreatePageRequest,
  PageRequest,
  RatingSummary,
  PageRequestStatus,
  UpdatePageRequest,
  WallPost,
  WallVisibility,
} from './business.model';
import { Page, toPage } from './page.model';

/** Items per page for lists and walls (the backend accepts 1 to 50). */
export const BUSINESS_PAGE_SIZE = 20;

/**
 * Calls to the Business Service through the Gateway (/api/v1/business-pages), Sprint 3.
 * Reading pages and walls is public; requests, decisions, edits and hiding need a login (the interceptor adds it).
 */
@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/business-pages`;
  /** Page of a Location (null = none), so the feed asks once per Location per visit. */
  private readonly byLocationCache = new Map<number, Observable<BusinessPage | null>>();

  // ----- page requests (S3-F01)

  submitRequest(body: CreatePageRequest): Observable<PageRequest> {
    return this.http.post<PageRequest>(`${this.url}/requests`, body);
  }

  myRequests(page = 0, size = BUSINESS_PAGE_SIZE): Observable<Page<PageRequest>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<unknown>(`${this.url}/requests/mine`, { params }).pipe(map(raw => toPage<PageRequest>(raw as never, size)));
  }

  /** Admin queue; no status = every request. */
  requests(status: PageRequestStatus | null, page = 0, size = BUSINESS_PAGE_SIZE): Observable<Page<PageRequest>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<unknown>(`${this.url}/requests`, { params }).pipe(map(raw => toPage<PageRequest>(raw as never, size)));
  }

  approve(requestId: number): Observable<PageRequest> {
    return this.http.post<PageRequest>(`${this.url}/requests/${requestId}/approve`, {}).pipe(
      tap(() => this.byLocationCache.clear()),
    );
  }

  reject(requestId: number, reason: string): Observable<PageRequest> {
    return this.http.post<PageRequest>(`${this.url}/requests/${requestId}/reject`, { reason });
  }

  // ----- pages (S3-F02)

  list(query = '', page = 0, size = BUSINESS_PAGE_SIZE): Observable<Page<BusinessPage>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<unknown>(this.url, { params }).pipe(map(raw => toPage<BusinessPage>(raw as never, size)));
  }

  get(pageId: number): Observable<BusinessPage> {
    return this.http.get<BusinessPage>(`${this.url}/${pageId}`);
  }

  /**
   * The official page of a Location, or null when it has none (404). Other errors are passed on.
   * The answer is kept for the visit (one call per Location).
   */
  byLocation(locationId: number): Observable<BusinessPage | null> {
    let cached = this.byLocationCache.get(locationId);
    if (!cached) {
      cached = this.http.get<BusinessPage>(`${this.url}/by-location/${locationId}`).pipe(
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 404) return of(null);
          this.byLocationCache.delete(locationId);
          return throwError(() => err);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.byLocationCache.set(locationId, cached);
    }
    return cached;
  }

  update(pageId: number, body: UpdatePageRequest): Observable<BusinessPage> {
    return this.http.put<BusinessPage>(`${this.url}/${pageId}`, body);
  }

  // ----- star ratings (S3-US12)

  rating(pageId: number): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(`${this.url}/${pageId}/rating`);
  }

  rate(pageId: number, stars: number): Observable<RatingSummary> {
    return this.http.put<RatingSummary>(`${this.url}/${pageId}/rating`, { stars });
  }

  removeRating(pageId: number): Observable<RatingSummary> {
    return this.http.delete<RatingSummary>(`${this.url}/${pageId}/rating`);
  }

  // ----- wall (S3-F03)

  /** `includeHidden` is only honoured by the server for members of the page. */
  wall(pageId: number, page = 0, size = BUSINESS_PAGE_SIZE, includeHidden = false): Observable<Page<WallPost>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (includeHidden) params = params.set('includeHidden', true);
    return this.http.get<unknown>(`${this.url}/${pageId}/wall`, { params }).pipe(map(raw => toPage<WallPost>(raw as never, size)));
  }

  hide(pageId: number, postId: number, reason: string | null): Observable<WallVisibility> {
    return this.http.post<WallVisibility>(`${this.url}/${pageId}/wall/${postId}/hide`, { reason: reason?.trim() || null });
  }

  unhide(pageId: number, postId: number): Observable<WallVisibility> {
    return this.http.post<WallVisibility>(`${this.url}/${pageId}/wall/${postId}/unhide`, {});
  }

  history(pageId: number, page = 0, size = BUSINESS_PAGE_SIZE): Observable<Page<BusinessAuditEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<unknown>(`${this.url}/${pageId}/wall/history`, { params })
      .pipe(map(raw => toPage<BusinessAuditEntry>(raw as never, size)));
  }
}

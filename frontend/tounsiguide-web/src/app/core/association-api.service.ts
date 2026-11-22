import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Association,
  AssociationHistoryEntry,
  AssociationStatus,
  PostAssociationSummary,
  ReviewRequest,
  SuggestRequest,
} from './association.model';
import { Page, toPage } from './page.model';

/** The summary endpoint accepts at most this many post ids per call. */
export const SUMMARY_BATCH = 100;

/** Association API (Location & Matching Service, through the Gateway): member and admin sides. */
@Injectable({ providedIn: 'root' })
export class AssociationApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/associations`;
  private readonly postsUrl = `${environment.apiBaseUrl}/api/v1/posts`;

  // ----- Public and member -----

  /** Every association of a post, best score first, with evidence. */
  forPost(postId: number): Observable<Association[]> {
    return this.http.get<Association[]>(`${this.postsUrl}/${postId}/associations`);
  }

  /**
   * For the feed: confirmed place and open proposals per post. One call per 100 posts.
   * Posts with no association are present with `confirmed: null`.
   */
  summary(postIds: Iterable<number>): Observable<Map<number, PostAssociationSummary>> {
    const ids = [...new Set(postIds)];
    if (!ids.length) return of(new Map());
    const batches: number[][] = [];
    for (let i = 0; i < ids.length; i += SUMMARY_BATCH) batches.push(ids.slice(i, i + SUMMARY_BATCH));
    return forkJoin(
      batches.map(batch =>
        this.http.get<Record<string, PostAssociationSummary>>(`${this.url}/summary`, {
          params: new HttpParams().set('postIds', batch.join(',')),
        }),
      ),
    ).pipe(
      map(responses => {
        const result = new Map<number, PostAssociationSummary>();
        for (const response of responses) {
          for (const [id, summary] of Object.entries(response ?? {})) result.set(Number(id), summary);
        }
        return result;
      }),
    );
  }

  /** A member suggests a place (S2-US04): 201 for a new association, 200 when evidence was added. */
  suggest(postId: number, body: SuggestRequest): Observable<Association> {
    return this.http.post<Association>(`${this.postsUrl}/${postId}/associations`, body);
  }

  /** Post author confirms a proposal (S2-US05). */
  confirm(id: number): Observable<Association> {
    return this.http.post<Association>(`${this.url}/${id}/confirm`, {});
  }

  /** Post author rejects a proposal. */
  reject(id: number): Observable<Association> {
    return this.http.post<Association>(`${this.url}/${id}/reject`, {});
  }

  /** Post author asks the members a question about a proposal; the status does not change. */
  clarify(id: number, note: string): Observable<Association> {
    return this.http.post<Association>(`${this.url}/${id}/clarify`, { note });
  }

  /** A member (not the author) disputes the confirmed place (S2-US07). The note is required. */
  dispute(id: number, note: string): Observable<Association> {
    return this.http.post<Association>(`${this.url}/${id}/dispute`, { note });
  }

  // ----- Admin -----

  /** Review queue: disputed first, then lowest score. */
  queue(statuses: AssociationStatus[] = ['DISPUTED', 'PROPOSED'], page = 0, size = 20): Observable<Page<Association>> {
    return this.http
      .get<unknown>(this.url, { params: { status: statuses.join(','), page, size } })
      .pipe(map(raw => toPage<Association>(raw as never, size)));
  }

  get(id: number): Observable<Association> {
    return this.http.get<Association>(`${this.url}/${id}`);
  }

  history(id: number): Observable<AssociationHistoryEntry[]> {
    return this.http.get<AssociationHistoryEntry[]>(`${this.url}/${id}/history`);
  }

  review(id: number, body: ReviewRequest): Observable<Association> {
    return this.http.post<Association>(`${this.url}/${id}/review`, body);
  }

  rescan(postId: number): Observable<void> {
    return this.http.post<void>(`${this.postsUrl}/${postId}/associations/rescan`, {});
  }
}

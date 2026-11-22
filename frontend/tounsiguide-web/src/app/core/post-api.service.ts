import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiPost, PostRequest } from './api-post.model';
import { Page, toPage } from './page.model';

/** Posts per page of the feed (the backend accepts 1 to 50). */
export const FEED_PAGE_SIZE = 20;

@Injectable({ providedIn: 'root' })
export class PostApiService {
  private readonly http = inject(HttpClient);
  private readonly postsUrl = `${environment.apiBaseUrl}/api/v1/posts`;

  /** One page of published posts, newest first (the server orders them); `module` = slug of a topic module. */
  list(page = 0, size = FEED_PAGE_SIZE, module?: string | null): Observable<Page<ApiPost>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (module) params = params.set('module', module);
    return this.http.get<unknown>(this.postsUrl, { params }).pipe(map(raw => toPage<ApiPost>(raw as never, size)));
  }

  /** Unified search (S3-US10): published posts whose text or author name contains `query` (2 to 100 characters). */
  search(query: string, page = 0, size = FEED_PAGE_SIZE): Observable<Page<ApiPost>> {
    const params = new HttpParams().set('q', query.trim()).set('page', page).set('size', size);
    return this.http.get<unknown>(this.postsUrl, { params }).pipe(map(raw => toPage<ApiPost>(raw as never, size)));
  }

  get(postId: number): Observable<ApiPost> {
    return this.http.get<ApiPost>(`${this.postsUrl}/${postId}`);
  }

  create(body: PostRequest): Observable<ApiPost> {
    return this.http.post<ApiPost>(this.postsUrl, body);
  }

  update(postId: number, body: PostRequest): Observable<ApiPost> {
    return this.http.put<ApiPost>(`${this.postsUrl}/${postId}`, body);
  }

  /** Completes after 204 No Content. Soft delete: the post disappears for everyone but administrators. */
  delete(postId: number): Observable<void> {
    return this.http.delete<void>(`${this.postsUrl}/${postId}`);
  }
}

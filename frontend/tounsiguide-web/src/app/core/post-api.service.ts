import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiPost, PostRequest } from './api-post.model';

@Injectable({ providedIn: 'root' })
export class PostApiService {
  private readonly http = inject(HttpClient);
  private readonly postsUrl = `${environment.apiBaseUrl}/api/v1/posts`;

  list(): Observable<ApiPost[]> {
    return this.http.get<ApiPost[]>(this.postsUrl);
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

  /** Completes after 204 No Content. The backend also deletes the post's comments. */
  delete(postId: number): Observable<void> {
    return this.http.delete<void>(`${this.postsUrl}/${postId}`);
  }
}

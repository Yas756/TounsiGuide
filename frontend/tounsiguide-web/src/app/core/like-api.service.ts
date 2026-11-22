import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';

/** Likes of one post or comment (S3-US11). `likedByMe` is false for a visitor. */
export interface LikeSummary {
  targetId: number;
  count: number;
  likedByMe: boolean;
}

/** Likes on posts and comments, served by the Community Service through the Gateway. */
@Injectable({ providedIn: 'root' })
export class LikeApiService {
  private readonly http = inject(HttpClient);
  private readonly postsUrl = `${environment.apiBaseUrl}/api/v1/posts`;

  /** Likes of several posts in one call (the server accepts up to 100 ids). */
  forPosts(ids: number[]): Observable<Map<number, LikeSummary>> {
    const unique = [...new Set(ids)].slice(0, 100);
    if (!unique.length) return of(new Map());
    const params = new HttpParams().set('ids', unique.join(','));
    return this.http.get<LikeSummary[]>(`${this.postsUrl}/likes`, { params }).pipe(map(toMap));
  }

  /** Likes of every visible comment of a post. */
  forComments(postId: number): Observable<Map<number, LikeSummary>> {
    return this.http.get<LikeSummary[]>(`${this.postsUrl}/${postId}/comments/likes`).pipe(map(toMap));
  }

  /** Likes (`liked` true) or removes the like; answers the new summary. */
  setPostLike(postId: number, liked: boolean): Observable<LikeSummary> {
    const url = `${this.postsUrl}/${postId}/like`;
    return liked ? this.http.put<LikeSummary>(url, null) : this.http.delete<LikeSummary>(url);
  }

  setCommentLike(postId: number, commentId: number, liked: boolean): Observable<LikeSummary> {
    const url = `${this.postsUrl}/${postId}/comments/${commentId}/like`;
    return liked ? this.http.put<LikeSummary>(url, null) : this.http.delete<LikeSummary>(url);
  }
}

function toMap(list: LikeSummary[]): Map<number, LikeSummary> {
  return new Map(list.map(s => [s.targetId, s]));
}

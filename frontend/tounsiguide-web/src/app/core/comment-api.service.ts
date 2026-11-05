import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiComment, CommentRequest } from './api-comment.model';

@Injectable({ providedIn: 'root' })
export class CommentApiService {
  private readonly http = inject(HttpClient);

  private commentsUrl(postId: number): string {
    return `${environment.apiBaseUrl}/api/v1/posts/${postId}/comments`;
  }

  list(postId: number): Observable<ApiComment[]> {
    return this.http.get<ApiComment[]>(this.commentsUrl(postId));
  }

  create(postId: number, body: CommentRequest): Observable<ApiComment> {
    return this.http.post<ApiComment>(this.commentsUrl(postId), body);
  }

  update(postId: number, commentId: number, body: CommentRequest): Observable<ApiComment> {
    return this.http.put<ApiComment>(`${this.commentsUrl(postId)}/${commentId}`, body);
  }

  delete(postId: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`${this.commentsUrl(postId)}/${commentId}`);
  }
}

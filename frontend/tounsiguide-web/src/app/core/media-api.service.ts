import { HttpClient, HttpEvent } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiMedia } from './api-media.model';

/**
 * Step 1 of attaching a photo or video: upload the file, get an id back.
 * Step 2 is sending that id in `mediaIds` when the post or comment is created or edited.
 */
@Injectable({ providedIn: 'root' })
export class MediaApiService {
  private readonly http = inject(HttpClient);
  private readonly mediaUrl = `${environment.apiBaseUrl}/api/v1/media`;

  /** Emits progress events, then the HttpResponse whose body is the stored media. */
  upload(file: File): Observable<HttpEvent<ApiMedia>> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ApiMedia>(this.mediaUrl, body, { reportProgress: true, observe: 'events' });
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ModuleStatus, ProposeModuleRequest, TopicModule } from './module.model';
import { Page, toPage } from './page.model';

/**
 * Topic modules through the Gateway (/api/v1/modules, Community Service). The list and module pages are public;
 * proposing needs a login; the queue and the decisions are for administrators. Posts of a module:
 * PostApiService.list(page, size, slug).
 */
@Injectable({ providedIn: 'root' })
export class ModuleApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/modules`;

  /** Approved modules by name, with their number of posts. */
  approved(): Observable<TopicModule[]> {
    return this.http.get<TopicModule[]>(this.url);
  }

  bySlug(slug: string): Observable<TopicModule> {
    return this.http.get<TopicModule>(`${this.url}/${encodeURIComponent(slug)}`);
  }

  mine(): Observable<TopicModule[]> {
    return this.http.get<TopicModule[]>(`${this.url}/mine`);
  }

  propose(body: ProposeModuleRequest): Observable<TopicModule> {
    return this.http.post<TopicModule>(this.url, body);
  }

  queue(status: ModuleStatus, page = 0, size = 20): Observable<Page<TopicModule>> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<unknown>(`${this.url}/queue`, { params }).pipe(map(raw => toPage<TopicModule>(raw as never, size)));
  }

  approve(id: number): Observable<TopicModule> {
    return this.http.post<TopicModule>(`${this.url}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<TopicModule> {
    return this.http.post<TopicModule>(`${this.url}/${id}/reject`, { reason: reason.trim() });
  }

  archive(id: number): Observable<TopicModule> {
    return this.http.post<TopicModule>(`${this.url}/${id}/archive`, {});
  }
}

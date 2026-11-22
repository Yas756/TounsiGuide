import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CreateReportRequest,
  ModerationDecision,
  ReportedContent,
  ReportItem,
  ReportTargetType,
  RestrictedContent,
} from './report.model';
import { Page, toPage } from './page.model';

export const REPORT_PAGE_SIZE = 20;

/**
 * Reports and moderation through the Gateway (/api/v1/reports, Community Service). Members report; the queue,
 * the decisions and the restricted list are for administrators (403 otherwise).
 */
@Injectable({ providedIn: 'root' })
export class ReportApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/reports`;

  report(body: CreateReportRequest): Observable<ReportItem> {
    return this.http.post<ReportItem>(this.url, body);
  }

  queue(page = 0, size = REPORT_PAGE_SIZE): Observable<Page<ReportedContent>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<unknown>(`${this.url}/queue`, { params }).pipe(map(raw => toPage<ReportedContent>(raw as never, size)));
  }

  restricted(page = 0, size = REPORT_PAGE_SIZE): Observable<Page<RestrictedContent>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<unknown>(`${this.url}/restricted`, { params }).pipe(map(raw => toPage<RestrictedContent>(raw as never, size)));
  }

  dismiss(type: ReportTargetType, id: number, reason: string | null): Observable<ModerationDecision> {
    return this.http.post<ModerationDecision>(`${this.url}/targets/${type}/${id}/dismiss`, { reason: reason?.trim() || null });
  }

  restrict(type: ReportTargetType, id: number, reason: string): Observable<ModerationDecision> {
    return this.http.post<ModerationDecision>(`${this.url}/targets/${type}/${id}/restrict`, { reason: reason.trim() });
  }

  restore(type: ReportTargetType, id: number, reason: string | null): Observable<ModerationDecision> {
    return this.http.post<ModerationDecision>(`${this.url}/targets/${type}/${id}/restore`, { reason: reason?.trim() || null });
  }

  history(type: ReportTargetType, id: number): Observable<ModerationDecision[]> {
    return this.http.get<ModerationDecision[]>(`${this.url}/targets/${type}/${id}/history`);
  }
}

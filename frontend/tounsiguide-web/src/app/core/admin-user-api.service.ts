import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Role } from './auth.model';
import { Page, toPage } from './page.model';

/** A row of GET /api/v1/admin/users (UserSummary in the Identity Service). */
export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  enabled?: boolean;
  createdAt?: string;
}

/** Admin endpoints of the Identity Service (AUTH_CONTRACT.md §4). ADMIN only, the backend answers 403 otherwise. */
@Injectable({ providedIn: 'root' })
export class AdminUserApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/admin/users`;

  list(page = 0, size = 20): Observable<Page<AdminUser>> {
    return this.http
      .get<unknown>(this.url, { params: { page, size } })
      .pipe(map(raw => toPage<AdminUser>(raw as never, size)));
  }

  /** Replaces the roles. The Identity Service always keeps MEMBER. */
  updateRoles(userId: string, roles: Role[]): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.url}/${userId}/roles`, { roles });
  }
}

import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize, firstValueFrom, Observable, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, AuthUser, LoginRequest, RegisterRequest, Role } from './auth.model';

/**
 * Routes of the Identity Service as exposed by the Gateway (see AUTH_CONTRACT.md).
 * If the Gateway publishes them under another prefix (for example /api/v1/auth),
 * change these two constants only.
 */
export const AUTH_PATH = '/auth';
export const USERS_PATH = '/users';

const REFRESH_KEY = 'tounsiguide.refreshToken';

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* storage unavailable: the session lasts until the page is closed */
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly authUrl = `${environment.apiBaseUrl}${AUTH_PATH}`;
  private readonly usersUrl = `${environment.apiBaseUrl}${USERS_PATH}`;

  /** The access token (15 min) lives in memory only. The refresh token is the only thing stored. */
  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthUser | null>(null);
  private refreshInFlight$: Observable<AuthResponse> | null = null;

  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.accessTokenSignal() !== null && this.userSignal() !== null);
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));

  accessToken(): string | null {
    return this.accessTokenSignal();
  }

  hasRefreshToken(): boolean {
    return readStorage(REFRESH_KEY) !== null;
  }

  hasRole(role: Role): boolean {
    return this.userSignal()?.roles.includes(role) ?? false;
  }

  /** UI hint only: the backend decides who may edit or delete (403). */
  isOwner(authorId: string | null | undefined): boolean {
    const user = this.userSignal();
    return !!user && !!authorId && user.id === authorId;
  }

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/login`, body).pipe(tap(r => this.store(r)));
  }

  /** Registration returns tokens, so the new member is logged in directly. */
  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/register`, body).pipe(tap(r => this.store(r)));
  }

  /**
   * Exchanges the refresh token for a new pair. The refresh token rotates on every use, and reusing an
   * old one revokes every session, so concurrent callers share one request.
   */
  refresh(): Observable<AuthResponse> {
    const refreshToken = readStorage(REFRESH_KEY);
    if (!refreshToken) return throwError(() => new Error('NO_REFRESH_TOKEN'));
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<AuthResponse>(`${this.authUrl}/refresh`, { refreshToken }).pipe(
        tap(r => this.store(r)),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight$;
  }

  /** Called once at startup: turns a stored refresh token back into a session. */
  async restoreSession(): Promise<void> {
    if (!this.hasRefreshToken()) return;
    try {
      await firstValueFrom(this.refresh());
    } catch (err) {
      // Forget the token only when the server rejected it; keep it if the Gateway was merely unreachable.
      if (err instanceof HttpErrorResponse && (err.status === 400 || err.status === 401)) this.endSession();
    }
  }

  /** Full profile of the caller, for example after a role change. */
  loadProfile(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${this.usersUrl}/me`).pipe(tap(user => this.userSignal.set(user)));
  }

  /** Revokes the refresh token on the server, then clears the local session. */
  logout(redirectTo: string | null = '/'): void {
    const refreshToken = readStorage(REFRESH_KEY);
    this.endSession();
    if (refreshToken) {
      // Idempotent on the server side; a failure changes nothing for the person.
      this.http.post<void>(`${this.authUrl}/logout`, { refreshToken }).subscribe({ error: () => {} });
    }
    if (redirectTo) this.router.navigateByUrl(redirectTo);
  }

  /** Clears the local session without calling the server (used when the refresh itself failed). */
  endSession(): void {
    this.accessTokenSignal.set(null);
    this.userSignal.set(null);
    writeStorage(REFRESH_KEY, null);
  }

  private store(response: AuthResponse): void {
    this.accessTokenSignal.set(response.accessToken);
    this.userSignal.set(response.user);
    writeStorage(REFRESH_KEY, response.refreshToken);
  }
}

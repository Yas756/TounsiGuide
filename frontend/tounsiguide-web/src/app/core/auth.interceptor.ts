import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AUTH_PATH } from './auth.service';
import { AuthService } from './auth.service';

/** register, login, refresh and logout are public: no token, no refresh attempt. */
const isAuthCall = (url: string) => url.includes(`${AUTH_PATH}/`);

/**
 * Adds the Bearer token to Gateway calls. On a 401 it tries ONE refresh and replays the request
 * (AUTH_CONTRACT.md §5); if the refresh fails, the session ends and the person is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const toGateway = req.url.startsWith(environment.apiBaseUrl);
  const withToken = (r: HttpRequest<unknown>, token: string) =>
    r.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  const token = auth.accessToken();
  const outgoing = toGateway && !isAuthCall(req.url) && token ? withToken(req, token) : req;

  return next(outgoing).pipe(
    catchError(error => {
      const canRefresh =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        toGateway &&
        !isAuthCall(req.url) &&
        (token !== null || auth.hasRefreshToken());
      if (!canRefresh) return throwError(() => error);

      return auth.refresh().pipe(
        catchError(refreshError => {
          const hadSession = auth.isLoggedIn() || token !== null;
          auth.endSession();
          if (hadSession) {
            router.navigate(['/login'], { queryParams: { returnUrl: router.url, expired: 1 } });
          }
          return throwError(() => (refreshError instanceof HttpErrorResponse ? error : refreshError));
        }),
        switchMap(response => next(withToken(req, response.accessToken))),
      );
    }),
  );
};

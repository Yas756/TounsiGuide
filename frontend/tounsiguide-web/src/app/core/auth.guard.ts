import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

/** Protects a route: anonymous visitors go to the login page and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Keeps logged-in members away from the login and register pages. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? router.createUrlTree(['/']) : true;
};

/**
 * Opens a route only to members who carry one of the given roles.
 * Anonymous visitors go to the login page; logged-in members without the role see /forbidden.
 * This is a convenience for the interface only: the backend still answers 403.
 */
export const roleGuard = (...roles: Role[]): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return roles.some(r => auth.hasRole(r)) ? true : router.createUrlTree(['/forbidden']);
};

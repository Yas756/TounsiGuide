/**
 * Global roles carried by the Identity Service. BUSINESS_REP (Sprint 3) is added when a page request is approved;
 * it appears in the next access token (after a new login). Rights on one page come from its members list.
 */
export type Role = 'MEMBER' | 'ADMIN' | 'BUSINESS_REP';

/** French labels of the global roles. */
export const ROLE_LABEL: Record<string, string> = { MEMBER: 'Membre', ADMIN: 'Administrateur', BUSINESS_REP: 'Représentant' };

/** User as returned by the Identity Service (UserResponse). */
export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  createdAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

/** Body returned by POST /api/v1/auth/register (201), /api/v1/auth/login and /api/v1/auth/refresh (200). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  /** Access token lifetime in seconds (900 = 15 min). */
  expiresIn: number;
  user: AuthUser;
}

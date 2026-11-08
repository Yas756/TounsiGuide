/** Global roles carried by the Identity Service (Sprint 2). */
export type Role = 'MEMBER' | 'ADMIN';

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

/** Body returned by POST /auth/register (201), /auth/login and /auth/refresh (200). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  /** Access token lifetime in seconds (900 = 15 min). */
  expiresIn: number;
  user: AuthUser;
}

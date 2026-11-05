import { HttpErrorResponse } from '@angular/common/http';

/** Common error body returned by the backend (see docs/api/community-service-api.md). */
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  details: Record<string, string>;
}

/** Error shape used by the UI, whatever the failure. */
export interface UiError {
  status: number;
  code: string;
  message: string;
  details: Record<string, string>;
}

function isApiErrorResponse(body: unknown): body is ApiErrorResponse {
  return !!body && typeof body === 'object' && 'code' in body && 'message' in body;
}

/** Turns any HTTP failure into a UiError with a message a person can understand. */
export function toUiError(error: unknown): UiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, code: 'UNKNOWN', message: 'Une erreur inattendue est survenue.', details: {} };
  }

  if (error.status === 0) {
    return {
      status: 0,
      code: 'GATEWAY_UNREACHABLE',
      message: "Impossible de joindre l'API Gateway. Vérifiez qu'elle tourne sur le port 8080.",
      details: {},
    };
  }

  if (isApiErrorResponse(error.error)) {
    return {
      status: error.status,
      code: error.error.code,
      message: error.error.message,
      details: error.error.details ?? {},
    };
  }

  const fallback: Record<number, string> = {
    400: 'La requête est invalide.',
    404: "La ressource demandée n'existe pas.",
    502: 'Le service communautaire ne répond pas.',
    503: 'Le service est temporairement indisponible.',
  };
  return {
    status: error.status,
    code: `HTTP_${error.status}`,
    message: fallback[error.status] ?? `Erreur serveur (${error.status}).`,
    details: {},
  };
}

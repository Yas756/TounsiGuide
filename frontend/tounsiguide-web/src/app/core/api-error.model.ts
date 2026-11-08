import { HttpErrorResponse } from '@angular/common/http';

/**
 * Error body returned by the backend. Community Service sends a `code`;
 * Identity Service sends `error` instead (see AUTH_CONTRACT.md).
 */
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code?: string;
  error?: string;
  message: string;
  path: string;
  details?: Record<string, string> | null;
}

/** Error shape used by the UI, whatever the failure. */
export interface UiError {
  status: number;
  code: string;
  message: string;
  details: Record<string, string>;
}

function isApiErrorResponse(body: unknown): body is ApiErrorResponse {
  return !!body && typeof body === 'object' && 'message' in body && typeof (body as { message: unknown }).message === 'string';
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
    const details = error.error.details ?? {};
    // Identity Service has no `code`: a 400 with field details is a validation error.
    const code =
      error.error.code ?? (error.status === 400 && Object.keys(details).length > 0 ? 'VALIDATION_ERROR' : `HTTP_${error.status}`);
    return { status: error.status, code, message: error.error.message, details };
  }

  const fallback: Record<number, string> = {
    400: 'La requête est invalide.',
    401: 'Vous devez être connecté pour effectuer cette action.',
    403: "Vous n'avez pas le droit d'effectuer cette action.",
    404: "La ressource demandée n'existe pas.",
    409: 'Cette ressource existe déjà.',
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

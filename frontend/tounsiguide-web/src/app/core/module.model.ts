import { UiError } from './api-error.model';

/** Topic modules (Community Service /api/v1/modules, Sprint 3 S3-B08). */
export type ModuleStatus = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export interface TopicModule {
  id: number;
  slug: string;
  name: string;
  description: string;
  /** Optional emoji. */
  icon: string | null;
  status: ModuleStatus;
  proposedByName: string | null;
  createdAt: string;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  /** Published posts in the module (public list and module page; 0 elsewhere). */
  postCount: number;
}

export interface ProposeModuleRequest {
  name: string;
  description: string;
  icon: string | null;
}

/** Same limits as the backend DTO. */
export const MODULE_NAME_MIN = 3;
export const MODULE_NAME_MAX = 60;
export const MODULE_DESCRIPTION_MAX = 500;

export const MODULE_STATUS_LABEL: Record<ModuleStatus, string> = {
  PROPOSED: 'En attente',
  APPROVED: 'Approuvé',
  REJECTED: 'Refusé',
  ARCHIVED: 'Archivé',
};

export function validateModuleForm(form: { name: string; description: string; icon: string }): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = form.name.trim();
  if (!name) errors['name'] = 'Le nom est obligatoire.';
  else if (name.length < MODULE_NAME_MIN || name.length > MODULE_NAME_MAX) errors['name'] = `Entre ${MODULE_NAME_MIN} et ${MODULE_NAME_MAX} caractères.`;
  else if (!/[\p{L}\p{N}]/u.test(name)) errors['name'] = 'Le nom doit contenir des lettres ou des chiffres.';
  const description = form.description.trim();
  if (!description) errors['description'] = 'Décrivez le thème en une ou deux phrases.';
  else if (description.length > MODULE_DESCRIPTION_MAX) errors['description'] = `${MODULE_DESCRIPTION_MAX} caractères au maximum.`;
  if ([...form.icon.trim()].length > 4) errors['icon'] = 'Une seule icône (emoji).';
  return errors;
}

/** French message for a failed module call. */
export function moduleErrorMessage(error: UiError): string {
  if (error.status === 0) return error.message;
  if (error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
  if (error.status === 404 && error.code === 'HTTP_404') {
    return 'Les modules ne sont pas joignables : la Gateway ne connaît pas encore ce service (redémarrez-la).';
  }
  if (error.status >= 500) return 'Le service des publications ne répond pas pour le moment. Réessayez dans un instant.';
  return error.message;
}

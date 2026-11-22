import { ApiMedia } from './api-media.model';
import { UiError } from './api-error.model';

/** Status of a page request (Business Service, docs/api/business-service-api.md). */
export type PageRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type PageRole = 'OWNER' | 'EDITOR';

/** Same limits as the Business Service DTOs. */
export const EVIDENCE_MAX_LENGTH = 2000;
export const PROPOSED_NAME_MAX_LENGTH = 120;
export const CONTACT_MAX_LENGTH = 255;
export const REJECT_REASON_MAX_LENGTH = 1000;
export const HIDE_REASON_MAX_LENGTH = 500;
export const PAGE_FIELD_LIMITS = {
  name: 120,
  description: 2000,
  openingHours: 500,
  phone: 40,
  email: 254,
  website: 255,
  address: 255,
} as const;

export interface PageRequest {
  id: number;
  locationId: number;
  /** Null when the Location Service could not be reached. */
  locationName: string | null;
  locationCity: string | null;
  requesterId: string;
  requesterName: string | null;
  proposedName: string | null;
  evidence: string;
  contact: string | null;
  status: PageRequestStatus;
  createdAt: string;
  decidedBy: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  /** The page created on approval. */
  pageId: number | null;
}

/** Body of POST /api/v1/business-pages/requests. */
export interface CreatePageRequest {
  locationId: number;
  proposedName: string | null;
  evidence: string;
  contact: string | null;
}

export interface BusinessPageLocation {
  id: number;
  name: string;
  city: string | null;
  address: string | null;
  category: string | null;
}

export interface BusinessPage {
  id: number;
  locationId: number;
  /** Null when the Location Service could not be reached. */
  location: BusinessPageLocation | null;
  name: string;
  description: string | null;
  openingHours: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  address: string | null;
  status: 'ACTIVE' | 'DELETED';
  verifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Whether the caller is a member of this page (false for visitors). */
  member: boolean;
  memberRole: PageRole | null;
  /** Star ratings (S3-US12): average rounded to 0.1 (null without ratings) and number of ratings. Older backends leave them out. */
  ratingAverage?: number | null;
  ratingCount?: number;
}

/** GET/PUT/DELETE /api/v1/business-pages/{id}/rating (S3-US12). */
export interface RatingSummary {
  pageId: number;
  average: number | null;
  count: number;
  /** distribution[0] = number of 1-star ratings … distribution[4] = 5-star ratings. */
  distribution: number[];
  /** The caller's own stars, null for a visitor or when not rated. */
  myStars: number | null;
  /** False for a visitor and for the members of the page (no self-rating). */
  canRate: boolean;
}

/** Body of PUT /api/v1/business-pages/{id}: the whole official information. */
export interface UpdatePageRequest {
  name: string;
  description: string | null;
  openingHours: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  address: string | null;
}

/** A post on a wall: the Community post plus the wall's own visibility (hidden only ever shown to members). */
export interface WallPost {
  id: number;
  content: string;
  authorId: string;
  authorName: string;
  locationId: number | null;
  createdAt: string;
  updatedAt: string;
  media: ApiMedia[];
  hidden: boolean;
  hiddenReason: string | null;
  hiddenAt: string | null;
}

export interface WallVisibility {
  pageId: number;
  postId: number;
  hidden: boolean;
  reason: string | null;
  actorId: string;
  actorName: string | null;
  changedAt: string;
}

export type BusinessAuditAction =
  | 'REQUEST_SUBMITTED'
  | 'REQUEST_APPROVED'
  | 'REQUEST_REJECTED'
  | 'PAGE_UPDATED'
  | 'POST_HIDDEN'
  | 'POST_UNHIDDEN';

export interface BusinessAuditEntry {
  id: number;
  action: BusinessAuditAction;
  pageId: number | null;
  requestId: number | null;
  postId: number | null;
  actorId: string;
  actorName: string | null;
  reason: string | null;
  createdAt: string;
}

export const REQUEST_STATUS_LABEL: Record<PageRequestStatus, string> = {
  PENDING: 'En attente',
  APPROVED: 'Approuvée',
  REJECTED: 'Refusée',
};

export const PAGE_ROLE_LABEL: Record<PageRole, string> = {
  OWNER: 'Propriétaire',
  EDITOR: 'Éditeur',
};

export const AUDIT_ACTION_LABEL: Record<BusinessAuditAction, string> = {
  REQUEST_SUBMITTED: 'Demande envoyée',
  REQUEST_APPROVED: 'Demande approuvée',
  REQUEST_REJECTED: 'Demande refusée',
  PAGE_UPDATED: 'Informations modifiées',
  POST_HIDDEN: 'Publication masquée',
  POST_UNHIDDEN: 'Publication affichée à nouveau',
};

/** "Café des Délices · Sidi Bou Saïd", or "Lieu n°14" when the Location Service did not answer. */
export function requestPlaceLabel(r: Pick<PageRequest, 'locationId' | 'locationName' | 'locationCity'>): string {
  if (!r.locationName) return `Lieu n°${r.locationId}`;
  return r.locationCity && r.locationCity !== r.locationName ? `${r.locationName} · ${r.locationCity}` : r.locationName;
}

/**
 * French message for a failed Business Service call. The service already answers in French (403, 404, 409,
 * dependency 503); the Gateway's own 503 (service not started) and network errors get a message here.
 */
export function businessErrorMessage(error: UiError): string {
  if (error.status === 0) return error.message;
  if (error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
  // A 404 without the service's error body comes from the Gateway itself: the business-pages route is unknown there.
  if (error.status === 404 && error.code === 'HTTP_404') {
    return "Les pages professionnelles ne sont pas joignables : la Gateway ne connaît pas encore ce service (redémarrez-la).";
  }
  if (error.status === 503 && error.code === 'SERVICE_UNAVAILABLE') {
    return 'Le service des pages professionnelles ne répond pas pour le moment. Réessayez dans un instant.';
  }
  if (error.status >= 500 && !error.code.endsWith('_UNAVAILABLE')) {
    return 'Le service des pages professionnelles a rencontré une erreur. Réessayez dans un instant.';
  }
  return error.message;
}

/** Same rules as UpdatePageRequest in the Business Service, with French messages. */
export function validatePageForm(form: UpdatePageRequest): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = form.name.trim();
  if (!name) errors['name'] = 'Le nom est obligatoire.';
  for (const [field, max] of Object.entries(PAGE_FIELD_LIMITS)) {
    const value = (form[field as keyof UpdatePageRequest] ?? '').trim();
    if (value.length > max) errors[field] ??= `${max} caractères au maximum.`;
  }
  const phone = (form.phone ?? '').trim();
  if (phone && !/^[0-9+()./ -]{4,40}$/.test(phone)) errors['phone'] ??= 'Numéro de téléphone invalide.';
  const email = (form.email ?? '').trim();
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors['email'] ??= 'Adresse e-mail invalide.';
  const website = (form.website ?? '').trim();
  if (website && !/^https?:\/\/\S+$/.test(website)) errors['website'] ??= 'Le site web doit commencer par http:// ou https://.';
  return errors;
}

/** Trims every field and turns blanks into null (the name stays a string). */
export function toUpdateRequest(form: UpdatePageRequest): UpdatePageRequest {
  const text = (value: string | null) => (value ?? '').trim() || null;
  return {
    name: form.name.trim(),
    description: text(form.description),
    openingHours: text(form.openingHours),
    phone: text(form.phone),
    email: text(form.email),
    website: text(form.website),
    address: text(form.address),
  };
}

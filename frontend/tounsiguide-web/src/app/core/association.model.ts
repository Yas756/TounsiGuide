/**
 * Association between a post and a Location (docs/api/association-api.md, implemented in location-service).
 * Everything that depends on that contract lives in this file and association-api.service.ts,
 * so a contract change only touches these two files and the pages that show associations.
 */
import { UiError } from './api-error.model';

export type AssociationStatus = 'PROPOSED' | 'CONFIRMED' | 'DISPUTED' | 'REJECTED';
export type AssociationOrigin = 'AUTHOR' | 'MEMBER' | 'MENTION' | 'ADMIN';
export type EvidenceDirection = 'SUPPORT' | 'OPPOSE';
export type ReviewDecision = 'VALIDATE' | 'REMOVE' | 'FORCE';

export interface AssociationEvidence {
  id: number;
  type: string;
  direction: EvidenceDirection;
  weight: number;
  actorId?: string | null;
  actorName: string | null;
  commentId: number | null;
  note: string | null;
  createdAt: string;
}

export interface AssociationMention {
  text: string;
  commentId: number | null;
  type: string;
}

export interface AssociationAnalysis {
  provider: string;
  providerVersion?: string;
  supportScore: number;
  opposeScore: number;
  reasoning: string;
  overallReasoning?: string;
  mentions?: AssociationMention[];
  candidates?: { locationId: number; score: number; matchedText?: string; commentId?: number }[];
  analysedAt?: string;
}

export interface AssociationHistoryEntry {
  fromStatus: AssociationStatus | null;
  toStatus: AssociationStatus;
  actorId: string | null;
  actorRole: string | null;
  reason: string | null;
  createdAt: string;
}

export interface Association {
  id: number;
  postId: number;
  location: { id: number; name: string; city?: string | null };
  origin: AssociationOrigin;
  status: AssociationStatus;
  /** 0 to 100: how likely the post belongs to this Location. */
  score: number;
  forced: boolean;
  analysis: AssociationAnalysis | null;
  evidence?: AssociationEvidence[];
  history?: AssociationHistoryEntry[];
  createdBy?: string | null;
  createdAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionReason?: string | null;
  /** The post was deleted in the Community Service: the association is kept as it was, for the history. */
  postDeleted?: boolean;
  postDeletedAt?: string | null;
}

export interface ReviewRequest {
  decision: ReviewDecision;
  reason: string;
}

/** A member suggests a place (from a comment when commentId is set). */
export interface SuggestRequest {
  locationId: number;
  note?: string | null;
  commentId?: number | null;
}

/** Feed summary of one post (GET /api/v1/associations/summary). */
export interface PostAssociationSummary {
  confirmed: { associationId: number; locationId: number; score: number } | null;
  openProposals: number;
}

/** Member actions on an association, used to choose the error message. */
export type AssociationAction = 'suggest' | 'confirm' | 'reject' | 'clarify' | 'dispute';

export const STATUS_LABEL: Record<AssociationStatus, string> = {
  PROPOSED: 'Proposée',
  CONFIRMED: 'Confirmée',
  DISPUTED: 'Contestée',
  REJECTED: 'Rejetée',
};

/** Badge shown in the admin review queue when the post of an association was deleted. */
export const POST_DELETED_LABEL = 'Publication supprimée';

export const ORIGIN_LABEL: Record<AssociationOrigin, string> = {
  AUTHOR: "Choisie par l'auteur",
  MEMBER: 'Suggérée par un membre',
  MENTION: 'Détectée dans le texte',
  ADMIN: 'Fixée par un admin',
};

/**
 * Where an association comes from. For MENTION it says whether the place was named in the post itself or in a
 * comment (the first mention evidence tells), instead of always "in a comment".
 */
export function originLabelOf(a: Pick<Association, 'origin' | 'evidence'>): string {
  if (a.origin === 'MENTION') {
    const mention = (a.evidence ?? []).find(e => e.type === 'TEXT_MENTION' || e.type === 'PAGE_MENTION');
    if (mention) return mention.commentId === null ? 'Détectée dans la publication' : 'Détectée dans un commentaire';
  }
  return ORIGIN_LABEL[a.origin];
}

/**
 * Evidence notes and history reasons written by the system are French since 2026-11-15. Older rows are still in
 * English: they are translated here for display. Anything else (a member's own note) is shown as written.
 */
const LEGACY_TEXTS: [RegExp, (...groups: string[]) => string][] = [
  [/^Mention found: '(.+)'$/, name => `Lieu cité : « ${name} »`],
  [/^Support for '(.+)' on the same post$/, name => `Soutien à « ${name} » sur la même publication`],
  [/^Place named in comment (\d+)$/, id => `Lieu cité dans le commentaire n° ${id}`],
  [/^Score fell to (\d+), below the dispute threshold (\d+)$/, (s, t) => `Score tombé à ${s}, sous le seuil de contestation ${t}`],
  [/^Place named in the post$/, () => 'Lieu cité dans la publication'],
  [/^Author tag; an administrator fixed another place$/, () => "Lieu choisi par l'auteur ; un administrateur a fixé un autre lieu"],
  [/^Author tag$/, () => "Lieu choisi par l'auteur"],
  [/^Author changed the place$/, () => "L'auteur a changé de lieu"],
  [/^Author claim withdrawn$/, () => "L'auteur a retiré le lieu"],
  [/^Confirmed by the author$/, () => "Confirmée par l'auteur"],
  [/^Rejected by the author$/, () => "Rejetée par l'auteur"],
  [/^Suggested by a member$/, () => 'Suggérée par un membre'],
  [/^Another place was confirmed for this post$/, () => 'Un autre lieu a été confirmé pour cette publication'],
];

export function displayText(text: string | null | undefined): string {
  if (!text) return '';
  for (const [pattern, french] of LEGACY_TEXTS) {
    const match = pattern.exec(text);
    if (match) return french(...match.slice(1));
  }
  return text;
}

/**
 * How an administrator settled a CONFIRMED place: FORCED (Forcer, or set directly by an admin) or VALIDATED (Valider).
 * Only the post author or an administrator can confirm, so a confirmation decided by someone else than the author is
 * an administrator's. Null when the place is not confirmed or the author confirmed it.
 */
export type AdminDecision = 'FORCED' | 'VALIDATED';

export function adminDecision(
  a: Pick<Association, 'status' | 'forced' | 'origin' | 'decidedBy'>,
  postAuthorId: string | null | undefined,
): AdminDecision | null {
  if (a.status !== 'CONFIRMED') return null;
  if (a.forced || a.origin === 'ADMIN') return 'FORCED';
  if (a.decidedBy && postAuthorId && a.decidedBy !== postAuthorId) return 'VALIDATED';
  return null;
}

export const ADMIN_DECISION_LABEL: Record<AdminDecision, string> = {
  FORCED: 'Fixé par un administrateur',
  VALIDATED: 'Validé par un administrateur',
};

export const EVIDENCE_LABEL: Record<string, string> = {
  AUTHOR_TAG: "Lieu choisi par l'auteur",
  AUTHOR_CONFIRMED: "Confirmée par l'auteur",
  PAGE_MENTION: "Mention d'une page professionnelle",
  MEMBER_SUGGESTION: "Suggestion d'un membre",
  TEXT_MENTION: 'Lieu cité dans le texte',
  COMPETING_CLAIM: 'Autre lieu proposé',
  MEMBER_DISPUTE: "Contestation d'un membre",
  BUSINESS_DISPUTE: 'Contestation du gérant',
};

/** CSS class of a score: green from 75 (the dispute threshold), orange from 50, red below. */
export function scoreClass(score: number): string {
  return score >= 75 ? 'score-high' : score >= 50 ? 'score-mid' : 'score-low';
}

/**
 * French message for a failed member action. The backend messages are in English and generic,
 * so 403, 404, 409 and 503 get a sentence that says what to do.
 */
export function associationErrorMessage(error: UiError, action: AssociationAction): string {
  if (error.code === 'COMMUNITY_SERVICE_UNAVAILABLE' || error.status === 503) {
    return 'Le service communautaire ne répond pas pour le moment. Réessayez dans un instant.';
  }
  if (error.status === 401) {
    return 'Votre session a expiré. Reconnectez-vous pour continuer.';
  }
  if (error.status === 404) {
    return action === 'suggest'
      ? "Ce lieu ou cette publication n'existe plus."
      : "Cette proposition n'existe plus. Rechargez la page.";
  }
  if (error.status === 403) {
    return action === 'dispute'
      ? 'Vous ne pouvez pas contester le lieu de votre propre publication : modifiez plutôt la publication.'
      : "Seul l'auteur de la publication peut faire cela.";
  }
  if (error.status === 409) {
    switch (action) {
      case 'suggest':
        return 'Vous avez déjà proposé ce lieu pour cette publication.';
      case 'dispute':
        return "Vous avez déjà contesté ce lieu, ou il n'est plus confirmé.";
      default:
        return "Cette proposition n'est plus en attente : elle a déjà été traitée, ou un administrateur a fixé le lieu.";
    }
  }
  if (error.code === 'VALIDATION_ERROR' && error.details['note']) {
    return 'Ajoutez une explication (500 caractères au maximum).';
  }
  return error.message;
}

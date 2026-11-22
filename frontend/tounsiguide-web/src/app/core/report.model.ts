import { UiError } from './api-error.model';

/** Reporting and moderation (Community Service /api/v1/reports, Sprint 3 S3-B07). */
export type ReportTargetType = 'POST' | 'COMMENT';
export type ReportReason = 'SPAM' | 'OFFENSIVE' | 'FALSE_INFO' | 'OTHER';
export type ReportStatus = 'OPEN' | 'DISMISSED' | 'ACTIONED';
export type ModerationAction = 'DISMISS' | 'RESTRICT' | 'RESTORE';
export type ContentStatus = 'PUBLISHED' | 'DELETED' | 'RESTRICTED';

/** Same limits as the backend DTOs. */
export const REPORT_DETAIL_MAX_LENGTH = 1000;
export const MODERATION_REASON_MAX_LENGTH = 1000;

export const REPORT_REASONS: ReportReason[] = ['SPAM', 'OFFENSIVE', 'FALSE_INFO', 'OTHER'];

export const REPORT_REASON_LABEL: Record<ReportReason, string> = {
  SPAM: 'Spam ou publicité',
  OFFENSIVE: 'Contenu offensant',
  FALSE_INFO: 'Information fausse',
  OTHER: 'Autre',
};

export const TARGET_LABEL: Record<ReportTargetType, string> = {
  POST: 'Publication',
  COMMENT: 'Commentaire',
};

export const MODERATION_ACTION_LABEL: Record<ModerationAction, string> = {
  DISMISS: 'Signalements rejetés',
  RESTRICT: 'Contenu restreint',
  RESTORE: 'Contenu rétabli',
};

export interface CreateReportRequest {
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  detail: string | null;
}

export interface ReportItem {
  id: number;
  targetType: ReportTargetType;
  targetId: number;
  postId: number;
  reporterName: string | null;
  reason: ReportReason;
  detail: string | null;
  status: ReportStatus;
  createdAt: string;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
}

/** One reported content in the moderation queue. */
export interface ReportedContent {
  targetType: ReportTargetType;
  targetId: number;
  postId: number;
  /** Null when the content no longer exists. */
  preview: string | null;
  authorId: string | null;
  authorName: string | null;
  contentStatus: ContentStatus | null;
  openReports: number;
  reasons: Partial<Record<ReportReason, number>>;
  firstReportedAt: string;
  lastReportedAt: string;
  reports: ReportItem[];
}

export interface RestrictedContent {
  targetType: ReportTargetType;
  targetId: number;
  postId: number;
  preview: string | null;
  authorName: string | null;
  restrictedAt: string | null;
  restrictionReason: string | null;
}

export interface ModerationDecision {
  id: number;
  targetType: ReportTargetType;
  targetId: number;
  action: ModerationAction;
  actorName: string | null;
  reason: string | null;
  reportsClosed: number;
  createdAt: string;
}

/** French message for a failed report or moderation call. */
export function reportErrorMessage(error: UiError): string {
  if (error.status === 0) return error.message;
  if (error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
  if (error.status === 409) return error.message || 'Vous avez déjà signalé ce contenu ; un modérateur va l’examiner.';
  if (error.status === 404 && error.code === 'HTTP_404') {
    return 'Les signalements ne sont pas joignables : la Gateway ne connaît pas encore ce service (redémarrez-la).';
  }
  if (error.status === 404) return 'Ce contenu n’existe plus ou n’est plus visible.';
  if (error.status === 403) return error.message || "Vous n'avez pas le droit d'effectuer cette action.";
  if (error.status >= 500) return 'Le service des publications ne répond pas pour le moment. Réessayez dans un instant.';
  return error.message;
}

import { ApiMedia } from './api-media.model';

/** RESTRICTED (Sprint 3 moderation): only administrators and the author still receive the post. */
export type PublicationStatus = 'PUBLISHED' | 'DELETED' | 'RESTRICTED';

/** Post as returned by the Community Service (through the Gateway). */
export interface ApiPost {
  id: number;
  content: string;
  authorId: string;
  authorName: string;
  /** Id of a Location in the Location & Matching Service (load it with LocationApiService.byIds). */
  locationId: number | null;
  /** Photos and videos, in display order. Older backends may leave it out. */
  media?: ApiMedia[];
  /** PUBLISHED, or DELETED (soft delete: only an administrator can still read a deleted post). Older backends leave it out. */
  publicationStatus?: PublicationStatus;
  createdAt: string;
  updatedAt: string;
  /** Topic module (Sprint 3), only when it is approved; otherwise null or absent. */
  moduleId?: number | null;
  moduleSlug?: string | null;
  moduleName?: string | null;
}

/** Same limits as the Community Service DTOs (CreatePostRequest / CreateCommentRequest). */
export const POST_MAX_LENGTH = 2000;
export const COMMENT_MAX_LENGTH = 1000;

/** Body for POST and PUT /api/v1/posts. The backend owns id, author and timestamps. */
export interface PostRequest {
  content: string;
  locationId: number | null;
  /** Ids from POST /api/v1/media. Left out on update = keep the current media; [] = remove them all. */
  mediaIds?: string[];
  /** Optional approved topic module; null = none (on update: removes it). */
  moduleId?: number | null;
}

import { ApiMedia } from './api-media.model';

/** Comment as returned by the Community Service (through the Gateway). */
export interface ApiComment {
  id: number;
  postId: number;
  content: string;
  authorId: string;
  authorName: string;
  /** At most one photo or video. Older backends may leave it out. */
  media?: ApiMedia[];
  createdAt: string;
  updatedAt: string;
  /** PUBLISHED, or RESTRICTED by a moderator (only administrators and the comment's author receive those). */
  publicationStatus?: 'PUBLISHED' | 'RESTRICTED';
}

/** Body for POST and PUT /api/v1/posts/{postId}/comments. */
export interface CommentRequest {
  content: string;
  /** Ids from POST /api/v1/media. Left out on update = keep the current media. */
  mediaIds?: string[];
}

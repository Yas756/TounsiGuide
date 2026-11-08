import { ApiPlace } from './api-place.model';

/** Post as returned by the Community Service (through the Gateway). */
export interface ApiPost {
  id: number;
  content: string;
  authorId: string;
  authorName: string;
  locationId: number | null;
  /** Place picked on the map or found with the autocomplete, if any. */
  place: ApiPlace | null;
  createdAt: string;
  updatedAt: string;
}

/** Body for POST and PUT /api/v1/posts. The backend owns id, author and timestamps. */
export interface PostRequest {
  content: string;
  locationId: number | null;
  place: ApiPlace | null;
}

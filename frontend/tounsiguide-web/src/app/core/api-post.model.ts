/** Post as returned by the Community Service (through the Gateway). */
export interface ApiPost {
  id: number;
  content: string;
  authorId: string;
  authorName: string;
  locationId: number | null;
  createdAt: string;
  updatedAt: string;
}

/** Body for POST and PUT /api/v1/posts. The backend owns id, author and timestamps. */
export interface PostRequest {
  content: string;
  locationId: number | null;
}

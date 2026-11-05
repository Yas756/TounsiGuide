/** Comment as returned by the Community Service (through the Gateway). */
export interface ApiComment {
  id: number;
  postId: number;
  content: string;
  authorId: string;
  authorName: string;
  createdAt: string;
  updatedAt: string;
}

/** Body for POST and PUT /api/v1/posts/{postId}/comments. */
export interface CommentRequest {
  content: string;
}

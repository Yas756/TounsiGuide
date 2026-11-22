import { environment } from '../../environments/environment';

export type MediaType = 'IMAGE' | 'VIDEO';

/** A photo or video stored by the Community Service (POST /api/v1/media). */
export interface ApiMedia {
  id: string;
  type: MediaType;
  /** Path on the Gateway, e.g. /api/v1/media/{id}/file. Use mediaSrc() to get a full URL. */
  url: string;
  contentType: string;
  sizeBytes: number;
}

/** Same limits as the backend (tounsiguide.media.* in community-service). */
export const MEDIA_LIMITS = {
  imageTypes: ['image/jpeg', 'image/png', 'image/webp'],
  videoTypes: ['video/mp4', 'video/webm'],
  maxImageBytes: 10 * 1024 * 1024,
  maxVideoBytes: 50 * 1024 * 1024,
  post: { maxItems: 6, maxVideos: 1 },
  comment: { maxItems: 1, maxVideos: 1 },
} as const;

export const MEDIA_ACCEPT = [...MEDIA_LIMITS.imageTypes, ...MEDIA_LIMITS.videoTypes].join(',');

/** Full URL of a media file, whatever form the backend used. */
export function mediaSrc(media: Pick<ApiMedia, 'url' | 'id'>): string {
  const url = media.url || `/api/v1/media/${media.id}/file`;
  return /^https?:\/\//.test(url) ? url : `${environment.apiBaseUrl}${url}`;
}

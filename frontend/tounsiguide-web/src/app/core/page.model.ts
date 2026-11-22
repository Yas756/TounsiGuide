/** A page of results, whatever shape the service uses (Spring Page or a small PageResponse record). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

type RawPage<T> = {
  content?: T[];
  items?: T[];
  page?: number;
  number?: number;
  size?: number;
  totalElements?: number;
  total?: number;
  totalPages?: number;
};

/** Accepts `{content, page|number, size, totalElements, totalPages}`, `{items, ...}` or a bare array. */
export function toPage<T>(raw: RawPage<T> | T[] | null | undefined, requestedSize: number): Page<T> {
  if (Array.isArray(raw)) {
    return { items: raw, page: 0, size: raw.length, totalElements: raw.length, totalPages: 1 };
  }
  const items = raw?.content ?? raw?.items ?? [];
  const size = raw?.size ?? requestedSize;
  const totalElements = raw?.totalElements ?? raw?.total ?? items.length;
  const totalPages = raw?.totalPages ?? Math.max(1, Math.ceil(totalElements / Math.max(1, size)));
  return { items, page: raw?.page ?? raw?.number ?? 0, size, totalElements, totalPages };
}

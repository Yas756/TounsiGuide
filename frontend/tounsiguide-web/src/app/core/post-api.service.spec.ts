import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { FEED_PAGE_SIZE, PostApiService } from './post-api.service';
import { ApiPost } from './api-post.model';
import { Page } from './page.model';

describe('PostApiService', () => {
  let service: PostApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl}/api/v1/posts`;

  const post = (id: number): ApiPost => ({
    id,
    content: `post ${id}`,
    authorId: 'u1',
    authorName: 'Sarra',
    locationId: null,
    media: [],
    createdAt: '2026-11-15T10:00:00Z',
    updatedAt: '2026-11-15T10:00:00Z',
    publicationStatus: 'PUBLISHED',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PostApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches posts with q, trimmed (S3-US10)', () => {
    service.search('  lablabi ', 0, 8).subscribe();

    const req = http.expectOne(r => r.url === url);
    expect(req.request.params.get('q')).toBe('lablabi');
    expect(req.request.params.get('size')).toBe('8');
    req.flush({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
  });

  it('asks for the first page of the default size', () => {
    service.list().subscribe();

    const req = http.expectOne(r => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe(String(FEED_PAGE_SIZE));
    req.flush({ content: [], page: 0, size: FEED_PAGE_SIZE, totalElements: 0, totalPages: 0 });
  });

  it('maps the backend page (content, page, size, totalElements, totalPages)', () => {
    let result: Page<ApiPost> | undefined;
    service.list(1, 2).subscribe(p => (result = p));

    const req = http.expectOne(r => r.url === url && r.params.get('page') === '1' && r.params.get('size') === '2');
    req.flush({ content: [post(3)], page: 1, size: 2, totalElements: 3, totalPages: 2 });

    expect(result).toEqual({ items: [post(3)], page: 1, size: 2, totalElements: 3, totalPages: 2 });
  });
});

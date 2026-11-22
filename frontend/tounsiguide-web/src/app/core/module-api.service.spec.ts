import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { ModuleApiService } from './module-api.service';
import { PostApiService } from './post-api.service';
import { validateModuleForm } from './module.model';

describe('ModuleApiService', () => {
  let service: ModuleApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl}/api/v1/modules`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ModuleApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists approved modules and proposes one', () => {
    service.approved().subscribe();
    service.propose({ name: 'Cafés', description: 'Où boire un café', icon: null }).subscribe();

    expect(http.match(r => r.url === url && r.method === 'GET').length).toBe(1);
    const post = http.match(r => r.url === url && r.method === 'POST')[0];
    expect(post.request.body).toEqual({ name: 'Cafés', description: 'Où boire un café', icon: null });
  });

  it('asks the admin queue by status and sends decisions to the module', () => {
    service.queue('PROPOSED', 0, 20).subscribe();
    service.reject(3, '  Doublon ').subscribe();
    service.approve(4).subscribe();
    service.archive(5).subscribe();

    expect(http.expectOne(r => r.url === `${url}/queue`).request.params.get('status')).toBe('PROPOSED');
    expect(http.expectOne(`${url}/3/reject`).request.body).toEqual({ reason: 'Doublon' });
    http.expectOne(`${url}/4/approve`);
    http.expectOne(`${url}/5/archive`);
  });

  it('filters the feed by module slug with the usual paging', () => {
    TestBed.inject(PostApiService).list(1, 20, 'cafes').subscribe();

    const req = http.expectOne(r => r.url === `${environment.apiBaseUrl}/api/v1/posts`);
    expect(req.request.params.get('module')).toBe('cafes');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('checks the proposal like the backend', () => {
    expect(Object.keys(validateModuleForm({ name: 'ab', description: ' ', icon: '' })).sort()).toEqual(['description', 'name']);
    expect(validateModuleForm({ name: '€€€', description: 'x', icon: '' })['name']).toContain('lettres');
    expect(validateModuleForm({ name: 'Cafés', description: 'Où boire un café', icon: '☕' })).toEqual({});
  });
});

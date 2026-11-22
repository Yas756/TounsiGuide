import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { BusinessApiService } from './business-api.service';
import { BusinessPage } from './business.model';

describe('BusinessApiService', () => {
  let service: BusinessApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl}/api/v1/business-pages`;

  const page = (id: number): BusinessPage => ({
    id, locationId: 14, location: null, name: 'Café des Délices', description: null, openingHours: null, phone: null,
    email: null, website: null, address: null, status: 'ACTIVE', verifiedAt: null, createdAt: '', updatedAt: '',
    member: false, memberRole: null,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BusinessApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends a page request to /requests', () => {
    const body = { locationId: 14, proposedName: null, evidence: 'Registre', contact: null };
    service.submitRequest(body).subscribe();

    const req = http.expectOne(`${url}/requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('asks the admin queue with the status, and without it for every request', () => {
    service.requests('PENDING', 0, 20).subscribe();
    service.requests(null, 1, 20).subscribe();

    const pending = http.expectOne(r => r.url === `${url}/requests` && r.params.get('status') === 'PENDING');
    const all = http.expectOne(r => r.url === `${url}/requests` && !r.params.has('status'));
    expect(all.request.params.get('page')).toBe('1');
    pending.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    all.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('answers null for a Location without page (404) and asks only once', () => {
    const answers: (BusinessPage | null)[] = [];
    service.byLocation(14).subscribe(p => answers.push(p));
    http.expectOne(`${url}/by-location/14`).flush({ message: 'x' }, { status: 404, statusText: 'Not Found' });
    service.byLocation(14).subscribe(p => answers.push(p));

    http.expectNone(`${url}/by-location/14`);
    expect(answers).toEqual([null, null]);
  });

  it('returns the page of a Location', () => {
    let found: BusinessPage | null = null;
    service.byLocation(15).subscribe(p => (found = p));
    http.expectOne(`${url}/by-location/15`).flush(page(30));

    expect(found!.id).toBe(30);
  });

  it('asks hidden wall posts only when told to', () => {
    service.wall(30, 0, 20).subscribe();
    service.wall(30, 0, 20, true).subscribe();

    const reqs = http.match(r => r.url === `${url}/30/wall`);
    expect(reqs[0].request.params.has('includeHidden')).toBeFalse();
    expect(reqs[1].request.params.get('includeHidden')).toBe('true');
    reqs.forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
  });

  it('hides with a trimmed reason, or null when blank', () => {
    service.hide(30, 5, '  Hors sujet ').subscribe();
    service.hide(30, 6, '   ').subscribe();

    expect(http.expectOne(`${url}/30/wall/5/hide`).request.body).toEqual({ reason: 'Hors sujet' });
    expect(http.expectOne(`${url}/30/wall/6/hide`).request.body).toEqual({ reason: null });
  });
});

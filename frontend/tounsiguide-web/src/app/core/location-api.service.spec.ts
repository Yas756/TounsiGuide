import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { LocationApiService } from './location-api.service';
import { ApiLocation } from './api-location.model';

/** S2-T01: the Location service client (directory search S2-US03, admin creation S2-US10, cache by id). */
describe('LocationApiService', () => {
  let service: LocationApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl}/api/v1/locations`;
  const location = (id: number): ApiLocation => ({
    id, name: `Lieu ${id}`, city: 'Tunis', address: null, latitude: 36.8, longitude: 10.18, category: 'cafe',
    source: 'DIRECTORY', aliases: [], createdAt: '2026-11-15T10:00:00Z',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(LocationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches the directory and then serves those Locations from its cache', () => {
    let found: ApiLocation[] | undefined;
    service.directory(' bardo ', 10).subscribe(list => (found = list));
    const req = http.expectOne(r => r.url === url);
    expect(req.request.params.get('query')).toBe('bardo');
    expect(req.request.params.get('limit')).toBe('10');
    req.flush([location(5)]);
    expect(found?.length).toBe(1);

    let byId: Map<number, ApiLocation> | undefined;
    service.byIds([5, null]).subscribe(m => (byId = m));
    expect(byId?.get(5)?.name).toBe('Lieu 5');   // no second request
  });

  it('creates a Location with POST', () => {
    service.create({ name: 'Bardo', city: 'Tunis', address: null, latitude: null, longitude: null, category: null, aliases: [] })
      .subscribe();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.name).toBe('Bardo');
    req.flush(location(30));
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { ReportApiService } from './report-api.service';
import { reportErrorMessage } from './report.model';

describe('ReportApiService', () => {
  let service: ReportApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl}/api/v1/reports`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ReportApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts a report', () => {
    service.report({ targetType: 'COMMENT', targetId: 88, reason: 'OFFENSIVE', detail: null }).subscribe();

    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ targetType: 'COMMENT', targetId: 88, reason: 'OFFENSIVE', detail: null });
    req.flush({});
  });

  it('sends the moderation decisions to the content they are about', () => {
    service.restrict('POST', 12, '  Publicité ').subscribe();
    service.dismiss('COMMENT', 88, '   ').subscribe();
    service.restore('POST', 12, null).subscribe();

    expect(http.expectOne(`${url}/targets/POST/12/restrict`).request.body).toEqual({ reason: 'Publicité' });
    expect(http.expectOne(`${url}/targets/COMMENT/88/dismiss`).request.body).toEqual({ reason: null });
    expect(http.expectOne(`${url}/targets/POST/12/restore`).request.body).toEqual({ reason: null });
  });

  it('reads the queue as a page', () => {
    let total = -1;
    service.queue(0, 20).subscribe(p => (total = p.totalElements));

    http.expectOne(r => r.url === `${url}/queue` && r.params.get('size') === '20')
      .flush({ content: [], page: 0, size: 20, totalElements: 3, totalPages: 1 });
    expect(total).toBe(3);
  });

  it('turns errors into French messages', () => {
    expect(reportErrorMessage({ status: 409, code: 'CONFLICT', message: 'Vous avez déjà signalé ce contenu', details: {} }))
      .toContain('déjà signalé');
    expect(reportErrorMessage({ status: 404, code: 'HTTP_404', message: 'x', details: {} })).toContain('Gateway');
    expect(reportErrorMessage({ status: 503, code: 'SERVICE_UNAVAILABLE', message: 'x', details: {} })).toContain('ne répond pas');
  });
});

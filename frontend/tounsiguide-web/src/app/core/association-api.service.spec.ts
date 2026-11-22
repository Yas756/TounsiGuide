import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AssociationApiService, SUMMARY_BATCH } from './association-api.service';
import { Association, PostAssociationSummary } from './association.model';

describe('AssociationApiService', () => {
  let service: AssociationApiService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  const association = (id: number): Association => ({
    id,
    postId: 1,
    location: { id: 14, name: 'Café Roma', city: 'Tunis' },
    origin: 'MEMBER',
    status: 'PROPOSED',
    score: 60,
    forced: false,
    analysis: null,
    evidence: [],
    createdAt: '2026-11-09T10:00:00Z',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AssociationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the associations of a post', () => {
    let result: Association[] | undefined;
    service.forPost(1).subscribe(list => (result = list));

    const req = http.expectOne(`${base}/api/v1/posts/1/associations`);
    expect(req.request.method).toBe('GET');
    req.flush([association(7)]);

    expect(result?.[0].location.name).toBe('Café Roma');
  });

  it('suggests a place from a comment', () => {
    service.suggest(1, { locationId: 14, commentId: 88, note: 'The sign is visible' }).subscribe();

    const req = http.expectOne(`${base}/api/v1/posts/1/associations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ locationId: 14, commentId: 88, note: 'The sign is visible' });
    req.flush(association(7), { status: 201, statusText: 'Created' });
  });

  it('confirms and rejects with an empty body', () => {
    service.confirm(7).subscribe();
    service.reject(8).subscribe();

    const confirm = http.expectOne(`${base}/api/v1/associations/7/confirm`);
    const reject = http.expectOne(`${base}/api/v1/associations/8/reject`);
    expect(confirm.request.method).toBe('POST');
    expect(reject.request.method).toBe('POST');
    confirm.flush(association(7));
    reject.flush(association(8));
  });

  it('sends the note with clarify and dispute', () => {
    service.clarify(7, 'Which branch?').subscribe();
    service.dispute(9, 'Wrong café').subscribe();

    expect(http.expectOne(`${base}/api/v1/associations/7/clarify`).request.body).toEqual({ note: 'Which branch?' });
    expect(http.expectOne(`${base}/api/v1/associations/9/dispute`).request.body).toEqual({ note: 'Wrong café' });
  });

  it('reads the feed summary into a map keyed by post id', () => {
    let result: Map<number, PostAssociationSummary> | undefined;
    service.summary([1, 2, 2]).subscribe(map => (result = map));

    const req = http.expectOne(r => r.url === `${base}/api/v1/associations/summary`);
    expect(req.request.params.get('postIds')).toBe('1,2');
    req.flush({
      '1': { confirmed: { associationId: 7, locationId: 14, score: 100 }, openProposals: 0 },
      '2': { confirmed: null, openProposals: 2 },
    });

    expect(result?.get(1)?.confirmed?.locationId).toBe(14);
    expect(result?.get(2)?.openProposals).toBe(2);
  });

  it('splits the summary in calls of at most 100 posts', () => {
    const ids = Array.from({ length: SUMMARY_BATCH + 5 }, (_, i) => i + 1);
    let result: Map<number, PostAssociationSummary> | undefined;
    service.summary(ids).subscribe(map => (result = map));

    const calls = http.match(r => r.url === `${base}/api/v1/associations/summary`);
    expect(calls.length).toBe(2);
    expect(calls[0].request.params.get('postIds')!.split(',').length).toBe(SUMMARY_BATCH);
    expect(calls[1].request.params.get('postIds')).toBe('101,102,103,104,105');
    calls[0].flush({ '1': { confirmed: null, openProposals: 1 } });
    calls[1].flush({ '105': { confirmed: null, openProposals: 0 } });

    expect(result?.size).toBe(2);
  });

  it('does not call the server for an empty summary', () => {
    let result: Map<number, PostAssociationSummary> | undefined;
    service.summary([]).subscribe(map => (result = map));

    http.expectNone(`${base}/api/v1/associations/summary`);
    expect(result?.size).toBe(0);
  });
});

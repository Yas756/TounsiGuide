import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AdminBusinessPage } from './admin-business.page';
import { BusinessApiService } from '../../core/business-api.service';
import { PageRequest } from '../../core/business.model';

describe('AdminBusinessPage', () => {
  const request = (id: number, patch: Partial<PageRequest> = {}): PageRequest => ({
    id, locationId: 14, locationName: 'Café des Délices', locationCity: 'Tunis', requesterId: 'u1',
    requesterName: 'Sarra', proposedName: 'Café officiel', evidence: 'Registre de commerce n° 123', contact: '+216 71',
    status: 'PENDING', createdAt: '2026-11-15T10:00:00Z', decidedBy: null, decidedByName: null, decidedAt: null,
    decisionReason: null, pageId: null, ...patch,
  });

  function render(api: Partial<Record<keyof BusinessApiService, unknown>>) {
    const items = [request(1), request(2)];
    TestBed.configureTestingModule({
      imports: [AdminBusinessPage],
      providers: [
        provideRouter([]),
        {
          provide: BusinessApiService,
          useValue: { requests: () => of({ items, page: 0, size: 20, totalElements: 2, totalPages: 1 }), ...api },
        },
      ],
    });
    const fixture = TestBed.createComponent(AdminBusinessPage);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the requester, the Location and the evidence of each pending request', () => {
    const el = render({}).nativeElement as HTMLElement;

    const first = el.querySelector('.admin-business-request')!;
    expect(first.textContent).toContain('Sarra');
    expect(first.textContent).toContain('Café des Délices · Tunis');
    expect(first.textContent).toContain('Registre de commerce n° 123');
  });

  it('refuses to reject without a reason', () => {
    const reject = jasmine.createSpy('reject');
    const fixture = render({ reject });
    const page = fixture.componentInstance;

    page.startReject(request(1));
    page.reject(request(1));
    fixture.detectChanges();

    expect(reject).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Le motif du refus est obligatoire.');
  });

  it('removes an approved request from the pending list', () => {
    const fixture = render({ approve: () => of(request(1, { status: 'APPROVED', pageId: 30 })) });

    fixture.componentInstance.approve(request(1));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.admin-business-request').length).toBe(1);
    expect(el.textContent).toContain('Demande approuvée');
  });

  it('explains that nothing was saved when the Identity Service is down', () => {
    const down = new HttpErrorResponse({
      status: 503,
      error: { status: 503, code: 'IDENTITY_SERVICE_UNAVAILABLE', message: 'Le service des comptes est temporairement indisponible', path: '', timestamp: '' },
    });
    const fixture = render({ approve: () => throwError(() => down) });

    fixture.componentInstance.approve(request(1));
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('.banner-error')!.textContent!;
    expect(banner).toContain('Le service des comptes');
    expect(banner).toContain('la demande reste en attente');
  });
});

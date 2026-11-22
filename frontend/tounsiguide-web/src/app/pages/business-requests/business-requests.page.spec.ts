import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BusinessRequestsPage, EMPTY_PAGE_REQUEST_FORM, validatePageRequestForm } from './business-requests.page';
import { BusinessApiService } from '../../core/business-api.service';
import { AuthService } from '../../core/auth.service';
import { LocationApiService } from '../../core/location-api.service';
import { PageRequest } from '../../core/business.model';
import { ApiLocation } from '../../core/api-location.model';

describe('BusinessRequestsPage', () => {
  const location: ApiLocation = {
    id: 14, name: 'Café des Délices', city: 'Sidi Bou Saïd', address: null, latitude: null, longitude: null,
    category: 'cafe', source: 'DIRECTORY', aliases: [], createdAt: '',
  };
  const request = (id: number, patch: Partial<PageRequest>): PageRequest => ({
    id, locationId: 14, locationName: 'Café des Délices', locationCity: 'Sidi Bou Saïd', requesterId: 'u1',
    requesterName: 'Sarra', proposedName: null, evidence: 'Registre', contact: null, status: 'PENDING',
    createdAt: '2026-11-15T10:00:00Z', decidedBy: null, decidedByName: null, decidedAt: null, decisionReason: null,
    pageId: null, ...patch,
  });

  function render(items: PageRequest[], roles: string[] = ['MEMBER'], submit = jasmine.createSpy('submitRequest')) {
    TestBed.configureTestingModule({
      imports: [BusinessRequestsPage],
      providers: [
        provideRouter([]),
        {
          provide: BusinessApiService,
          useValue: {
            myRequests: () => of({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }),
            submitRequest: submit,
          },
        },
        { provide: AuthService, useValue: { hasRole: (r: string) => roles.includes(r), isLoggedIn: () => true } },
        { provide: LocationApiService, useValue: {} },
      ],
    });
    const fixture = TestBed.createComponent(BusinessRequestsPage);
    fixture.detectChanges();
    return fixture;
  }

  it('needs a Location and evidence of at most 2000 characters', () => {
    expect(Object.keys(validatePageRequestForm(EMPTY_PAGE_REQUEST_FORM)).sort()).toEqual(['evidence', 'locationId']);
    expect(validatePageRequestForm({ ...EMPTY_PAGE_REQUEST_FORM, location, evidence: 'a'.repeat(2001) })['evidence']).toContain('2000');
    expect(validatePageRequestForm({ ...EMPTY_PAGE_REQUEST_FORM, location, evidence: 'Registre' })).toEqual({});
  });

  it('does not send an invalid form', () => {
    const submit = jasmine.createSpy('submitRequest');
    const fixture = render([], ['MEMBER'], submit);

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(submit).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.field-error').length).toBe(2);
  });

  it('sends the chosen Location with trimmed text', () => {
    const submit = jasmine.createSpy('submitRequest').and.returnValue(of(request(9, {})));
    const fixture = render([], ['MEMBER'], submit);
    const page = fixture.componentInstance;

    page.setLocation(location);
    page.set('evidence', '  Registre de commerce ');
    page.set('contact', '   ');
    page.submit();

    expect(submit).toHaveBeenCalledWith({ locationId: 14, proposedName: null, evidence: 'Registre de commerce', contact: null });
  });

  it('tells an approved requester to log in again until the role reaches the session, and shows refusal reasons', () => {
    const el = render([
      request(1, { status: 'APPROVED', pageId: 30, decidedAt: '2026-11-15T11:00:00Z' }),
      request(2, { status: 'REJECTED', decisionReason: 'Preuve insuffisante' }),
    ]).nativeElement as HTMLElement;

    expect(el.querySelector('.relogin-hint')?.textContent).toContain('Reconnectez-vous pour gérer votre page');
    expect(el.textContent).toContain('Motif du refus : Preuve insuffisante');
    expect(el.querySelector('a[href="/pages/30"]')).not.toBeNull();
  });

  it('drops the re-login hint once the session has BUSINESS_REP', () => {
    const el = render([request(1, { status: 'APPROVED', pageId: 30 })], ['MEMBER', 'BUSINESS_REP']).nativeElement as HTMLElement;

    expect(el.querySelector('.relogin-hint')).toBeNull();
  });
});

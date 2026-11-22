import { businessErrorMessage, requestPlaceLabel, toUpdateRequest, UpdatePageRequest, validatePageForm } from './business.model';
import { UiError } from './api-error.model';

describe('business model helpers', () => {
  const form = (patch: Partial<UpdatePageRequest> = {}): UpdatePageRequest => ({
    name: 'Café', description: '', openingHours: '', phone: '', email: '', website: '', address: '', ...patch,
  });
  const error = (status: number, code: string, message = 'Message du service'): UiError => ({ status, code, message, details: {} });

  it('requires a name and checks phone, e-mail and website like the backend', () => {
    expect(validatePageForm(form())).toEqual({});
    const errors = validatePageForm(form({ name: ' ', phone: 'abc', email: 'nope', website: 'cafe.tn' }));
    expect(Object.keys(errors).sort()).toEqual(['email', 'name', 'phone', 'website']);
    expect(validatePageForm(form({ description: 'a'.repeat(2001) }))['description']).toContain('2000');
  });

  it('trims the fields and sends blanks as null', () => {
    expect(toUpdateRequest(form({ name: ' Café ', description: '  ', website: ' https://cafe.tn ' }))).toEqual({
      name: 'Café', description: null, openingHours: null, phone: null, email: null, website: 'https://cafe.tn', address: null,
    });
  });

  it('keeps the French messages of the service and explains when the service itself is down', () => {
    expect(businessErrorMessage(error(409, 'CONFLICT', 'Ce lieu a déjà une page professionnelle vérifiée'))).toContain('déjà une page');
    expect(businessErrorMessage(error(503, 'IDENTITY_SERVICE_UNAVAILABLE', 'Le service des comptes est indisponible'))).toContain('comptes');
    expect(businessErrorMessage(error(503, 'SERVICE_UNAVAILABLE', 'This part of TounsiGuide is unavailable'))).toContain('pages professionnelles');
    expect(businessErrorMessage(error(401, 'UNAUTHORIZED'))).toContain('Reconnectez-vous');
    expect(businessErrorMessage(error(404, 'HTTP_404'))).toContain('Gateway');
    expect(businessErrorMessage(error(404, 'RESOURCE_NOT_FOUND', 'Page introuvable'))).toBe('Page introuvable');
  });

  it('labels a request place, or its number when the Location Service did not answer', () => {
    expect(requestPlaceLabel({ locationId: 14, locationName: 'Café', locationCity: 'Tunis' })).toBe('Café · Tunis');
    expect(requestPlaceLabel({ locationId: 14, locationName: null, locationCity: null })).toBe('Lieu n°14');
  });
});

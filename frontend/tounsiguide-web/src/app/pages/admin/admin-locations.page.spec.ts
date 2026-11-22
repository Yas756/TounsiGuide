import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import {
  AdminLocationsPage,
  createLocationErrorMessage,
  EMPTY_LOCATION_FORM,
  splitAliases,
  toCreateRequest,
  validateLocationForm,
} from './admin-locations.page';
import { LocationApiService } from '../../core/location-api.service';
import { ApiLocation, CreateLocationRequest } from '../../core/api-location.model';

const location = (id: number, name: string): ApiLocation => ({
  id,
  name,
  city: 'Tunis',
  address: null,
  latitude: null,
  longitude: null,
  category: 'museum',
  source: 'DIRECTORY',
  aliases: [],
  createdAt: '2026-11-15T10:00:00Z',
});

describe('admin locations form helpers', () => {
  it('requires a name and both coordinates or none', () => {
    expect(validateLocationForm(EMPTY_LOCATION_FORM)['name']).toContain('obligatoire');
    expect(validateLocationForm({ ...EMPTY_LOCATION_FORM, name: 'Bardo', latitude: '36.8' })['coordinates']).toBeDefined();
    expect(validateLocationForm({ ...EMPTY_LOCATION_FORM, name: 'Bardo', latitude: '95', longitude: '10' })['latitude'])
      .toBe('Entre -90 et 90.');
    expect(validateLocationForm({ ...EMPTY_LOCATION_FORM, name: 'Bardo', latitude: '36,8095', longitude: '10.1347' }))
      .toEqual({});
  });

  it('splits aliases, drops blanks and duplicates', () => {
    expect(splitAliases(' Bardo, , Musée du Bardo, bardo ')).toEqual(['Bardo', 'Musée du Bardo']);
  });

  it('builds the request the API expects', () => {
    expect(
      toCreateRequest({ ...EMPTY_LOCATION_FORM, name: ' Bardo ', city: 'Tunis', latitude: '36,8', longitude: '10.1', aliases: 'Le Bardo' }),
    ).toEqual({
      name: 'Bardo', city: 'Tunis', address: null, latitude: 36.8, longitude: 10.1, category: null, aliases: ['Le Bardo'],
    });
  });

  it('explains a duplicate (409) in French', () => {
    expect(createLocationErrorMessage({ status: 409, code: 'CONFLICT', message: 'x', details: {} })).toContain('existe déjà');
  });
});

describe('AdminLocationsPage', () => {
  let fixture: ComponentFixture<AdminLocationsPage>;
  let created: CreateLocationRequest[];
  let createResult: () => ReturnType<LocationApiService['create']>;

  beforeEach(() => {
    created = [];
    createResult = () => of(location(42, 'Musée national du Bardo'));
    TestBed.configureTestingModule({
      imports: [AdminLocationsPage],
      providers: [
        {
          provide: LocationApiService,
          useValue: {
            directory: () => of([location(1, 'Café Example')]),
            create: (body: CreateLocationRequest) => {
              created.push(body);
              return createResult();
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(AdminLocationsPage);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  it('lists the directory', () => {
    expect(el().querySelectorAll('.location-row').length).toBe(1);
    expect(el().textContent).toContain('Café Example');
  });

  it('does not send an invalid form', () => {
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(created.length).toBe(0);
    expect(el().querySelector('.field-error')?.textContent).toContain('obligatoire');
  });

  it('adds the new Location on top of the list and clears the form', () => {
    const page = fixture.componentInstance;
    page.set('name', 'Musée national du Bardo');
    page.set('city', 'Tunis');
    page.set('aliases', 'Bardo');

    page.submit();
    fixture.detectChanges();

    expect(created).toEqual([jasmine.objectContaining({ name: 'Musée national du Bardo', city: 'Tunis', aliases: ['Bardo'] })]);
    expect(el().querySelector('.location-row strong')?.textContent).toBe('Musée national du Bardo');
    expect(el().querySelector('[role=status]')?.textContent).toContain('ajouté');
    expect(page.form().name).toBe('');
  });

  it('shows the 409 duplicate nicely and keeps what was typed', () => {
    createResult = () => throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'CONFLICT', message: 'dup' } }));
    const page = fixture.componentInstance;
    page.set('name', 'Café Example');
    page.set('city', 'Tunis');

    page.submit();
    fixture.detectChanges();

    expect(el().querySelector('[role=alert]')?.textContent).toContain('existe déjà');
    expect(page.form().name).toBe('Café Example');
    expect(page.saving()).toBeFalse();
  });
});

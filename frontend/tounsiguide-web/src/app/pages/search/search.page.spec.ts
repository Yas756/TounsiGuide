import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { SearchPage } from './search.page';
import { LocationApiService } from '../../core/location-api.service';
import { BusinessApiService } from '../../core/business-api.service';
import { PostApiService } from '../../core/post-api.service';
import { ApiLocation } from '../../core/api-location.model';
import { BusinessPage } from '../../core/business.model';
import { ApiPost } from '../../core/api-post.model';

describe('SearchPage (S3-US10)', () => {
  const location: ApiLocation = { id: 1, name: 'Café des Délices', city: 'Sidi Bou Saïd', address: null, latitude: 36.87,
    longitude: 10.35, category: 'cafe', source: 'DIRECTORY', aliases: [], createdAt: '' };
  const other: ApiLocation = { ...location, id: 2, name: 'Café des Nattes' };
  const page: BusinessPage = { id: 30, locationId: 1, location: { id: 1, name: 'Café des Délices', city: 'Sidi Bou Saïd', address: null, category: 'cafe' },
    name: 'Café des Délices', description: 'Vue sur le golfe', openingHours: null, phone: null, email: null, website: null,
    address: null, status: 'ACTIVE', verifiedAt: '2026-11-15T10:00:00Z', createdAt: '', updatedAt: '', member: false, memberRole: null };
  const post: ApiPost = { id: 101, content: 'Le thé aux pignons du Café des Délices', authorId: 'a', authorName: 'Sarra Ben Ali',
    locationId: 1, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z' };
  const pageOf = <T>(items: T[], total = items.length) => ({ items, page: 0, size: 8, totalElements: total, totalPages: 1 });

  let locations: jasmine.SpyObj<LocationApiService>;
  let business: jasmine.SpyObj<BusinessApiService>;
  let posts: jasmine.SpyObj<PostApiService>;

  function render(q: string): ComponentFixture<SearchPage> {
    TestBed.configureTestingModule({
      imports: [SearchPage],
      providers: [
        provideRouter([]),
        { provide: LocationApiService, useValue: locations },
        { provide: BusinessApiService, useValue: business },
        { provide: PostApiService, useValue: posts },
      ],
    });
    const fixture = TestBed.createComponent(SearchPage);
    fixture.componentRef.setInput('q', q);
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    locations = jasmine.createSpyObj<LocationApiService>('LocationApiService', ['directory']);
    business = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['list']);
    posts = jasmine.createSpyObj<PostApiService>('PostApiService', ['search']);
    locations.directory.and.returnValue(of([location, other]));
    business.list.and.returnValue(of(pageOf([page])));
    posts.search.and.returnValue(of(pageOf([post], 12)));
  });

  it('asks the three services once and shows the results grouped by type', () => {
    const el: HTMLElement = render('  café  ').nativeElement;

    expect(locations.directory).toHaveBeenCalledOnceWith('café', 8);
    expect(business.list).toHaveBeenCalledOnceWith('café', 0, 8);
    expect(posts.search).toHaveBeenCalledOnceWith('café', 0, 8);
    expect(el.querySelector('#search-locations')?.textContent).toContain('Lieux');
    expect(el.querySelector('#search-locations')?.textContent).toContain('(2)');
    expect(el.querySelector('#search-pages')?.textContent).toContain('(1)');
    expect(el.querySelector('#search-posts')?.textContent).toContain('(12)');
    expect(el.textContent).toContain('Le thé aux pignons');
    expect(el.textContent).toContain('Les 1 plus récentes sur 12');
  });

  it('links a place to its verified page when the pages group found one', () => {
    const el: HTMLElement = render('café').nativeElement;
    const links = Array.from(el.querySelectorAll('#search-locations ~ ul a')).map(a => a.textContent?.trim());
    expect(links).toContain('✓ Voir la page officielle');
    expect(links).toContain('Voir sur la carte');
  });

  it('keeps the other groups when one service is down', () => {
    posts.search.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const el: HTMLElement = render('café').nativeElement;
    expect(el.textContent).toContain('Les publications ne répondent pas pour le moment.');
    expect(el.textContent).toContain('Café des Nattes');
  });

  it('does not search with fewer than two characters', () => {
    const el: HTMLElement = render('a').nativeElement;
    expect(posts.search).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Tapez au moins 2 caractères.');
  });

  it('says so when nothing matches', () => {
    locations.directory.and.returnValue(of([]));
    business.list.and.returnValue(of(pageOf([])));
    posts.search.and.returnValue(of(pageOf([])));
    expect(render('zzz').nativeElement.textContent).toContain('Aucun résultat');
  });
});

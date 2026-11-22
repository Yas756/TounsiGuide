import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { appendWall, BusinessPagePage } from './business-page.page';
import { BusinessApiService } from '../../core/business-api.service';
import { AuthService } from '../../core/auth.service';
import { BusinessPage, WallPost } from '../../core/business.model';

describe('BusinessPagePage', () => {
  const businessPage = (member: boolean): BusinessPage => ({
    id: 30, locationId: 14, location: { id: 14, name: 'Café des Délices', city: 'Sidi Bou Saïd', address: null, category: 'cafe' },
    name: 'Café des Délices', description: 'Vue sur le golfe', openingHours: '8h-23h', phone: '+216 71 000 000',
    email: null, website: 'https://cafe.tn', address: null, status: 'ACTIVE', verifiedAt: '2026-11-15T10:00:00Z',
    createdAt: '', updatedAt: '', member, memberRole: member ? 'OWNER' : null,
  });
  const wallPost = (id: number, hidden = false): WallPost => ({
    id, content: `Post ${id}`, authorId: 'a', authorName: 'Sarra', locationId: 14, createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z', media: [], hidden, hiddenReason: hidden ? 'Hors sujet' : null,
    hiddenAt: hidden ? '2026-11-15T12:00:00Z' : null,
  });

  let api: jasmine.SpyObj<BusinessApiService>;

  function render(member: boolean, admin = false, posts = [wallPost(9), wallPost(5, true)]): ComponentFixture<BusinessPagePage> {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['get', 'wall', 'history', 'hide', 'unhide', 'update', 'rating']);
    api.rating.and.returnValue(of({ pageId: 30, average: null, count: 0, distribution: [0, 0, 0, 0, 0], myStars: null, canRate: false }));
    api.get.and.returnValue(of(businessPage(member)));
    api.wall.and.callFake((_id, _page, _size, includeHidden) => {
      const items = includeHidden ? posts : posts.filter(p => !p.hidden);
      return of({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });
    });
    api.history.and.returnValue(of({
      items: [{ id: 1, action: 'POST_HIDDEN', pageId: 30, requestId: null, postId: 5, actorId: 'u1', actorName: 'Sarra', reason: 'Hors sujet', createdAt: '2026-11-15T12:00:00Z' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    }));
    TestBed.configureTestingModule({
      imports: [BusinessPagePage],
      providers: [
        provideRouter([]),
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { isAdmin: signal(admin), isLoggedIn: signal(true) } },
      ],
    });
    const fixture = TestBed.createComponent(BusinessPagePage);
    fixture.componentRef.setInput('id', '30');
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }

  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent ?? '';
  const buttons = (f: ComponentFixture<unknown>, label: string) =>
    Array.from((f.nativeElement as HTMLElement).querySelectorAll('button')).filter(b => b.textContent?.includes(label));

  it('shows the official information, the verified badge and one h1', () => {
    const fixture = render(false);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('h1').length).toBe(1);
    expect(el.querySelector('h1')?.textContent).toContain('Café des Délices');
    expect(text(fixture)).toContain('Page vérifiée');
    expect(text(fixture)).toContain('8h-23h');
    expect(text(fixture)).toContain('Sidi Bou Saïd');
  });

  it('gives a visitor the public wall only: no edit, hide, toggle or history', () => {
    const fixture = render(false);

    expect(api.wall).toHaveBeenCalledWith(30, 0, 20, false);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.wall-post').length).toBe(1);
    expect(buttons(fixture, 'Modifier les informations').length).toBe(0);
    expect(buttons(fixture, 'Masquer de ce mur').length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).querySelector('.wall-toggle')).toBeNull();
    expect(api.history).not.toHaveBeenCalled();
  });

  it('lets a member show hidden posts, flagged, and explains the wall only hides them from this page', () => {
    const fixture = render(true);
    expect(text(fixture)).toContain('elle reste dans le fil général');
    expect(buttons(fixture, 'Masquer de ce mur').length).toBe(1);

    fixture.componentInstance.toggleHidden();
    fixture.detectChanges();

    expect(api.wall).toHaveBeenCalledWith(30, 0, 20, true);
    const hidden = (fixture.nativeElement as HTMLElement).querySelector('.wall-post-hidden')!;
    expect(hidden.textContent).toContain('Masquée sur ce mur');
    expect(buttons(fixture, 'Afficher à nouveau').length).toBe(1);
    expect(text(fixture)).toContain('Historique du mur');
    expect(text(fixture)).toContain('Publication masquée');
  });

  it('hides a post with the reason typed, then reloads the wall and the history', () => {
    const fixture = render(true);
    api.hide.and.returnValue(of({ pageId: 30, postId: 9, hidden: true, reason: 'Pub', actorId: 'u1', actorName: 'Sarra', changedAt: '' }));
    const page = fixture.componentInstance;

    page.startHide(wallPost(9));
    page.hideReason.set('Pub');
    page.hide(wallPost(9));

    expect(api.hide).toHaveBeenCalledWith(30, 9, 'Pub');
    expect(api.wall.calls.count()).toBe(2);
    expect(api.history.calls.count()).toBe(2);
  });

  it('shows the history to an administrator who is not a member, without member actions', () => {
    const fixture = render(false, true);

    expect(text(fixture)).toContain('Historique du mur');
    expect(buttons(fixture, 'Masquer de ce mur').length).toBe(0);
  });

  it('explains a 403 when saving the official information', () => {
    const fixture = render(true);
    api.update.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403, error: { status: 403, code: 'FORBIDDEN', message: 'x', path: '', timestamp: '' } })));
    const page = fixture.componentInstance;

    page.startEdit();
    page.save();
    fixture.detectChanges();

    expect(text(fixture)).toContain('Seuls les membres de cette page peuvent modifier ses informations.');
  });

  it('appends wall pages without repeating a post', () => {
    expect(appendWall([wallPost(9), wallPost(8)], [wallPost(8), wallPost(7)]).map(p => p.id)).toEqual([9, 8, 7]);
  });
});

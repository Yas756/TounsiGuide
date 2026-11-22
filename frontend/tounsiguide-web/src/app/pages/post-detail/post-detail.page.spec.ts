import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ReportApiService } from '../../core/report-api.service';
import { ModuleApiService } from '../../core/module-api.service';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { PostDetailPage } from './post-detail.page';
import { PostApiService } from '../../core/post-api.service';
import { CommentApiService } from '../../core/comment-api.service';
import { LocationApiService } from '../../core/location-api.service';
import { AssociationApiService } from '../../core/association-api.service';
import { MediaApiService } from '../../core/media-api.service';
import { AuthService } from '../../core/auth.service';
import { ApiPost, PublicationStatus } from '../../core/api-post.model';
import { LikeApiService } from '../../core/like-api.service';

/** Soft delete (S2-B04) in the post page: a deleted post is read-only, even for its author or an admin. */
describe('PostDetailPage', () => {
  const post = (status: PublicationStatus): ApiPost => ({
    id: 7, content: 'Un café', authorId: 'admin-1', authorName: 'Admin', locationId: null, media: [],
    createdAt: '2026-11-15T10:00:00Z', updatedAt: '2026-11-15T10:00:00Z', publicationStatus: status,
  });
  const comment = {
    id: 3, postId: 7, content: 'Mon commentaire', authorId: 'admin-1', authorName: 'Admin', media: [],
    createdAt: '2026-11-15T10:05:00Z', updatedAt: '2026-11-15T10:05:00Z',
  };

  function render(status: PublicationStatus): HTMLElement {
    TestBed.configureTestingModule({
      imports: [PostDetailPage],
      providers: [
        { provide: LikeApiService, useValue: { forPosts: () => of(new Map()), forComments: () => of(new Map()) } },
        provideRouter([]),
        { provide: PostApiService, useValue: { get: () => of(post(status)) } },
        { provide: CommentApiService, useValue: { list: () => of([comment]) } },
        { provide: LocationApiService, useValue: { byIds: () => of(new Map()) } },
        { provide: AssociationApiService, useValue: { forPost: () => of([]) } },
        { provide: MediaApiService, useValue: {} },
        { provide: ReportApiService, useValue: {} },
        { provide: ModuleApiService, useValue: { approved: () => of([]) } },
        {
          provide: AuthService,
          useValue: { isLoggedIn: signal(true), isAdmin: signal(true), isOwner: (id: string) => id === 'admin-1', user: signal(null) },
        },
      ],
    });
    const fixture = TestBed.createComponent(PostDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('closes the discussion and hides edit/delete on a deleted post', () => {
    const el = render('DELETED');

    expect(el.textContent).toContain('Publication supprimée : seuls les administrateurs la voient encore.');
    expect(el.textContent).toContain('la discussion est fermée');
    expect(el.querySelector('form.comment-form')).toBeNull();
    expect(el.querySelector('.post-detail-footer .actions')).toBeNull();
    expect(el.querySelector('.comment .btn-text')).toBeNull();
  });

  it('keeps the comment form, a length counter and the author actions on a published post', () => {
    const el = render('PUBLISHED');

    expect(el.querySelector('form.comment-form')).not.toBeNull();
    expect(el.querySelector('#comment-count')?.textContent?.trim()).toBe('0 / 1000');
    expect(el.querySelector('.post-detail-footer .actions')).not.toBeNull();
  });

  it('asks a soft-delete confirmation that no longer says the deletion is final', () => {
    const el = render('PUBLISHED');
    const confirm = spyOn(window, 'confirm').and.returnValue(false);

    (el.querySelector('.post-detail-footer .btn-danger') as HTMLButtonElement).click();

    const text = confirm.calls.mostRecent().args[0] as string;
    expect(text).toContain('disparaîtra du fil');
    expect(text).not.toContain('définitive');
  });

  it('tells the author a restricted post is hidden and closes its discussion', () => {
    const el = render('RESTRICTED');

    expect(el.textContent).toContain('Publication restreinte par un modérateur');
    expect(el.textContent).toContain('Publication restreinte : la discussion est fermée.');
    expect(el.querySelector('form.comment-form')).toBeNull();
    expect(el.querySelector('app-report-button button')).toBeNull();
  });
});

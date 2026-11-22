import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminReviewPage } from './admin-review.page';
import { AssociationApiService } from '../../core/association-api.service';
import { PostApiService } from '../../core/post-api.service';
import { Association, POST_DELETED_LABEL } from '../../core/association.model';
import { Page } from '../../core/page.model';

describe('AdminReviewPage', () => {
  const association = (id: number, postDeleted?: boolean): Association => ({
    id,
    postId: id,
    location: { id: 14, name: 'Café Roma', city: 'Tunis' },
    origin: 'AUTHOR',
    status: 'DISPUTED',
    score: 63,
    forced: false,
    analysis: null,
    evidence: [],
    createdAt: '2026-11-10T10:00:00Z',
    postDeleted,
  });

  function render(items: Association[]): HTMLElement {
    const page: Page<Association> = { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
    TestBed.configureTestingModule({
      imports: [AdminReviewPage],
      providers: [
        provideRouter([]),
        { provide: AssociationApiService, useValue: { queue: () => of(page) } },
        { provide: PostApiService, useValue: {} },
      ],
    });
    const fixture = TestBed.createComponent(AdminReviewPage);
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a "Publication supprimée" badge only for associations of deleted posts', () => {
    const el = render([association(1, true), association(2, false), association(3)]);

    const items = Array.from(el.querySelectorAll('.review-item'));
    expect(items.length).toBe(3);
    expect(items[0].querySelector('.pill-deleted')?.textContent?.trim()).toBe(POST_DELETED_LABEL);
    expect(items[1].querySelector('.pill-deleted')).toBeNull();
    expect(items[2].querySelector('.pill-deleted')).toBeNull();
  });
});

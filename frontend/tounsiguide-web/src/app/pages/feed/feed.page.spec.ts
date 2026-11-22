import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { appendNew, FeedPage, FEED_PRELOAD_MARGIN } from './feed.page';
import { PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { LocationApiService } from '../../core/location-api.service';
import { AssociationApiService } from '../../core/association-api.service';
import { ApiPost } from '../../core/api-post.model';
import { Page } from '../../core/page.model';
import { LikeApiService } from '../../core/like-api.service';

/** Stands in for the browser's IntersectionObserver so a test can "scroll" the sentinel into view. */
class FakeObserver {
  static instances: FakeObserver[] = [];
  readonly observed = new Set<Element>();
  disconnected = false;

  constructor(
    readonly callback: IntersectionObserverCallback,
    readonly options?: IntersectionObserverInit,
  ) {
    FakeObserver.instances.push(this);
  }

  observe(element: Element): void {
    this.observed.add(element);
    this.disconnected = false;
  }

  unobserve(element: Element): void {
    this.observed.delete(element);
  }

  disconnect(): void {
    this.observed.clear();
    this.disconnected = true;
  }

  /** The sentinel comes within the preload margin. */
  intersect(): void {
    const entries = [...this.observed].map(target => ({ isIntersecting: true, target }) as IntersectionObserverEntry);
    this.callback(entries, this as unknown as IntersectionObserver);
  }

  static last(): FakeObserver {
    return FakeObserver.instances[FakeObserver.instances.length - 1];
  }
}

const post = (id: number): ApiPost => ({
  id,
  content: `post ${id}`,
  authorId: 'u1',
  authorName: 'Sarra',
  locationId: null,
  media: [],
  createdAt: '2026-11-15T10:00:00Z',
  updatedAt: '2026-11-15T10:00:00Z',
});

const page = (ids: number[], index: number, totalPages: number): Page<ApiPost> => ({
  items: ids.map(post),
  page: index,
  size: 2,
  totalElements: totalPages * 2,
  totalPages,
});

describe('appendNew', () => {
  it('keeps the order and skips posts already shown', () => {
    const merged = appendNew([post(5), post(4)], [post(4), post(3)]);
    expect(merged.map(p => p.id)).toEqual([5, 4, 3]);
  });

  it('returns the same array when nothing is new', () => {
    const shown = [post(1)];
    expect(appendNew(shown, [post(1)])).toBe(shown);
  });
});

describe('FeedPage infinite scroll', () => {
  let originalObserver: typeof IntersectionObserver;
  let responses: Map<number, () => Observable<Page<ApiPost>>>;
  let listCalls: number[];
  let summaryCalls: number[][];
  let fixture: ComponentFixture<FeedPage>;

  beforeEach(() => {
    originalObserver = window.IntersectionObserver;
    (window as unknown as { IntersectionObserver: unknown }).IntersectionObserver = FakeObserver;
    FakeObserver.instances = [];
    responses = new Map();
    listCalls = [];
    summaryCalls = [];

    TestBed.configureTestingModule({
      imports: [FeedPage],
      providers: [
        { provide: LikeApiService, useValue: { forPosts: () => of(new Map()), forComments: () => of(new Map()) } },
        provideRouter([]),
        { provide: AuthService, useValue: { isLoggedIn: () => false } },
        {
          provide: PostApiService,
          useValue: {
            list: (index: number) => {
              listCalls.push(index);
              return responses.get(index)!();
            },
          },
        },
        { provide: LocationApiService, useValue: { byIds: () => of(new Map()) } },
        {
          provide: AssociationApiService,
          useValue: {
            summary: (ids: number[]) => {
              summaryCalls.push([...ids]);
              return of(new Map());
            },
          },
        },
      ],
    });
  });

  afterEach(() => {
    (window as unknown as { IntersectionObserver: unknown }).IntersectionObserver = originalObserver;
  });

  function start(): HTMLElement {
    fixture = TestBed.createComponent(FeedPage);
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('links a post to its topic module', () => {
    responses.set(0, () => of({
      items: [{ ...post(1), moduleId: 3, moduleSlug: 'cafes', moduleName: 'Cafés' }, post(2)],
      page: 0, size: 2, totalElements: 2, totalPages: 1,
    }));
    const el = start();

    const chips = el.querySelectorAll('a.module-chip');
    expect(chips.length).toBe(1);
    expect(chips[0].getAttribute('href')).toBe('/modules/cafes');
    expect(chips[0].textContent?.trim()).toBe('Cafés');
    expect(el.textContent).not.toContain('Bientôt');
  });

  function scrollToEnd(): void {
    FakeObserver.last().intersect();
    fixture.detectChanges();
  }

  const cardIds = (el: HTMLElement) => Array.from(el.querySelectorAll('.post-card .post-content')).map(c => c.textContent);

  it('loads the first page and watches a sentinel 400px ahead', () => {
    responses.set(0, () => of(page([4, 3], 0, 2)));

    const el = start();

    expect(listCalls).toEqual([0]);
    expect(cardIds(el)).toEqual(['post 4', 'post 3']);
    expect(el.querySelector('.feed-sentinel')).not.toBeNull();
    expect(FakeObserver.last().options?.rootMargin).toContain(FEED_PRELOAD_MARGIN);
    expect(FakeObserver.last().observed.size).toBe(1);
  });

  it('loads the next page when the sentinel comes into view and removes duplicates', () => {
    responses.set(0, () => of(page([4, 3], 0, 2)));
    // A post was published meanwhile: page 1 starts with the last post of page 0.
    responses.set(1, () => of(page([3, 2], 1, 2)));
    const el = start();

    scrollToEnd();

    expect(listCalls).toEqual([0, 1]);
    expect(cardIds(el)).toEqual(['post 4', 'post 3', 'post 2']);
    expect(summaryCalls).toEqual([[4, 3], [2]]);
  });

  it('never asks for the same page twice while it is loading', () => {
    const pending = new Subject<Page<ApiPost>>();
    responses.set(0, () => of(page([4, 3], 0, 3)));
    responses.set(1, () => pending);
    const el = start();

    scrollToEnd();
    scrollToEnd();
    fixture.componentInstance.loadMore();

    expect(listCalls).toEqual([0, 1]);
    expect(el.querySelector('.feed-loading-more')).not.toBeNull();
    expect(el.querySelector('.feed-list')?.getAttribute('aria-busy')).toBe('true');
    pending.next(page([2, 1], 1, 3));
    pending.complete();
    fixture.detectChanges();
    expect(el.querySelector('.feed-loading-more')).toBeNull();
  });

  it('stops after the last page and says so', () => {
    responses.set(0, () => of(page([4, 3], 0, 2)));
    responses.set(1, () => of(page([2], 1, 2)));
    const el = start();

    scrollToEnd();
    fixture.componentInstance.loadMore();

    expect(listCalls).toEqual([0, 1]);
    expect(el.querySelector('.feed-sentinel')).toBeNull();
    expect(el.querySelector('.feed-end')?.textContent).toContain('Vous avez tout vu');
  });

  it('a single page shows the end marker at once', () => {
    responses.set(0, () => of(page([1], 0, 1)));

    const el = start();

    expect(el.querySelector('.feed-sentinel')).toBeNull();
    expect(el.querySelector('.feed-end')).not.toBeNull();
  });

  it('keeps the loaded posts when a page fails and retries inline', () => {
    let fail = true;
    responses.set(0, () => of(page([4, 3], 0, 2)));
    responses.set(1, () =>
      fail ? throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Unavailable' })) : of(page([2], 1, 2)),
    );
    const el = start();

    scrollToEnd();
    expect(cardIds(el)).toEqual(['post 4', 'post 3']);
    const retry = el.querySelector<HTMLButtonElement>('.feed-more-error button');
    expect(retry).not.toBeNull();
    expect(el.querySelector('.feed-sentinel')).toBeNull();

    fail = false;
    retry!.click();
    fixture.detectChanges();

    expect(listCalls).toEqual([0, 1, 1]);
    expect(cardIds(el)).toEqual(['post 4', 'post 3', 'post 2']);
    expect(el.querySelector('.feed-more-error')).toBeNull();
  });

  it('stops watching when the page is left', () => {
    responses.set(0, () => of(page([4, 3], 0, 2)));
    start();
    const observer = FakeObserver.last();

    fixture.destroy();

    expect(observer.disconnected).toBeTrue();
  });
});

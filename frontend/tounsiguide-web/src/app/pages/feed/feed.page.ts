import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FEED_PAGE_SIZE, PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { LocationApiService } from '../../core/location-api.service';
import { AssociationApiService } from '../../core/association-api.service';
import { PostAssociationSummary } from '../../core/association.model';
import { ApiPost } from '../../core/api-post.model';
import { ApiLocation } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { LikeButtonComponent } from '../../shared/like-button.component';
import { LikeApiService, LikeSummary } from '../../core/like-api.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { LocationTagComponent } from '../../shared/location-tag.component';
import { OfficialPageLinkComponent } from '../../shared/official-page-link.component';
import { MediaGalleryComponent } from '../../shared/media-gallery.component';
import { relativeTime, fullDate, wasEdited } from '../../shared/format';

/** The next page starts loading when the end of the list is this close to the viewport. */
export const FEED_PRELOAD_MARGIN = '400px';

/**
 * Appends the posts not already shown, keeping the order. A post published while the user scrolls shifts the
 * server pages by one, so the next page can repeat the last post of the previous one.
 */
export function appendNew(shown: ApiPost[], incoming: ApiPost[]): ApiPost[] {
  const ids = new Set(shown.map(p => p.id));
  const fresh: ApiPost[] = [];
  for (const post of incoming) {
    if (!ids.has(post.id)) {
      ids.add(post.id);
      fresh.push(post);
    }
  }
  return fresh.length ? [...shown, ...fresh] : shown;
}

/**
 * The general feed, with infinite scroll: page 0 first, then the next page loads by itself when a sentinel
 * placed after the last card comes near the viewport (IntersectionObserver). The server orders the posts.
 */
@Component({
  selector: 'app-feed-page',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, AvatarComponent, LocationTagComponent, MediaGalleryComponent, OfficialPageLinkComponent,
    LikeButtonComponent],
  templateUrl: './feed.page.html',
})
export class FeedPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly postApi = inject(PostApiService);
  private readonly locationApi = inject(LocationApiService);
  private readonly associationApi = inject(AssociationApiService);
  private readonly injector = inject(Injector);

  readonly posts = signal<ApiPost[]>([]);
  /** Likes of the posts shown (S3-US11); a failure only shows 0 likes. */
  readonly likeById = signal<Map<number, LikeSummary>>(new Map());
  private readonly likeApi = inject(LikeApiService);
  /** First page. */
  readonly loading = signal(true);
  readonly loadError = signal<UiError | null>(null);
  /** Next pages: the posts already shown stay when one fails. */
  readonly loadingMore = signal(false);
  readonly moreError = signal<UiError | null>(null);
  readonly hasMore = signal(false);
  /** Short message for screen readers (aria-live). */
  readonly status = signal('');
  /** Without IntersectionObserver (very old browsers), a button loads the next page instead. */
  readonly canObserve = typeof IntersectionObserver !== 'undefined';

  /** Places of the posts, loaded from the Location service by id. */
  readonly locationById = signal(new Map<number, ApiLocation>());

  /** Place status of each post (Location & Matching Service). */
  readonly summaryById = signal(new Map<number, PostAssociationSummary>());

  readonly relativeTime = relativeTime;
  readonly fullDate = fullDate;
  readonly wasEdited = wasEdited;

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');
  private observer: IntersectionObserver | null = null;
  private nextPage = 0;
  /** Bumped by a reload, so an answer to an older request is ignored. */
  private generation = 0;

  constructor() {
    // Watch the sentinel whenever it is (re)rendered; it only exists while more pages remain.
    effect(() => {
      const element = this.sentinel()?.nativeElement;
      this.observer?.disconnect();
      if (element && this.canObserve) {
        this.observer ??= new IntersectionObserver(
          entries => {
            if (entries.some(entry => entry.isIntersecting)) this.loadMore();
          },
          { rootMargin: `0px 0px ${FEED_PRELOAD_MARGIN} 0px` },
        );
        this.observer.observe(element);
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.observer?.disconnect();
      this.observer = null;
    });
  }

  ngOnInit(): void {
    this.loadPosts();
  }

  /** (Re)loads the feed from the first page. */
  loadPosts(): void {
    const generation = ++this.generation;
    this.loading.set(true);
    this.loadError.set(null);
    this.moreError.set(null);
    this.loadingMore.set(false);
    this.postApi.list(0, FEED_PAGE_SIZE).subscribe({
      next: page => {
        if (generation !== this.generation) return;
        this.posts.set(appendNew([], page.items));
        this.nextPage = page.page + 1;
        this.hasMore.set(page.items.length > 0 && page.page + 1 < page.totalPages);
        this.loading.set(false);
        this.loadDetails(page.items);
      },
      error: err => {
        if (generation !== this.generation) return;
        this.loadError.set(toUiError(err));
        this.loading.set(false);
      },
    });
  }

  /** Called by the sentinel (or the fallback button). Does nothing while a page is loading or after the last one. */
  loadMore(): void {
    if (this.loading() || this.loadingMore() || !this.hasMore() || this.moreError()) return;
    const generation = this.generation;
    this.loadingMore.set(true);
    this.postApi.list(this.nextPage, FEED_PAGE_SIZE).subscribe({
      next: page => {
        if (generation !== this.generation) return;
        const before = this.posts().length;
        this.posts.update(shown => appendNew(shown, page.items));
        const added = this.posts().slice(before);
        this.nextPage = page.page + 1;
        this.hasMore.set(page.items.length > 0 && page.page + 1 < page.totalPages);
        this.loadingMore.set(false);
        this.status.set(
          `${added.length} publication${added.length > 1 ? 's' : ''} de plus` + (this.hasMore() ? '.' : ' · fin du fil.'),
        );
        this.loadDetails(added);
        this.checkSentinelAgain();
      },
      error: err => {
        if (generation !== this.generation) return;
        this.moreError.set(toUiError(err));
        this.loadingMore.set(false);
      },
    });
  }

  /** Inline retry after a failed page. */
  retryMore(): void {
    this.moreError.set(null);
    this.loadMore();
  }

  /**
   * An IntersectionObserver only reports changes: if the sentinel is still near the viewport after a short page,
   * observing it again delivers a fresh entry, so the next page loads without waiting for a scroll.
   */
  private checkSentinelAgain(): void {
    afterNextRender(
      () => {
        const element = this.sentinel()?.nativeElement;
        if (element && this.observer) {
          this.observer.unobserve(element);
          this.observer.observe(element);
        }
      },
      { injector: this.injector },
    );
  }

  /** Places and association summaries of the posts just added (not of the whole list again). */
  private loadDetails(posts: ApiPost[]): void {
    this.loadLocations(posts.map(p => p.locationId));
    this.loadSummaries(posts);
    this.loadLikes(posts);
  }

  private loadLikes(posts: ApiPost[]): void {
    if (!posts.length) return;
    this.likeApi.forPosts(posts.map(p => p.id)).subscribe({
      next: found => this.likeById.update(known => new Map([...known, ...found])),
      error: () => {},
    });
  }

  /** A failure only hides the place names: the tag then shows "Lieu n°…". */
  private loadLocations(ids: (number | null)[]): void {
    if (!ids.some(id => id !== null)) return;
    this.locationApi.byIds(ids).subscribe({
      next: found => this.locationById.update(known => new Map([...known, ...found])),
      error: () => {},
    });
  }

  /**
   * Confirmed place and open proposals of the given posts, in one call (per 100 posts).
   * If it fails, the feed falls back to the author's place tag.
   */
  private loadSummaries(posts: ApiPost[]): void {
    if (!posts.length) return;
    this.associationApi.summary(posts.map(p => p.id)).subscribe({
      next: found => {
        this.summaryById.update(known => new Map([...known, ...found]));
        this.loadLocations([...found.values()].map(s => s.confirmed?.locationId ?? null));
      },
      error: () => {},
    });
  }

  summaryOf(postId: number): PostAssociationSummary | undefined {
    return this.summaryById().get(postId);
  }
}

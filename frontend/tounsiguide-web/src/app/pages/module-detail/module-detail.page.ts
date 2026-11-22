import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ModuleApiService } from '../../core/module-api.service';
import { FEED_PAGE_SIZE, PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { moduleErrorMessage, TopicModule } from '../../core/module.model';
import { ApiPost } from '../../core/api-post.model';
import { toUiError } from '../../core/api-error.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { MediaGalleryComponent } from '../../shared/media-gallery.component';
import { fullDate, relativeTime, wasEdited } from '../../shared/format';
import { appendNew, FEED_PRELOAD_MARGIN } from '../feed/feed.page';

/**
 * S3-F05: one approved module and its posts, newest first, with the feed's infinite scroll
 * (GET /api/v1/posts?module=slug, same paging as the feed).
 */
@Component({
  selector: 'app-module-detail-page',
  standalone: true,
  imports: [RouterLink, AvatarComponent, MediaGalleryComponent],
  template: `
    <a class="link back" routerLink="/modules">← Retour aux modules</a>

    @if (loading()) {
      <div class="card skeleton" aria-hidden="true"><div></div><div></div></div>
      <span class="sr-only">Chargement du module…</span>
    } @else if (notFound()) {
      <div class="card empty">
        <p><strong>Ce module n'existe pas ou n'est plus disponible.</strong></p>
        <a class="btn btn-primary" routerLink="/modules">Voir les modules</a>
      </div>
    } @else if (error()) {
      <div class="banner banner-error" role="alert">
        <strong>{{ error() }}</strong>
        <button type="button" class="btn btn-ghost" (click)="load()">Réessayer</button>
      </div>
    } @else { @if (module(); as m) {
      <section class="feed-hero" aria-labelledby="module-title">
        <div>
          <p class="eyebrow">Module</p>
          <h1 id="module-title"><span aria-hidden="true">{{ m.icon || '◎' }}</span> {{ m.name }}</h1>
          <p class="feed-lead muted">{{ m.description }}</p>
          <p class="muted small">{{ m.postCount }} publication{{ m.postCount > 1 ? 's' : '' }}</p>
        </div>
        @if (auth.isLoggedIn()) {
          <a class="btn btn-primary feed-publish" routerLink="/create-post" [queryParams]="{ module: m.slug }">
            <span aria-hidden="true">+</span> Publier dans ce module
          </a>
        }
      </section>

      <section class="feed-list" aria-label="Publications du module" [attr.aria-busy]="postsLoading() || loadingMore()">
        @if (postsLoading()) {
          <div class="card skeleton feed-skeleton" aria-hidden="true"><div></div><div></div><div></div></div>
        } @else if (postsError()) {
          <div class="banner banner-error" role="alert">
            <strong>{{ postsError() }}</strong>
            <button type="button" class="btn btn-ghost" (click)="loadPosts()">Réessayer</button>
          </div>
        } @else {
          @for (p of posts(); track p.id) {
            <article class="card post post-card">
              <header class="post-head">
                <app-avatar [name]="p.authorName" />
                <div class="post-author">
                  <strong>{{ p.authorName }}</strong>
                  <span class="muted small" [title]="fullDate(p.createdAt)">
                    {{ relativeTime(p.createdAt) }}@if (wasEdited(p.createdAt, p.updatedAt)) { · modifié }
                  </span>
                </div>
                <span class="post-kicker">Publication</span>
              </header>
              <p class="post-content">{{ p.content }}</p>
              <app-media-gallery [media]="p.media" />
              <footer class="post-foot">
                <span></span>
                <a class="post-discussion-link" [routerLink]="['/posts', p.id]">Voir la discussion <span aria-hidden="true">→</span></a>
              </footer>
            </article>
          } @empty {
            <div class="card empty feed-empty">
              <p><strong>Aucune publication dans ce module pour l'instant.</strong></p>
              <p class="muted">Choisissez ce module en publiant pour lancer le thème.</p>
            </div>
          }
          @if (moreError()) {
            <div class="feed-more-error" role="alert">
              <span class="muted small">Impossible de charger la suite.</span>
              <button type="button" class="btn btn-ghost btn-small" (click)="retryMore()">Réessayer</button>
            </div>
          } @else if (hasMore()) {
            <div #sentinel class="feed-sentinel" aria-hidden="true"></div>
            @if (loadingMore()) {
              <div class="feed-loading-more"><span class="feed-spinner" aria-hidden="true"></span>
                <span class="muted small">Chargement…</span></div>
            } @else if (!canObserve) {
              <button type="button" class="btn btn-ghost feed-more-button" (click)="loadMore()">Afficher plus</button>
            }
          } @else if (posts().length) {
            <p class="feed-end muted small">Vous avez tout vu</p>
          }
        }
      </section>
    } }
  `,
})
export class ModuleDetailPage {
  private readonly moduleApi = inject(ModuleApiService);
  private readonly postApi = inject(PostApiService);
  private readonly injector = inject(Injector);
  readonly auth = inject(AuthService);

  /** Route parameter :slug (withComponentInputBinding). */
  readonly slug = input.required<string>();

  readonly module = signal<TopicModule | null>(null);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);

  readonly posts = signal<ApiPost[]>([]);
  readonly postsLoading = signal(false);
  readonly postsError = signal<string | null>(null);
  readonly loadingMore = signal(false);
  readonly moreError = signal(false);
  readonly hasMore = signal(false);
  readonly canObserve = typeof IntersectionObserver !== 'undefined';

  readonly relativeTime = relativeTime;
  readonly fullDate = fullDate;
  readonly wasEdited = wasEdited;

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');
  private observer: IntersectionObserver | null = null;
  private nextPage = 0;
  private generation = 0;

  constructor() {
    effect(() => {
      this.slug();
      untracked(() => this.load());
    });
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

  load(): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.moduleApi.bySlug(this.slug()).subscribe({
      next: m => {
        this.module.set(m);
        this.loading.set(false);
        this.loadPosts();
      },
      error: err => {
        const error = toUiError(err);
        if (error.status === 404 && error.code !== 'HTTP_404') this.notFound.set(true);
        else this.error.set(moduleErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  loadPosts(): void {
    const generation = ++this.generation;
    this.postsLoading.set(true);
    this.postsError.set(null);
    this.moreError.set(false);
    this.postApi.list(0, FEED_PAGE_SIZE, this.slug()).subscribe({
      next: page => {
        if (generation !== this.generation) return;
        this.posts.set(appendNew([], page.items));
        this.nextPage = page.page + 1;
        this.hasMore.set(page.items.length > 0 && page.page + 1 < page.totalPages);
        this.postsLoading.set(false);
      },
      error: err => {
        if (generation !== this.generation) return;
        this.postsError.set(moduleErrorMessage(toUiError(err)));
        this.postsLoading.set(false);
      },
    });
  }

  loadMore(): void {
    if (this.postsLoading() || this.loadingMore() || !this.hasMore() || this.moreError()) return;
    const generation = this.generation;
    this.loadingMore.set(true);
    this.postApi.list(this.nextPage, FEED_PAGE_SIZE, this.slug()).subscribe({
      next: page => {
        if (generation !== this.generation) return;
        this.posts.update(shown => appendNew(shown, page.items));
        this.nextPage = page.page + 1;
        this.hasMore.set(page.items.length > 0 && page.page + 1 < page.totalPages);
        this.loadingMore.set(false);
        afterNextRender(() => {
          const element = this.sentinel()?.nativeElement;
          if (element && this.observer) {
            this.observer.unobserve(element);
            this.observer.observe(element);
          }
        }, { injector: this.injector });
      },
      error: () => {
        if (generation !== this.generation) return;
        this.moreError.set(true);
        this.loadingMore.set(false);
      },
    });
  }

  retryMore(): void {
    this.moreError.set(false);
    this.loadMore();
  }
}

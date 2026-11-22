import { Component, computed, DestroyRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { CommentApiService } from '../../core/comment-api.service';
import { LocationApiService } from '../../core/location-api.service';
import { AssociationApiService } from '../../core/association-api.service';
import { Association, associationErrorMessage, STATUS_LABEL } from '../../core/association.model';
import { ApiPost, COMMENT_MAX_LENGTH, PostRequest } from '../../core/api-post.model';
import { ApiComment } from '../../core/api-comment.model';
import { ApiLocation } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { PostFormComponent } from '../../shared/post-form.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { AvatarComponent } from '../../shared/avatar.component';
import { LocationTagComponent } from '../../shared/location-tag.component';
import { PlacePickerComponent } from '../../shared/place-picker.component';
import { OfficialPageLinkComponent } from '../../shared/official-page-link.component';
import { ReportButtonComponent } from '../../shared/report-button.component';
import { PostPlacesComponent } from '../../shared/post-places.component';
import { MediaGalleryComponent } from '../../shared/media-gallery.component';
import { MediaPickerComponent } from '../../shared/media-picker.component';
import { MEDIA_LIMITS } from '../../core/api-media.model';
import { CommentRequest } from '../../core/api-comment.model';
import { fullDate, relativeTime, wasEdited } from '../../shared/format';
import { LikeButtonComponent } from '../../shared/like-button.component';
import { LikeApiService, LikeSummary } from '../../core/like-api.service';

@Component({
  selector: 'app-post-detail-page',
  standalone: true,
  imports: [
    FormsModule, RouterLink, PostFormComponent, ErrorBannerComponent, AvatarComponent, LocationTagComponent,
    PlacePickerComponent, PostPlacesComponent, MediaGalleryComponent, MediaPickerComponent, OfficialPageLinkComponent,
    ReportButtonComponent, LikeButtonComponent,
  ],
  templateUrl: './post-detail.page.html',
})
export class PostDetailPage {
  readonly auth = inject(AuthService);
  private readonly postApi = inject(PostApiService);
  private readonly commentApi = inject(CommentApiService);
  private readonly locationApi = inject(LocationApiService);
  private readonly associationApi = inject(AssociationApiService);
  private readonly router = inject(Router);
  private readonly likeApi = inject(LikeApiService);

  /** Likes of the post and of its comments (S3-US11); a failure only shows 0 likes. */
  readonly postLike = signal<LikeSummary | undefined>(undefined);
  readonly commentLikes = signal<Map<number, LikeSummary>>(new Map());

  /**
   * The backend updates associations in the background after a post or comment changes
   * (author tag, place names found in the text), so the panel is refreshed again shortly after.
   */
  static readonly BACKGROUND_REFRESH_MS = 1500;
  private refreshTimer?: ReturnType<typeof setTimeout>;

  /** Route parameter :id, bound by withComponentInputBinding(). */
  readonly id = input.required<string>();
  readonly postId = computed(() => Number(this.id()));

  // Post
  readonly post = signal<ApiPost | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<UiError | null>(null);
  readonly notFound = computed(() => this.loadError()?.status === 404 || Number.isNaN(this.postId()));

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<UiError | null>(null);
  readonly deleting = signal(false);
  readonly deleteError = signal<UiError | null>(null);

  // Location of the post (Location & Matching Service)
  readonly location = signal<ApiLocation | undefined>(undefined);

  // Comments
  readonly comments = signal<ApiComment[]>([]);
  readonly commentsError = signal<UiError | null>(null);
  readonly newComment = signal('');
  readonly sendingComment = signal(false);
  readonly commentError = signal<UiError | null>(null);
  readonly editingCommentId = signal<number | null>(null);
  readonly editedComment = signal('');
  readonly commentActionError = signal<UiError | null>(null);
  readonly busyCommentId = signal<number | null>(null);
  /** Photo or video attached to the new comment. */
  readonly commentLimits = MEDIA_LIMITS.comment;
  readonly commentMax = COMMENT_MAX_LENGTH;
  private readonly commentMedia = viewChild(MediaPickerComponent);
  readonly commentMediaBlocking = computed(() => !!this.commentMedia()?.uploading() || !!this.commentMedia()?.hasErrors());

  // Places proposed for the post (Location & Matching Service)
  readonly associations = signal<Association[]>([]);
  /** Confirmed Location of the post: its official business page, if any, is linked under the places panel (S3). */
  readonly confirmedLocationId = computed(() => this.associations().find(a => a.status === 'CONFIRMED')?.location.id ?? null);
  readonly associationsLoading = signal(false);
  readonly associationsError = signal<UiError | null>(null);
  /** Place mentioned with the new comment, suggested to the author once the comment is published. */
  readonly commentPlace = signal<ApiLocation | null>(null);
  readonly suggestError = signal<string | null>(null);

  /** Soft-deleted post (only an administrator can still read it): read-only, the discussion is closed. */
  readonly isDeleted = computed(() => this.post()?.publicationStatus === 'DELETED');
  /** Restricted by a moderator: only the author and administrators get here; the discussion is closed. */
  readonly isRestricted = computed(() => this.post()?.publicationStatus === 'RESTRICTED');

  readonly isAuthor = computed(() => {
    const p = this.post();
    return !!p && this.auth.isOwner(p.authorId);
  });

  /** Places each comment points to (a member's suggestion, or a name found in the comment). */
  readonly placesByComment = computed(() => {
    const byComment = new Map<number, { name: string; status: string }[]>();
    for (const a of this.associations()) {
      for (const ev of a.evidence ?? []) {
        if (ev.commentId === null || (ev.type !== 'MEMBER_SUGGESTION' && ev.type !== 'TEXT_MENTION')) continue;
        const list = byComment.get(ev.commentId) ?? [];
        if (!list.some(x => x.name === a.location.name)) {
          list.push({ name: a.location.name, status: STATUS_LABEL[a.status] });
        }
        byComment.set(ev.commentId, list);
      }
    }
    return byComment;
  });

  readonly sortedComments = computed(() =>
    [...this.comments()].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt)),
  );

  readonly fullDate = fullDate;
  readonly relativeTime = relativeTime;
  readonly wasEdited = wasEdited;

  constructor() {
    effect(() => {
      const id = this.postId();
      untracked(() => this.load(id));
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.refreshTimer));
  }

  load(id = this.postId()): void {
    this.editing.set(false);
    this.post.set(null);
    this.comments.set([]);
    this.associations.set([]);
    this.associationsError.set(null);
    this.loadError.set(null);
    this.commentsError.set(null);
    if (Number.isNaN(id)) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    // The post is loaded first; comments are loaded separately so that a
    // comment failure never hides the post itself.
    this.postApi.get(id).subscribe({
      next: post => {
        this.post.set(post);
        this.loading.set(false);
        this.loadLocation(post);
        this.reloadComments();
        this.loadAssociations();
        this.loadLikes(post.id);
      },
      error: err => {
        this.loadError.set(toUiError(err));
        this.loading.set(false);
      },
    });
  }

  /** A failure only hides the place name and map: the tag then shows "Lieu n°…". */
  private loadLocation(post: ApiPost): void {
    this.location.set(undefined);
    if (post.locationId === null) return;
    this.locationApi.byIds([post.locationId]).subscribe({
      next: found => this.location.set(found.get(post.locationId!)),
      error: () => {},
    });
  }

  /** Loads the places proposed for the post. `quiet` keeps the current list on screen while reloading. */
  loadAssociations(quiet = false): void {
    if (!quiet) this.associationsLoading.set(true);
    this.associationsError.set(null);
    this.associationApi.forPost(this.postId()).subscribe({
      next: list => {
        this.associations.set(list);
        this.associationsLoading.set(false);
      },
      error: err => {
        if (!quiet) this.associationsError.set(toUiError(err));
        this.associationsLoading.set(false);
      },
    });
  }

  /** Reload now, and once more after the backend's background work. */
  private refreshAssociations(): void {
    this.loadAssociations(true);
    clearTimeout(this.refreshTimer);
    this.refreshTimer = setTimeout(() => this.loadAssociations(true), PostDetailPage.BACKGROUND_REFRESH_MS);
  }

  private loadLikes(postId: number): void {
    this.postLike.set(undefined);
    this.likeApi.forPosts([postId]).subscribe({ next: m => this.postLike.set(m.get(postId)), error: () => {} });
  }

  private loadCommentLikes(): void {
    this.likeApi.forComments(this.postId()).subscribe({ next: m => this.commentLikes.set(m), error: () => {} });
  }

  reloadComments(): void {
    this.commentsError.set(null);
    this.commentApi.list(this.postId()).subscribe({
      next: c => {
        this.comments.set(c);
        this.loadCommentLikes();
      },
      error: err => this.commentsError.set(toUiError(err)),
    });
  }

  // ----- Post actions -----

  save(body: PostRequest): void {
    this.saving.set(true);
    this.saveError.set(null);
    this.postApi.update(this.postId(), body).subscribe({
      next: updated => {
        this.post.set(updated);
        this.loadLocation(updated);
        this.refreshAssociations();
        this.editing.set(false);
        this.saving.set(false);
      },
      error: err => {
        this.saveError.set(toUiError(err));
        this.saving.set(false);
      },
    });
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.saveError.set(null);
  }

  deletePost(): void {
    if (!confirm('Supprimer cette publication ? Elle disparaîtra du fil et de la discussion pour tout le monde. '
      + 'Les administrateurs la conservent pour l\'historique.')) return;
    this.deleting.set(true);
    this.deleteError.set(null);
    this.postApi.delete(this.postId()).subscribe({
      // Leave the page only after the backend confirmed with 204 No Content.
      next: () => this.router.navigate(['/feed']),
      error: err => {
        this.deleteError.set(toUiError(err));
        this.deleting.set(false);
      },
    });
  }

  // ----- Comment actions -----

  addComment(): void {
    const content = this.newComment().trim();
    if (!content || content.length > this.commentMax || this.sendingComment() || this.commentMediaBlocking()) return;
    this.sendingComment.set(true);
    this.commentError.set(null);
    this.suggestError.set(null);
    const place = this.commentPlace();
    const body: CommentRequest = { content };
    const mediaIds = this.commentMedia()?.mediaIds() ?? [];
    if (mediaIds.length) body.mediaIds = mediaIds;
    this.commentApi.create(this.postId(), body).subscribe({
      next: created => {
        this.comments.update(list => [...list, created]);
        this.newComment.set('');
        this.commentMedia()?.reset();
        this.commentPlace.set(null);
        this.sendingComment.set(false);
        if (place) {
          this.suggestPlace(place, created.id, content);
        } else {
          this.refreshAssociations();
        }
      },
      error: err => {
        this.commentError.set(toUiError(err));
        this.sendingComment.set(false);
      },
    });
  }

  /** The comment is already published: a failed suggestion only shows a message under the form. */
  private suggestPlace(place: ApiLocation, commentId: number, content: string): void {
    this.associationApi
      .suggest(this.postId(), { locationId: place.id, commentId, note: content.slice(0, 500) })
      .subscribe({
        next: () => this.refreshAssociations(),
        error: err => {
          this.suggestError.set(
            `Commentaire publié, mais le lieu « ${place.name} » n'a pas pu être proposé : `
              + associationErrorMessage(toUiError(err), 'suggest'),
          );
          this.refreshAssociations();
        },
      });
  }

  startEditComment(c: ApiComment): void {
    this.editingCommentId.set(c.id);
    this.editedComment.set(c.content);
    this.commentActionError.set(null);
  }

  saveComment(c: ApiComment): void {
    const content = this.editedComment().trim();
    if (!content) return;
    this.busyCommentId.set(c.id);
    this.commentActionError.set(null);
    this.commentApi.update(this.postId(), c.id, { content }).subscribe({
      next: updated => {
        this.comments.update(list => list.map(x => (x.id === updated.id ? updated : x)));
        this.editingCommentId.set(null);
        this.busyCommentId.set(null);
        this.refreshAssociations();
      },
      error: err => {
        this.commentActionError.set(toUiError(err));
        this.busyCommentId.set(null);
      },
    });
  }

  deleteComment(c: ApiComment): void {
    if (!confirm('Supprimer ce commentaire ?')) return;
    this.busyCommentId.set(c.id);
    this.commentActionError.set(null);
    this.commentApi.delete(this.postId(), c.id).subscribe({
      next: () => {
        this.comments.update(list => list.filter(x => x.id !== c.id));
        this.busyCommentId.set(null);
      },
      error: err => {
        this.commentActionError.set(toUiError(err));
        this.busyCommentId.set(null);
      },
    });
  }
}

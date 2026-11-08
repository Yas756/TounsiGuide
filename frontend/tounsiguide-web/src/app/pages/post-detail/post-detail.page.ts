import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { CommentApiService } from '../../core/comment-api.service';
import { LocationApiService } from '../../core/location-api.service';
import { ApiPost, PostRequest } from '../../core/api-post.model';
import { ApiComment } from '../../core/api-comment.model';
import { ApiLocation } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { PostFormComponent } from '../../shared/post-form.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { AvatarComponent } from '../../shared/avatar.component';
import { LocationTagComponent } from '../../shared/location-tag.component';
import { LocationMapComponent } from '../../shared/location-map.component';
import { fullDate, relativeTime, wasEdited } from '../../shared/format';

@Component({
  selector: 'app-post-detail-page',
  standalone: true,
  imports: [
    FormsModule, RouterLink, PostFormComponent, ErrorBannerComponent, AvatarComponent, LocationTagComponent,
    LocationMapComponent,
  ],
  templateUrl: './post-detail.page.html',
})
export class PostDetailPage {
  readonly auth = inject(AuthService);
  private readonly postApi = inject(PostApiService);
  private readonly commentApi = inject(CommentApiService);
  private readonly locationApi = inject(LocationApiService);
  private readonly router = inject(Router);

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
  }

  load(id = this.postId()): void {
    this.editing.set(false);
    this.post.set(null);
    this.comments.set([]);
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

  reloadComments(): void {
    this.commentsError.set(null);
    this.commentApi.list(this.postId()).subscribe({
      next: c => this.comments.set(c),
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
    if (!confirm('Supprimer cette publication et tous ses commentaires ? Cette action est définitive.')) return;
    this.deleting.set(true);
    this.deleteError.set(null);
    this.postApi.delete(this.postId()).subscribe({
      // Leave the page only after the backend confirmed with 204 No Content.
      next: () => this.router.navigate(['/']),
      error: err => {
        this.deleteError.set(toUiError(err));
        this.deleting.set(false);
      },
    });
  }

  // ----- Comment actions -----

  addComment(): void {
    const content = this.newComment().trim();
    if (!content || this.sendingComment()) return;
    this.sendingComment.set(true);
    this.commentError.set(null);
    this.commentApi.create(this.postId(), { content }).subscribe({
      next: created => {
        this.comments.update(list => [...list, created]);
        this.newComment.set('');
        this.sendingComment.set(false);
      },
      error: err => {
        this.commentError.set(toUiError(err));
        this.sendingComment.set(false);
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

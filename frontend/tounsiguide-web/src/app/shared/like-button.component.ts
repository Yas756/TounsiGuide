import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { LikeApiService, LikeSummary } from '../core/like-api.service';

/**
 * « J'aime » on a post or a comment (S3-US11). Shows the count to everyone; a click toggles the caller's like
 * (optimistic, undone if the server refuses). A visitor is sent to the login page.
 */
@Component({
  selector: 'app-like-button',
  standalone: true,
  template: `
    <button type="button" class="like-button" [class.liked]="liked()" [class.compact]="compact()"
            [attr.aria-pressed]="liked()" [disabled]="busy()" (click)="toggle()"
            [attr.aria-label]="(liked() ? 'Ne plus aimer' : 'J’aime') + ' · ' + count() + ' j’aime'"
            [title]="auth.isLoggedIn() ? (liked() ? 'Ne plus aimer' : 'J’aime') : 'Connectez-vous pour aimer'">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20.5s-7.5-4.6-7.5-10.2A4.3 4.3 0 0112 7.6a4.3 4.3 0 017.5 2.7c0 5.6-7.5 10.2-7.5 10.2z"/></svg>
      <span class="like-count">{{ count() }}</span>
    </button>
  `,
})
export class LikeButtonComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(LikeApiService);
  private readonly router = inject(Router);

  readonly postId = input.required<number>();
  /** Set for a comment's like; absent for the post itself. */
  readonly commentId = input<number | null>(null);
  readonly summary = input<LikeSummary | undefined>(undefined);
  readonly compact = input(false);

  readonly liked = signal(false);
  readonly count = signal(0);
  readonly busy = signal(false);
  readonly targetId = computed(() => this.commentId() ?? this.postId());

  constructor() {
    effect(() => {
      const s = this.summary();
      this.liked.set(s?.likedByMe ?? false);
      this.count.set(s?.count ?? 0);
    }, { allowSignalWrites: true });
  }

  toggle(): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    const want = !this.liked();
    const before = { liked: this.liked(), count: this.count() };
    this.liked.set(want);
    this.count.update(c => Math.max(0, c + (want ? 1 : -1)));
    this.busy.set(true);
    const commentId = this.commentId();
    const call = commentId === null
      ? this.api.setPostLike(this.postId(), want)
      : this.api.setCommentLike(this.postId(), commentId, want);
    call.subscribe({
      next: s => {
        this.liked.set(s.likedByMe);
        this.count.set(s.count);
        this.busy.set(false);
      },
      error: () => {
        this.liked.set(before.liked);
        this.count.set(before.count);
        this.busy.set(false);
      },
    });
  }
}

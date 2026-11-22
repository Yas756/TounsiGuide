import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { AssociationApiService } from '../../core/association-api.service';
import { PostApiService } from '../../core/post-api.service';
import {
  Association,
  AssociationHistoryEntry,
  AssociationStatus,
  EVIDENCE_LABEL,
  displayText,
  originLabelOf,
  POST_DELETED_LABEL,
  ReviewDecision,
  STATUS_LABEL,
} from '../../core/association.model';
import { ApiPost } from '../../core/api-post.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { Page } from '../../core/page.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { relativeTime, fullDate } from '../../shared/format';

type Filter = 'ALL' | 'DISPUTED' | 'PROPOSED';
const PAGE_SIZE = 20;

interface Detail {
  association: Association;
  post: ApiPost | null;
  postError: UiError | null;
  history: AssociationHistoryEntry[];
}

/**
 * Association review queue (S2-US08, docs/api/association-api.md §6 Admin).
 * The admin sees the post, the claim, the score, the evidence and the matcher's reasoning, then decides:
 * validate, remove or force. The system never decides alone.
 */
@Component({
  selector: 'app-admin-review',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent, LowerCasePipe],
  templateUrl: './admin-review.page.html',
})
export class AdminReviewPage {
  private readonly api = inject(AssociationApiService);
  private readonly posts = inject(PostApiService);
  private readonly router = inject(Router);

  /** ?status=DISPUTED|PROPOSED, bound by withComponentInputBinding(). */
  readonly status = input<string | undefined>();
  readonly filter = computed<Filter>(() => {
    const s = this.status();
    return s === 'DISPUTED' || s === 'PROPOSED' ? s : 'ALL';
  });

  readonly page = signal<Page<Association> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<UiError | null>(null);
  readonly unavailable = computed(() => {
    const s = this.error()?.status;
    return s === 404 || s === 502 || s === 503 || s === 0;
  });

  readonly openId = signal<number | null>(null);
  readonly detail = signal<Detail | null>(null);
  readonly detailLoading = signal(false);
  readonly detailError = signal<UiError | null>(null);

  readonly reason = signal('');
  readonly deciding = signal<ReviewDecision | null>(null);
  readonly decideError = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly statusLabel = STATUS_LABEL;
  readonly originLabelOf = originLabelOf;
  readonly displayText = displayText;
  readonly postDeletedLabel = POST_DELETED_LABEL;

  constructor() {
    effect(() => {
      this.filter();
      untracked(() => this.load(0));
    });
  }

  setFilter(f: Filter): void {
    this.router.navigate([], { queryParams: { status: f === 'ALL' ? null : f }, queryParamsHandling: 'merge' });
  }

  load(pageIndex: number): void {
    const statuses: AssociationStatus[] = this.filter() === 'ALL' ? ['DISPUTED', 'PROPOSED'] : [this.filter() as AssociationStatus];
    this.loading.set(true);
    this.error.set(null);
    this.api.queue(statuses, pageIndex, PAGE_SIZE).subscribe({
      next: p => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(toUiError(err));
        this.page.set(null);
        this.loading.set(false);
      },
    });
  }

  toggle(a: Association): void {
    if (this.openId() === a.id) {
      this.openId.set(null);
      return;
    }
    this.openId.set(a.id);
    this.reason.set('');
    this.decideError.set(null);
    this.notice.set(null);
    this.loadDetail(a);
  }

  private loadDetail(a: Association): void {
    this.detail.set(null);
    this.detailError.set(null);
    this.detailLoading.set(true);
    forkJoin({
      association: this.api.get(a.id),
      history: this.api.history(a.id).pipe(catchError(() => of([] as AssociationHistoryEntry[]))),
      post: this.posts.get(a.postId).pipe(catchError(err => of(toUiError(err)))),
    }).subscribe({
      next: ({ association, history, post }) => {
        const isError = (p: ApiPost | UiError): p is UiError => 'code' in p && 'status' in p && !('content' in p);
        this.detail.set({
          association,
          history: history.length ? history : association.history ?? [],
          post: isError(post) ? null : post,
          postError: isError(post) ? post : null,
        });
        this.detailLoading.set(false);
      },
      error: err => {
        this.detailError.set(toUiError(err));
        this.detailLoading.set(false);
      },
    });
  }

  decide(decision: ReviewDecision): void {
    const d = this.detail();
    const reason = this.reason().trim();
    if (!d || !reason || this.deciding()) return;
    this.deciding.set(decision);
    this.decideError.set(null);
    this.api.review(d.association.id, { decision, reason }).subscribe({
      next: updated => {
        this.deciding.set(null);
        this.notice.set(
          decision === 'REMOVE' ? 'Association retirée.' : decision === 'FORCE' ? 'Association forcée.' : 'Association validée.',
        );
        // A decided association leaves the queue.
        this.page.update(p =>
          p && { ...p, items: p.items.filter(x => x.id !== updated?.id && x.id !== d.association.id), totalElements: Math.max(0, p.totalElements - 1) },
        );
        this.openId.set(null);
        this.detail.set(null);
      },
      error: err => {
        this.decideError.set(toUiError(err));
        this.deciding.set(null);
      },
    });
  }

  evidenceLabel(type: string): string {
    return EVIDENCE_LABEL[type] ?? type;
  }

  scoreClass(score: number): string {
    return score >= 75 ? 'score-high' : score >= 50 ? 'score-mid' : 'score-low';
  }

  ago(iso: string): string {
    return relativeTime(iso);
  }

  date(iso: string): string {
    return fullDate(iso);
  }
}

import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminUserApiService } from '../../core/admin-user-api.service';
import { AssociationApiService } from '../../core/association-api.service';
import { Association } from '../../core/association.model';
import { Page } from '../../core/page.model';

type Count = number | string | null;

/** Items read to count the open associations (the queue lists those of existing posts first). */
export const OVERVIEW_SAMPLE = 100;

/**
 * Open associations of posts that still exist. Those of deleted posts stay in the queue (flagged, to be closed)
 * but are not work "to settle first". The queue puts existing posts first, so one page of 100 is enough;
 * beyond that the counter says "100+".
 */
export function countLive(page: Page<Association>): number | string {
  const live = page.items.filter(a => !a.postDeleted).length;
  return live === page.items.length && page.totalElements > page.items.length ? `${live}+` : live;
}

/** Admin dashboard home: a few counters, each linking to the page where the work is done. */
@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="stat-grid">
      <a class="card stat" routerLink="/admin/review" [queryParams]="{ status: 'DISPUTED' }">
        <span class="stat-label">Lieux contestés</span>
        <span class="stat-value">{{ show(disputed()) }}</span>
        <span class="muted small">À trancher en priorité</span>
      </a>
      <a class="card stat" routerLink="/admin/review" [queryParams]="{ status: 'PROPOSED' }">
        <span class="stat-label">Propositions en attente</span>
        <span class="stat-value">{{ show(proposed()) }}</span>
        <span class="muted small">Lieux suggérés ou détectés</span>
      </a>
      <a class="card stat" routerLink="/admin/users">
        <span class="stat-label">Comptes</span>
        <span class="stat-value">{{ show(users()) }}</span>
        <span class="muted small">Membres et administrateurs</span>
      </a>
    </div>
    @if (disputed() === 'error' || proposed() === 'error') {
      <p class="banner banner-info small" role="status">
        La file de révision n'est pas encore disponible : le service des associations n'est pas joignable.
      </p>
    }
  `,
})
export class AdminOverviewPage implements OnInit {
  private readonly users$ = inject(AdminUserApiService);
  private readonly associations = inject(AssociationApiService);

  readonly users = signal<Count>(null);
  readonly disputed = signal<Count>(null);
  readonly proposed = signal<Count>(null);

  ngOnInit(): void {
    this.users$.list(0, 1).subscribe({ next: p => this.users.set(p.totalElements), error: () => this.users.set('error') });
    this.associations.queue(['DISPUTED'], 0, OVERVIEW_SAMPLE).subscribe({
      next: p => this.disputed.set(countLive(p)),
      error: () => this.disputed.set('error'),
    });
    this.associations.queue(['PROPOSED'], 0, OVERVIEW_SAMPLE).subscribe({
      next: p => this.proposed.set(countLive(p)),
      error: () => this.proposed.set('error'),
    });
  }

  show(c: Count): string {
    return c === null ? '…' : c === 'error' ? '—' : String(c);
  }
}

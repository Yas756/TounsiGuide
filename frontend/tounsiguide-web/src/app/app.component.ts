import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Header search box (S3-US10): one query, results grouped by type on /search. */
  search(event: Event, query: string): void {
    event.preventDefault();
    const q = query.trim();
    if (q) this.router.navigate(['/search'], { queryParams: { q } });
  }
}

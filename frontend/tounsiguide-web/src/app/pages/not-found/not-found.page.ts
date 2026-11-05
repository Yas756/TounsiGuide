import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="card empty">
      <p><strong>Cette page n'existe pas.</strong></p>
      <a class="btn btn-primary" routerLink="/">Revenir au fil général</a>
    </div>
  `,
})
export class NotFoundPage {}

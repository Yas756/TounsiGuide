import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="card empty">
      <p><strong>Cette page est réservée aux administrateurs.</strong></p>
      <p class="muted small">Si votre rôle vient de changer, déconnectez-vous puis reconnectez-vous.</p>
      <a class="btn btn-primary" routerLink="/feed">Revenir au fil général</a>
    </div>
  `,
})
export class ForbiddenPage {}

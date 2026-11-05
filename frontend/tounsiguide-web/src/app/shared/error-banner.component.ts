import { Component, input, output } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { UiError } from '../core/api-error.model';

/** Shows a backend or network error, including per-field validation details. */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [KeyValuePipe],
  template: `
    @if (error(); as e) {
      <div class="banner banner-error" role="alert">
        <div>
          <strong>{{ e.message }}</strong>
          @if (e.details | keyvalue; as fields) {
            @if (fields.length) {
              <ul>
                @for (f of fields; track f.key) {
                  <li><code>{{ f.key }}</code> : {{ f.value }}</li>
                }
              </ul>
            }
          }
        </div>
        @if (retryable()) {
          <button type="button" class="btn btn-ghost" (click)="retry.emit()">Réessayer</button>
        }
      </div>
    }
  `,
})
export class ErrorBannerComponent {
  readonly error = input<UiError | null>(null);
  readonly retryable = input(false);
  readonly retry = output<void>();
}

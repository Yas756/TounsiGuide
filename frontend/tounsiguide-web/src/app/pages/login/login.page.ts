import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { toUiError, UiError } from '../../core/api-error.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query parameters, bound by withComponentInputBinding(). */
  readonly returnUrl = input<string | undefined>();
  readonly expired = input<string | undefined>();

  readonly email = signal('');
  readonly password = signal('');
  readonly busy = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly canSubmit = computed(() => this.email().trim().length > 0 && this.password().length > 0 && !this.busy());

  submit(): void {
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.login({ email: this.email().trim(), password: this.password() }).subscribe({
      next: () => {
        // Only follow internal paths, never an external address.
        const target = this.returnUrl();
        this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/');
      },
      error: err => {
        const ui = toUiError(err);
        this.error.set(
          ui.status === 401 ? { ...ui, message: 'E-mail ou mot de passe incorrect.' } : ui,
        );
        this.busy.set(false);
      },
    });
  }
}

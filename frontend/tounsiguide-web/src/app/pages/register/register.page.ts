import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { toUiError, UiError } from '../../core/api-error.model';
import { ErrorBannerComponent } from '../../shared/error-banner.component';

const MIN_PASSWORD_LENGTH = 8;

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './register.page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string | undefined>();

  readonly displayName = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly confirm = signal('');
  readonly busy = signal(false);
  readonly error = signal<UiError | null>(null);

  readonly mismatch = computed(() => this.confirm().length > 0 && this.confirm() !== this.password());
  readonly tooShort = computed(() => this.password().length > 0 && this.password().length < MIN_PASSWORD_LENGTH);
  readonly minLength = MIN_PASSWORD_LENGTH;

  readonly canSubmit = computed(
    () =>
      this.displayName().trim().length >= 2 &&
      this.email().trim().length > 0 &&
      this.password().length >= MIN_PASSWORD_LENGTH &&
      this.confirm() === this.password() &&
      !this.busy(),
  );

  submit(): void {
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth
      .register({
        displayName: this.displayName().trim(),
        email: this.email().trim(),
        password: this.password(),
      })
      .subscribe({
        next: () => {
          const target = this.returnUrl();
          this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/');
        },
        error: err => {
          const ui = toUiError(err);
          this.error.set(
            ui.status === 409 ? { ...ui, message: 'Un compte existe déjà avec cet e-mail.' } : ui,
          );
          this.busy.set(false);
        },
      });
  }
}

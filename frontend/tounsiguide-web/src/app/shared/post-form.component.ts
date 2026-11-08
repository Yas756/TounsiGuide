import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiLocation } from '../core/api-location.model';
import { PostRequest } from '../core/api-post.model';
import { PlacePickerComponent } from './place-picker.component';

/**
 * Form used to create and edit a post. The place is optional:
 * a post can always be published without one.
 */
@Component({
  selector: 'app-post-form',
  standalone: true,
  imports: [FormsModule, PlacePickerComponent],
  templateUrl: './post-form.component.html',
})
export class PostFormComponent {
  readonly initialContent = input('');
  readonly initialLocation = input<ApiLocation | null>(null);
  readonly submitLabel = input('Publier');
  readonly busy = input(false);
  readonly showCancel = input(false);
  /** Backend validation errors keyed by field name. */
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<PostRequest>();
  readonly cancelled = output<void>();

  readonly content = signal('');
  readonly location = signal<ApiLocation | null>(null);
  readonly canSubmit = computed(() => this.content().trim().length > 0 && !this.busy());

  constructor() {
    effect(() => this.content.set(this.initialContent()), { allowSignalWrites: true });
    effect(() => this.location.set(this.initialLocation()), { allowSignalWrites: true });
  }

  submit(): void {
    if (!this.canSubmit()) return;
    this.submitted.emit({
      content: this.content().trim(),
      locationId: this.location()?.id ?? null,
    });
  }

  /** Called by the parent after the server confirmed the creation. */
  reset(): void {
    this.content.set('');
    this.location.set(null);
  }
}

import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiLocation } from '../core/api-location.model';
import { PostRequest } from '../core/api-post.model';

/**
 * Form used to create and edit a post. The location is optional:
 * a post can always be published without one.
 */
@Component({
  selector: 'app-post-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './post-form.component.html',
})
export class PostFormComponent {
  readonly locations = input<ApiLocation[]>([]);
  readonly locationsUnavailable = input(false);
  readonly initialContent = input('');
  readonly initialLocationId = input<number | null>(null);
  readonly submitLabel = input('Publier');
  readonly busy = input(false);
  readonly showCancel = input(false);
  /** Backend validation errors keyed by field name. */
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<PostRequest>();
  readonly cancelled = output<void>();

  readonly content = signal('');
  readonly locationId = signal<number | null>(null);
  readonly canSubmit = computed(() => this.content().trim().length > 0 && !this.busy());

  constructor() {
    effect(() => this.content.set(this.initialContent()), { allowSignalWrites: true });
    effect(() => this.locationId.set(this.initialLocationId()), { allowSignalWrites: true });
  }

  submit(): void {
    if (!this.canSubmit()) return;
    this.submitted.emit({ content: this.content().trim(), locationId: this.locationId() });
  }

  /** Called by the parent after the server confirmed the creation. */
  reset(): void {
    this.content.set('');
    this.locationId.set(null);
  }
}

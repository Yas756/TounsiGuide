import { Component, computed, effect, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiLocation } from '../core/api-location.model';
import { POST_MAX_LENGTH, PostRequest } from '../core/api-post.model';
import { PlacePickerComponent } from './place-picker.component';
import { MediaPickerComponent } from './media-picker.component';
import { ApiMedia, MEDIA_LIMITS } from '../core/api-media.model';
import { ModuleApiService } from '../core/module-api.service';
import { TopicModule } from '../core/module.model';

/**
 * Form used to create and edit a post. The place is optional:
 * a post can always be published without one.
 */
@Component({
  selector: 'app-post-form',
  standalone: true,
  imports: [FormsModule, PlacePickerComponent, MediaPickerComponent],
  templateUrl: './post-form.component.html',
})
export class PostFormComponent implements OnInit {
  private readonly moduleApi = inject(ModuleApiService);

  readonly initialContent = input('');
  readonly initialLocation = input<ApiLocation | null>(null);
  /** Photos and videos already attached (when editing). */
  readonly initialMedia = input<ApiMedia[] | null | undefined>([]);
  /** Topic module already chosen (when editing). */
  readonly initialModuleId = input<number | null>(null);
  /** Topic module to preselect by slug (« Publier dans ce module »). */
  readonly initialModuleSlug = input<string | null>(null);
  readonly submitLabel = input('Publier');
  readonly busy = input(false);
  readonly showCancel = input(false);
  /** Backend validation errors keyed by field name. */
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<PostRequest>();
  readonly cancelled = output<void>();

  readonly content = signal('');
  readonly location = signal<ApiLocation | null>(null);
  /** Approved modules for the select; empty when none or when the list could not be loaded. */
  readonly modules = signal<TopicModule[]>([]);
  readonly modulesFailed = signal(false);
  readonly moduleId = signal<number | null>(null);
  readonly limits = MEDIA_LIMITS.post;
  readonly maxLength = POST_MAX_LENGTH;
  readonly picker = viewChild(MediaPickerComponent);
  /** Waits for uploads to finish; a failed upload must be removed before publishing. */
  readonly mediaBlocking = computed(() => !!this.picker()?.uploading() || !!this.picker()?.hasErrors());
  readonly canSubmit = computed(() => {
    const length = this.content().trim().length;
    return length > 0 && length <= this.maxLength && !this.busy() && !this.mediaBlocking();
  });

  constructor() {
    effect(() => this.content.set(this.initialContent()), { allowSignalWrites: true });
    effect(() => this.location.set(this.initialLocation()), { allowSignalWrites: true });
    // The module of the edited post, else the one asked by slug (« Publier dans ce module ») once the list is loaded.
    effect(() => {
      const id = this.initialModuleId();
      const slug = this.initialModuleSlug();
      const list = this.modules();
      this.moduleId.set(id ?? (slug ? list.find(m => m.slug === slug)?.id ?? null : null));
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    this.moduleApi.approved().subscribe({
      next: list => this.modules.set(list),
      error: () => this.modulesFailed.set(true),
    });
  }

  /** The select works with strings; "" means no module. */
  setModule(value: string): void {
    this.moduleId.set(value ? Number(value) : null);
  }

  submit(): void {
    if (!this.canSubmit()) return;
    const picker = this.picker();
    const body: PostRequest = {
      content: this.content().trim(),
      locationId: this.location()?.id ?? null,
      moduleId: this.moduleId(),
    };
    // Only sent when the author changed the media, so an edit never drops them by accident.
    if (picker?.dirty()) body.mediaIds = picker.mediaIds();
    this.submitted.emit(body);
  }

  /** Called by the parent after the server confirmed the creation. */
  reset(): void {
    this.content.set('');
    this.location.set(null);
    this.moduleId.set(null);
    this.picker()?.reset();
  }
}

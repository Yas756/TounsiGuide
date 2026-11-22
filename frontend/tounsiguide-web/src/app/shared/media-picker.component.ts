import { HttpEventType, HttpResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, OnDestroy, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { ApiMedia, MEDIA_ACCEPT, MEDIA_LIMITS, MediaType, mediaSrc } from '../core/api-media.model';
import { MediaApiService } from '../core/media-api.service';
import { toUiError } from '../core/api-error.model';

interface PickedItem {
  key: number;
  type: MediaType;
  previewUrl: string;
  media: ApiMedia | null;
  progress: number;
  error: string | null;
  upload?: Subscription;
  objectUrl?: boolean;
}

@Component({
  selector: 'app-media-picker',
  standalone: true,
  template: `
    <div class="media-picker">
      @if (items().length) {
        <div class="picked" aria-label="Fichiers sélectionnés">
          @for (it of orderedItems(); track it.key) {
            <div class="picked-item" [class.has-error]="!!it.error" [class.picked-video]="it.type === 'VIDEO'">
              @if (it.type === 'VIDEO') {
                <video [src]="it.previewUrl" muted playsinline preload="metadata"></video>
                <span class="kind">Vidéo</span>
              } @else {
                <img [src]="it.previewUrl" alt="Aperçu" />
              }
              @if (!it.media && !it.error) {
                <span class="progress" [style.width.%]="it.progress" aria-hidden="true"></span>
              }
              @if (it.error) {
                <span class="item-error">{{ it.error }}</span>
              }
              <button type="button" class="remove" (click)="remove(it.key)" [disabled]="disabled()"
                      aria-label="Retirer ce fichier">×</button>
            </div>
          }
        </div>
      }
      <div class="picker-action-row">
        @if (canAdd()) {
          <label class="add" [class.disabled]="disabled()">
            <input type="file" [accept]="accept" [multiple]="maxItems() > 1" (change)="onFiles($event)"
                   [disabled]="disabled()" />
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 16-5-5-8 8"/></svg>
            {{ maxItems() > 1 ? 'Ajouter une photo ou une vidéo' : 'Ajouter une photo ou vidéo' }}
          </label>
        }
        @if (items().length) { <span class="picker-count muted small">{{ items().length }} / {{ maxItems() }}</span> }
      </div>
      @if (message(); as msg) {
        <span class="field-error">{{ msg }}</span>
        <span class="hint muted">Formats acceptés : JPEG, PNG, WebP, MP4 ou WebM. Limite : 10 Mo par image, 50 Mo par vidéo.</span>
      }
    </div>
  `,
  styles: [`
    .media-picker { display: grid; gap: .55rem; margin: .75rem 0; }
    .picked { display: grid; grid-template-columns: minmax(0, 1.5fr) repeat(2, minmax(70px, .7fr)); gap: 6px; max-width: 520px; }
    .picked-item { position: relative; min-width: 0; min-height: 80px; aspect-ratio: 1; overflow: hidden; border-radius: var(--radius-sm); background: var(--muted-surface); border: 1px solid var(--line); }
    .picked-item.picked-video { grid-row: span 2; aspect-ratio: 4 / 3; }
    .picked-item.has-error { border-color: var(--danger); }
    .picked-item img, .picked-item video { width: 100%; height: 100%; object-fit: cover; display: block; }
    .picked-item.picked-video video { object-fit: contain; background: #111; }
    .kind { position: absolute; left: 5px; bottom: 5px; padding: .12rem .3rem; border-radius: 4px; background: rgba(0, 0, 0, .65); color: #fff; font-size: .68rem; }
    .progress { position: absolute; left: 0; bottom: 0; height: 4px; background: var(--brand); transition: width .2s; }
    .item-error { position: absolute; inset: 0; display: grid; place-items: center; text-align: center; padding: .3rem; font-size: .72rem; background: var(--danger-bg); color: var(--danger); }
    .remove { position: absolute; top: 5px; right: 5px; display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; border: 0; background: rgba(0, 0, 0, .7); color: #fff; font-size: 17px; line-height: 1; cursor: pointer; }
    .picker-action-row { display: flex; align-items: center; gap: .7rem; flex-wrap: wrap; }
    .add { display: inline-flex; align-items: center; gap: .45rem; width: fit-content; min-height: 44px; cursor: pointer; color: var(--primary-foreground); font-size: .9rem; font-weight: 700; padding: .55rem .85rem; border-radius: 10px; background: var(--brand); box-shadow: var(--shadow-sm); }
    .add:hover { background: var(--primary-hover); }
    .add.disabled { opacity: .5; cursor: default; }
    .add input { position: absolute; width: 1px; height: 1px; opacity: 0; }
    .add:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
    .add svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 2; }
    .hint { font-size: .78rem; }
    @media (max-width: 520px) { .picked { grid-template-columns: minmax(0, 1.4fr) repeat(2, minmax(54px, .7fr)); } }
  `],
})
export class MediaPickerComponent implements OnDestroy {
  private readonly mediaApi = inject(MediaApiService);

  readonly initialMedia = input<ApiMedia[] | null | undefined>([]);
  readonly maxItems = input<number>(MEDIA_LIMITS.post.maxItems);
  readonly maxVideos = input<number>(MEDIA_LIMITS.post.maxVideos);
  readonly disabled = input(false);

  readonly accept = MEDIA_ACCEPT;
  readonly items = signal<PickedItem[]>([]);
  readonly message = signal<string | null>(null);
  readonly dirty = signal(false);

  readonly uploading = computed(() => this.items().some(it => !it.media && !it.error));
  readonly hasErrors = computed(() => this.items().some(it => !!it.error));
  readonly mediaIds = computed(() => this.items().filter(it => it.media).map(it => it.media!.id));
  readonly canAdd = computed(() => this.items().length < this.maxItems());
  readonly orderedItems = computed(() => [...this.items()].sort((a, b) => Number(b.type === 'VIDEO') - Number(a.type === 'VIDEO')));

  private nextKey = 1;

  constructor() {
    effect(() => {
      const initial = this.initialMedia() ?? [];
      untracked(() => this.load(initial));
    });
  }

  onFiles(event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    const files = Array.from(inputEl.files ?? []);
    inputEl.value = '';
    this.message.set(null);
    for (const file of files) {
      const problem = this.check(file);
      if (problem) {
        this.message.set(problem);
        continue;
      }
      this.start(file);
    }
  }

  remove(key: number): void {
    const it = this.items().find(i => i.key === key);
    if (!it) return;
    it.upload?.unsubscribe();
    if (it.objectUrl) URL.revokeObjectURL(it.previewUrl);
    this.items.update(list => list.filter(i => i.key !== key));
    this.message.set(null);
    this.dirty.set(true);
  }

  reset(): void {
    this.load([]);
  }

  ngOnDestroy(): void {
    this.clear();
  }

  private load(media: ApiMedia[]): void {
    this.clear();
    this.items.set(media.map(m => ({
      key: this.nextKey++, type: m.type, previewUrl: mediaSrc(m), media: m, progress: 100, error: null,
    })));
    this.dirty.set(false);
    this.message.set(null);
  }

  private clear(): void {
    for (const it of this.items()) {
      it.upload?.unsubscribe();
      if (it.objectUrl) URL.revokeObjectURL(it.previewUrl);
    }
  }

  private check(file: File): string | null {
    const isImage = (MEDIA_LIMITS.imageTypes as readonly string[]).includes(file.type);
    const isVideo = (MEDIA_LIMITS.videoTypes as readonly string[]).includes(file.type);
    if (!isImage && !isVideo) return `« ${file.name} » : format non accepté (JPEG, PNG, WebP, MP4 ou WebM).`;
    if (this.items().length >= this.maxItems()) return `${this.maxItems()} fichier(s) maximum.`;
    if (isImage && file.size > MEDIA_LIMITS.maxImageBytes) return `« ${file.name} » dépasse 10 Mo.`;
    if (isVideo && file.size > MEDIA_LIMITS.maxVideoBytes) return `« ${file.name} » dépasse 50 Mo.`;
    if (isVideo && this.items().filter(i => i.type === 'VIDEO').length >= this.maxVideos()) {
      return this.maxVideos() === 1 ? 'Une seule vidéo par publication.' : `${this.maxVideos()} vidéos maximum.`;
    }
    return null;
  }

  private start(file: File): void {
    const key = this.nextKey++;
    const item: PickedItem = {
      key,
      type: file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE',
      previewUrl: URL.createObjectURL(file),
      objectUrl: true,
      media: null,
      progress: 0,
      error: null,
    };
    this.items.update(list => [...list, item]);
    this.dirty.set(true);

    item.upload = this.mediaApi.upload(file).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.patch(key, { progress: Math.round((100 * event.loaded) / event.total) });
        } else if (event instanceof HttpResponse && event.body) {
          this.patch(key, { media: event.body, progress: 100 });
        }
      },
      error: err => {
        const ui = toUiError(err);
        this.patch(key, { error: ui.message || 'Échec de l’envoi' });
      },
    });
  }

  private patch(key: number, change: Partial<PickedItem>): void {
    this.items.update(list => list.map(i => (i.key === key ? { ...i, ...change } : i)));
  }
}

import { Component, computed, input, signal } from '@angular/core';
import { ApiMedia, mediaSrc } from '../core/api-media.model';

/** Photos and videos of a post or comment. Shows nothing when the list is empty. */
@Component({
  selector: 'app-media-gallery',
  standalone: true,
  template: `
    @if (orderedItems().length) {
      <section class="media-gallery" [class.media-compact]="compact()" [attr.data-count]="orderedItems().length" aria-label="Fichiers joints">
        @if (active(); as item) {
          <div class="media-stage">
            @if (item.type === 'VIDEO') {
              <video [src]="src(item)" controls preload="metadata" playsinline></video>
            } @else {
              <img [src]="src(item)" alt="Photo jointe" loading="lazy" />
            }
            @if (orderedItems().length > 1) {
              <button type="button" class="media-nav media-nav-previous" (click)="previous()" aria-label="Fichier précédent">‹</button>
              <button type="button" class="media-nav media-nav-next" (click)="next()" aria-label="Fichier suivant">›</button>
            }
            <span class="media-counter">{{ activeIndex() + 1 }} / {{ orderedItems().length }}</span>
          </div>
          @if (orderedItems().length > 1) {
            <div class="media-rail" aria-label="Choisir un fichier">
              @for (thumb of orderedItems(); track thumb.id; let i = $index) {
                <button type="button" class="media-thumb" [class.active]="i === activeIndex()" (click)="select(i)" [attr.aria-label]="'Afficher le fichier ' + (i + 1)">
                  @if (thumb.type === 'VIDEO') {
                    <video [src]="src(thumb)" muted playsinline preload="metadata"></video>
                    <span class="thumb-type">Vidéo</span>
                  } @else {
                    <img [src]="src(thumb)" alt="Aperçu de la photo" loading="lazy" />
                  }
                </button>
              }
            </div>
          }
        }
      </section>
    }
  `,
  styles: [`
    .media-gallery { display: grid; grid-template-columns: minmax(0, 1fr) 92px; gap: 6px; margin: .85rem 0; min-width: 0; }
    .media-stage { position: relative; min-width: 0; overflow: hidden; border-radius: var(--radius-sm); background: #111; aspect-ratio: 16 / 10; }
    .media-stage img, .media-stage video { display: block; width: 100%; height: 100%; object-fit: contain; }
    .media-stage img { background: var(--muted-surface); }
    .media-rail { display: grid; align-content: start; gap: 6px; max-height: 100%; overflow-y: auto; }
    .media-thumb { position: relative; min-width: 0; padding: 0; overflow: hidden; aspect-ratio: 1; border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--muted-surface); cursor: pointer; }
    .media-thumb.active { border: 2px solid var(--brand); }
    .media-thumb img, .media-thumb video { display: block; width: 100%; height: 100%; object-fit: cover; }
    .thumb-type { position: absolute; right: 3px; bottom: 3px; padding: .08rem .25rem; border-radius: 4px; background: rgba(0, 0, 0, .7); color: #fff; font-size: .58rem; }
    .media-nav { position: absolute; top: 50%; display: grid; place-items: center; width: 34px; height: 34px; border: 0; border-radius: 50%; background: rgba(0, 0, 0, .58); color: #fff; font-size: 1.65rem; line-height: 1; transform: translateY(-50%); cursor: pointer; }
    .media-nav:hover, .media-nav:focus-visible { background: rgba(0, 0, 0, .82); }
    .media-nav-previous { left: .6rem; }
    .media-nav-next { right: .6rem; }
    .media-counter { position: absolute; right: .6rem; bottom: .6rem; padding: .18rem .4rem; border-radius: 999px; background: rgba(0, 0, 0, .62); color: #fff; font-size: .7rem; font-variant-numeric: tabular-nums; }
    .media-compact { max-width: 360px; grid-template-columns: minmax(0, 1fr) 64px; }
    .media-compact .media-stage { aspect-ratio: 4 / 3; }
    @media (max-width: 520px) {
      .media-gallery { grid-template-columns: minmax(0, 1fr) 66px; }
      .media-nav { width: 30px; height: 30px; }
    }
  `],
})
export class MediaGalleryComponent {
  readonly media = input<ApiMedia[] | null | undefined>([]);
  readonly compact = input(false);
  readonly currentIndex = signal(0);
  readonly orderedItems = computed(() => [...(this.media() ?? [])].sort((a, b) => Number(b.type === 'VIDEO') - Number(a.type === 'VIDEO')));
  readonly activeIndex = computed(() => Math.min(this.currentIndex(), Math.max(0, this.orderedItems().length - 1)));
  readonly active = computed(() => this.orderedItems()[this.activeIndex()]);
  readonly src = mediaSrc;

  select(index: number): void {
    this.currentIndex.set(index);
  }

  previous(): void {
    const count = this.orderedItems().length;
    if (count > 1) this.currentIndex.set((this.activeIndex() - 1 + count) % count);
  }

  next(): void {
    const count = this.orderedItems().length;
    if (count > 1) this.currentIndex.set((this.activeIndex() + 1) % count);
  }
}

import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PostApiService } from '../../core/post-api.service';
import { PostRequest } from '../../core/api-post.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { PostFormComponent } from '../../shared/post-form.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';

@Component({
  selector: 'app-create-post-page',
  standalone: true,
  imports: [RouterLink, PostFormComponent, ErrorBannerComponent],
  template: `
    <a class="link back" routerLink="/feed">← Retour au fil</a>
    <section class="composer-heading">
      <div>
        <p class="eyebrow">Contribution</p>
        <h1>Partager avec la communauté</h1>
        <p class="composer-lead muted">Une recommandation, une question ou une expérience peut commencer ici. Le contexte se construit ensuite avec la communauté.</p>
      </div>
      <span class="composer-status"><span aria-hidden="true"></span> Fil général</span>
    </section>

    <div class="composer-layout">
      <section class="card composer-card" aria-labelledby="composer-form-title">
        <div class="form-card-heading">
          <div>
            <span class="card-kicker">Nouvelle publication</span>
            <h2 id="composer-form-title">Qu'avez-vous envie de partager ?</h2>
          </div>
          <span class="muted small">Les champs marqués sont facultatifs sauf le contenu.</span>
        </div>
        <app-post-form
          submitLabel="Publier dans le fil"
          [initialModuleSlug]="moduleSlug"
          [busy]="publishing()"
          [fieldErrors]="publishError()?.details ?? {}"
          (submitted)="publish($event)" />
        @if (publishError(); as e) {
          @if (e.code !== 'VALIDATION_ERROR') { <app-error-banner [error]="e" /> }
        }
      </section>

      <aside class="composer-guide" aria-label="Conseils de publication">
        <section class="guide-block">
          <span class="card-kicker">Comment ça marche</span>
          <h2>Une publication en trois temps.</h2>
          <ol class="guide-steps">
            <li><strong>Écrivez librement.</strong><span class="muted">Décrivez ce que vous avez vécu ou ce que vous cherchez.</span></li>
            <li><strong>Ajoutez du contexte.</strong><span class="muted">Un lieu ou un média aide les autres à comprendre.</span></li>
            <li><strong>Échangez.</strong><span class="muted">La communauté peut commenter et proposer un lieu.</span></li>
          </ol>
        </section>
        <section class="guide-callout">
          <strong>Choisissez un module thématique.</strong>
          <p class="muted small">Facultatif : la publication rejoint toujours le fil général, et aussi le module choisi.
            <a class="link" routerLink="/modules">Voir les modules</a></p>
        </section>
      </aside>
    </div>
  `,
})
export class CreatePostPage {
  private readonly postApi = inject(PostApiService);
  private readonly router = inject(Router);
  /** ?module=slug from « Publier dans ce module ». */
  readonly moduleSlug: string | null = inject(ActivatedRoute).snapshot.queryParamMap.get('module');

  readonly publishing = signal(false);
  readonly publishError = signal<UiError | null>(null);

  publish(body: PostRequest): void {
    this.publishing.set(true);
    this.publishError.set(null);
    this.postApi.create(body).subscribe({
      next: () => {
        this.publishing.set(false);
        this.router.navigate(['/feed']);
      },
      error: err => {
        this.publishError.set(toUiError(err));
        this.publishing.set(false);
      },
    });
  }
}

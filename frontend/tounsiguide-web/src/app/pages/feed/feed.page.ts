import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { LocationApiService } from '../../core/location-api.service';
import { ApiPost, PostRequest } from '../../core/api-post.model';
import { ApiLocation } from '../../core/api-location.model';
import { toUiError, UiError } from '../../core/api-error.model';
import { PostFormComponent } from '../../shared/post-form.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { AvatarComponent } from '../../shared/avatar.component';
import { LocationTagComponent } from '../../shared/location-tag.component';
import { relativeTime, fullDate, wasEdited } from '../../shared/format';

@Component({
  selector: 'app-feed-page',
  standalone: true,
  imports: [RouterLink, PostFormComponent, ErrorBannerComponent, AvatarComponent, LocationTagComponent],
  templateUrl: './feed.page.html',
})
export class FeedPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly postApi = inject(PostApiService);
  private readonly locationApi = inject(LocationApiService);
  private readonly form = viewChild(PostFormComponent);

  readonly posts = signal<ApiPost[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<UiError | null>(null);

  /** Places of the posts, loaded from the Location service by id. */
  readonly locationById = signal(new Map<number, ApiLocation>());

  readonly publishing = signal(false);
  readonly publishError = signal<UiError | null>(null);

  readonly sortedPosts = computed(() =>
    [...this.posts()].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)),
  );

  readonly relativeTime = relativeTime;
  readonly fullDate = fullDate;
  readonly wasEdited = wasEdited;

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.postApi.list().subscribe({
      next: posts => {
        this.posts.set(posts);
        this.loading.set(false);
        this.loadLocations(posts);
      },
      error: err => {
        this.loadError.set(toUiError(err));
        this.loading.set(false);
      },
    });
  }

  /** A failure only hides the place names: the tag then shows "Lieu n°…". */
  private loadLocations(posts: ApiPost[]): void {
    this.locationApi.byIds(posts.map(p => p.locationId)).subscribe({
      next: found => this.locationById.update(known => new Map([...known, ...found])),
      error: () => {},
    });
  }

  publish(body: PostRequest): void {
    this.publishing.set(true);
    this.publishError.set(null);
    this.postApi.create(body).subscribe({
      // The UI is updated only with the post the server returned (201 Created).
      next: created => {
        this.posts.update(list => [created, ...list]);
        this.loadLocations([created]);
        this.form()?.reset();
        this.publishing.set(false);
      },
      error: err => {
        this.publishError.set(toUiError(err));
        this.publishing.set(false);
      },
    });
  }
}

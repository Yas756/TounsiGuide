import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { StarRatingComponent } from './star-rating.component';
import { BusinessApiService } from '../core/business-api.service';
import { AuthService } from '../core/auth.service';
import { RatingSummary } from '../core/business.model';

describe('StarRatingComponent (S3-US12)', () => {
  const summary = (over: Partial<RatingSummary> = {}): RatingSummary => ({
    pageId: 30, average: 4.5, count: 2, distribution: [0, 0, 0, 1, 1], myStars: null, canRate: true, ...over,
  });
  let api: jasmine.SpyObj<BusinessApiService>;

  function render(s: RatingSummary, loggedIn = true): ComponentFixture<StarRatingComponent> {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['rating', 'rate', 'removeRating']);
    api.rating.and.returnValue(of(s));
    TestBed.configureTestingModule({
      imports: [StarRatingComponent],
      providers: [provideRouter([]), { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { isLoggedIn: signal(loggedIn) } }],
    });
    const fixture = TestBed.createComponent(StarRatingComponent);
    fixture.componentRef.setInput('pageId', 30);
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }

  it('shows the average and the number of ratings', () => {
    const el: HTMLElement = render(summary()).nativeElement;
    expect(api.rating).toHaveBeenCalledWith(30);
    expect(el.querySelector('.rating-average')?.textContent).toContain('4,5');
    expect(el.textContent).toContain('2 notes');
  });

  it('rates with the chosen number of stars', () => {
    const f = render(summary());
    api.rate.and.returnValue(of(summary({ myStars: 4, count: 3, average: 4.3 })));
    (f.nativeElement.querySelector('[aria-label="4 étoiles"]') as HTMLButtonElement).click();
    f.detectChanges();
    expect(api.rate).toHaveBeenCalledWith(30, 4);
    expect(f.nativeElement.textContent).toContain('Votre note');
    expect(f.nativeElement.textContent).toContain('Retirer ma note');
  });

  it('tells page members they cannot rate their own page', () => {
    const el: HTMLElement = render(summary({ canRate: false })).nativeElement;
    expect(el.querySelector('.star-pick')).toBeNull();
    expect(el.textContent).toContain('ne peuvent pas la noter');
  });

  it('invites a visitor to log in', () => {
    const el: HTMLElement = render(summary({ canRate: false }), false).nativeElement;
    expect(el.textContent).toContain('Connectez-vous');
  });

  it('says when there is no rating yet', () => {
    const el: HTMLElement = render(summary({ average: null, count: 0, distribution: [0, 0, 0, 0, 0] })).nativeElement;
    expect(el.textContent).toContain('Pas encore de note');
  });

  it('shows the server message when rating fails', () => {
    const f = render(summary());
    api.rate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Les membres d\'une page ne peuvent pas la noter' } })));
    (f.nativeElement.querySelector('[aria-label="5 étoiles"]') as HTMLButtonElement).click();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});

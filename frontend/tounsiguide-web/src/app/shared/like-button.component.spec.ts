import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { LikeButtonComponent } from './like-button.component';
import { LikeApiService } from '../core/like-api.service';
import { AuthService } from '../core/auth.service';

describe('LikeButtonComponent (S3-US11)', () => {
  let api: jasmine.SpyObj<LikeApiService>;

  function render(loggedIn: boolean, commentId: number | null = null): ComponentFixture<LikeButtonComponent> {
    api = jasmine.createSpyObj<LikeApiService>('LikeApiService', ['setPostLike', 'setCommentLike']);
    TestBed.configureTestingModule({
      imports: [LikeButtonComponent],
      providers: [provideRouter([]), { provide: LikeApiService, useValue: api },
        { provide: AuthService, useValue: { isLoggedIn: signal(loggedIn) } }],
    });
    const fixture = TestBed.createComponent(LikeButtonComponent);
    fixture.componentRef.setInput('postId', 7);
    fixture.componentRef.setInput('commentId', commentId);
    fixture.componentRef.setInput('summary', { targetId: commentId ?? 7, count: 2, likedByMe: false });
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }
  const button = (f: ComponentFixture<LikeButtonComponent>) => f.nativeElement.querySelector('button') as HTMLButtonElement;

  it('shows the count from the summary', () => {
    expect(button(render(true)).textContent).toContain('2');
  });

  it('likes a post and shows the server count', () => {
    const f = render(true);
    api.setPostLike.and.returnValue(of({ targetId: 7, count: 3, likedByMe: true }));
    button(f).click();
    f.detectChanges();
    expect(api.setPostLike).toHaveBeenCalledWith(7, true);
    expect(button(f).getAttribute('aria-pressed')).toBe('true');
    expect(button(f).textContent).toContain('3');
  });

  it('likes a comment through the comment endpoint', () => {
    const f = render(true, 12);
    api.setCommentLike.and.returnValue(of({ targetId: 12, count: 3, likedByMe: true }));
    button(f).click();
    expect(api.setCommentLike).toHaveBeenCalledWith(7, 12, true);
  });

  it('puts the count back when the server refuses', () => {
    const f = render(true);
    api.setPostLike.and.returnValue(throwError(() => new Error('down')));
    button(f).click();
    f.detectChanges();
    expect(button(f).textContent).toContain('2');
    expect(button(f).getAttribute('aria-pressed')).toBe('false');
  });

  it('sends a visitor to the login page', () => {
    const f = render(false);
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    button(f).click();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], jasmine.any(Object));
    expect(api.setPostLike).not.toHaveBeenCalled();
  });
});

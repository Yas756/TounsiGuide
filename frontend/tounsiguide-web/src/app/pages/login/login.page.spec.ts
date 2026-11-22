import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LoginPage } from './login.page';
import { AuthService } from '../../core/auth.service';

/** S2-F01 / S2-T01: the login form. */
describe('LoginPage', () => {
  let login: jasmine.Spy;

  function create(returnUrl?: string) {
    login = jasmine.createSpy('login');
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
    });
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    fixture.detectChanges();
    return fixture;
  }

  it('cannot be sent empty', () => {
    const page = create().componentInstance;
    expect(page.canSubmit()).toBeFalse();
    page.submit();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs in and follows an internal return URL only', () => {
    const fixture = create('//evil.example');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    login.and.returnValue(of({}));
    const page = fixture.componentInstance;
    page.email.set(' sarra@example.tn ');
    page.password.set('secret-123');

    page.submit();

    expect(login).toHaveBeenCalledWith({ email: 'sarra@example.tn', password: 'secret-123' });
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows a French message on a wrong password (401)', () => {
    const fixture = create();
    login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Bad credentials' } })));
    const page = fixture.componentInstance;
    page.email.set('sarra@example.tn');
    page.password.set('wrong');

    page.submit();
    fixture.detectChanges();

    expect(page.error()?.message).toBe('E-mail ou mot de passe incorrect.');
    expect(page.busy()).toBeFalse();
  });
});

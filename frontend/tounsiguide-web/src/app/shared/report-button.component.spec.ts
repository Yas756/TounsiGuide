import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ReportButtonComponent } from './report-button.component';
import { ReportApiService } from '../core/report-api.service';
import { AuthService } from '../core/auth.service';

describe('ReportButtonComponent', () => {
  let report: jasmine.Spy;

  function render(opts: { loggedIn?: boolean; authorId?: string; answer?: unknown } = {}): ComponentFixture<ReportButtonComponent> {
    report = jasmine.createSpy('report').and.returnValue(opts.answer ?? of({}));
    TestBed.configureTestingModule({
      imports: [ReportButtonComponent],
      providers: [
        { provide: ReportApiService, useValue: { report } },
        { provide: AuthService, useValue: { isLoggedIn: signal(opts.loggedIn ?? true), isOwner: (id: string | null) => id === 'me' } },
      ],
    });
    const fixture = TestBed.createComponent(ReportButtonComponent);
    fixture.componentRef.setInput('targetType', 'POST');
    fixture.componentRef.setInput('targetId', 12);
    fixture.componentRef.setInput('authorId', opts.authorId ?? 'someone');
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;

  it('is hidden for visitors and on one’s own content', () => {
    expect(el(render({ loggedIn: false })).querySelector('button')).toBeNull();
    TestBed.resetTestingModule();
    expect(el(render({ authorId: 'me' })).querySelector('button')).toBeNull();
  });

  it('needs a reason, then sends the report and confirms', () => {
    const fixture = render();
    el(fixture).querySelector<HTMLButtonElement>('.report-trigger')!.click();
    fixture.detectChanges();

    fixture.componentInstance.send();
    fixture.detectChanges();
    expect(report).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Choisissez un motif.');

    fixture.componentInstance.reason.set('SPAM');
    fixture.componentInstance.detail.set('  Lien publicitaire ');
    fixture.componentInstance.send();
    fixture.detectChanges();

    expect(report).toHaveBeenCalledWith({ targetType: 'POST', targetId: 12, reason: 'SPAM', detail: 'Lien publicitaire' });
    expect(el(fixture).textContent).toContain("Signalement envoyé, un modérateur va l'examiner.");
  });

  it('says so when the member already reported it (409)', () => {
    const conflict = new HttpErrorResponse({ status: 409, error: { status: 409, code: 'CONFLICT', message: 'x', path: '', timestamp: '' } });
    const fixture = render({ answer: throwError(() => conflict) });
    fixture.componentInstance.open.set(true);
    fixture.componentInstance.reason.set('OTHER');

    fixture.componentInstance.send();
    fixture.detectChanges();

    expect(el(fixture).textContent).toContain('Vous avez déjà signalé ce contenu');
  });
});

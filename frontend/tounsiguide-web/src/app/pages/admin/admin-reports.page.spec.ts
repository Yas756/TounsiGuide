import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminReportsPage } from './admin-reports.page';
import { ReportApiService } from '../../core/report-api.service';
import { ModerationDecision, ReportedContent, RestrictedContent } from '../../core/report.model';

describe('AdminReportsPage', () => {
  const reported = (id: number, count: number): ReportedContent => ({
    targetType: 'POST', targetId: id, postId: id, preview: `Publication ${id}`, authorId: 'a', authorName: 'Sarra',
    contentStatus: 'PUBLISHED', openReports: count, reasons: { SPAM: count - 1, FALSE_INFO: 1 },
    firstReportedAt: '2026-11-15T10:00:00Z', lastReportedAt: '2026-11-15T12:00:00Z',
    reports: [{ id: 1, targetType: 'POST', targetId: id, postId: id, reporterName: 'Ali', reason: 'SPAM', detail: 'Lien',
      status: 'OPEN', createdAt: '2026-11-15T10:00:00Z', decidedByName: null, decidedAt: null, decisionReason: null }],
  });
  const restrictedItem: RestrictedContent = {
    targetType: 'COMMENT', targetId: 88, postId: 12, preview: 'Insulte', authorName: 'Karim',
    restrictedAt: '2026-11-15T12:00:00Z', restrictionReason: 'Insultes',
  };

  let api: jasmine.SpyObj<ReportApiService>;

  function render(): ComponentFixture<AdminReportsPage> {
    api = jasmine.createSpyObj<ReportApiService>('ReportApiService', ['queue', 'restricted', 'dismiss', 'restrict', 'restore']);
    api.queue.and.returnValue(of({ items: [reported(12, 3), reported(13, 1)], page: 0, size: 20, totalElements: 2, totalPages: 1 }));
    api.restricted.and.returnValue(of({ items: [restrictedItem], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    api.restrict.and.returnValue(of({} as ModerationDecision));
    api.dismiss.and.returnValue(of({} as ModerationDecision));
    api.restore.and.returnValue(of({} as ModerationDecision));
    TestBed.configureTestingModule({
      imports: [AdminReportsPage],
      providers: [provideRouter([]), { provide: ReportApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(AdminReportsPage);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;

  it('shows each reported content with its preview, count and reasons', () => {
    const fixture = render();
    const first = el(fixture).querySelector('.report-item')!;

    expect(first.textContent).toContain('Publication 12');
    expect(first.textContent).toContain('3 signalements');
    expect(first.textContent).toContain('Spam ou publicité · 2');
    expect(first.textContent).toContain('Information fausse · 1');
  });

  it('refuses to restrict without a reason, then restricts and removes it from the queue', () => {
    const fixture = render();
    const page = fixture.componentInstance;

    page.start(reported(12, 3), 'RESTRICT');
    page.confirm(reported(12, 3));
    fixture.detectChanges();
    expect(api.restrict).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Le motif de la restriction est obligatoire.');

    page.reason.set('Publicité');
    page.confirm(reported(12, 3));
    fixture.detectChanges();

    expect(api.restrict).toHaveBeenCalledWith('POST', 12, 'Publicité');
    expect(el(fixture).querySelectorAll('.report-item').length).toBe(1);
    expect(el(fixture).textContent).toContain('restreint');
  });

  it('dismisses with an optional reason', () => {
    const fixture = render();
    const page = fixture.componentInstance;

    page.start(reported(13, 1), 'DISMISS');
    page.confirm(reported(13, 1));
    fixture.detectChanges();

    expect(api.dismiss).toHaveBeenCalledWith('POST', 13, null);
    expect(el(fixture).textContent).toContain('le contenu reste publié');
  });

  it('lists restricted contents and restores one', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const fixture = render();
    fixture.componentInstance.setView('RESTRICTED');
    fixture.detectChanges();
    expect(el(fixture).textContent).toContain('Motif : Insultes');

    fixture.componentInstance.restore(restrictedItem);
    fixture.detectChanges();

    expect(api.restore).toHaveBeenCalledWith('COMMENT', 88, null);
    expect(el(fixture).textContent).toContain('Aucun contenu restreint.');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OfficialPageLinkComponent } from './official-page-link.component';
import { BusinessApiService } from '../core/business-api.service';

describe('OfficialPageLinkComponent', () => {
  function render(answer: ReturnType<BusinessApiService['byLocation']>): HTMLElement {
    TestBed.configureTestingModule({
      imports: [OfficialPageLinkComponent],
      providers: [provideRouter([]), { provide: BusinessApiService, useValue: { byLocation: () => answer } }],
    });
    const fixture = TestBed.createComponent(OfficialPageLinkComponent);
    fixture.componentRef.setInput('locationId', 14);
    fixture.detectChanges(); // runs the effect that asks for the page
    fixture.detectChanges(); // shows its answer
    return fixture.nativeElement as HTMLElement;
  }

  it('links to the official page when the Location has one', () => {
    const el = render(of({ id: 30, name: 'Café des Délices' } as never));

    const link = el.querySelector('a.official-page-link')!;
    expect(link.textContent).toContain('Voir la page officielle');
    expect(link.getAttribute('href')).toBe('/pages/30');
  });

  it('shows nothing when there is no page or the call fails', () => {
    expect(render(of(null)).querySelector('a')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(throwError(() => new Error('down'))).querySelector('a')).toBeNull();
  });
});

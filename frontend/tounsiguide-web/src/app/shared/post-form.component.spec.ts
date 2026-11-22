import { TestBed } from '@angular/core/testing';
import { PostFormComponent } from './post-form.component';
import { LocationApiService } from '../core/location-api.service';
import { MediaApiService } from '../core/media-api.service';
import { ModuleApiService } from '../core/module-api.service';
import { of } from 'rxjs';
import { POST_MAX_LENGTH, PostRequest } from '../core/api-post.model';

/** S2-T01 + audit item A5: the post form shows the length limit and never sends an invalid post. */
describe('PostFormComponent', () => {
  function create() {
    TestBed.configureTestingModule({
      imports: [PostFormComponent],
      providers: [
        { provide: LocationApiService, useValue: {} },
        { provide: MediaApiService, useValue: {} },
        { provide: ModuleApiService, useValue: { approved: () => of([]) } },
      ],
    });
    const fixture = TestBed.createComponent(PostFormComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the characters used out of 2,000', () => {
    const fixture = create();
    fixture.componentInstance.content.set('Bonjour');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.char-count')?.textContent?.trim()).toBe(`7 / ${POST_MAX_LENGTH}`);
    expect(el.querySelector('textarea')?.getAttribute('maxlength')).toBe(String(POST_MAX_LENGTH));
  });

  it('refuses empty or too long content and sends a trimmed post without a place', () => {
    const fixture = create();
    const form = fixture.componentInstance;
    const sent: PostRequest[] = [];
    form.submitted.subscribe(body => sent.push(body));

    form.content.set('   ');
    expect(form.canSubmit()).toBeFalse();
    form.content.set('x'.repeat(POST_MAX_LENGTH + 1));
    expect(form.canSubmit()).toBeFalse();

    form.content.set('  Un bon café  ');
    form.submit();

    expect(sent).toEqual([{ content: 'Un bon café', locationId: null, moduleId: null }]);
  });
});

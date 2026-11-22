import { TestBed } from '@angular/core/testing';
import { PostPlacesComponent } from './post-places.component';
import { AssociationApiService } from '../core/association-api.service';
import { Association, AssociationStatus } from '../core/association.model';

describe('PostPlacesComponent', () => {
  const association = (id: number, status: AssociationStatus, commentId: number | null = null): Association => ({
    id,
    postId: 1,
    location: { id, name: id === 1 ? 'Café Example' : 'Médina de Tunis', city: 'Tunis' },
    origin: id === 1 ? 'MEMBER' : 'MENTION',
    status,
    score: 72,
    forced: false,
    analysis: null,
    evidence: [{ id: id * 10, type: id === 1 ? 'MEMBER_SUGGESTION' : 'TEXT_MENTION', direction: 'SUPPORT', weight: 2,
      actorName: null, commentId, note: "Mention found: 'Medina'", createdAt: '2026-11-15T10:00:00Z' }],
    createdAt: '2026-11-15T10:00:00Z',
  });

  function render(list: Association[], postAuthorId: string | null = null): HTMLElement {
    TestBed.configureTestingModule({
      imports: [PostPlacesComponent],
      providers: [{ provide: AssociationApiService, useValue: {} }],
    });
    const fixture = TestBed.createComponent(PostPlacesComponent);
    fixture.componentRef.setInput('associations', list);
    fixture.componentRef.setInput('postAuthorId', postAuthorId);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says a moderator will review a disputed place, not the author', () => {
    const el = render([association(1, 'DISPUTED'), association(2, 'PROPOSED')]);

    expect(el.querySelector('.places-lead')?.textContent).toContain('Un modérateur va vérifier');
    expect(el.querySelector('.places-lead')?.textContent).not.toContain("en attente de l'auteur");
    expect(el.querySelector('.status-disputed')?.textContent?.trim()).toBe('Contesté');
  });

  it('shows an administrator decision instead of the score, with the score kept as evidence strength', () => {
    const validated = { ...association(1, 'CONFIRMED'), score: 65, decidedBy: 'admin-1' };
    const el = render([validated], 'author-1');

    const lead = el.querySelector('.places-lead')!.textContent!;
    expect(lead).toContain('Validé par un administrateur');
    expect(lead).not.toContain('confiance 65');
    expect(lead).toContain('Solidité des éléments : 65 / 100');
    expect(el.querySelector('.score-admin')?.getAttribute('aria-label')).toContain('solidité des éléments 65');
  });

  it('keeps the confidence score when the author confirmed, with a separator before any pill', () => {
    const byAuthor = { ...association(1, 'CONFIRMED'), score: 87, decidedBy: 'author-1' };
    const el = render([byAuthor], 'author-1');

    expect(el.querySelector('.places-lead')!.textContent).toContain('confiance 87 / 100');
    expect(el.querySelector('.score-admin')).toBeNull();

    const forced = render2({ ...association(1, 'CONFIRMED'), score: 57, forced: true, decidedBy: 'admin-1' });
    expect(forced.querySelector('.places-lead')!.textContent!.replace(/\s+/g, ' ')).toContain('Tunis · Fixé par un administrateur');
  });

  function render2(a: Association): HTMLElement {
    const fixture = TestBed.createComponent(PostPlacesComponent);
    fixture.componentRef.setInput('associations', [a]);
    fixture.componentRef.setInput('postAuthorId', 'author-1');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('still waits for the author when places are only proposed', () => {
    const el = render([association(2, 'PROPOSED')]);

    expect(el.querySelector('.places-lead')?.textContent).toContain("1 proposition en attente de l'auteur");
  });

  it('labels a mention from the post text correctly and shows older notes in French', () => {
    const el = render([association(2, 'PROPOSED', null)]);

    expect(el.textContent).toContain('Détectée dans la publication');
    expect(el.textContent).not.toContain('Détectée dans un commentaire');
    expect(el.querySelector('.ev-note')?.textContent).toBe('Lieu cité : « Medina »');
  });
});

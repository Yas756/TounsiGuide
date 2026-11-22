import { UiError } from './api-error.model';
import { adminDecision, associationErrorMessage, displayText, originLabelOf, scoreClass } from './association.model';

describe('association model helpers', () => {
  const error = (status: number, code = `HTTP_${status}`, details: Record<string, string> = {}): UiError => ({
    status,
    code,
    message: 'backend message',
    details,
  });

  it('explains 403 differently for a dispute and for author actions', () => {
    expect(associationErrorMessage(error(403, 'FORBIDDEN'), 'dispute')).toContain('votre propre publication');
    expect(associationErrorMessage(error(403, 'FORBIDDEN'), 'confirm')).toContain("Seul l'auteur");
  });

  it('explains 409 per action', () => {
    expect(associationErrorMessage(error(409, 'CONFLICT'), 'suggest')).toContain('déjà proposé');
    expect(associationErrorMessage(error(409, 'CONFLICT'), 'dispute')).toContain('déjà contesté');
    expect(associationErrorMessage(error(409, 'CONFLICT'), 'confirm')).toContain("n'est plus en attente");
  });

  it('explains an unavailable Community Service', () => {
    expect(associationErrorMessage(error(503, 'COMMUNITY_SERVICE_UNAVAILABLE'), 'confirm')).toContain('communautaire');
  });

  it('asks for a note on a validation error and otherwise keeps the backend message', () => {
    expect(associationErrorMessage(error(400, 'VALIDATION_ERROR', { note: 'note is required' }), 'dispute'))
      .toContain('explication');
    expect(associationErrorMessage(error(500), 'confirm')).toBe('backend message');
  });

  it('colours scores from the dispute threshold', () => {
    expect(scoreClass(100)).toBe('score-high');
    expect(scoreClass(75)).toBe('score-high');
    expect(scoreClass(60)).toBe('score-mid');
    expect(scoreClass(49)).toBe('score-low');
  });
});

describe('association display texts (2026-11-15)', () => {
  const mention = (commentId: number | null) => ({
    origin: 'MENTION' as const,
    evidence: [{ id: 1, type: 'TEXT_MENTION', direction: 'SUPPORT' as const, weight: 2, actorName: null, commentId,
      note: null, createdAt: '2026-11-15T10:00:00Z' }],
  });

  it('says where a mention was found', () => {
    expect(originLabelOf(mention(null))).toBe('Détectée dans la publication');
    expect(originLabelOf(mention(88))).toBe('Détectée dans un commentaire');
    expect(originLabelOf({ origin: 'MENTION', evidence: [] })).toBe('Détectée dans le texte');
    expect(originLabelOf({ origin: 'AUTHOR', evidence: [] })).toBe("Choisie par l'auteur");
  });

  it('translates the English system texts of older rows and keeps members notes as written', () => {
    expect(displayText("Mention found: 'Medina'")).toBe('Lieu cité : « Medina »');
    expect(displayText("Support for 'Café Roma' on the same post")).toBe('Soutien à « Café Roma » sur la même publication');
    expect(displayText('Score fell to 71, below the dispute threshold 75')).toBe('Score tombé à 71, sous le seuil de contestation 75');
    expect(displayText('Author changed the place')).toBe("L'auteur a changé de lieu");
    expect(displayText("C'est le Café Example")).toBe("C'est le Café Example");
    expect(displayText(null)).toBe('');
  });
});

describe('adminDecision', () => {
  const confirmed = { status: 'CONFIRMED' as const, forced: false, origin: 'MEMBER' as const, decidedBy: 'author-1' };

  it('tells an administrator decision from the author confirmation', () => {
    expect(adminDecision(confirmed, 'author-1')).toBeNull();
    expect(adminDecision({ ...confirmed, decidedBy: 'admin-1' }, 'author-1')).toBe('VALIDATED');
    expect(adminDecision({ ...confirmed, forced: true, decidedBy: 'admin-1' }, 'author-1')).toBe('FORCED');
    expect(adminDecision({ ...confirmed, origin: 'ADMIN' }, 'author-1')).toBe('FORCED');
  });

  it('says nothing for an unconfirmed place or an unknown author', () => {
    expect(adminDecision({ ...confirmed, status: 'DISPUTED', decidedBy: 'admin-1' }, 'author-1')).toBeNull();
    expect(adminDecision({ ...confirmed, decidedBy: 'admin-1' }, null)).toBeNull();
  });
});

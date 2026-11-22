import { initials } from './format';

describe('initials', () => {
  it('takes the first letter of each word and ignores punctuation', () => {
    expect(initials('Sarra (démo)')).toBe('SD');
    expect(initials('Sarra Ben Ahmed')).toBe('SB');
    expect(initials('Ali')).toBe('A');
    expect(initials('« Amira »')).toBe('A');
    expect(initials('')).toBe('?');
  });
});

import { countLive } from './admin-overview.page';
import { Association } from '../../core/association.model';
import { Page } from '../../core/page.model';

describe('admin overview counters', () => {
  const item = (id: number, postDeleted: boolean) => ({ id, postDeleted }) as Association;
  const page = (items: Association[], totalElements = items.length): Page<Association> =>
    ({ items, page: 0, size: 100, totalElements, totalPages: 1 });

  it('does not count the associations of deleted posts', () => {
    expect(countLive(page([item(1, false), item(2, true), item(3, true)]))).toBe(1);
    expect(countLive(page([]))).toBe(0);
  });

  it('says 100+ when the sample is full of existing posts', () => {
    const full = Array.from({ length: 100 }, (_, i) => item(i, false));
    expect(countLive(page(full, 140))).toBe('100+');
  });
});

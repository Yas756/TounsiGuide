import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AdminUsersPage, withAdmin } from './admin-users.page';
import { AdminUserApiService } from '../../core/admin-user-api.service';
import { AuthService } from '../../core/auth.service';

describe('AdminUsersPage', () => {
  it('keeps BUSINESS_REP when giving or taking the ADMIN role', () => {
    expect(withAdmin(['MEMBER', 'BUSINESS_REP'], true)).toEqual(['MEMBER', 'ADMIN', 'BUSINESS_REP']);
    expect(withAdmin(['MEMBER', 'ADMIN', 'BUSINESS_REP'], false)).toEqual(['MEMBER', 'BUSINESS_REP']);
    expect(withAdmin(['MEMBER'], true)).toEqual(['MEMBER', 'ADMIN']);
  });

  it('shows BUSINESS_REP as « Représentant »', () => {
    TestBed.configureTestingModule({
      imports: [AdminUsersPage],
      providers: [
        {
          provide: AdminUserApiService,
          useValue: {
            list: () => of({
              items: [{ id: 'u1', email: 'rep@example.com', displayName: 'Rep', roles: ['MEMBER', 'BUSINESS_REP'] }],
              page: 0, size: 20, totalElements: 1, totalPages: 1,
            }),
          },
        },
        { provide: AuthService, useValue: { user: signal(null) } },
      ],
    });
    const fixture = TestBed.createComponent(AdminUsersPage);
    fixture.detectChanges();

    const pills = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.user-roles .pill')).map(p => p.textContent?.trim());
    expect(pills).toEqual(['Membre', 'Représentant']);
  });
});

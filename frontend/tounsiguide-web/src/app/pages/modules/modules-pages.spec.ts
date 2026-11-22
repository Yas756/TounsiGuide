import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ModulesPage } from './modules.page';
import { ModuleDetailPage } from '../module-detail/module-detail.page';
import { SuggestModulePage } from '../suggest-module/suggest-module.page';
import { AdminModulesPage } from '../admin/admin-modules.page';
import { PostFormComponent } from '../../shared/post-form.component';
import { ModuleApiService } from '../../core/module-api.service';
import { PostApiService } from '../../core/post-api.service';
import { AuthService } from '../../core/auth.service';
import { LocationApiService } from '../../core/location-api.service';
import { MediaApiService } from '../../core/media-api.service';
import { TopicModule } from '../../core/module.model';
import { PostRequest } from '../../core/api-post.model';

const module = (id: number, patch: Partial<TopicModule> = {}): TopicModule => ({
  id, slug: id === 3 ? 'cafes' : `m-${id}`, name: id === 3 ? 'Cafés' : `Module ${id}`, description: 'Où boire un café', icon: '☕',
  status: 'APPROVED', proposedByName: 'Sarra', createdAt: '2026-11-15T10:00:00Z', decidedByName: null, decidedAt: null,
  decisionReason: null, postCount: 4, ...patch,
});

const auth = { isLoggedIn: signal(true), isAdmin: signal(false), user: signal(null), isOwner: () => false, hasRole: () => false };
const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;

describe('ModulesPage', () => {
  it('lists approved modules with their post count and links to their page', () => {
    TestBed.configureTestingModule({
      imports: [ModulesPage],
      providers: [provideRouter([]), { provide: ModuleApiService, useValue: { approved: () => of([module(3)]) } },
        { provide: AuthService, useValue: auth }],
    });
    const fixture = TestBed.createComponent(ModulesPage);
    fixture.detectChanges();

    const card = el(fixture).querySelector('a.module-card')!;
    expect(card.getAttribute('href')).toBe('/modules/cafes');
    expect(card.textContent).toContain('4 publications');
    expect(el(fixture).textContent).not.toContain('Sous développement');
  });
});

describe('ModuleDetailPage', () => {
  function render(bySlug: unknown, list = jasmine.createSpy('list')) {
    TestBed.configureTestingModule({
      imports: [ModuleDetailPage],
      providers: [provideRouter([]), { provide: ModuleApiService, useValue: { bySlug: () => bySlug } },
        { provide: PostApiService, useValue: { list } }, { provide: AuthService, useValue: auth }],
    });
    const fixture = TestBed.createComponent(ModuleDetailPage);
    fixture.componentRef.setInput('slug', 'cafes');
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }

  it('shows the module and asks the feed for its posts', () => {
    const list = jasmine.createSpy('list').and.returnValue(of({
      items: [{ id: 12, content: 'Bon café', authorId: 'a', authorName: 'Sarra', locationId: null, media: [],
        createdAt: '2026-11-15T10:00:00Z', updatedAt: '2026-11-15T10:00:00Z' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    }));
    const fixture = render(of(module(3)), list);

    expect(list).toHaveBeenCalledWith(0, 20, 'cafes');
    expect(el(fixture).querySelectorAll('h1').length).toBe(1);
    expect(el(fixture).querySelector('h1')?.textContent).toContain('Cafés');
    expect(el(fixture).querySelectorAll('.post-card').length).toBe(1);
    expect(el(fixture).querySelector('a[href="/posts/12"]')).not.toBeNull();
  });

  it('says when the module does not exist', () => {
    const missing = new HttpErrorResponse({ status: 404, error: { status: 404, code: 'RESOURCE_NOT_FOUND', message: 'Module introuvable', path: '', timestamp: '' } });
    const fixture = render(throwError(() => missing));

    expect(el(fixture).textContent).toContain("Ce module n'existe pas");
  });
});

describe('SuggestModulePage', () => {
  function render(propose: jasmine.Spy, mine: TopicModule[] = []) {
    TestBed.configureTestingModule({
      imports: [SuggestModulePage],
      providers: [provideRouter([]), { provide: ModuleApiService, useValue: { mine: () => of(mine), propose } }],
    });
    const fixture = TestBed.createComponent(SuggestModulePage);
    fixture.detectChanges();
    return fixture;
  }

  it('checks the form, sends the proposal and adds it to « Mes propositions »', () => {
    const propose = jasmine.createSpy('propose').and.returnValue(of(module(7, { status: 'PROPOSED', name: 'Plages' })));
    const fixture = render(propose);
    const page = fixture.componentInstance;

    page.submit();
    expect(propose).not.toHaveBeenCalled();

    page.name.set(' Plages ');
    page.description.set(' Les plus belles plages ');
    page.submit();
    fixture.detectChanges();

    expect(propose).toHaveBeenCalledWith({ name: 'Plages', description: 'Les plus belles plages', icon: null });
    expect(el(fixture).querySelector('.module-proposal')?.textContent).toContain('En attente');
    expect(el(fixture).textContent).toContain('Un administrateur va l');
  });

  it('explains a duplicate name (409) and shows refusal reasons', () => {
    const conflict = new HttpErrorResponse({ status: 409, error: { status: 409, code: 'CONFLICT', message: 'x', path: '', timestamp: '' } });
    const fixture = render(jasmine.createSpy('propose').and.returnValue(throwError(() => conflict)),
      [module(8, { status: 'REJECTED', decisionReason: 'Doublon de « Cafés »' })]);
    const page = fixture.componentInstance;

    page.name.set('Cafés');
    page.description.set('Où boire un café');
    page.submit();
    fixture.detectChanges();

    expect(el(fixture).textContent).toContain('Un module porte déjà ce nom');
    expect(el(fixture).textContent).toContain('Motif du refus : Doublon de « Cafés »');
  });
});

describe('AdminModulesPage', () => {
  let api: jasmine.SpyObj<ModuleApiService>;

  function render() {
    api = jasmine.createSpyObj<ModuleApiService>('ModuleApiService', ['queue', 'approve', 'reject', 'archive']);
    api.queue.and.returnValue(of({ items: [module(7, { status: 'PROPOSED' }), module(8, { status: 'PROPOSED' })], page: 0, size: 20, totalElements: 2, totalPages: 1 }));
    api.approve.and.returnValue(of(module(7)));
    api.reject.and.returnValue(of(module(8, { status: 'REJECTED' })));
    TestBed.configureTestingModule({
      imports: [AdminModulesPage],
      providers: [provideRouter([]), { provide: ModuleApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(AdminModulesPage);
    fixture.detectChanges();
    return fixture;
  }

  it('approves a proposal, which leaves the « Proposés » tab', () => {
    const fixture = render();

    fixture.componentInstance.approve(module(7, { status: 'PROPOSED' }));
    fixture.detectChanges();

    expect(api.approve).toHaveBeenCalledWith(7);
    expect(el(fixture).querySelectorAll('.admin-module').length).toBe(1);
    expect(el(fixture).textContent).toContain('approuvé');
  });

  it('needs a reason to reject', () => {
    const fixture = render();
    const page = fixture.componentInstance;

    page.startReject(module(8, { status: 'PROPOSED' }));
    page.reject(module(8, { status: 'PROPOSED' }));
    fixture.detectChanges();
    expect(api.reject).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Le motif du refus est obligatoire.');

    page.reason.set('Doublon');
    page.reject(module(8, { status: 'PROPOSED' }));
    expect(api.reject).toHaveBeenCalledWith(8, 'Doublon');
  });
});

describe('PostFormComponent with modules', () => {
  function create(slug: string | null, moduleId: number | null = null) {
    TestBed.configureTestingModule({
      imports: [PostFormComponent],
      providers: [
        { provide: LocationApiService, useValue: {} },
        { provide: MediaApiService, useValue: {} },
        { provide: ModuleApiService, useValue: { approved: () => of([module(3), module(4)]) } },
      ],
    });
    const fixture = TestBed.createComponent(PostFormComponent);
    fixture.componentRef.setInput('initialModuleSlug', slug);
    fixture.componentRef.setInput('initialModuleId', moduleId);
    fixture.detectChanges();
    return fixture;
  }

  it('offers the approved modules and preselects the one of « Publier dans ce module »', () => {
    const fixture = create('cafes');
    const form = fixture.componentInstance;
    const sent: PostRequest[] = [];
    form.submitted.subscribe(body => sent.push(body));

    expect(el(fixture).querySelectorAll('#post-module option').length).toBe(3);
    form.content.set('Bon café');
    form.submit();

    expect(sent[0].moduleId).toBe(3);
  });

  it('keeps the module of an edited post and lets the author remove it', () => {
    const fixture = create(null, 4);
    const form = fixture.componentInstance;
    expect(form.moduleId()).toBe(4);

    form.setModule('');
    expect(form.moduleId()).toBeNull();
  });
});

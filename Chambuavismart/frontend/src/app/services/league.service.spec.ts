import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LeagueService } from './league.service';

describe('LeagueService (season-specific form guide and table)', () => {
  let service: LeagueService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(LeagueService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests league table with required seasonId', () => {
    service.getLeagueTable(123, 456).subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/league/123/table');
    expect(req.request.params.get('seasonId')).toBe('456');
    req.flush([]);
  });

  it('requests form guide with seasonId, limit and scope', () => {
    service.getFormGuide(99, 777, 6, 'overall').subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/form-guide/99');
    expect(req.request.params.get('seasonId')).toBe('777');
    expect(req.request.params.get('limit')).toBe('6');
    expect(req.request.params.get('scope')).toBe('overall');
    req.flush([]);
  });

  it('supports "all" limit and scope=home', () => {
    service.getFormGuide(99, 444, 'all', 'home').subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/form-guide/99');
    expect(req.request.params.get('seasonId')).toBe('444');
    expect(req.request.params.get('limit')).toBe('all');
    expect(req.request.params.get('scope')).toBe('home');
    req.flush([]);
  });
});

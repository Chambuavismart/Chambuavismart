import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LeagueTableComponent } from './league-table.component';
import { LeagueService } from '../services/league.service';
import { SeasonService } from '../services/season.service';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

describe('LeagueTableComponent', () => {
  let component: LeagueTableComponent;
  let fixture: ComponentFixture<LeagueTableComponent>;
  let leagueSvcSpy: jasmine.SpyObj<LeagueService>;
  let seasonSvcSpy: jasmine.SpyObj<SeasonService>;

  beforeEach(async () => {
    leagueSvcSpy = jasmine.createSpyObj('LeagueService', ['getLeagueTable', 'getLeagues']);
    seasonSvcSpy = jasmine.createSpyObj('SeasonService', ['listSeasons']);
    // Default spies
    leagueSvcSpy.getLeagues.and.returnValue(of([]));
    seasonSvcSpy.listSeasons.and.returnValue(of([{ id: 100, name: '2024/2025', startDate: '2024-08-01', endDate: '2025-05-31' }]));
    leagueSvcSpy.getLeagueTable.and.returnValue(of([
      { position: 1, teamId: 1, teamName: 'A', mp: 2, w: 2, d: 0, l: 0, gf: 5, ga: 1, gd: 4, pts: 6 },
      { position: 2, teamId: 2, teamName: 'B', mp: 2, w: 1, d: 0, l: 1, gf: 3, ga: 2, gd: 1, pts: 3 }
    ]));

    await TestBed.configureTestingModule({
      imports: [LeagueTableComponent],
      providers: [
        { provide: LeagueService, useValue: leagueSvcSpy },
        { provide: SeasonService, useValue: seasonSvcSpy },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ leagueId: '1' })) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LeagueTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render rows for returned data', () => {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('A');
    expect(rows[1].textContent).toContain('B');
  });

  it('fetches table when league changes', () => {
    leagueSvcSpy.getLeagueTable.calls.reset();
    seasonSvcSpy.listSeasons.and.returnValue(of([{ id: 200, name: '2025/2026', startDate: '2025-08-01', endDate: '2026-05-31' }]));
    component.onLeagueChange(7);
    expect(leagueSvcSpy.getLeagueTable).toHaveBeenCalledTimes(1);
    const [leagueIdArg, seasonIdArg] = leagueSvcSpy.getLeagueTable.calls.mostRecent().args as any[];
    expect(leagueIdArg).toBe(7);
    expect(seasonIdArg).toBe(200);
  });
});

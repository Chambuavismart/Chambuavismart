import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { FormGuideComponent } from './form-guide.component';
import { LeagueService } from '../services/league.service';
import { SeasonService } from '../services/season.service';

describe('FormGuideComponent', () => {
  let component: FormGuideComponent;
  let fixture: ComponentFixture<FormGuideComponent>;
  let leagueSvcSpy: jasmine.SpyObj<LeagueService>;
  let seasonSvcSpy: jasmine.SpyObj<SeasonService>;

  beforeEach(async () => {
    leagueSvcSpy = jasmine.createSpyObj('LeagueService', ['getLeagues', 'getFormGuide']);
    seasonSvcSpy = jasmine.createSpyObj('SeasonService', ['listSeasons']);

    leagueSvcSpy.getLeagues.and.returnValue(of([{ id: 1, name: 'EPL', country: 'England', season: '2025/2026' }] as any));
    leagueSvcSpy.getFormGuide.and.returnValue(of([]));
    seasonSvcSpy.listSeasons.and.returnValue(of([{ id: 100, name: '2025/2026' }] as any));

    await TestBed.configureTestingModule({
      imports: [FormGuideComponent],
      providers: [
        { provide: LeagueService, useValue: leagueSvcSpy },
        { provide: SeasonService, useValue: seasonSvcSpy },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FormGuideComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('passes seasonId to service when season changes', () => {
    component.leagueId = 77;
    // ensure no pending calls
    leagueSvcSpy.getFormGuide.calls.reset();

    // simulate season change
    component.onSeasonChange(555);

    expect(leagueSvcSpy.getFormGuide).toHaveBeenCalled();
    const args = leagueSvcSpy.getFormGuide.calls.mostRecent().args as any[];
    expect(args[0]).toBe(77); // leagueId
    expect(args[1]).toBe(555); // seasonId
    expect(args[2]).toBe('all'); // default limit is 'all'
    expect(args[3]).toBe('overall'); // default scope
  });
});

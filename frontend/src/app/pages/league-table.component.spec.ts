import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LeagueTableComponent } from './league-table.component';
import { LeagueService } from '../services/league.service';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

describe('LeagueTableComponent', () => {
  let component: LeagueTableComponent;
  let fixture: ComponentFixture<LeagueTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeagueTableComponent],
      providers: [
        { provide: LeagueService, useValue: {
          getLeagueTable: () => of([
            { position: 1, teamId: 1, teamName: 'A', mp: 2, w: 2, d: 0, l: 0, gf: 5, ga: 1, gd: 4, pts: 6 },
            { position: 2, teamId: 2, teamName: 'B', mp: 2, w: 1, d: 0, l: 1, gf: 3, ga: 2, gd: 1, pts: 3 }
          ])
        } },
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
});

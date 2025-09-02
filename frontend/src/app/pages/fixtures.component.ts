import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, AsyncPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FixturesService, LeagueWithUpcomingDTO, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';

@Component({
  selector: 'app-fixtures',
  standalone: true,
  imports: [NgFor, NgIf, FormsModule, AsyncPipe, DatePipe],
  styles: [`
    :host { display:block; color:#e6eef8; }
    .page { display:flex; gap:16px; }
    .sidebar { width: 300px; background:#0b1220; border:1px solid #1f2937; border-radius:12px; padding:12px; }
    .league { border-bottom:1px solid #1f2937; padding:8px 4px; }
    .league:last-child { border-bottom:0; }
    .league-header { display:flex; align-items:center; justify-content:space-between; cursor:pointer; padding:6px 8px; border-radius:8px; }
    .league-header:hover { background:#0f172a; }
    .badge { background:#0ea5e9; color:#04110a; padding:2px 8px; border-radius:999px; font-size:12px; font-weight:700; }
    .content { flex:1; }
    .grid { display:grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap:12px; }
    .card { background:#0b1220; border:1px solid #1f2937; border-radius:16px; padding:12px; box-shadow: 0 2px 10px rgba(0,0,0,.25); }
    .muted { color:#9fb3cd; }
    .teams { font-weight:700; }
    .status { display:inline-block; padding:2px 8px; border-radius:999px; font-size:12px; font-weight:700; }
    .status.UPCOMING { background:#0ea5e9; color:#04110a; }
    .status.LIVE { background:#ef4444; color:#04110a; }
    .status.FINISHED { background:#9ca3af; color:#04110a; }
    .toolbar { display:flex; align-items:center; gap:8px; margin-bottom:12px; }
    .select { background:#0b1220; border:1px solid #1f2937; color:#e6eef8; padding:6px 8px; border-radius:8px; }

    @media (max-width: 900px) {
      .page { flex-direction: column; }
      .sidebar { display:none; }
      .grid { grid-template-columns: 1fr; }
      .mobile { display:block; }
    }
  `],
  template: `
    <h1 style="font-weight:800; margin: 8px 0 12px;">Fixtures</h1>

    <!-- Mobile league picker -->
    <div class="mobile">
      <div class="toolbar">
        <label class="muted">League</label>
        <select class="select" [(ngModel)]="selectedLeagueId" (change)="loadFixtures()">
          <option *ngFor="let l of leagues" [value]="l.leagueId">{{ l.leagueName }} ({{ l.upcomingCount }})</option>
        </select>
        <label class="muted">Upcoming only</label>
        <input type="checkbox" [(ngModel)]="upcomingOnly" (change)="loadFixtures()"/>
      </div>
    </div>

    <div class="page">
      <aside class="sidebar">
        <div *ngFor="let l of leagues" class="league">
          <div class="league-header" (click)="toggleLeague(l.leagueId)">
            <div style="font-weight:800; color:#19b562">{{ l.leagueName }}</div>
            <span class="badge">{{ l.upcomingCount }}</span>
          </div>
          <div *ngIf="expandedLeagueId === l.leagueId" style="padding:8px 4px;">
            <button class="select" (click)="selectLeague(l.leagueId)">View fixtures</button>
          </div>
        </div>
      </aside>

      <section class="content">
        <div class="toolbar">
          <div class="muted">Selected:</div>
          <div style="font-weight:700;">{{ currentLeagueName || '—' }}</div>
          <div style="flex:1"></div>
          <label class="muted">Upcoming only</label>
          <input type="checkbox" [(ngModel)]="upcomingOnly" (change)="loadFixtures()"/>
        </div>

        <div *ngIf="fixtures?.length === 0" class="muted">No fixtures.</div>
        <div class="grid">
          <div class="card" *ngFor="let f of fixtures">
            <div class="muted">{{ f.round }} • {{ f.dateTime | date:'d MMM, HH:mm' }}</div>
            <div class="teams">{{ f.homeTeam }} vs {{ f.awayTeam }}</div>
            <div class="muted" style="margin: 4px 0 6px;">{{ f.homeScore !== null && f.awayScore !== null ? (f.homeScore + ' - ' + f.awayScore) : '- -' }}</div>
            <span class="status {{ f.status }}">{{ statusLabel(f) }}</span>
          </div>
        </div>
      </section>
    </div>
  `
})
export class FixturesComponent implements OnInit {
  private api = inject(FixturesService);

  leagues: LeagueWithUpcomingDTO[] = [];
  selectedLeagueId: number | null = null;
  currentLeagueName: string | null = null;
  fixtures: FixtureDTO[] = [];
  expandedLeagueId: number | null = null;
  upcomingOnly = false;

  ngOnInit(): void {
    this.api.getLeagues().subscribe(ls => {
      this.leagues = ls;
      if (ls.length && this.selectedLeagueId == null) {
        this.selectedLeagueId = ls[0].leagueId;
        this.loadFixtures();
      }
    });
  }

  toggleLeague(id: number) {
    this.expandedLeagueId = this.expandedLeagueId === id ? null : id;
  }

  selectLeague(id: number) {
    this.selectedLeagueId = id;
    this.loadFixtures();
  }

  loadFixtures() {
    if (this.selectedLeagueId == null) return;
    this.api.getLeagueFixtures(this.selectedLeagueId, this.upcomingOnly).subscribe((res: LeagueFixturesResponse) => {
      this.currentLeagueName = res.leagueName;
      this.fixtures = res.fixtures;
    });
  }

  // Derive display status using UTC comparison (for View All Fixtures page)
  statusLabel(f: FixtureDTO): string {
    if (this.hasResults(f)) return 'Completed';
    const fixtureMs = this.toUtcMillis(f?.dateTime);
    const nowMs = Date.now();
    if (!isNaN(fixtureMs) && fixtureMs < nowMs) return 'Awaiting Results';
    return 'Upcoming';
  }

  private hasResults(f: { homeScore: number | null; awayScore: number | null }): boolean {
    return f?.homeScore != null && f?.awayScore != null;
  }

  private toUtcMillis(iso: string | undefined): number {
    if (!iso) return NaN;
    const hasTZ = /[zZ]|[+-]\d{2}:\d{2}$/.test(iso);
    const s = hasTZ ? iso : iso + 'Z';
    const t = Date.parse(s);
    if (!isNaN(t)) return t;
    return new Date(iso).getTime();
  }
}

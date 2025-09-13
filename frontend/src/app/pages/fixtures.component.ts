import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { NgFor, NgIf, DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, NavigationEnd } from '@angular/router';
import { FixturesService, LeagueWithUpcomingDTO, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';
import { GlobalLeadersService, GlobalLeader } from '../services/global-leaders.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-fixtures',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, FormsModule, DatePipe],
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
    .card.hl { border-color:#19b562; box-shadow: 0 0 0 2px rgba(25,181,98,0.25), 0 2px 12px rgba(25,181,98,0.25); }
    .leader-flag { display:inline-block; background:#19b562; color:#04110a; font-size:11px; font-weight:800; padding:2px 6px; border-radius:6px; margin-bottom:6px; }
    .muted { color:#9fb3cd; }
    .teams { font-weight:700; }
    .status { display:inline-block; padding:2px 8px; border-radius:999px; font-size:12px; font-weight:700; }
    .status.UPCOMING { background:#0ea5e9; color:#04110a; }
    .status.AWAITING { background:#f59e0b; color:#04110a; }
    .status.RESULTS_MISSING { background:#ef4444; color:#04110a; }
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
    <h1 style="font-weight:800; margin: 8px 0 4px;">Fixtures</h1>
    <div class="muted" style="margin: 0 0 12px;">Note: Fixtures currently don’t use season filters. Season context may be inferred heuristically.</div>

    <!-- Mobile league picker -->
    <div class="mobile">
      <div class="toolbar">
        <label class="muted">League</label>
        <select class="select" [(ngModel)]="selectedLeagueId" (ngModelChange)="loadFixtures()">
          <option *ngFor="let l of leagues" [ngValue]="l.leagueId">{{ l.leagueCountry }} – {{ l.leagueName }} – {{ l.season }} ({{ l.upcomingCount }})</option>
        </select>
        <label class="muted">Upcoming only</label>
        <input type="checkbox" [(ngModel)]="upcomingOnly" (change)="loadFixtures()"/>
      </div>
    </div>

    <div class="page">
      <aside class="sidebar">
        <div *ngFor="let l of leagues" class="league">
          <div class="league-header" (click)="toggleLeague(l.leagueId)">
            <div style="font-weight:800; color:#19b562">{{ l.leagueCountry }} – {{ l.leagueName }} – {{ l.season }}</div>
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

        <div *ngIf="isLoading" class="muted">Refreshing fixtures...</div>
        <ng-container *ngIf="!isLoading">
          <div *ngIf="fixtures?.length === 0" class="muted">No fixtures.</div>
          <div class="grid">
            <div class="card" *ngFor="let f of fixtures" (click)="openAnalysis(f)" style="cursor:pointer;" [ngClass]="{ 'hl': isFixtureToday(f) && involvesLeader(f) }">
              <div *ngIf="isFixtureToday(f) && involvesLeader(f)" class="leader-flag" [attr.title]="leaderTooltip(f)" aria-label="Leader match details">Leader Match Today</div>
              <div class="muted">{{ f.round }} • {{ f.dateTime | date:'d MMM, HH:mm' }}</div>
              <div class="teams">{{ f.homeTeam }} vs {{ f.awayTeam }}</div>
              <div class="muted" style="margin: 4px 0 6px;">{{ f.homeScore !== null && f.awayScore !== null ? (f.homeScore + ' - ' + f.awayScore) : '- -' }}</div>
              <span class="status {{ computeStatus(f) }}">{{ statusLabel(f) }}</span>
            </div>
          </div>
        </ng-container>
      </section>
    </div>
  `
})
export class FixturesComponent implements OnInit, OnDestroy {
  openAnalysis(f: FixtureDTO) {
    // Navigate to Played Matches tab with pre-filled H2H teams, preserving orientation
    // Include leagueId so the Fixture Analysis tab can scope lookups correctly
    const leagueId = this.selectedLeagueId ?? null;
    this.router.navigate(['/played-matches-summary'], {
      queryParams: {
        h2hHome: f?.homeTeam ?? '',
        h2hAway: f?.awayTeam ?? '',
        ...(leagueId ? { leagueId } : {})
      }
    });
  }
  private api = inject(FixturesService);
  private router = inject(Router);
  private leadersApi = inject(GlobalLeadersService);

  leagues: LeagueWithUpcomingDTO[] = [];
  selectedLeagueId: number | null = null;
  currentLeagueName: string | null = null;
  fixtures: FixtureDTO[] = [];
  expandedLeagueId: number | null = null;
  upcomingOnly = true;
  isLoading = false;

  // cache of leader teams across categories; lowercase names for matching
  private leaderTeams = new Set<string>();
  private leadersByTeam = new Map<string, GlobalLeader[]>();

  private refreshIntervalId: any = null;
  private routerEventsSub: any = null;
  private onFixturesRefresh = (_e?: any) => {
    // reload fixtures for the current league selection
    this.loadFixtures();
  }
  private onFixturesOpen = (_e?: any) => {
    // tab explicitly opened: refresh before showing
    this.loadFixtures();
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.api.getLeagues().subscribe(ls => {
      this.leagues = ls;
      if (ls.length && this.selectedLeagueId == null) {
        this.selectedLeagueId = ls[0].leagueId;
      }
      // Preload leaders cache, then load fixtures
      this.loadLeaders();
      this.loadFixtures();
    });

    // When navigating (even to the same route), refresh if URL contains /fixtures
    this.routerEventsSub = this.router.events.subscribe(ev => {
      if (ev instanceof NavigationEnd) {
        const url = ev.urlAfterRedirects || ev.url;
        if (typeof url === 'string' && url.includes('/fixtures')) {
          this.loadFixtures();
        }
      }
    });

    // Background silent refresh every 60 seconds while component is active
    this.refreshIntervalId = setInterval(() => this.loadFixtures(), 60_000);
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
    this.isLoading = true;
    this.api.getLeagueFixtures(this.selectedLeagueId, this.upcomingOnly).subscribe((res: LeagueFixturesResponse) => {
      this.currentLeagueName = res.leagueName;
      // Replace fixtures array to prevent duplicates
      this.fixtures = (res.fixtures || []).slice();
      this.isLoading = false;
    }, _err => {
      // On error, stop loading but keep previous fixtures
      this.isLoading = false;
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
    window.removeEventListener('fixtures:open', this.onFixturesOpen as EventListener);
    if (this.routerEventsSub) {
      this.routerEventsSub.unsubscribe?.();
      this.routerEventsSub = null;
    }
    if (this.refreshIntervalId) {
      clearInterval(this.refreshIntervalId);
      this.refreshIntervalId = null;
    }
  }

  // Derive display status using current time with 24h overdue rule
  statusLabel(f: FixtureDTO): string {
    const s = this.computeStatus(f);
    switch (s) {
      case 'FINISHED': return 'Finished';
      case 'RESULTS_MISSING': return 'Results Missing';
      case 'AWAITING': return 'Awaiting Results';
      default: return 'Upcoming';
    }
  }

  computeStatus(f: { dateTime: string; homeScore: number | null; awayScore: number | null }): 'UPCOMING' | 'AWAITING' | 'RESULTS_MISSING' | 'FINISHED' {
    if (this.hasResults(f)) return 'FINISHED';
    const fixtureMs = this.toUtcMillis(f?.dateTime);
    if (isNaN(fixtureMs)) return 'UPCOMING';
    const nowMs = Date.now();
    if (fixtureMs > nowMs) return 'UPCOMING';
    const overdueMs = 24 * 60 * 60 * 1000;
    return (fixtureMs < (nowMs - overdueMs)) ? 'RESULTS_MISSING' : 'AWAITING';
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

  // New: load leader teams across main categories to enable fixture highlighting
  private loadLeaders() {
    const categories = ['btts','over15','over25','wins','draws'];
    const calls = categories.map(c => this.leadersApi.getLeaders(c, 5, 5, 'overall', 0));
    forkJoin(calls).subscribe((lists: GlobalLeader[][]) => {
      const names = new Set<string>();
      const byTeam = new Map<string, GlobalLeader[]>();
      for (const list of lists) {
        for (const l of list) {
          if (!l?.teamName) continue;
          const key = l.teamName.toLowerCase();
          names.add(key);
          const arr = byTeam.get(key) || [];
          arr.push(l);
          byTeam.set(key, arr);
        }
      }
      this.leaderTeams = names;
      this.leadersByTeam = byTeam;
    }, _err => {
      // Keep empty set on error
      this.leaderTeams = new Set<string>();
      this.leadersByTeam = new Map<string, GlobalLeader[]>();
    });
  }

  isFixtureToday(f: { dateTime: string }): boolean {
    const d = f?.dateTime ? new Date(f.dateTime) : null;
    if (!d || isNaN(d.getTime())) return false;
    const now = new Date();
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
  }

  involvesLeader(f: { homeTeam: string; awayTeam: string }): boolean {
    const home = f?.homeTeam?.toLowerCase?.();
    const away = f?.awayTeam?.toLowerCase?.();
    if (!home || !away) return false;
    return this.leaderTeams.has(home) || this.leaderTeams.has(away);
  }

  leaderTooltip(f: FixtureDTO): string | null {
    if (!f) return null;
    const homeKey = f.homeTeam?.toLowerCase?.();
    const awayKey = f.awayTeam?.toLowerCase?.();
    const parts: string[] = [];
    const fmt = (cat: string) => {
      switch (cat) {
        case 'btts': return 'BTTS';
        case 'over15': return 'Over 1.5 Goals';
        case 'over25': return 'Over 2.5 Goals';
        case 'wins': return 'Wins';
        case 'draws': return 'Draws';
        default: return cat;
      }
    };
    const build = (teamLabel: string, key: string | undefined) => {
      if (!key) return;
      const entries = this.leadersByTeam.get(key) || [];
      if (!entries.length) return;
      const details = entries
        .sort((a,b) => b.statPct - a.statPct)
        .map(e => `${fmt(e.category)}: ${Math.round(e.statPct)}% (${e.statCount}/${e.matchesPlayed})`)
        .join('; ');
      parts.push(`${teamLabel} leads in ${details}`);
    };

    build(f.homeTeam, homeKey);
    if (awayKey !== homeKey) {
      build(f.awayTeam, awayKey);
    }

    return parts.length ? parts.join(' • ') : null;
  }
}

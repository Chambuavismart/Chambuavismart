import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { NgFor, NgIf, DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, NavigationEnd, RouterLink, RouterLinkActive } from '@angular/router';
import { FixturesService, LeagueWithUpcomingDTO, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';
import { LeagueService, GroupedLeagueDTO } from '../services/league.service';
import { GlobalLeadersService, GlobalLeader } from '../services/global-leaders.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-fixtures',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, FormsModule, DatePipe, RouterLink, RouterLinkActive],
  styles: [`
    /* Flashscore-inspired dark theme */
    :host { display:block; color:#e0e0e0; font-family: Inter, Roboto, Arial, sans-serif; background:#0a0a0a; }
    .fs-layout { display:flex; min-height:100vh; }
    /* Sidebar Navigation */
    .fs-sidebar { width:20%; min-width:240px; background:#1a1a1a; padding:16px 12px; position:sticky; top:0; height:100vh; overflow:auto; border-right:1px solid #2a2a2a; }
    .brand { color:#fff; font-weight:700; font-size:18px; margin-bottom:16px; }
    .nav-list { list-style:none; margin:0; padding:0; }
    .nav-item { display:flex; align-items:center; gap:10px; padding:10px 12px; color:#ccc; border-radius:8px; cursor:pointer; transition: background 0.2s ease, color 0.2s ease; }
    .nav-item:hover { background:#333333; color:#007bff; }
    .nav-item:hover i { color:#007bff; }
    .nav-item.active { background:#007bff; color:#ffffff; }
    .nav-item.active i { color:#ffffff; }
    .nav-item i { width:18px; text-align:center; color:#6ea8fe; }

    /* Main Content */
    .fs-main { width:80%; padding:20px; }
    .fs-grid { display:grid; grid-template-columns: 1fr; gap:16px; }
    .card { background:#2a2a2a; border:1px solid #3a3a3a; border-radius:8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
    .card .card-body { padding:16px; }
    .title { font-size:24px; font-weight:800; color:#ffffff; }
    .subtitle { color:#e0e0e0; font-size:14px; display:flex; align-items:center; gap:8px; text-decoration:none; opacity:0.9; }
    .subtitle i { color:#6ea8fe; }

    .toolbar { display:flex; align-items:center; gap:8px; margin-bottom:12px; }
    .muted { color:#9aa0a6; }
    .badge { display:inline-block; font-size:12px; border-radius:9999px; padding:2px 8px; background:#0b2f55; color:#6ea8fe; border:1px solid #123e73; }

    /* Search input */
    .search-input { background:#2a2a2a; border:1px solid #404040; color:#e0e0e0; border-radius:8px; padding:10px 12px; width:100%; }
    .search-input::placeholder { color:#9aa0a6; }

    /* League accordion */
    .league-card { background:#2a2a2a; border:1px solid #3a3a3a; border-radius:8px; margin-bottom:15px; overflow:hidden; }
    .league-header { display:flex; align-items:center; justify-content:space-between; padding:12px 14px; cursor:pointer; transition: background .2s ease; }
    .league-header:hover { background:#333333; }
    .league-name { font-weight:700; color:#ffffff; font-size:18px; display:flex; align-items:center; gap:8px; }
    .league-actions { display:flex; align-items:center; gap:10px; }
    .chev { color:#9aa0a6; transition: transform .2s ease; }
    .chev.expanded { transform: rotate(180deg); }

    .fixtures-list { background:#1a1a1a; border-top:1px solid #333333; }
    .fixture-row { display:flex; align-items:center; gap:10px; padding:10px 12px; border-bottom:1px solid #2a2a2a; }
    .fixture-row:last-child { border-bottom:none; }
    .fixture-time { color:#e0e0e0; flex:0 0 190px; font-size:14px; }
    .fixture-teams { color:#ffffff; font-weight:700; flex:1; display:flex; align-items:center; justify-content:center; gap:8px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .fixture-teams .vs { color:#9aa0a6; font-weight:600; }
    .fixture-status { flex:0 0 120px; display:flex; justify-content:flex-end; }

    /* Status chips */
    .status-chip { font-size:12px; padding:2px 8px; border-radius:999px; border:1px solid #2d2d2d; color:#e0e0e0; background:#101010; }
    .status-chip.UPCOMING { background:#113a2a; color:#28a745; border-color:#1f7a4c; }
    .status-chip.AWAITING { background:#2f2a11; color:#ffd166; border-color:#7a6a1f; }
    .status-chip.RESULTS_MISSING { background:#3a1111; color:#ff6b6b; border-color:#7a1f1f; }
    .status-chip.FINISHED { background:#1f1f1f; color:#bdbdbd; border-color:#333; }

    .btn { border-radius:8px; padding:8px 12px; border:1px solid transparent; cursor:pointer; transition: background .2s ease, border-color .2s ease, transform .05s ease; font-weight:600; }
    .btn:active { transform: translateY(1px); }
    .btn-primary { background:#007bff; color:#fff; }
    .btn-primary:hover { background:#0056b3; }

    .loader { color:#6ea8fe; display:inline-flex; align-items:center; gap:8px; }

    /* Responsive */
    @media (max-width: 1024px){ .fs-sidebar { position:relative; height:auto; } .fs-main { width:100%; padding:12px; } .fs-layout { flex-direction:column; } }
    @media (max-width: 768px){ .fs-sidebar { order:2; width:100%; height:auto; } .fs-main { order:1; } .fixture-time { flex-basis:140px; } }
  `],
  template: `
    <div class="fs-layout">
      <!-- Sidebar Navigation -->
      <aside class="fs-sidebar">
        <div class="brand">ChambuVS</div>
        <ul class="nav-list">
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/']"><i class="fa fa-home"></i> <span>Home</span></a></li>
          <li class="nav-item active" aria-current="page"><i class="fa fa-calendar"></i> <span>Fixtures</span></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/match-analysis']"><i class="fa fa-chart-line"></i> <span>Match Analysis</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/form-guide']"><i class="fa fa-list"></i> <span>Form Guide</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/quick-insights']"><i class="fa fa-bolt"></i> <span>Quick Insights</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/league']"><i class="fa fa-table"></i> <span>League Table</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/data-management']"><i class="fa fa-database"></i> <span>Data Management</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/team-search']"><i class="fa fa-search"></i> <span>Team Search</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/played-matches-summary']"><i class="fa fa-lightbulb"></i> <span>Fixtures Analysis</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/analysis-pdfs']"><i class="fa fa-history"></i> <span>Fixture Analysis History</span></a></li>
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/admin']"><i class="fa fa-user-shield"></i> <span>Admin</span></a></li>
        </ul>
      </aside>

      <!-- Main Content -->
      <main class="fs-main" role="main">
        <!-- Top card with title and search -->
        <div class="card" style="margin-bottom:12px;">
          <div class="card-body">
            <div style="display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap;">
              <div>
                <div class="title">Fixtures Calendar</div>
                <div class="subtitle"><i class="fa fa-calendar"></i> View upcoming matches by league</div>
              </div>
              <div style="flex:1; max-width:420px; min-width:240px;">
                <input type="text" class="search-input" [(ngModel)]="leagueSearch" placeholder="Search leagues by country or name..." aria-label="Search leagues"/>
              </div>
            </div>
          </div>
        </div>

        <!-- Accordion list of leagues -->
        <section aria-label="Leagues list">
          <div *ngFor="let l of visibleLeagues; trackBy: trackLeague" class="league-card">
            <div class="league-header" (click)="toggleAccordion(l.leagueId)" [attr.aria-expanded]="expandedLeagueId === l.leagueId" [attr.aria-label]="'League: ' + l.leagueCountry + ' - ' + l.leagueName + ' ' + l.season">
              <div class="league-name"><i class="fa fa-globe"></i> <span>{{ l.leagueCountry }} - {{ l.leagueName }} {{ l.season }}</span></div>
              <div class="league-actions">
                <span class="badge" title="Upcoming fixtures">{{ l.upcomingCount }}</span>
                <i class="fa fa-chevron-down chev" [ngClass]="{ 'expanded': expandedLeagueId === l.leagueId }"></i>
              </div>
            </div>
            <div class="fixtures-list" *ngIf="expandedLeagueId === l.leagueId">
              <div class="fixture-row" *ngIf="isLeagueLoading(l.leagueId)">
                <span class="loader"><i class="fa fa-spinner fa-spin"></i> Loading fixtures...</span>
              </div>
              <ng-container *ngIf="!isLeagueLoading(l.leagueId)">
                <div class="fixture-row" *ngIf="(leagueFixturesMap.get(l.leagueId)?.length || 0) === 0">
                  <em class="muted">No upcoming fixtures for this league</em>
                </div>
                <div class="fixture-row" *ngFor="let f of leagueFixturesMap.get(l.leagueId)">
                  <div class="fixture-time">{{ f.dateTime | date:'d MMM yyyy, HH:mm' }} EAT</div>
                  <div class="fixture-teams">
                    <span class="team">{{ f.homeTeam }}</span>
                    <span class="vs">vs</span>
                    <span class="team">{{ f.awayTeam }}</span>
                  </div>
                  <div class="fixture-status">
                    <span class="status-chip" [ngClass]="computeStatus(f)">{{ statusLabel(f) }}</span>
                  </div>
                  <div>
                    <button class="btn btn-primary" (click)="openAnalysisForLeague(l.leagueId, f); $event.stopPropagation();" title="Open details">Details</button>
                  </div>
                </div>
              </ng-container>
            </div>
          </div>

          <div *ngIf="visibleLeagues.length < filteredLeagues.length" style="text-align:center; margin-top:8px;">
            <button class="btn btn-primary" (click)="loadMoreLeagues()">Load More</button>
          </div>
        </section>
      </main>
    </div>
  `
})
export class FixturesComponent implements OnInit, OnDestroy {
  // UI state for search, pagination, and per-league loading
  leagueSearch: string = '';
  pageSize = 20;
  visibleLeagueCount = 20;
  leagueFixturesMap: Map<number, FixtureDTO[]> = new Map<number, FixtureDTO[]>();
  loadingLeagueIds: Set<number> = new Set<number>();

  get filteredLeagues(): LeagueWithUpcomingDTO[] {
    const q = (this.leagueSearch || '').trim().toLowerCase();
    if (!q) return this.leagues || [];
    return (this.leagues || []).filter(l => {
      const a = `${l.leagueCountry || ''} ${l.leagueName || ''} ${l.season || ''}`.toLowerCase();
      return a.includes(q);
    });
  }
  get visibleLeagues(): LeagueWithUpcomingDTO[] {
    return this.filteredLeagues.slice(0, this.visibleLeagueCount);
  }
  loadMoreLeagues() { this.visibleLeagueCount += this.pageSize; }

  // trackBy function for leagues list to prevent unnecessary DOM re-renders
  trackLeague(index: number, l: LeagueWithUpcomingDTO): number { return l?.leagueId ?? index; }

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

  openAnalysisForLeague(leagueId: number, f: FixtureDTO) {
    this.selectedLeagueId = leagueId;
    this.openAnalysis(f);
  }

  toggleAccordion(id: number) {
    const next = this.expandedLeagueId === id ? null : id;
    this.expandedLeagueId = next;
    if (next != null && !this.leagueFixturesMap.has(id)) {
      this.loadLeagueFixtures(id);
    }
  }

  isLeagueLoading(id: number): boolean {
    return this.loadingLeagueIds.has(id);
  }

  private loadLeagueFixtures(leagueId: number) {
    if (this.loadingLeagueIds.has(leagueId)) return;
    this.loadingLeagueIds.add(leagueId);
    const upcomingOnly = this.filterMode === 'UPCOMING';
    this.api.getLeagueFixtures(leagueId, upcomingOnly).subscribe((res: LeagueFixturesResponse) => {
      const fixtures = (res?.fixtures || []).slice();
      this.leagueFixturesMap.set(leagueId, fixtures);
      this.loadingLeagueIds.delete(leagueId);
    }, _err => {
      this.leagueFixturesMap.set(leagueId, []);
      this.loadingLeagueIds.delete(leagueId);
    });
  }
  private api = inject(FixturesService);
  private router = inject(Router);
  private leadersApi = inject(GlobalLeadersService);
  private leagueService = inject(LeagueService);

  leagues: LeagueWithUpcomingDTO[] = [];
  // Grouped leagues for mobile dropdown (Country — League Name, seasons latest->oldest)
  groupedLeagues: GroupedLeagueDTO[] = [];
  selectedLeagueId: number | null = null;
  currentLeagueName: string | null = null;
  fixtures: FixtureDTO[] = [];
  expandedLeagueId: number | null = null;
  filterMode: 'ALL' | 'UPCOMING' | 'AWAITING' | 'RESULTS_MISSING' | 'FINISHED' = 'UPCOMING';
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

    // Load grouped leagues for the mobile dropdown
    this.leagueService.getGroupedLeaguesForUpload().subscribe(groups => {
      this.groupedLeagues = groups || [];
      // If nothing is selected yet, default to the first option
      if ((this.selectedLeagueId == null) && this.groupedLeagues.length) {
        const firstGroup = this.groupedLeagues[0];
        const firstOpt = firstGroup?.options?.[0];
        if (firstOpt && typeof firstOpt.leagueId === 'number') {
          this.selectedLeagueId = firstOpt.leagueId;
        }
      }
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

  viewAllLeagues() {
    this.selectedLeagueId = 0;
    this.loadFixtures();
  }

  loadFixtures() {
    if (this.selectedLeagueId == null) return;
    this.isLoading = true;
    const upcomingOnly = this.filterMode === 'UPCOMING';

    // All Leagues mode
    if (this.selectedLeagueId === 0) {
      const ensureLeagues = (this.leagues && this.leagues.length)
        ? Promise.resolve(this.leagues)
        : new Promise<LeagueWithUpcomingDTO[]>((resolve) => {
            this.api.getLeagues().subscribe(ls => {
              this.leagues = ls || [];
              resolve(this.leagues);
            }, _err => resolve([]));
          });

      ensureLeagues.then(ls => {
        const ids = (ls || []).map(l => l.leagueId);
        if (!ids.length) {
          this.currentLeagueName = 'All Leagues';
          this.fixtures = [];
          this.isLoading = false;
          return;
        }
        const calls = ids.map(id => this.api.getLeagueFixtures(id, upcomingOnly));
        forkJoin(calls).subscribe((responses: LeagueFixturesResponse[]) => {
          const merged: FixtureDTO[] = [];
          for (const r of responses) {
            const fx = r?.fixtures || [];
            for (const f of fx) {
              // Attach leagueName for context in All mode
              merged.push({ ...(f as any), leagueName: r.leagueName } as any);
            }
          }
          merged.sort((a: any, b: any) => {
            const ta = new Date(a.dateTime).getTime();
            const tb = new Date(b.dateTime).getTime();
            return ta - tb;
          });
          this.currentLeagueName = 'All Leagues';
          this.fixtures = merged;
          this.isLoading = false;
        }, _err => {
          this.isLoading = false;
        });
      });
      return;
    }

    // Single league mode
    this.api.getLeagueFixtures(this.selectedLeagueId, upcomingOnly).subscribe((res: LeagueFixturesResponse) => {
      this.currentLeagueName = res.leagueName;
      // Replace fixtures array to prevent duplicates
      this.fixtures = (res.fixtures || []).slice();
      this.isLoading = false;
    }, _err => {
      // On error, stop loading but keep previous fixtures
      this.isLoading = false;
    });
  }

  onFilterChange() {
    // If switching to UPCOMING, we may need to refetch with backend filter.
    // If switching away, ensure we have all data by fetching without upcomingOnly.
    this.loadFixtures();
  }

  get filteredFixtures(): FixtureDTO[] {
    if (!this.fixtures?.length) return [];
    if (this.filterMode === 'ALL') return this.fixtures;
    return this.fixtures.filter(f => this.computeStatus(f) === this.filterMode);
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

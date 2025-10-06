import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { NgFor, NgIf, DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, NavigationEnd, RouterLink, RouterLinkActive } from '@angular/router';
import { FixturesService, LeagueWithUpcomingDTO, LeagueFixturesResponse, FixtureDTO, SearchFixtureItemDTO } from '../services/fixtures.service';
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

    /* Dropdown visibility + pill styling */
    .search-dropdown { padding: 8px; }
    .search-dropdown .fixture-row { align-items: flex-start; flex-wrap: wrap; }
    .search-dropdown .result-pill { position: relative; display:flex; align-items:center; gap:10px; width:100%; padding:10px 12px; border-radius:16px; border:1px solid #334155; background: linear-gradient(135deg, rgba(30, 64, 175, 0.45), rgba(6, 95, 70, 0.45)); box-shadow: 0 2px 6px rgba(0,0,0,0.35); transition: transform .05s ease, box-shadow .2s ease, background .2s ease; cursor:pointer; }
    .search-dropdown .result-pill:hover { box-shadow: 0 6px 18px rgba(0,0,0,0.45); background: linear-gradient(135deg, rgba(37, 99, 235, 0.55), rgba(5, 150, 105, 0.55)); }
    .search-dropdown .result-pill:active { transform: translateY(1px); }
    .search-dropdown .fixture-time { flex: 0 0 auto; font-size: 13px; white-space: nowrap; color:#d1e9ff; background: rgba(2, 132, 199, 0.25); border: 1px solid rgba(56, 189, 248, 0.35); padding:2px 6px; border-radius:999px; }
    .search-dropdown .fixture-teams { flex: 1 1 100%; display: block; white-space: normal; overflow: visible; text-overflow: clip; font-weight: 800; color: #ffffff; margin-top: 4px; }
    .search-dropdown .fixture-status { flex: 0 0 auto; margin-left: auto; }
    .search-dropdown .team { display: inline; }

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
          <li class="nav-item" routerLinkActive="active"><a [routerLink]="['/analysis']"><i class="fa fa-wand-magic-sparkles"></i> <span>Analysis (Auto)</span></a></li>
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
              <div style="flex:1; max-width:420px; min-width:240px; position:relative;">
                <input type="text" class="search-input" [(ngModel)]="leagueSearch" (ngModelChange)="onLeagueSearchChange()" placeholder="Search leagues by country or name..." aria-label="Search leagues"/>
              </div>
              <div style="flex:1; max-width:420px; min-width:240px; position:relative;">
                <input type="text" class="search-input" [(ngModel)]="teamSearch" (ngModelChange)="onTeamSearchChange()" placeholder="Search fixture by team (type at least 3 letters)..." aria-label="Search fixture by team"/>
                <div *ngIf="showTeamDropdown" class="search-dropdown" style="position:absolute; top:44px; left:0; right:0; background:#1a1a1a; border:1px solid #333; border-radius:8px; z-index:1000; max-height:320px; overflow:auto;">
                  <div *ngIf="teamLoading" class="fixture-row"><span class="loader"><i class="fa fa-spinner fa-spin"></i> Searching...</span></div>
                  <div *ngIf="!teamLoading && (!teamResults || teamResults.length===0)" class="fixture-row"><em class="muted">No matching upcoming fixtures</em></div>
                  <div *ngFor="let it of teamResults" class="fixture-row result-pill" (click)="selectTeamResult(it)">
                    <div class="fixture-time">{{ toSafeDate(it.fixture?.dateTime) | date:'d MMM yyyy, HH:mm':'Africa/Nairobi' }} EAT</div>
                    <div class="fixture-teams" [attr.title]="(it.fixture?.homeTeam || '') + ' vs ' + (it.fixture?.awayTeam || '')">
                      <span class="team">{{ it.fixture?.homeTeam }}</span>
                      <span class="vs">vs</span>
                      <span class="team">{{ it.fixture?.awayTeam }}</span>
                    </div>
                    <div class="fixture-status">
                      <span class="status-chip">{{ it.leagueCountry || '' }} {{ it.leagueName ? '• ' + it.leagueName : '' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Accordion list of leagues -->
        <section aria-label="Leagues list">
          <div *ngFor="let l of visibleLeagues; trackBy: trackLeague" class="league-card">
            <div class="league-header" (click)="toggleAccordion(l.leagueId)" [attr.aria-expanded]="expandedLeagueId === l.leagueId" [attr.aria-label]="'League: ' + l.leagueCountry + ' - ' + l.leagueName + ' ' + l.season">
              <div class="league-name">
                <i class="fa fa-globe"></i>
                <span [attr.title]="leagueCountryTooltip(l)" (click)="$event.stopPropagation()">{{ l.leagueCountry }}</span>
                <span> - {{ l.leagueName }} {{ l.season }}</span>
              </div>
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
                <div class="fixture-row" *ngFor="let f of leagueFixturesMap.get(l.leagueId)" (click)="openAnalysisForLeague(l.leagueId, f)">
                  <div class="fixture-time">{{ toSafeDate(f.dateTime) | date:'d MMM yyyy, HH:mm':'Africa/Nairobi' }} EAT</div>
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
  // Team search state
  teamSearch: string = '';
  teamResults: SearchFixtureItemDTO[] = [];
  teamLoading: boolean = false;
  showTeamDropdown: boolean = false;
  private teamDebounceHandle: any = null;
  // Safely convert input to Date or null to avoid NG02100 in DatePipe
  toSafeDate(input: any): Date | null {
    if (!input) return null;
    try {
      const d = new Date(input);
      return isNaN(d.getTime()) ? null : d;
    } catch {
      return null;
    }
  }
  // UI state for search, pagination, and per-league loading
  leagueSearch: string = '';
  pageSize = 20;
  visibleLeagueCount = 20;
  leagueFixturesMap: Map<number, FixtureDTO[]> = new Map<number, FixtureDTO[]>();
  loadingLeagueIds: Set<number> = new Set<number>();

  get filteredLeagues(): LeagueWithUpcomingDTO[] {
    const q = (this.leagueSearch || '').trim().toLowerCase();
    const source = (this.leagues || []).slice();
    const filtered = q
      ? source.filter(l => {
          const a = `${l.leagueCountry || ''} ${l.leagueName || ''} ${l.season || ''}`.toLowerCase();
          return a.includes(q);
        })
      : source;
    // Sort countries A→Z; tie-break by league name A→Z
    filtered.sort((a, b) => {
      const ca = (a.leagueCountry || '').toLowerCase();
      const cb = (b.leagueCountry || '').toLowerCase();
      if (ca !== cb) return ca.localeCompare(cb);
      const la = (a.leagueName || '').toLowerCase();
      const lb = (b.leagueName || '').toLowerCase();
      return la.localeCompare(lb);
    });
    return filtered;
  }
  get visibleLeagues(): LeagueWithUpcomingDTO[] {
    return this.filteredLeagues.slice(0, this.visibleLeagueCount);
  }
  loadMoreLeagues() { this.visibleLeagueCount += this.pageSize; this.preloadStatusCountsForVisibleLeagues(); }

  // trackBy function for leagues list to prevent unnecessary DOM re-renders
  trackLeague(index: number, l: LeagueWithUpcomingDTO): number { return l?.leagueId ?? index; }

  openAnalysis(f: FixtureDTO) {
    // Automation: Navigate to unified Analysis view (Recommendation Card + tabs via links)
    const leagueId = this.selectedLeagueId ?? null;
    const qp: any = {
      homeTeamName: f?.homeTeam ?? '',
      awayTeamName: f?.awayTeam ?? ''
    };
    if (leagueId) qp.leagueId = leagueId;
    if (f?.id != null) qp.fixtureId = f.id;
    this.router.navigate(['/analysis'], { queryParams: qp });
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

  // Per-league cached counts for AWAITING and RESULTS_MISSING to power country tooltips
  private leagueStatusCounts: Map<number, { awaiting: number; missing: number }> = new Map<number, { awaiting: number; missing: number }>();

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
      // Preload status counts for visible leagues to power country tooltips
      this.preloadStatusCountsForVisibleLeagues();
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
    // Refresh tooltip counts as status categories may be of interest
    this.preloadStatusCountsForVisibleLeagues();
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
    if (this.teamDebounceHandle) {
      clearTimeout(this.teamDebounceHandle);
      this.teamDebounceHandle = null;
    }
    this.showTeamDropdown = false;
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

  // Tooltip for country showing which leagues have fixtures awaiting or with missing results
  leagueCountryTooltip(l: LeagueWithUpcomingDTO): string | null {
    if (!l) return null;
    const country = l.leagueCountry;
    const related = (this.leagues || []).filter(x => x.leagueCountry === country);
    const lines: string[] = [];
    for (const item of related) {
      const counts = this.leagueStatusCounts.get(item.leagueId);
      const awaiting = counts?.awaiting || 0;
      const missing = counts?.missing || 0;
      if (awaiting || missing) {
        const parts: string[] = [];
        if (awaiting) parts.push(`${awaiting} awaiting`);
        if (missing) parts.push(`${missing} missing results`);
        lines.push(`${item.leagueName}: ${parts.join(', ')}`);
      }
    }
    return lines.length ? lines.join(' • ') : null;
  }

  // Preload per-league status counts for visible leagues (and their countries)
  private preloadStatusCountsForVisibleLeagues(): void {
    try {
      const needIds = new Set<number>();
      // Start with currently visible leagues
      for (const lg of this.visibleLeagues) {
        if (!this.leagueStatusCounts.has(lg.leagueId)) needIds.add(lg.leagueId);
        // Also consider other leagues in the same country (if any counts missing)
        const siblings = (this.leagues || []).filter(x => x.leagueCountry === lg.leagueCountry);
        for (const s of siblings) {
          if (!this.leagueStatusCounts.has(s.leagueId)) needIds.add(s.leagueId);
        }
      }
      if (!needIds.size) return;
      const calls = Array.from(needIds).map(id => this.api.getLeagueFixtures(id, false));
      forkJoin(calls).subscribe({
        next: (responses: LeagueFixturesResponse[]) => {
          for (const r of responses) {
            const fx = r?.fixtures || [];
            let awaiting = 0, missing = 0;
            for (const f of fx) {
              const st = this.computeStatus(f as any);
              if (st === 'AWAITING') awaiting++;
              else if (st === 'RESULTS_MISSING') missing++;
            }
            this.leagueStatusCounts.set(r.leagueId, { awaiting, missing });
          }
        },
        error: _err => {
          // Ignore errors; tooltips will simply not be available for those leagues
        }
      });
    } catch {
      // no-op
    }
  }

  onLeagueSearchChange(): void {
    // Trigger preloading for the current visible set when search changes
    this.preloadStatusCountsForVisibleLeagues();
  }

  // Team search: debounce and query backend for nearest upcoming fixtures by team prefix
  onTeamSearchChange(): void {
    const q = (this.teamSearch || '').trim();
    if (this.teamDebounceHandle) {
      clearTimeout(this.teamDebounceHandle);
      this.teamDebounceHandle = null;
    }

    if (q.length < 3) {
      // Hide dropdown and clear results when input is too short
      this.teamResults = [];
      this.teamLoading = false;
      this.showTeamDropdown = false;
      return;
    }

    // Show dropdown and indicate loading after a short debounce
    this.showTeamDropdown = true;
    this.teamLoading = true;
    this.teamDebounceHandle = setTimeout(() => {
      // Limit 12 for a bit more coverage while keeping UI compact
      this.api.searchFixtures(q, 12).subscribe({
        next: (items) => {
          this.teamResults = items || [];
          this.teamLoading = false;
          this.showTeamDropdown = true;
        },
        error: _err => {
          this.teamResults = [];
          this.teamLoading = false;
          this.showTeamDropdown = true; // still show container with "No matches"
        }
      });
    }, 250);
  }

  // When a team+fixture is selected from dropdown, behave like clicking that fixture's Details
  selectTeamResult(it: SearchFixtureItemDTO): void {
    if (!it) return;
    this.showTeamDropdown = false;
    const f = it.fixture as any;
    const leagueId = (it.leagueId != null) ? Number(it.leagueId) : (this.selectedLeagueId ?? null);
    if (leagueId != null) {
      this.selectedLeagueId = leagueId;
      // Open analysis for the matched fixture
      this.openAnalysisForLeague(leagueId, f);
    } else {
      this.openAnalysis(f);
    }
  }
}

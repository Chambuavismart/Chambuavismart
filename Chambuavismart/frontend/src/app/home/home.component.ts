import { Component, inject, OnInit, OnDestroy, NgZone, ChangeDetectorRef } from '@angular/core';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { NgFor, NgIf, DatePipe, NgClass, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FixturesService, LeagueFixturesResponse, SearchFixtureItemDTO } from '../services/fixtures.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';
import { GlobalLeadersContainerComponent } from '../components/global-leaders-container/global-leaders-container.component';
import { GlobalLeadersService, GlobalLeader } from '../services/global-leaders.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, FormsModule, NgFor, NgIf, NgClass, NgStyle, DatePipe, GlobalLeadersContainerComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {
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
  // Sidebar UI state
  sidebarOpen: boolean = true;
  activeNav: string = 'Home';
  private fixturesApi = inject(FixturesService);
  private router = inject(Router);
  private leadersApi = inject(GlobalLeadersService);
  private route = inject(ActivatedRoute);
  private colorCache = inject(AnalysisColorCacheService);
  private zone = inject(NgZone);
  private cdr = inject(ChangeDetectorRef);

  today = new Date();
  showCalendar = false;
  calendarYear = this.today.getFullYear();
  calendarMonth = this.today.getMonth(); // 0-based
  availableDates = new Set<string>(); // 'YYYY-MM-DD'
  selectedDate: string | null = null;
  pastFixtures: LeagueFixturesResponse[] = [];
  loadingPast = false;
  season: string | undefined; // Optional: set if you want to constrain to a specific season

  // Today's fixtures state
  todayIso: string = '';
  todayFixtures: LeagueFixturesResponse[] = [];
  // Stable, precomputed flat list for rendering (Phase 1)
  todayFlatData: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
  
  // Search state for today's fixtures
  searchQuery: string = '';
  showSuggestions: boolean = false;
  highlightedIndex: number = -1;
  todayLoading = false;

  // When a past date is selected, hide today's fixtures section
  get showOnlySelectedDate(): boolean {
    return !!(this.selectedDate && this.todayIso && this.selectedDate < this.todayIso);
  }

  // Total count of today's fixtures across all leagues
  get todayCount(): number {
    return this.todayFlatData.length;
  }

  // Unified normalization for team names (single source of truth)
  normalizeTeamName(f: any, side: 'home' | 'away'): string {
    if (!f) return '';
    const primaryKeys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of primaryKeys) {
      const v = (f as any)[k];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    // Nested common structures
    const nested = side === 'home'
      ? ((f as any).home || (f as any).home_team || (f as any).teams?.home || (f as any).teamHome)
      : ((f as any).away || (f as any).away_team || (f as any).teams?.away || (f as any).teamAway);
    if (nested) {
      if (typeof nested === 'string' && nested.trim()) return nested.trim();
      if (typeof (nested as any).name === 'string' && (nested as any).name.trim()) return (nested as any).name.trim();
      if (typeof (nested as any).team === 'string' && (nested as any).team.trim()) return (nested as any).team.trim();
      if (typeof (nested as any).title === 'string' && (nested as any).title.trim()) return (nested as any).title.trim();
      if (typeof (nested as any).shortName === 'string' && (nested as any).shortName.trim()) return (nested as any).shortName.trim();
    }
    return '';
  }

  // Flattened, filtered, time-sorted list of today's fixtures across all leagues

  // Global search results populated from backend
  searchResults: SearchFixtureItemDTO[] = [];

  onSearchInput(): void {
    const q = this.searchQuery.trim();
    this.highlightedIndex = -1;
    if (q.length < 3) {
      this.searchResults = [];
      this.showSuggestions = false;
      return;
    }
    this.fixturesApi.searchFixtures(q, 10, this.season).subscribe({
      next: res => {
        this.searchResults = Array.isArray(res) ? res : [];
        this.showSuggestions = this.searchResults.length > 0;
      },
      error: _err => {
        this.searchResults = [];
        this.showSuggestions = false;
      }
    });
  }

  onSearchFocus(): void {
    this.showSuggestions = this.searchQuery.trim().length >= 3 && this.searchResults.length > 0;
  }

  onSearchBlur(): void {
    // Slight delay to allow click event on suggestion to fire before closing
    setTimeout(() => { this.showSuggestions = false; }, 150);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    const suggestions = this.searchResults;
    if (!suggestions.length) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.highlightedIndex = (this.highlightedIndex + 1) % suggestions.length;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.highlightedIndex = (this.highlightedIndex <= 0 ? suggestions.length - 1 : this.highlightedIndex - 1);
    } else if (event.key === 'Enter') {
      if (this.highlightedIndex >= 0 && this.highlightedIndex < suggestions.length) {
        event.preventDefault();
        const sel = suggestions[this.highlightedIndex];
        this.selectSuggestion(sel);
      }
    } else if (event.key === 'Escape') {
      this.showSuggestions = false;
    }
  }

  selectSuggestion(item: SearchFixtureItemDTO): void {
    if (!item) return;
    this.goToAnalysis({ leagueId: item.leagueId!, leagueName: item.leagueName }, item.fixture);
    this.clearSearch();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.showSuggestions = false;
    this.highlightedIndex = -1;
  }

  // Leaders cache for highlighting
  private leaderTeams: Set<string> = new Set<string>();
  private leadersByTeam: Map<string, GlobalLeader[]> = new Map<string, GlobalLeader[]>();

  private onFixturesRefresh = (_e?: any) => {
    // refresh today's fixtures and selected date view (if any)
    this.loadToday();
    if (this.selectedDate) {
      // Re-fetch for the currently selected date
      this.selectDate(this.selectedDate);
    }
  }

  ngOnInit(): void {
    try {
      this.sidebarOpen = window.innerWidth >= 768;
      const path = (window.location?.pathname || '').toLowerCase();
      if (path.includes('fixtures-analysis-history')) this.activeNav = 'Fixture Analysis History';
      else if (path.includes('fixtures-analysis')) this.activeNav = 'Fixtures Analysis';
      else if (path.includes('fixtures')) this.activeNav = 'Fixtures';
      else if (path.includes('match-analysis')) this.activeNav = 'Match Analysis';
      else if (path.includes('form-guide')) this.activeNav = 'Form Guide';
      else if (path.includes('quick-insights')) this.activeNav = 'Quick Insights';
      else if (path.includes('league-table')) this.activeNav = 'League Table';
      else if (path.includes('team-search')) this.activeNav = 'Team Search';
      else if (path.includes('admin')) this.activeNav = 'Admin';
      else this.activeNav = 'Home';
    } catch {}
    this.todayIso = this.toIsoLocal(this.today);
    this.loadLeaders();
    this.loadToday();
    // Check if a date is provided in the URL (from global navbar calendar)
    const qpDate = this.route.snapshot.queryParamMap.get('date');
    if (qpDate && /^\d{4}-\d{2}-\d{2}$/.test(qpDate)) {
      // Defer selection slightly to let initial state settle
      setTimeout(() => this.selectDate(qpDate), 0);
    }
    window.addEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
    window.addEventListener('fixtures:colors-updated', this._onColorsUpdated as EventListener);
  }

  toggleSidebar(){ this.sidebarOpen = !this.sidebarOpen; }

  setActive(key: string){ this.activeNav = key; }

  get todayLabel(): string {
    try {
      const opts: Intl.DateTimeFormatOptions = { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true, timeZoneName: 'short' };
      return new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'Africa/Nairobi' }).format(new Date());
    } catch {
      return this.formattedDate;
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
    window.removeEventListener('fixtures:colors-updated', this._onColorsUpdated as EventListener);
  }

  private isDev(): boolean {
    try {
      const host = (window && window.location && window.location.hostname) ? window.location.hostname : '';
      return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.local');
    } catch { return false; }
  }

  private loadToday(): void {
    this.todayLoading = true;
    this.todayFixtures = [];
    this.todayFlatData = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching TODAY fixtures for', this.todayIso);
    this.fixturesApi.getFixturesByDate(this.todayIso, this.season).subscribe({
      next: res => {
        this.todayFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
        // Build stable flat list once (normalize, filter, sort)
        const tz = 'Africa/Nairobi';
        const seen = new Set<number>();
        const flat: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
        for (const lg of this.todayFixtures) {
          const fx = lg?.fixtures || [];
          for (const f of fx) {
            if (!f) continue;
            const home = this.normalizeTeamName(f, 'home');
            const away = this.normalizeTeamName(f, 'away');
            // Attach normalized names directly to fixture for consistent template binding
            (f as any).homeTeam = home;
            (f as any).awayTeam = away;
            if (!home || !away) continue; // exclude invalid entries
            if (!this.isSameDayInTz((f as any).dateTime, tz)) continue; // keep only today's fixtures in EAT
            const id: number | undefined = typeof (f as any).id === 'number' ? (f as any).id : undefined;
            if (id != null) {
              if (seen.has(id)) continue;
              seen.add(id);
            }
            // Preformat date for display to minimize DatePipe usage
            let formattedDate: string | null = null;
            try {
              const d = new Date((f as any).dateTime);
              if (!isNaN(d.getTime())) {
                formattedDate = new Intl.DateTimeFormat('en-GB', {
                  weekday: 'short', day: '2-digit', month: 'short', year: 'numeric',
                  hour: '2-digit', minute: '2-digit', hour12: true, timeZone: tz, timeZoneName: 'short'
                }).format(d);
              }
            } catch { formattedDate = null; }
            (f as any).formattedDate = formattedDate;

            flat.push({ leagueId: lg.leagueId, leagueName: lg.leagueName, leagueCountry: lg.leagueCountry, fixture: f });
          }
        }
        // Sort with priority: fixtures without analysis colors first, then by kickoff time ascending
        flat.sort((a, b) => this.compareFixturesForToday(a, b));
        this.todayFlatData = flat;
        if (this.isDev()) {
          try {
            // eslint-disable-next-line no-console
            console.log('[HomeComponent] Normalized today fixtures:', {
              totalLeagues: this.todayFixtures.length,
              total: this.todayFlatData.length,
              sample: this.todayFlatData.slice(0, 5).map(x => ({ home: x.fixture.homeTeam, away: x.fixture.awayTeam, dt: x.fixture.formattedDate || x.fixture.dateTime }))
            });
          } catch {}
        }
        const allValid = this.todayFlatData.every(it => !!(it.fixture.homeTeam && it.fixture.awayTeam));
        if (!allValid) {
          // eslint-disable-next-line no-console
          console.warn('[HomeComponent] Some fixtures missing names after normalization');
        }
        this.todayLoading = false;
      },
      error: err => {
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] TODAY fixtures load error', err);
        this.todayFixtures = []; this.todayFlatData = []; this.todayLoading = false;
      }
    });
  }

  get formattedDate(): string {
    const d = this.today;
    const day = d.getDate();
    const month = d.toLocaleString('en-GB', { month: 'short' });
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  get calendarMonthLabel(): string {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    const month = d.toLocaleString('en-GB', { month: 'long' });
    return `${month} ${this.calendarYear}`;
  }

  private toIsoLocal(d: Date): string {
    const y = d.getFullYear();
    const m = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private parseIsoLocal(iso: string): Date {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, (m ?? 1) - 1, d ?? 1);
  }

  get daysGrid(): { day: number; inMonth: boolean; iso: string; hasFixtures: boolean; isPast: boolean; }[] {
    const first = new Date(this.calendarYear, this.calendarMonth, 1);
    const startDay = first.getDay() === 0 ? 6 : first.getDay() - 1; // Monday=0
    const daysInMonth = new Date(this.calendarYear, this.calendarMonth + 1, 0).getDate();
    const prevMonthDays = new Date(this.calendarYear, this.calendarMonth, 0).getDate();

    const grid: { day: number; inMonth: boolean; iso: string; hasFixtures: boolean; isPast: boolean; }[] = [];

    // previous month fillers
    for (let i = startDay - 1; i >= 0; i--) {
      const day = prevMonthDays - i;
      const d = new Date(this.calendarYear, this.calendarMonth - 1, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: false, iso, hasFixtures: this.availableDates.has(iso), isPast: d <= this.today });
    }

    // current month
    for (let day = 1; day <= daysInMonth; day++) {
      const d = new Date(this.calendarYear, this.calendarMonth, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: true, iso, hasFixtures: this.availableDates.has(iso), isPast: d <= this.today });
    }

    // next month fillers to complete weeks
    while (grid.length % 7 !== 0) {
      const last = grid[grid.length - 1];
      const lastDate = this.parseIsoLocal(last.iso);
      const nextDate = new Date(lastDate.getFullYear(), lastDate.getMonth(), lastDate.getDate() + 1);
      const iso = this.toIsoLocal(nextDate);
      grid.push({ day: nextDate.getDate(), inMonth: false, iso, hasFixtures: this.availableDates.has(iso), isPast: nextDate <= this.today });
    }

    return grid;
  }

  stats = {
    matchesAnalyzed: 0,
    avgConfidence: 75,
    fixturesUploaded: 0
  };

  toggleCalendar() {
    this.showCalendar = !this.showCalendar;
    if (this.showCalendar) {
      this.fetchAvailableDates();
    }
  }

  // Load leaders for highlighting logic
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
      this.leaderTeams = new Set<string>();
      this.leadersByTeam = new Map<string, GlobalLeader[]>();
    });
  }

  prevMonth() {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() - 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  nextMonth() {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() + 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  fetchAvailableDates() {
    const year = this.calendarYear;
    const month = this.calendarMonth + 1; // 1-based
    this.fixturesApi.getAvailableDates(year, month, this.season).subscribe(ds => {
      this.availableDates = new Set((ds ?? []).filter(Boolean));
    });
  }

  selectDate(iso: string) {
    // Always allow selecting any date; backend will return fixtures or an empty list.
    this.selectedDate = iso;
    this.loadingPast = true;
    this.pastFixtures = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching fixtures for date', iso);
    const hasDot = this.availableDates?.has?.(iso) === true;
    const handleSuccess = (res: any, attemptedRefresh: boolean) => {
      this.pastFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
      // eslint-disable-next-line no-console
      console.debug('[HomeComponent] fixtures loaded:', this.pastFixtures.length, '(refresh:', attemptedRefresh, ')');
      if (hasDot && (!this.pastFixtures || this.pastFixtures.length === 0) && !attemptedRefresh) {
        // Retry once with refresh=true if calendar indicated fixtures should exist
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] empty result but calendar shows availability; retrying with refresh=true');
        this.fixturesApi.getFixturesByDate(iso, this.season, true).subscribe({
          next: r2 => { handleSuccess(r2, true); },
          error: e2 => { handleError(e2, true); }
        });
        return;
      }
      this.loadingPast = false;
    };
    const handleError = (err: any, attemptedRefresh: boolean) => {
      // eslint-disable-next-line no-console
      console.debug('[HomeComponent] fixtures load error', err, '(refresh:', attemptedRefresh, ')');
      this.pastFixtures = [];
      this.loadingPast = false;
    };
    this.fixturesApi.getFixturesByDate(iso, this.season).subscribe({
      next: res => handleSuccess(res, false),
      error: err => handleError(err, false)
    });
    this.showCalendar = false;
  }

  // Derive display status using current time with 24h overdue rule
  statusLabel(f: { dateTime: string; homeScore: number | null; awayScore: number | null; status?: any }): string {
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
    // fallback
    const d = new Date(iso);
    return d.getTime();
  }

  // Determine if a fixture has been analysed based on presence of team colors in cache
  private hasAnalysisColors(item: { leagueId?: number; fixture: any }): boolean {
    try {
      const leagueId = item?.leagueId ?? undefined;
      const f = item?.fixture || {};
      const home = (f.homeTeam || this.normalizeTeamName(f, 'home') || '').toString().trim();
      const away = (f.awayTeam || this.normalizeTeamName(f, 'away') || '').toString().trim();
      if (!home && !away) return false;
      const c1 = home ? this.colorCache.getTeamColor(home, leagueId) : null;
      const c2 = away ? this.colorCache.getTeamColor(away, leagueId) : null;
      return !!(c1 || c2);
    } catch { return false; }
  }

  // Comparator: unanalysed fixtures first; within each group, order by kickoff time ascending
  private compareFixturesForToday = (a: { leagueId?: number; fixture: any }, b: { leagueId?: number; fixture: any }): number => {
    const aAnalysed = this.hasAnalysisColors(a) ? 1 : 0;
    const bAnalysed = this.hasAnalysisColors(b) ? 1 : 0;
    if (aAnalysed !== bAnalysed) return aAnalysed - bAnalysed; // 0 (unanalysed) comes before 1 (analysed)
    const ta = this.toUtcMillis(a.fixture?.dateTime);
    const tb = this.toUtcMillis(b.fixture?.dateTime);
    if (isNaN(ta) && isNaN(tb)) return 0;
    if (isNaN(ta)) return 1;
    if (isNaN(tb)) return -1;
    return ta - tb;
  }

  private resortTodayFlatData(): void {
    if (!Array.isArray(this.todayFlatData) || this.todayFlatData.length === 0) return;
    this.todayFlatData = this.todayFlatData.slice().sort(this.compareFixturesForToday);
  }

  // Returns {y,m,d} of given Date in a given IANA timezone using Intl API
  private getYMDInTz(date: Date, timeZone: string): { y: number; m: number; d: number } {
    const fmt = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
    const parts = fmt.formatToParts(date);
    const y = Number(parts.find(p => p.type === 'year')?.value);
    const m = Number(parts.find(p => p.type === 'month')?.value);
    const d = Number(parts.find(p => p.type === 'day')?.value);
    return { y, m, d };
  }

  // Check if an ISO string occurs on the same calendar day as now in a specific time zone
  private isSameDayInTz(iso: string | undefined, timeZone: string): boolean {
    if (!iso) return false;
    const dt = new Date(iso);
    if (isNaN(dt.getTime())) return false;
    const todayYMD = this.getYMDInTz(new Date(), timeZone);
    const thatYMD = this.getYMDInTz(dt, timeZone);
    return todayYMD.y === thatYMD.y && todayYMD.m === thatYMD.m && todayYMD.d === thatYMD.d;
  }

  // Leader helpers
  private isFixtureToday(f: { dateTime: string }): boolean {
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

  leaderTooltip(f: any): string | null {
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
        .sort((a: any, b: any) => (b.statPct - a.statPct))
        .map((e: any) => `${fmt(e.category)}: ${Math.round(e.statPct)}% (${e.statCount}/${e.matchesPlayed})`)
        .join('; ');
      parts.push(`${teamLabel} leads in ${details}`);
    };

    build(f.homeTeam, homeKey);
    if (awayKey !== homeKey) {
      build(f.awayTeam, awayKey);
    }

    return parts.length ? parts.join(' • ') : null;
  }

  // New: robust display resolver for team names at render-time
  displayTeamName(f: any, side: 'home' | 'away'): string {
    if (!f) return '';
    const keys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of keys) {
      const v = (f as any)[k];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    // Common nested structures
    const nested = side === 'home'
      ? ((f as any).home || (f as any).home_team || (f as any).teams?.home || (f as any).teamHome)
      : ((f as any).away || (f as any).away_team || (f as any).teams?.away || (f as any).teamAway);
    if (nested) {
      if (typeof nested === 'string' && nested.trim()) return nested.trim();
      if (typeof nested.name === 'string' && nested.name.trim()) return nested.name.trim();
      if (typeof nested.team === 'string' && nested.team.trim()) return nested.team.trim();
      if (typeof nested.title === 'string' && nested.title.trim()) return nested.title.trim();
    }
    // Fallback to normalized fields if present
    const fallback = side === 'home' ? f.homeTeam : f.awayTeam;
    return typeof fallback === 'string' ? (fallback || '').trim() : '';
  }

  // Style for colored team pills based on Fixtures Analysis cache
  teamPillStyle(teamName: string | null | undefined, leagueId?: number | null): {[k: string]: string} {
    const name = (teamName || '').toString().trim();
    if (!name) return {};
    try {
      const color = this.colorCache.getTeamColor(name, leagueId ?? undefined);
      if (!color) return {};
      return {
        background: color,
        color: '#ffffff',
        padding: '2px 6px',
        borderRadius: '6px',
        border: '1px solid rgba(255,255,255,0.15)'
      };
    } catch { return {}; }
  }

  // React to color updates from Fixtures Analysis tab
  private _onColorsUpdated = (_ev: Event) => {
    try {
      this.zone.run(() => {
        // Resort based on updated analysis colors and refresh view
        this.resortTodayFlatData();
        try { this.cdr.detectChanges(); } catch {}
      });
    } catch { /* no-op */ }
  };
 
   // Navigate to Played Matches Summary (Fixture Analysis) with preselected teams
   // Reuse the exact logic used in Fixtures tab: h2hHome, h2hAway, and optional leagueId
   goToAnalysis(league: { leagueId?: number; leagueName?: string } | null, f: any) {
    if (!f) return;
    const leagueId = league?.leagueId ?? null;
    this.router.navigate(['/played-matches-summary'], {
      queryParams: {
        h2hHome: (f?.homeTeam || this.normalizeTeamName(f, 'home') || ''),
        h2hAway: (f?.awayTeam || this.normalizeTeamName(f, 'away') || ''),
        ...(leagueId ? { leagueId } : {})
      }
    });
  }

  // trackBy to stabilize DOM and performance
  trackByFixtureId(index: number, item: { fixture: any }): string | number {
    const f = item?.fixture as any;
    return (f && (typeof f.id === 'number' || typeof f.id === 'string')) ? f.id : `${f?.homeTeam || ''}-${f?.awayTeam || ''}-${f?.dateTime || index}`;
  }
}

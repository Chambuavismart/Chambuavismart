import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { NgFor, NgIf, DatePipe, NgClass } from '@angular/common';
import { FixturesService, LeagueFixturesResponse } from '../services/fixtures.service';
import { GlobalLeadersContainerComponent } from '../components/global-leaders-container/global-leaders-container.component';
import { GlobalLeadersService, GlobalLeader } from '../services/global-leaders.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, NgFor, NgIf, NgClass, DatePipe, GlobalLeadersContainerComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {
  private fixturesApi = inject(FixturesService);
  private router = inject(Router);
  private leadersApi = inject(GlobalLeadersService);
  private route = inject(ActivatedRoute);

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
  todayLoading = false;

  // When a past date is selected, hide today's fixtures section
  get showOnlySelectedDate(): boolean {
    return !!(this.selectedDate && this.todayIso && this.selectedDate < this.todayIso);
  }

  // Total count of today's fixtures across all leagues
  get todayCount(): number {
    return this.todayFlat.length;
  }

  // Flattened, time-sorted list of today's fixtures across all leagues
  get todayFlat(): { leagueId: number; leagueName: string; leagueCountry?: string; fixture: { id: number; round: string; dateTime: string; homeTeam: string; awayTeam: string; homeScore: number | null; awayScore: number | null; status: any; }; }[] {
    const items: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
    for (const lg of this.todayFixtures || []) {
      const fs = lg?.fixtures || [];
      for (const f of fs) {
        if (!f) continue;
        items.push({ leagueId: lg.leagueId, leagueName: lg.leagueName, leagueCountry: lg.leagueCountry, fixture: f });
      }
    }
    items.sort((a, b) => {
      const ta = this.toUtcMillis(a.fixture?.dateTime);
      const tb = this.toUtcMillis(b.fixture?.dateTime);
      if (isNaN(ta) && isNaN(tb)) return 0;
      if (isNaN(ta)) return 1; // push invalid/unknown times to bottom
      if (isNaN(tb)) return -1;
      return ta - tb; // earliest first
    });
    return items;
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
  }

  ngOnDestroy(): void {
    window.removeEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
  }

  private loadToday(): void {
    this.todayLoading = true;
    this.todayFixtures = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching TODAY fixtures for', this.todayIso);
    this.fixturesApi.getFixturesByDate(this.todayIso, this.season).subscribe({
      next: res => {
        this.todayFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] TODAY fixtures loaded:', this.todayFixtures.length);
        this.todayLoading = false;
      },
      error: err => {
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] TODAY fixtures load error', err);
        this.todayFixtures = []; this.todayLoading = false;
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

  // Navigate to Played Matches Summary (Fixture Analysis) with preselected teams
  // Reuse the exact logic used in Fixtures tab: h2hHome, h2hAway, and optional leagueId
  goToAnalysis(league: { leagueId?: number; leagueName?: string } | null, f: any) {
    if (!f) return;
    const leagueId = league?.leagueId ?? null;
    this.router.navigate(['/played-matches-summary'], {
      queryParams: {
        h2hHome: f?.homeTeam ?? '',
        h2hAway: f?.awayTeam ?? '',
        ...(leagueId ? { leagueId } : {})
      }
    });
  }
}

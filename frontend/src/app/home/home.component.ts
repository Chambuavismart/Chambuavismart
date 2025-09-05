import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgFor, NgIf, DatePipe, NgClass } from '@angular/common';
import { FixturesService, LeagueFixturesResponse } from '../services/fixtures.service';
import { GlobalLeadersContainerComponent } from '../components/global-leaders-container/global-leaders-container.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, NgFor, NgIf, NgClass, DatePipe, GlobalLeadersContainerComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {
  private fixturesApi = inject(FixturesService);

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
    this.loadToday();
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
    // allow only dates that have fixtures (past or future)
    if (!this.availableDates.has(iso)) {
      // Still surface a UX response: show empty state for clarity
      this.selectedDate = iso;
      this.pastFixtures = [];
      this.loadingPast = false;
      // eslint-disable-next-line no-console
      console.debug('[HomeComponent] selectDate (no fixtures for)', iso);
      return;
    }
    this.selectedDate = iso;
    this.loadingPast = true;
    this.pastFixtures = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching fixtures for date', iso);
    this.fixturesApi.getFixturesByDate(iso, this.season).subscribe({
      next: res => {
        // defensive: ensure array
        this.pastFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] fixtures loaded:', this.pastFixtures.length);
        this.loadingPast = false;
      },
      error: err => {
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] fixtures load error', err);
        this.pastFixtures = []; this.loadingPast = false; }
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
}

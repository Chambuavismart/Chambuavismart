import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { FixturesService } from '../services/fixtures.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgIf, NgFor],
  template: `
    <nav class="topnav">
      <div class="nav-inner">
        <a class="brand" routerLink="/">
          <svg class="logo" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
            <defs>
              <linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
                <stop offset="0%" stop-color="#19b562"/>
                <stop offset="100%" stop-color="#0ea5e9"/>
              </linearGradient>
            </defs>
            <circle cx="16" cy="16" r="14" fill="url(#g)"/>
            <path d="M10 17l4 4 8-8" fill="none" stroke="#04110a" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span class="brand-text">ChambuaViSmart</span>
        </a>
        <div class="actions">
          <div class="calendar-wrap">
            <button class="calendar-btn" (click)="toggleCalendar()" aria-haspopup="dialog" [attr.aria-expanded]="showCalendar" title="Select fixtures date">
              <span class="cal-icon"></span>
              <span class="cal-text">Fixtures Calendar</span>
            </button>
            <div class="calendar-pop" *ngIf="showCalendar">
              <div class="cal-inner">
                <div class="cal-header">
                  <button (click)="prevMonth()" class="nav">←</button>
                  <div class="month">{{ calendarMonthLabel }}</div>
                  <button (click)="nextMonth()" class="nav">→</button>
                </div>
                <div class="dow">
                  <div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div><div>Sun</div>
                </div>
                <div class="grid">
                  <button *ngFor="let d of daysGrid" (click)="selectDate(d.iso)" [class.dim]="!d.inMonth">
                    <span>{{ d.day }}</span>
                    <span *ngIf="d.hasFixtures" class="dot"></span>
                  </button>
                </div>
                <div class="hint">Dates with a dot have fixtures. You can select any date.</div>
              </div>
            </div>
          </div>

          <button class="burger" (click)="open = !open" aria-label="Toggle navigation">
            <span></span><span></span><span></span>
          </button>
        </div>
        <div class="links" [class.open]="open">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Home</a>
          <a routerLink="/fixtures" routerLinkActive="active">Fixtures</a>
          <a routerLink="/match-analysis" routerLinkActive="active">Match Analysis</a>
          <a routerLink="/form-guide" routerLinkActive="active">Form Guide</a>
          <a routerLink="/quick-insights" routerLinkActive="active">Quick Insights</a>
          <a routerLink="/league" routerLinkActive="active">League Table</a>
          <a routerLink="/data-management" routerLinkActive="active">Data Management</a>
          <a routerLink="/team-search" routerLinkActive="active">Team Search</a>
          <a routerLink="/played-matches-summary" routerLinkActive="active">Fixtures Analysis</a>
          <a routerLink="/analysis-pdfs" routerLinkActive="active">Fixture Analysis History</a>
          <a routerLink="/streak-insights" routerLinkActive="active">Streak Insights</a>
          <a routerLink="/over-1-5" routerLinkActive="active">Over 1.5</a>
          <a routerLink="/team-outcome-distribution" routerLinkActive="active">Team Outcome distribution</a>
          <a routerLink="/admin" routerLinkActive="active">Admin</a>
        </div>
      </div>
    </nav>
  `,
  styles: [`
    :host { display:block; }
    .topnav { position: sticky; top: 0; z-index: 50; background: #0b1220; border-bottom: 1px solid #1f2937; color: #e6eef8; }
    .nav-inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; flex-wrap: wrap; gap: 8px 12px; }
    .brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: inherit; min-width: 0; }
    .logo { height: 28px; width: auto; display: block; }
    .brand-text { font-weight: 800; letter-spacing: .2px; }
    .actions { display:flex; align-items:center; gap:10px; margin-left:auto; }
    .burger { background: transparent; border: 0; cursor: pointer; padding: 6px; border-radius: 6px; }
    .burger span { display:block; width: 22px; height: 2px; background: #e6eef8; margin: 4px 0; }
    .links { display: flex; align-items: center; gap: 10px 12px; flex: 1 1 auto; justify-content: flex-end; flex-wrap: wrap; min-width: 0; }
    .links a { color: #cfe0f4; text-decoration: none; padding: 8px 10px; border-radius: 8px; transition: background .15s ease, color .15s ease; white-space: nowrap; }
    .links a:hover { background: #0f172a; color: #ffffff; }
    .links a.active { background: #19b562; color: #04110a; }

    .calendar-wrap { position: relative; }
    .calendar-btn { display:inline-flex; align-items:center; gap:6px; padding:6px 10px; border-radius:6px; border:1px solid #334155; background:#0f172a; color:#e6eef8; cursor:pointer; }
    .calendar-btn:hover { background:#111827; }
    .cal-icon { width:14px; height:14px; border:2px solid #1e6bd6; border-radius:2px; display:inline-block; }
    .calendar-pop { position:absolute; right:0; top:100%; margin-top:8px; z-index:1003; }
    .cal-inner { background:#0b1220; color:#e6eef8; border:1px solid #1f2937; border-radius:8px; box-shadow:0 10px 30px rgba(0,0,0,.35); padding:10px; min-width:280px; }
    .cal-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:6px; }
    .cal-header .nav { border:1px solid #334155; background:#0f172a; color:#e6eef8; border-radius:6px; padding:2px 8px; cursor:pointer; }
    .month { font-weight:800; }
    .dow { display:grid; grid-template-columns: repeat(7, 1fr); gap:6px; font-size:12px; text-align:center; color:#9fb3cd; margin-bottom:6px; }
    .grid { display:grid; grid-template-columns: repeat(7, 1fr); gap:6px; }
    .grid button { position:relative; height:32px; border:1px solid #334155; background:#0f172a; color:#e6eef8; border-radius:6px; cursor:pointer; }
    .grid button.dim { opacity:.6; }
    .grid .dot { position:absolute; left:50%; transform:translateX(-50%); bottom:4px; width:6px; height:6px; background:#1e6bd6; border-radius:50%; }
    .hint { margin-top:6px; font-size:12px; color:#9fb3cd; }

    @media (max-width: 900px) {
      .links { position: absolute; left: 0; right: 0; top: 100%; background: #0b1220; border-bottom: 1px solid #1f2937; display: none; flex-direction: column; align-items: stretch; padding: 8px; flex-wrap: nowrap; }
      .links.open { display: flex; }
      .links a { padding: 10px 12px; }
    }
  `]
})
export class NavbarComponent { 
  open = false;
  showCalendar = false;
  private fixturesApi = inject(FixturesService);
  private router = inject(Router);

  today = new Date();
  calendarYear = this.today.getFullYear();
  calendarMonth = this.today.getMonth(); // 0-based
  availableDates = new Set<string>();

  get calendarMonthLabel(): string {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    const month = d.toLocaleString('en-GB', { month: 'long' });
    return `${month} ${this.calendarYear}`;
  }

  toggleCalendar(){
    this.showCalendar = !this.showCalendar;
    if (this.showCalendar) this.fetchAvailableDates();
  }

  prevMonth(){
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() - 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  nextMonth(){
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() + 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  fetchAvailableDates(){
    const year = this.calendarYear;
    const month = this.calendarMonth + 1;
    this.fixturesApi.getAvailableDates(year, month).subscribe(ds => {
      this.availableDates = new Set((ds ?? []).filter(Boolean));
    });
  }

  get daysGrid(): { day:number; inMonth:boolean; iso:string; hasFixtures:boolean; }[] {
    const first = new Date(this.calendarYear, this.calendarMonth, 1);
    const startDay = first.getDay() === 0 ? 6 : first.getDay() - 1; // Monday=0
    const daysInMonth = new Date(this.calendarYear, this.calendarMonth + 1, 0).getDate();
    const prevMonthDays = new Date(this.calendarYear, this.calendarMonth, 0).getDate();

    const grid: { day:number; inMonth:boolean; iso:string; hasFixtures:boolean; }[] = [];

    for (let i = startDay - 1; i >= 0; i--) {
      const day = prevMonthDays - i;
      const d = new Date(this.calendarYear, this.calendarMonth - 1, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: false, iso, hasFixtures: this.availableDates.has(iso) });
    }
    for (let day = 1; day <= daysInMonth; day++) {
      const d = new Date(this.calendarYear, this.calendarMonth, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: true, iso, hasFixtures: this.availableDates.has(iso) });
    }
    while (grid.length % 7 !== 0) {
      const last = grid[grid.length - 1];
      const lastDate = this.parseIsoLocal(last.iso);
      const nextDate = new Date(lastDate.getFullYear(), lastDate.getMonth(), lastDate.getDate() + 1);
      const iso = this.toIsoLocal(nextDate);
      grid.push({ day: nextDate.getDate(), inMonth: false, iso, hasFixtures: this.availableDates.has(iso) });
    }

    return grid;
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

  selectDate(iso: string){
    this.showCalendar = false;
    this.open = false;
    this.router.navigate(['/'], { queryParams: { date: iso } });
  }
}

import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FixturesService, FixtureDTO, LeagueFixturesResponse } from '../services/fixtures.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';

interface SidebarFixtureItem {
  idx: number;
  fixture: FixtureDTO;
  leagueId?: number;
  leagueName?: string;
  leagueCountry?: string;
}

interface DateGroup {
  date: string; // ISO YYYY-MM-DD
  items: SidebarFixtureItem[];
}

@Component({
  selector: 'app-fixtures-sidebar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <aside class="fixtures-sidebar" *ngIf="groups.length">
      <h3 class="title">This Week's Fixtures</h3>
      <div class="date-group" *ngFor="let g of groups">
        <button class="date-head" (click)="toggleGroup(g.date)" [attr.aria-expanded]="isOpen(g.date) ? 'true' : 'false'" aria-controls="list-{{ g.date }}">
          <span class="label">{{ g.date | date:'EEEE, d MMM' }}</span>
          <span class="spacer"></span>
          <span class="count">{{ g.items.length }}</span>
          <span class="chev" [class.open]="isOpen(g.date)">▾</span>
        </button>
        <ol class="list" *ngIf="isOpen(g.date)" [attr.id]="'list-' + g.date">
          <li *ngFor="let it of g.items; let i = index" (click)="openOver15(it)" class="row" [title]="tooltip(it)">
            <div class="line">
              <span class="team home" [ngStyle]="teamPillStyle(it.fixture.homeTeam, it.leagueId)" [innerHTML]="formatTeamLabelHtml(it.fixture.homeTeam, it.leagueId)"></span>
              <span class="vs">vs</span>
              <span class="team away" [ngStyle]="teamPillStyle(it.fixture.awayTeam, it.leagueId)" [innerHTML]="formatTeamLabelHtml(it.fixture.awayTeam, it.leagueId)"></span>
            </div>
            <div class="meta">
              <span class="lg">{{ it.leagueCountry || '-' }} • {{ it.leagueName || 'League' }}</span>
              <span class="dt">{{ it.fixture.dateTime | date:'yyyy-MM-dd HH:mm' }}</span>
            </div>
          </li>
        </ol>
      </div>
    </aside>
  `,
  styles: [`
    .fixtures-sidebar { position: sticky; top: 0; max-height: 100vh; overflow: auto; padding: 12px; border-left: 1px solid #334155; background: #0f172a; color:#e6eef8; }
    .title { margin: 4px 0 10px; font-size: 14px; font-weight: 700; color: #e6eef8; }
    .date-head { margin: 10px 0 6px; font-weight: 800; color:#cfe0f4; border-bottom:1px solid #334155; padding:8px 6px; display:flex; align-items:center; width:100%; background:transparent; border-radius:6px; cursor:pointer; }
    .date-head:hover { background:#13233a; }
    .date-head .spacer { flex:1 1 auto; }
    .date-head .count { font-weight:700; color:#e6eef8; opacity:0.9; margin-right:6px; }
    .date-head .chev { transition: transform .15s ease; display:inline-block; }
    .date-head .chev.open { transform: rotate(180deg); }
    .list { list-style: decimal inside; margin: 0; padding: 0 0 4px 0; }
    .row { cursor: pointer; padding: 8px 6px; border-radius: 8px; margin: 0 0 6px; transition: background .15s, transform .05s; border:1px solid transparent; }
    .row:hover { background: #13233a; border-color:#3b4a61; }
    .line { display:flex; align-items:center; gap:8px; font-weight: 700; }
    .team { display:inline-block; }
    .vs { opacity: .9; color:#cfe0f4; }
    .meta { font-size: 12px; color: #cfe0f4; display: flex; gap: 8px; margin-top:2px; }
    .dt { opacity: .85; }
  `]
})
export class FixturesSidebarComponent implements OnInit {
  private fixturesApi = inject(FixturesService);
  private router = inject(Router);
  private colorCache = inject(AnalysisColorCacheService);

  items: SidebarFixtureItem[] = [];
  groups: DateGroup[] = [];
  // Accordion state: only one open at a time
  openDate: string | null = null;
  private todayIso: string = '';

  ngOnInit(): void {
    // Build a 7-day window starting today (local timezone, server is Africa/Nairobi but we use ISO dates for the API)
    const today = new Date();
    today.setHours(0,0,0,0);
    this.todayIso = today.toISOString().slice(0,10);
    // Default: all groups closed initially; user chooses which to open
    this.openDate = null
    const days: string[] = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(today.getTime());
      d.setDate(today.getDate() + i);
      const iso = d.toISOString().slice(0,10); // YYYY-MM-DD
      days.push(iso);
    }
    // Fetch fixtures per day and flatten
    const promises = days.map(date => this.fixturesApi.getFixturesByDate(date).toPromise());
    Promise.all(promises).then((perDay) => {
      const merged: SidebarFixtureItem[] = [];
      let counter = 1;
      perDay.forEach((dayResp) => {
        (dayResp || []).forEach((grp: LeagueFixturesResponse) => {
          const leagueId = grp.leagueId;
          const leagueName = grp.leagueName;
          const leagueCountry = grp.leagueCountry;
          (grp.fixtures || []).forEach((fx: FixtureDTO) => {
            merged.push({ idx: counter++, fixture: fx, leagueId, leagueName, leagueCountry });
          });
        });
      });
      // Sort by date/time ascending
      merged.sort((a, b) => (new Date(a.fixture.dateTime).getTime()) - (new Date(b.fixture.dateTime).getTime()));
      // Re-number after sort
      this.items = merged.map((m, i) => ({ ...m, idx: i + 1 }));
      // Build date groups
      const map = new Map<string, SidebarFixtureItem[]>();
      for (const it of this.items) {
        const d = (it.fixture.dateTime || '').toString().slice(0,10);
        if (!map.has(d)) map.set(d, []);
        map.get(d)!.push(it);
      }
      this.groups = Array.from(map.entries()).sort((a,b) => a[0].localeCompare(b[0]))
        .map(([date, items]) => ({ date, items }));
      // If today's date has no group, collapse all by default
      if (!this.groups.some(g => g.date === this.todayIso)) {
        this.openDate = null;
      }
    }).catch((_err) => {
      this.items = [];
      this.groups = [];
      // On error, keep all closed
      this.openDate = null;
    });
  }

  isOpen(date: string): boolean { return this.openDate === date; }
  toggleGroup(date: string): void {
    this.openDate = (this.openDate === date) ? null : date;
  }

  // Style for colored team pills (mirrors HomeComponent logic at a high level)
  teamPillStyle(teamName: string | null | undefined, leagueId?: number | null): {[k: string]: string} {
    const name = (teamName || '').toString().trim();
    if (!name) return {};
    try {
      const colorRaw = this.colorCache.getTeamColor(name, leagueId ?? undefined);
      if (!colorRaw) return {};
      const s = colorRaw.toString().toLowerCase();
      let bg: string | null = null;
      if (s.includes('green') || s.includes('#16a34a') || s.includes('16,160,16')) bg = '#16a34a';
      else if (s.includes('red') || s.includes('#ef4444') || s.includes('204, 43, 59')) bg = '#ef4444';
      else if (s.includes('orange') || s.includes('#f59e0b') || s.includes('255, 165, 0')) bg = '#f59e0b';
      else return {};
      const style: {[k: string]: string} = {
        background: bg,
        color: '#ffffff',
        padding: '2px 6px',
        borderRadius: '6px',
        border: '1px solid rgba(255,255,255,0.15)'
      };
      if (this.colorCache.isDoubleGreen(name, leagueId ?? undefined)) {
        style.boxShadow = 'inset 0 0 0 3px #16a34a, inset 0 0 0 7px rgba(22,163,74,0.65), 0 0 0 2px #16a34a, 0 0 10px 3px rgba(22,163,74,0.75)';
      }
      return style;
    } catch { return {}; }
  }

  formatTeamLabelHtml(teamName: string | null | undefined, leagueId?: number | null) {
    const name = (teamName || '').toString().trim();
    if (!name) return '';
    try {
      const hasD = this.colorCache.hasDrawHeavyD(name, leagueId ?? undefined);
      const hasFire = this.colorCache.isDoubleGreen(name, leagueId ?? undefined);
      const streak = this.colorCache.getTeamStreakCount(name, leagueId ?? undefined);
      const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const parts: string[] = [];
      if (hasD) {
        parts.push('<span style="font-weight:800;color:#ff7a00;text-shadow:0 0 6px rgba(255,122,0,0.85), 0 0 12px rgba(255,122,0,0.6);font-size:1.05em;letter-spacing:0.5px;">D</span>');
      }
      if (hasFire) parts.push('🔥');
      const safeName = esc(name);
      const label = streak && streak > 0 ? `${safeName} <span style='opacity:0.95;font-weight:700'>( ${streak} )</span>` : safeName;
      parts.push(label);
      return parts.join(' ');
    } catch { return name; }
  }

  tooltip(it: SidebarFixtureItem): string {
    const f = it.fixture;
    const when = new Date(f.dateTime).toISOString().replace('T',' ').slice(0,16);
    return `${it.leagueCountry || ''} • ${it.leagueName || ''} • ${when}`.trim();
  }

  openOver15(it: SidebarFixtureItem){
    const f = it.fixture;
    // Navigate to Over 1.5 route with query params that identify the fixture
    this.router.navigate(['/over-1-5'], {
      queryParams: {
        home: f.homeTeam,
        away: f.awayTeam,
        dt: f.dateTime,
        lg: it.leagueName || undefined,
        c: it.leagueCountry || undefined
      }
    });
  }
}

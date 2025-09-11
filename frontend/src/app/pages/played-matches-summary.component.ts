import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchService, TeamBreakdownDto, H2HSuggestion, H2HMatchDto, FormSummaryDto } from '../services/match.service';
import { ConfigService, FlagsDto } from '../services/config.service';
import { TeamService, TeamSuggestion } from '../services/team.service';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, takeUntil } from 'rxjs';

@Component({
  selector: 'app-played-matches-summary',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="container">
      <h1>Played Matches</h1>

      <div class="kpi-card">
        <div class="kpi-title">Total Matches Played</div>
        <div class="kpi-value" [class.loading]="loading">
          <ng-container *ngIf="!loading; else loadingTpl">{{ total | number }}</ng-container>
        </div>
        <ng-template #loadingTpl>Loading…</ng-template>
      </div>

      <div class="search-card">
        <label class="label" for="teamSearch">Search Team (min 3 chars)</label>
        <input id="teamSearch" type="text" [(ngModel)]="query" (input)="onQueryChange(query)" placeholder="Start typing team name…" />
        <ul class="suggestions" *ngIf="suggestions.length > 0 && !selectedTeam">
          <li *ngFor="let s of suggestions" (click)="selectTeam(s)">{{ s.name }}<span *ngIf="s.country"> ({{ s.country }})</span></li>
        </ul>
        <div class="hint" *ngIf="query && query.length < 3">Keep typing…</div>
      </div>

      <div class="profile-card" *ngIf="selectedTeam">
        <div class="profile-header">
          <div class="team-name">{{ selectedTeam.name }}</div>
          <button class="clear" (click)="clearSelection()">Clear</button>
        </div>
        <div class="stat-row">
          <div class="stat-label">Matches involved (from Played matches):</div>
          <div class="stat-value" [class.loading]="loadingTeamCount">
            <ng-container *ngIf="!loadingTeamCount; else loadingTpl2">{{ teamCount }}</ng-container>
            <ng-template #loadingTpl2>…</ng-template>
          </div>
        </div>
        <div class="breakdown">
          <div class="breakdown-title">Results Breakdown (all matches played)</div>
          <div class="breakdown-row" [class.loading]="loadingBreakdown" *ngIf="!loadingBreakdown; else loadingTpl3">
            <div>Wins: {{ breakdown?.wins ?? 0 }} <span class="pct">{{ pct(breakdown?.wins) }}%</span></div>
            <div>Draws: {{ breakdown?.draws ?? 0 }} <span class="pct">{{ pct(breakdown?.draws) }}%</span></div>
            <div>Losses: {{ breakdown?.losses ?? 0 }} <span class="pct">{{ pct(breakdown?.losses) }}%</span></div>
          </div>
          <div class="breakdown-row" [class.loading]="loadingBreakdown" *ngIf="!loadingBreakdown">
            <div>BTTS: {{ breakdown?.btts ?? 0 }} <span class="pct">{{ pct(breakdown?.btts) }}%</span></div>
          </div>
          <div class="breakdown-row" [class.loading]="loadingBreakdown" *ngIf="!loadingBreakdown">
            <div>Over 2.5: {{ breakdown?.over25 ?? 0 }} <span class="pct">{{ pct(breakdown?.over25) }}%</span></div>
          </div>
          <ng-template #loadingTpl3>Loading…</ng-template>
        </div>
      </div>

      <!-- H2H Search -->
      <div class="search-card">
        <label class="label" for="h2hSearch">Search Head-to-Head (min 3 chars)</label>
        <input id="h2hSearch" type="text" [(ngModel)]="h2hQuery" (input)="onH2HQueryChange(h2hQuery)" placeholder="Type team(s) name…" />
        <ul class="suggestions" *ngIf="h2hSuggestions.length > 0 && !h2hSelected">
          <li *ngFor="let s of h2hSuggestions">
            <div class="h2h-option" (click)="selectH2H(s.teamA, s.teamB)">{{ s.teamA }} vs {{ s.teamB }}</div>
            <div class="h2h-option" (click)="selectH2H(s.teamB, s.teamA)">{{ s.teamB }} vs {{ s.teamA }}</div>
          </li>
        </ul>
        <div class="hint" *ngIf="h2hQuery && h2hQuery.length < 3">Keep typing…</div>
      </div>

      <!-- Profiles side by side when H2H selected -->
      <div class="profiles-grid" *ngIf="h2hSelected">
        <div class="profile-card">
          <div class="profile-header">
            <div class="team-name">{{ h2hHome }}</div>
            <button class="clear" (click)="clearH2H()">Clear H2H</button>
          </div>
          <div class="stat-row">
            <div class="stat-label">Matches involved:</div>
            <div class="stat-value" [class.loading]="loadingHomeCount">
              <ng-container *ngIf="!loadingHomeCount; else loadingHomeCountTpl">{{ homeCount }}</ng-container>
              <ng-template #loadingHomeCountTpl>…</ng-template>
            </div>
          </div>
          <div class="breakdown">
            <div class="breakdown-title">Results Breakdown</div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown; else loadingHomeBdTpl">
              <div [ngClass]="h2hClass('wins')">Wins: {{ homeBreakdown?.wins ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.wins, homeBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('draws')">Draws: {{ homeBreakdown?.draws ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.draws, homeBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('losses')">Losses: {{ homeBreakdown?.losses ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.losses, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div [ngClass]="h2hClass('btts')">BTTS: {{ homeBreakdown?.btts ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.btts, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div [ngClass]="h2hClass('over25')">Over 2.5: {{ homeBreakdown?.over25 ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.over25, homeBreakdown?.total) }}%</span></div>
            </div>
            <ng-template #loadingHomeBdTpl>Loading…</ng-template>
          </div>
        </div>
        <div class="profile-card">
          <div class="profile-header">
            <div class="team-name">{{ h2hAway }}</div>
            <button class="clear" (click)="clearH2H()">Clear H2H</button>
          </div>
          <div class="stat-row">
            <div class="stat-label">Matches involved:</div>
            <div class="stat-value" [class.loading]="loadingAwayCount">
              <ng-container *ngIf="!loadingAwayCount; else loadingAwayCountTpl">{{ awayCount }}</ng-container>
              <ng-template #loadingAwayCountTpl>…</ng-template>
            </div>
          </div>
          <div class="breakdown">
            <div class="breakdown-title">Results Breakdown</div>
            <div class="breakdown-row" [class.loading]="loadingAwayBreakdown" *ngIf="!loadingAwayBreakdown; else loadingAwayBdTpl">
              <div [ngClass]="h2hClass('wins', false)">Wins: {{ awayBreakdown?.wins ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.wins, awayBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('draws', false)">Draws: {{ awayBreakdown?.draws ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.draws, awayBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('losses', false)">Losses: {{ awayBreakdown?.losses ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.losses, awayBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingAwayBreakdown" *ngIf="!loadingAwayBreakdown">
              <div [ngClass]="h2hClass('btts', false)">BTTS: {{ awayBreakdown?.btts ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.btts, awayBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingAwayBreakdown" *ngIf="!loadingAwayBreakdown">
              <div [ngClass]="h2hClass('over25', false)">Over 2.5: {{ awayBreakdown?.over25 ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.over25, awayBreakdown?.total) }}%</span></div>
            </div>
            <ng-template #loadingAwayBdTpl>Loading…</ng-template>
          </div>
        </div>
      </div>

      <!-- Insights Panel -->
      <div class="info-card" *ngIf="h2hSelected && predictiveOn">
        <div class="info-title">Insights</div>
        <div class="info-text">{{ buildInsightsText() }}</div>
      </div>

      <!-- H2H GD KPI Card -->
      <div class="kpi-card" *ngIf="h2hSelected && predictiveOn">
        <div class="kpi-title">H2H Goal Differential ({{ h2hHome }} perspective)</div>
        <div class="kpi-value" [class.loading]="false">
          <span title="Aggregate GD across valid H2H matches">{{ formatSigned(gdAggregate) }}</span>
        </div>
        <div class="breakdown-row" style="margin-top:8px;">
          <div>Average GD: <strong>{{ formatAvg(gdAverage) }}</strong></div>
          <div class="hint" *ngIf="gdInsufficient">Insufficient data: fewer than 3 valid H2H matches</div>
        </div>
      </div>

      <!-- Form & Streaks (Last 5) -->
      <div class="profiles-grid" *ngIf="h2hSelected && predictiveOn">
        <div class="profile-card">
          <div class="breakdown-title">{{ h2hHome }} — Last 5</div>
          <div class="breakdown-row" *ngIf="homeForm; else homeFormLoading">
            <div class="form-badges">
              <span *ngFor="let r of (homeForm?.recentResults || []); let i = index" [ngClass]="badgeClass(r)">{{ r }}</span>
            </div>
            <div class="sparkline" *ngIf="homeForm?.ppgSeries?.length">
              <div class="spark-bar" *ngFor="let v of homeForm?.ppgSeries" [style.height]="barHeight(v)" [title]="v.toFixed(1)"></div>
            </div>
            <div class="ppg-fallback" *ngIf="homeForm?.ppgSeries?.length">PPG Trend: {{ formatPpgTrend(homeForm?.ppgSeries || []) }}</div>
            <div class="hint" *ngIf="(homeForm?.recentResults?.length || 0) < 5">Insufficient data.</div>
          </div>
          <ng-template #homeFormLoading>
            <div class="hint">Loading…</div>
          </ng-template>
          <div class="breakdown-row" *ngIf="homeForm">
            <div>Streak: <strong>{{ formatStreak(homeForm?.currentStreak) }}</strong></div>
            <div>Win rate: <strong>{{ homeForm?.winRate ?? 0 }}%</strong></div>
            <div>Points: <strong>{{ homeForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
        </div>
        <div class="profile-card">
          <div class="breakdown-title">{{ h2hAway }} — Last 5</div>
          <div class="breakdown-row" *ngIf="awayForm; else awayFormLoading">
            <div class="form-badges">
              <span *ngFor="let r of (awayForm?.recentResults || []); let i = index" [ngClass]="badgeClass(r)">{{ r }}</span>
            </div>
            <div class="sparkline" *ngIf="awayForm?.ppgSeries?.length">
              <div class="spark-bar" *ngFor="let v of awayForm?.ppgSeries" [style.height]="barHeight(v)" [title]="v.toFixed(1)"></div>
            </div>
            <div class="ppg-fallback" *ngIf="awayForm?.ppgSeries?.length">PPG Trend: {{ formatPpgTrend(awayForm?.ppgSeries || []) }}</div>
            <div class="hint" *ngIf="(awayForm?.recentResults?.length || 0) < 5">Insufficient data.</div>
          </div>
          <ng-template #awayFormLoading>
            <div class="hint">Loading…</div>
          </ng-template>
          <div class="breakdown-row" *ngIf="awayForm">
            <div>Streak: <strong>{{ formatStreak(awayForm?.currentStreak) }}</strong></div>
            <div>Win rate: <strong>{{ awayForm?.winRate ?? 0 }}%</strong></div>
            <div>Points: <strong>{{ awayForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
        </div>
      </div>

      <!-- H2H Matches Table -->
      <div class="profile-card" *ngIf="h2hSelected">
        <div class="breakdown-title">Head-to-Head Results History</div>
        <table class="h2h-table" *ngIf="h2hMatches?.length; else noH2HMatches">
          <thead>
            <tr>
              <th>Year</th>
              <th>Date</th>
              <th>Home Team</th>
              <th>Away Team</th>
              <th>Result</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of h2hMatches">
              <td>{{ m.year }}</td>
              <td>{{ m.date }}</td>
              <td [ngClass]="homeClass(m)">{{ m.homeTeam }}</td>
              <td [ngClass]="awayClass(m)">{{ m.awayTeam }}</td>
              <td>{{ m.result }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #noH2HMatches>
          <div class="hint">No matches found. Check if team names match stored records, or try swapping orientation.</div>
        </ng-template>
      </div>

    </section>
  `,
  styles: [`
    .stat-better { color:#10b981; font-weight:700; }
    .stat-better .pct { color:#10b981; }
    .stat-equal { color:#f59e0b; font-weight:700; }
    .stat-equal .pct { color:#f59e0b; }
    .container { max-width: 1000px; margin: 0 auto; padding: 16px; color: #e6eef8; }
    h1 { margin: 0 0 16px; font-size: 22px; font-weight: 700; }
    .kpi-card, .search-card, .profile-card { background: #0b1220; border: 1px solid #1f2937; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.25); margin-top: 16px; }
    .kpi-title { color: #9fb6d4; font-weight: 600; margin-bottom: 8px; }
    .info-card { background:#051426; border:1px solid #1e3a5f; border-radius:12px; padding:16px; margin-top:16px; }
    .info-title { color:#93c5fd; font-weight:700; margin-bottom:6px; }
    .info-text { color:#cbd5e1; line-height:1.4; }
    .kpi-value { font-size: 40px; font-weight: 800; letter-spacing: .5px; color: #19b562; min-height: 48px; }
    .kpi-value.loading { color: #6b7280; }
    .label { display:block; color:#9fb6d4; margin-bottom:8px; }
    input[type="text"] { width:100%; padding:10px 12px; border-radius:8px; border:1px solid #334155; background:#0a1220; color:#e6eef8; }
    .suggestions { list-style:none; margin:8px 0 0; padding:0; border:1px solid #334155; border-radius:8px; background:#0a1220; max-height:220px; overflow:auto; }
    .suggestions li { padding:8px 12px; cursor:pointer; }
    .suggestions li:hover { background:#111827; }
    .h2h-option { padding:6px 12px; cursor:pointer; }
    .h2h-option:hover { background:#111827; }
    .hint { color:#6b7280; margin-top:6px; }
    .profile-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }
    .team-name { font-size:18px; font-weight:700; }
    .clear { background:#374151; color:#e5e7eb; border:none; padding:6px 10px; border-radius:8px; cursor:pointer; }
    .clear:hover { background:#4b5563; }
    .stat-row { display:flex; justify-content:space-between; align-items:center; }
    .stat-label { color:#9fb6d4; }
    .stat-value { font-size:24px; font-weight:800; color:#f59e0b; }
    .stat-value.loading { color:#6b7280; }
    .breakdown { margin-top: 12px; }
    .breakdown-title { color:#9fb6d4; margin-bottom:6px; font-weight:600; }
    .breakdown-row { display:flex; gap:16px; flex-wrap:wrap; }
    .breakdown-row .pct { color:#9fb6d4; margin-left:6px; }
    .profiles-grid { display:grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-badges { display:flex; gap:8px; align-items:center; }
    .sparkline { display:flex; gap:4px; align-items:flex-end; min-height:16px; }
    .spark-bar { width:6px; background:#19b562; border-radius:2px; opacity:0.9; }
    .ppg-fallback { color:#9fb6d4; font-size:12px; }
    .badge { display:inline-block; min-width:24px; text-align:center; padding:4px 8px; border-radius:999px; font-weight:800; }
    .badge-win { background:#065f46; color:#10b981; border:1px solid #10b981; }
    .badge-draw { background:#1f2937; color:#9ca3af; border:1px solid #9ca3af; }
    .badge-loss { background:#3f1d1d; color:#ef4444; border:1px solid #ef4444; }
    .h2h-table { width:100%; border-collapse: collapse; margin-top: 12px; }
    .h2h-table th, .h2h-table td { text-align:left; padding:8px; border-bottom:1px solid #334155; }
    .win { color: #10b981; font-weight: 700; } /* green */
    .loss { color: #ef4444; font-weight: 700; } /* red */
    .draw { color: #f59e0b; font-weight: 700; } /* orange */
    :host { display: block; }
    body { background: #0a0f1a; }
  `]
})
export class PlayedMatchesSummaryComponent implements OnInit, OnDestroy {
  private matchService = inject(MatchService);
  private teamService = inject(TeamService);
  private configService = inject(ConfigService);

  total = 0;
  loading = true;

  // Single team search
  query = '';
  suggestions: TeamSuggestion[] = [];
  selectedTeam: TeamSuggestion | null = null;
  teamCount = 0;
  loadingTeamCount = false;

  breakdown: TeamBreakdownDto | null = null;
  loadingBreakdown = false;

  // Feature flag
  predictiveOn = false;

  // H2H
  h2hQuery = '';
  h2hSuggestions: H2HSuggestion[] = [];
  h2hSelected = false;
  h2hHome = '';
  h2hAway = '';
  h2hMatches: H2HMatchDto[] = [];
  homeForm: FormSummaryDto | null = null;
  awayForm: FormSummaryDto | null = null;
  // GD summary (client-side computation for UI, oriented to h2hHome)
  gdAggregate: number | null = null;
  gdAverage: number | null = null;
  gdInsufficient = true;

  homeCount = 0; awayCount = 0;
  loadingHomeCount = false; loadingAwayCount = false;
  homeBreakdown: TeamBreakdownDto | null = null; awayBreakdown: TeamBreakdownDto | null = null;
  loadingHomeBreakdown = false; loadingAwayBreakdown = false;

  private destroy$ = new Subject<void>();
  private query$ = new Subject<string>();
  private h2hQuery$ = new Subject<string>();

  // --- Insights composition (client-side for Played Matches tab) ---
  buildInsightsText(): string {
    if (!this.h2hSelected) return '';
    const parts: string[] = [];
    // GD
    if (this.gdAggregate !== null && this.gdAggregate !== undefined) {
      parts.push(`${this.h2hHome} has ${this.formatSigned(this.gdAggregate)} GD in H2H`);
    }
    // Streaks
    const streakText = (s?: string | null) => {
      if (!s || s === '0') return null;
      if (/^\d+[WDL]$/i.test(s)) return `${s} in a row`;
      return s;
    };
    const hs = streakText(this.homeForm?.currentStreak);
    const as = streakText(this.awayForm?.currentStreak);
    if (hs) parts.push(`${this.h2hHome} on ${hs}`);
    if (as) parts.push(`${this.h2hAway} on ${as}`);
    // PPG trends
    const ppgTrend = (series?: number[] | null) => {
      if (!series || series.length < 2) return null;
      const start = series[series.length - 1] ?? 0;
      const end = series[0] ?? 0;
      return `${start.toFixed(1)} → ${end.toFixed(1)}`;
    };
    const hpt = ppgTrend(this.homeForm?.ppgSeries);
    const apt = ppgTrend(this.awayForm?.ppgSeries);
    if (hpt) parts.push(`${this.h2hHome} improved from ${hpt} PPG`);
    if (apt) parts.push(`${this.h2hAway} improved from ${apt} PPG`);

    if (parts.length === 0) return 'Limited match history available.';
    return parts.join('; ');
  }

  ngOnInit(): void {
    // Load flags
    this.configService.getFlags().pipe(takeUntil(this.destroy$)).subscribe({
      next: (f) => {
        this.predictiveOn = !!f?.predictiveH2HPhase1Enabled;
        // If user already selected H2H and features just turned on, backfill
        if (this.h2hSelected && this.predictiveOn) {
          // Fetch forms if missing
          if (!this.homeForm && this.h2hHome) {
            this.matchService.getFormByTeamName(this.h2hHome).subscribe({ next: v => this.homeForm = v });
          }
          if (!this.awayForm && this.h2hAway) {
            this.matchService.getFormByTeamName(this.h2hAway).subscribe({ next: v => this.awayForm = v });
          }
          // Compute GD if not computed
          if (this.gdAggregate === null && this.h2hMatches?.length) {
            this.computeGDFromMatches();
          }
        }
      },
      error: () => { this.predictiveOn = false; }
    });

    this.matchService.getTotalPlayedMatches().subscribe({
      next: (v) => { this.total = v ?? 0; this.loading = false; },
      error: () => { this.total = 0; this.loading = false; }
    });

    this.query$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap(q => q && q.trim().length >= 3 ? this.teamService.searchTeams(q.trim()) : of([] as TeamSuggestion[])),
        takeUntil(this.destroy$)
      )
      .subscribe(list => {
        this.suggestions = list ?? [];
      });

    this.h2hQuery$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap(q => q && q.trim().length >= 3 ? this.matchService.suggestH2H(q.trim()) : of([] as H2HSuggestion[])),
        takeUntil(this.destroy$)
      )
      .subscribe(list => {
        this.h2hSuggestions = list ?? [];
      });
  }

  onQueryChange(q: string) {
    this.selectedTeam = null; // reset selection when typing
    this.teamCount = 0;
    this.breakdown = null;
    this.query$.next(q || '');
  }

  selectTeam(s: TeamSuggestion) {
    this.selectedTeam = s;
    this.suggestions = [];
    this.loadingTeamCount = true;
    this.loadingBreakdown = true;
    // Count across all leagues/seasons by team name to include historical duplicates of team entities
    this.matchService.getPlayedTotalByTeamName(s.name).subscribe({
      next: v => { this.teamCount = v ?? 0; this.loadingTeamCount = false; },
      error: () => { this.teamCount = 0; this.loadingTeamCount = false; }
    });
    // Get breakdown across all matches played (all seasons)
    this.matchService.getResultsBreakdownByTeamName(s.name).subscribe({
      next: b => { this.breakdown = b; this.loadingBreakdown = false; },
      error: () => { this.breakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0 }; this.loadingBreakdown = false; }
    });
  }

  clearSelection() {
    this.selectedTeam = null;
    this.teamCount = 0;
    this.breakdown = null;
  }

  onH2HQueryChange(q: string) {
    this.h2hSelected = false;
    this.h2hHome = '';
    this.h2hAway = '';
    this.h2hMatches = [];
    this.h2hQuery$.next(q || '');
  }

  private computeGDFromMatches() {
    const list = this.h2hMatches ?? [];
    if (!this.h2hHome) { this.gdAggregate = null; this.gdAverage = null; this.gdInsufficient = true; return; }
    let agg = 0; let valid = 0;
    for (const m of list) {
      const s = this.parseScore(m?.result || '');
      if (!s) continue;
      const hn = (m.homeTeam || '').trim();
      const an = (m.awayTeam || '').trim();
      let scored = 0, conceded = 0;
      if (hn && hn.localeCompare(this.h2hHome, undefined, { sensitivity: 'accent', usage: 'search' }) === 0) {
        scored = s.hg; conceded = s.ag;
      } else if (an && an.localeCompare(this.h2hHome, undefined, { sensitivity: 'accent', usage: 'search' }) === 0) {
        scored = s.ag; conceded = s.hg;
      } else {
        // Fallback: assume orientation is as listed (home perspective) if names mismatch
        scored = s.hg; conceded = s.ag;
      }
      agg += (scored - conceded);
      valid++;
    }
    this.gdAggregate = valid > 0 ? agg : null;
    this.gdAverage = valid > 0 ? +(agg / valid).toFixed(2) : null;
    this.gdInsufficient = valid < 3;
  }

  selectH2H(home: string, away: string) {
    this.h2hSelected = true;
    this.h2hHome = home;
    this.h2hAway = away;
    this.h2hSuggestions = [];

    // Load profiles for each side
    this.loadingHomeCount = this.loadingAwayCount = true;
    this.loadingHomeBreakdown = this.loadingAwayBreakdown = true;
    this.homeForm = null; this.awayForm = null;

    this.matchService.getPlayedTotalByTeamName(home).subscribe({
      next: v => { this.homeCount = v ?? 0; this.loadingHomeCount = false; },
      error: () => { this.homeCount = 0; this.loadingHomeCount = false; }
    });
    this.matchService.getResultsBreakdownByTeamName(home).subscribe({
      next: b => { this.homeBreakdown = b; this.loadingHomeBreakdown = false; },
      error: () => { this.homeBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0 }; this.loadingHomeBreakdown = false; }
    });

    this.matchService.getPlayedTotalByTeamName(away).subscribe({
      next: v => { this.awayCount = v ?? 0; this.loadingAwayCount = false; },
      error: () => { this.awayCount = 0; this.loadingAwayCount = false; }
    });
    this.matchService.getResultsBreakdownByTeamName(away).subscribe({
      next: b => { this.awayBreakdown = b; this.loadingAwayBreakdown = false; },
      error: () => { this.awayBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0 }; this.loadingAwayBreakdown = false; }
    });

    // Load last-5 form for each team (only if feature flag ON)
    if (this.predictiveOn) {
      this.matchService.getFormByTeamName(home).subscribe({
        next: f => { this.homeForm = f; },
        error: () => { this.homeForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 }; }
      });
      this.matchService.getFormByTeamName(away).subscribe({
        next: f => { this.awayForm = f; },
        error: () => { this.awayForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 }; }
      });
    }

    // Load H2H matches respecting orientation
    this.matchService.getH2HMatches(home, away).subscribe({
      next: list => { this.h2hMatches = list ?? []; if (this.predictiveOn) this.computeGDFromMatches(); },
      error: () => { this.h2hMatches = []; if (this.predictiveOn) this.computeGDFromMatches(); }
    });
  }

  clearH2H() {
    this.h2hSelected = false;
    this.h2hHome = '';
    this.h2hAway = '';
    this.h2hMatches = [];
    this.homeForm = null;
    this.awayForm = null;
    this.gdAggregate = null;
    this.gdAverage = null;
    this.gdInsufficient = true;
  }

  pct(v?: number | null): string {
    const total = this.breakdown?.total ?? 0;
    const n = v ?? 0;
    if (total <= 0) return '0';
    const p = Math.round((n / total) * 100);
    return String(p);
  }

  pct2(v?: number | null, total?: number | null): string {
    const t = total ?? 0;
    const n = v ?? 0;
    if (t <= 0) return '0';
    const p = Math.round((n / t) * 100);
    return String(p);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // --- H2H result helpers ---
  private parseScore(result: string | null | undefined): { hg: number; ag: number } | null {
    if (!result) return null;
    const m = result.match(/^(\d+)\s*-\s*(\d+)$/);
    if (!m) return null;
    const hg = parseInt(m[1], 10);
    const ag = parseInt(m[2], 10);
    if (Number.isNaN(hg) || Number.isNaN(ag)) return null;
    return { hg, ag };
  }
  homeClass(m: H2HMatchDto): string | undefined {
    const s = this.parseScore(m.result);
    if (!s) return undefined;
    if (s.hg > s.ag) return 'win';
    if (s.hg < s.ag) return 'loss';
    return 'draw';
  }
  awayClass(m: H2HMatchDto): string | undefined {
    const s = this.parseScore(m.result);
    if (!s) return undefined;
    if (s.ag > s.hg) return 'win';
    if (s.ag < s.hg) return 'loss';
    return 'draw';
  }

  // --- H2H percentage comparison helpers for coloring ---
  private pctFor(b?: TeamBreakdownDto | null, key?: 'wins'|'draws'|'losses'|'btts'|'over25'): number {
    if (!b || !key) return 0;
    const total = b.total ?? 0;
    const val = (b as any)[key] ?? 0;
    if (!total || total <= 0) return 0;
    return Math.round((val / total) * 100);
  }

  h2hClass(key: 'wins'|'draws'|'losses'|'btts'|'over25', isHome: boolean = true): string | undefined {
    const hp = this.pctFor(this.homeBreakdown, key);
    const ap = this.pctFor(this.awayBreakdown, key);
    if (hp === ap) return 'stat-equal';
    if (isHome) {
      return hp > ap ? 'stat-better' : undefined;
    } else {
      return ap > hp ? 'stat-better' : undefined;
    }
  }

  formatSigned(v: number | null): string {
    if (v === null || v === undefined) return '—';
    if (v > 0) return '+' + v;
    return String(v);
  }
  formatAvg(v: number | null): string {
    if (v === null || v === undefined || Number.isNaN(v)) return '—';
    const sign = v > 0 ? '+' : '';
    return sign + v.toFixed(2);
  }

  // --- Form rendering helpers ---
  badgeClass(r: string | null | undefined): string {
    const v = (r || '').toUpperCase();
    if (v === 'W') return 'badge badge-win';
    if (v === 'D') return 'badge badge-draw';
    if (v === 'L') return 'badge badge-loss';
    return 'badge';
  }
  formatStreak(s: string | null | undefined): string {
    if (!s) return '—';
    // s like "3W" -> "3W in a row"
    if (/^\d+[WDL]$/i.test(s)) return `${s} in a row`;
    return s;
  }
  barHeight(v: number | null | undefined): string {
    const val = typeof v === 'number' ? v : 0;
    const h = Math.max(2, Math.round((val / 3) * 24)); // 0..3 -> 0..24 px
    return h + 'px';
  }
  formatPpgTrend(series: number[]): string {
    if (!series || series.length === 0) return '—';
    const start = series[series.length - 1]; // oldest in our most-recent-first list
    const end = series[0]; // most recent
    return `${(start ?? 0).toFixed(1)} → ${(end ?? 0).toFixed(1)}`;
  }
}

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
        <div class="section-desc">Total number of matches in Chambuavismart, across all seasons, leagues, and competitions.</div>
        <div class="kpi-value" [class.loading]="loading">
          <ng-container *ngIf="!loading; else loadingTpl">{{ total | number }}</ng-container>
        </div>
        <ng-template #loadingTpl>Loading…</ng-template>
      </div>

      <div class="search-card">
        <label class="label" for="teamSearch">Search Team (min 3 chars)</label>
        <div class="section-desc">Find a team to view their overall Played Matches stats across the entire dataset.</div>
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
        <div class="section-desc">Team-wide summary based on all played matches recorded in Chambuavismart.</div>
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
        <div class="section-desc">Compare two teams to analyze their head-to-head history. Pick the order to set home/away orientation.</div>
        <input id="h2hSearch" type="text" [(ngModel)]="h2hQuery" (input)="onH2HQueryChange(h2hQuery)" placeholder="Type team(s) name…" />
        <ul class="suggestions" *ngIf="h2hSuggestions.length > 0 && !h2hSelected">
          <li *ngFor="let s of h2hSuggestions">
            <div class="h2h-option" (click)="selectH2H(s.teamA, s.teamB)">{{ s.teamA }} vs {{ s.teamB }}</div>
            <div class="h2h-option" (click)="selectH2H(s.teamB, s.teamA)">{{ s.teamB }} vs {{ s.teamA }}</div>
            <div class="hint" *ngIf="showTeamHint">Teams not found in this league. Try another spelling or league.</div>
            <div class="hint" *ngIf="showDataHint">No matches in this season.</div>
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
          <div class="section-desc">Summary of all played matches for {{ h2hHome }} across Chambuavismart (all seasons and leagues). These figures are not limited to this H2H pairing.</div>
          <div class="stat-row">
            <div class="stat-label">Matches involved:</div>
            <div class="stat-value" [class.loading]="loadingHomeCount">
              <ng-container *ngIf="!loadingHomeCount; else loadingHomeCountTpl">{{ homeCount }}</ng-container>
              <ng-template #loadingHomeCountTpl>…</ng-template>
            </div>
          </div>
          <div class="breakdown">
            <div class="breakdown-title">Results Breakdown</div>
            <div class="section-desc">Win/Draw/Loss share across all played matches. BTTS counts games where both teams scored; Over 2.5 counts games with 3 or more total goals.</div>
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
            <div class="section-desc">Win/Draw/Loss share across all played matches. BTTS counts games where both teams scored; Over 2.5 counts games with 3 or more total goals.</div>
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
        <div class="section-desc">Quick narrative summary of key H2H signals including goal differential, current streaks, and trends.</div>
        <div class="info-text">{{ buildInsightsText() }}</div>
      </div>

      <!-- H2H GD KPI Card -->
      <div class="kpi-card" *ngIf="h2hSelected && predictiveOn">
        <div class="kpi-title">H2H Goal Differential ({{ h2hHome }} perspective)</div>
        <div class="section-desc">Aggregate and average goal difference across valid head-to-head matches, viewed from {{ h2hHome }}’s perspective.</div>
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
          <div class="breakdown-title">{{ h2hHome }} — Last 5 <span *ngIf="homeForm">({{ (homeForm?.recentResults?.length || 0) }} matches available)</span></div>
          <div class="section-desc">Recent head-to-head form vs {{ h2hAway }} (up to 5 matches). Points use W=3, D=1, L=0.</div>
          <div class="breakdown-row" *ngIf="homeForm; else homeFormLoading">
            <div class="form-badges compact">
              <span class="compact-seq">
                <ng-container *ngFor="let r of (homeForm?.recentResults || []); let i = index">
                  <span [ngClass]="letterClass(r)">{{ r }}</span><span *ngIf="i < (homeForm?.recentResults?.length || 0) - 1"></span>
                </ng-container>
              </span>
            </div>
            <div class="sparkline" *ngIf="homeForm?.ppgSeries?.length">
              <div class="spark-bar" *ngFor="let v of homeForm?.ppgSeries" [style.height]="barHeight(v)" [title]="v.toFixed(1)"></div>
            </div>
            <div class="ppg-fallback" *ngIf="homeForm?.ppgSeries?.length">PPG Trend: {{ formatPpgTrend(homeForm?.ppgSeries || []) }}</div>
          </div>
          <ng-template #homeFormLoading>
            <div class="hint">Loading…</div>
          </ng-template>
          <div class="hint" *ngIf="homeForm && (homeForm?.recentResults?.length || 0) === 0">No recent matches found</div>
          <div class="breakdown-row" *ngIf="homeForm">
            <div>Streak: <strong>{{ formatStreak(homeForm?.currentStreak) }}</strong></div>
            <div>Win rate: <strong>{{ homeForm?.winRate ?? 0 }}%</strong></div>
            <div>Points: <strong>{{ homeForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
        </div>
        <div class="profile-card">
          <div class="breakdown-title">{{ h2hAway }} — Last 5 <span *ngIf="awayForm">({{ (awayForm?.recentResults?.length || 0) }} matches available)</span></div>
          <div class="section-desc">Recent head-to-head form vs {{ h2hHome }} (up to 5 matches). Points use W=3, D=1, L=0.</div>
          <div class="breakdown-row" *ngIf="awayForm; else awayFormLoading">
            <div class="form-badges compact">
              <span class="compact-seq">
                <ng-container *ngFor="let r of (awayForm?.recentResults || []); let i = index">
                  <span [ngClass]="letterClass(r)">{{ r }}</span><span *ngIf="i < (awayForm?.recentResults?.length || 0) - 1"></span>
                </ng-container>
              </span>
            </div>
            <div class="sparkline" *ngIf="awayForm?.ppgSeries?.length">
              <div class="spark-bar" *ngFor="let v of awayForm?.ppgSeries" [style.height]="barHeight(v)" [title]="v.toFixed(1)"></div>
            </div>
            <div class="ppg-fallback" *ngIf="awayForm?.ppgSeries?.length">PPG Trend: {{ formatPpgTrend(awayForm?.ppgSeries || []) }}</div>
          </div>
          <ng-template #awayFormLoading>
            <div class="hint">Loading…</div>
          </ng-template>
          <div class="hint" *ngIf="awayForm && (awayForm?.recentResults?.length || 0) === 0">No recent matches found</div>
          <div class="breakdown-row" *ngIf="awayForm">
            <div>Streak: <strong>{{ formatStreak(awayForm?.currentStreak) }}</strong></div>
            <div>Win rate: <strong>{{ awayForm?.winRate ?? 0 }}%</strong></div>
            <div>Points: <strong>{{ awayForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
        </div>
      </div>

      <!-- H2H Matches Table (Chosen Orientation) -->
      <div class="profile-card" *ngIf="h2hSelected">
        <div class="breakdown-title">Head-to-Head Results History</div>
        <div class="section-desc">Complete list of prior meetings between the selected teams. Table shows the matches in the chosen orientation (home vs away). The total below includes both orientations.</div>
        <div class="hint" *ngIf="h2hAnyCount !== null">Total head-to-head matches between {{ h2hHome }} and {{ h2hAway }} (all orientations): <strong>{{ h2hAnyCount }}</strong></div>
        <table class="h2h-table" *ngIf="h2hMatches?.length; else noH2HMatches">
          <thead>
            <tr>
              <th>Year</th>
              <th>Date</th>
              <th>Home Team</th>
              <th>Away Team</th>
              <th>Result</th>
              <th>Season</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of h2hMatches">
              <td>{{ m.year }}</td>
              <td>{{ m.date }}</td>
              <td [ngClass]="homeClass(m)">{{ m.homeTeam }}</td>
              <td [ngClass]="awayClass(m)">{{ m.awayTeam }}</td>
              <td>{{ m.result }}</td>
              <td>{{ m.season || 'Archive' }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #noH2HMatches>
          <div class="hint">No prior head-to-head matches</div>
        </ng-template>
      </div>

      <!-- H2H Matches Table (All Orientations) -->
      <div class="profile-card" *ngIf="h2hSelected">
        <div class="breakdown-title">Head-to-Head Results (All Orientations)</div>
        <div class="section-desc">This table includes matches where either team was home or away, combining both orientations for a complete history.</div>
        <table class="h2h-table" *ngIf="h2hMatchesAll?.length; else noH2HAll">
          <thead>
            <tr>
              <th>Year</th>
              <th>Date</th>
              <th>Home Team</th>
              <th>Away Team</th>
              <th>Result</th>
              <th>Season</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of h2hMatchesAll">
              <td>{{ m.year }}</td>
              <td>{{ m.date }}</td>
              <td [ngClass]="homeClass(m)">{{ m.homeTeam }}</td>
              <td [ngClass]="awayClass(m)">{{ m.awayTeam }}</td>
              <td>{{ m.result }}</td>
              <td>{{ m.season || 'Archive' }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #noH2HAll>
          <div class="hint">No matches found across both orientations</div>
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
    .section-desc { color:#9aa4b2; font-size:12px; margin:4px 0 8px; }
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
    .form-badges.compact { gap:4px; }
    .compact-seq { font-weight:800; letter-spacing:2px; font-size:16px; }
    .letter-win { color:#10b981; }
    .letter-draw { color:#f59e0b; }
    .letter-loss { color:#ef4444; }
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
  h2hMatchesAll: H2HMatchDto[] = [];
  h2hAnyCount: number | null = null;
  homeForm: FormSummaryDto | null = null;
  awayForm: FormSummaryDto | null = null;
  // UI hints
  showTeamHint = false;
  showDataHint = false;
  // league/season context (fallbacks for H2H form call)
  leagueId: number = ((window as any).__LEAGUE_ID__ as number) || 1; // default EPL=1
  seasonName: string = ((window as any).__SEASON_NAME__ as string) || '2025/2026';
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
          // ID-only flow: no fallback name-based form fetching here.
          if (!this.homeForm) { this.homeForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 }; }
          if (!this.awayForm) { this.awayForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 }; }
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
    this.showTeamHint = false;
    this.showDataHint = false;
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

  async selectH2H(home: any, away: any) {
    this.h2hAnyCount = null;
    // Robustly extract team names from string or object inputs
    const getTeamName = (t: any): string | null => {
      if (!t) return null;
      if (typeof t === 'string') return t.trim();
      const tryProps = ['name', 'teamName', 'label', 'value', 'text'];
      for (const p of tryProps) {
        const v = (t as any)[p];
        if (typeof v === 'string') return v.trim();
      }
      // Single-key object with string value
      if (typeof t === 'object') {
        const keys = Object.keys(t);
        if (keys.length === 1) {
          const v = (t as any)[keys[0]];
          if (typeof v === 'string') return v.trim();
        }
      }
      // Fallback: try to stringify and extract common fields
      try {
        const s = JSON.stringify(t);
        const m = s.match(/"(?:name|teamName|label|value|text)"\s*:\s*"([^"]+)"/);
        if (m && m[1]) return m[1].trim();
      } catch (_) { /* ignore */ }
      console.error('[PlayedMatches] Unexpected team structure:', JSON.stringify(t, null, 2));
      return null;
    };

    // Precise raw log for diagnostics
    try {
      console.log('[PlayedMatches] Raw H2H input:', JSON.stringify({ home, away }));
    } catch {
      console.log('[PlayedMatches] Raw H2H input:', { home, away });
    }

    let homeName = getTeamName(home);
    let awayName = getTeamName(away);

    // Final coercion safeguard to avoid passing objects or "[object Object]" to services
    const coerce = (v: any): string | null => {
      if (typeof v === 'string') return v.trim();
      try {
        const s = String(v).trim();
        if (!s || s.toLowerCase() === '[object object]') return null;
        return s;
      } catch {
        return null;
      }
    };
    homeName = coerce(homeName);
    awayName = coerce(awayName);

    if (!homeName || !awayName || homeName.length < 2 || awayName.length < 2) {
      console.error('[PlayedMatches] Invalid team names from:', { home, away, homeName, awayName });
      this.showTeamHint = true;
      return;
    }
    console.log('[PlayedMatches] Extracted names for league', this.leagueId, ':', JSON.stringify({ homeName, awayName }, null, 2));

    this.h2hSelected = true;
    this.h2hHome = homeName;
    this.h2hAway = awayName;
    this.h2hSuggestions = [];

    // Load profiles for each side
    this.loadingHomeCount = this.loadingAwayCount = true;
    this.loadingHomeBreakdown = this.loadingAwayBreakdown = true;
    this.homeForm = null; this.awayForm = null;

    this.matchService.getPlayedTotalByTeamName(homeName).subscribe({
      next: v => { this.homeCount = v ?? 0; this.loadingHomeCount = false; },
      error: () => { this.homeCount = 0; this.loadingHomeCount = false; }
    });
    this.matchService.getResultsBreakdownByTeamName(homeName).subscribe({
      next: b => { this.homeBreakdown = b; this.loadingHomeBreakdown = false; },
      error: () => { this.homeBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0 }; this.loadingHomeBreakdown = false; }
    });

    this.matchService.getPlayedTotalByTeamName(awayName).subscribe({
      next: v => { this.awayCount = v ?? 0; this.loadingAwayCount = false; },
      error: () => { this.awayCount = 0; this.loadingAwayCount = false; }
    });
    this.matchService.getResultsBreakdownByTeamName(awayName).subscribe({
      next: b => { this.awayBreakdown = b; this.loadingAwayBreakdown = false; },
      error: () => { this.awayBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0 }; this.loadingAwayBreakdown = false; }
    });

    // Load last-5 form for each team (only if feature flag ON)
    if (this.predictiveOn) {
      this.homeForm = null; this.awayForm = null;

      // Resolve league/season context
      let seasonId: number | null = (window as any).__SEASON_ID__ ?? null;
      if (seasonId == null || typeof seasonId !== 'number') {
        try {
          const sid = await this.matchService.getSeasonId(this.leagueId, this.seasonName).toPromise();
          if (typeof sid === 'number') seasonId = sid;
        } catch (e) {
          console.warn('[PlayedMatches] seasonId resolution failed', e);
        }
      }
      if (seasonId == null || typeof seasonId !== 'number') {
        console.warn('[PlayedMatches] No valid seasonId; skipping Last-5. Check league/season context.');
        this.showDataHint = true;
      } else {
        // Resolve team IDs scoped to league
        let homeId: number | null = null;
        let awayId: number | null = null;
        try {
          homeId = await this.teamService.getScopedTeamId(homeName, this.leagueId).toPromise();
          awayId = await this.teamService.getScopedTeamId(awayName, this.leagueId).toPromise();
        } catch (e) {
          console.warn('[PlayedMatches] league-scoped team id resolution failed', e);
        }

        if (typeof homeId === 'number' && typeof awayId === 'number') {
          console.debug('[PlayedMatches] Resolved IDs', { homeId, awayId, seasonId });
          console.debug('[PlayedMatches] calling getH2HFormByIds', { homeId, awayId, seasonId });
          this.matchService.getH2HFormByIds(homeId, awayId, seasonId, 5).subscribe({
            next: list => {
              const safe = Array.isArray(list) ? list : [];
              const toSummary = (team: any, teamLabel: string): FormSummaryDto => {
                const seqStr: string = team?.last5?.streak || '0';
                const recent: string[] = [];
                const matches = team?.matches || [];
                for (let i = 0; i < Math.min(5, matches.length); i++) {
                  const m = matches[i];
                  const rs = (m?.result || '').split('-');
                  if (rs.length === 2) {
                    const hg = parseInt(rs[0], 10); const ag = parseInt(rs[1], 10);
                    if (!Number.isNaN(hg) && !Number.isNaN(ag)) {
                      const my = (m?.homeTeam || '').localeCompare(teamLabel, undefined, { sensitivity: 'accent', usage: 'search' }) === 0
                        ? hg : ((m?.awayTeam || '').localeCompare(teamLabel, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? ag : hg);
                      const opp = (m?.homeTeam || '').localeCompare(teamLabel, undefined, { sensitivity: 'accent', usage: 'search' }) === 0
                        ? ag : ((m?.awayTeam || '').localeCompare(teamLabel, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? hg : ag);
                      if (my > opp) recent.push('W'); else if (my === opp) recent.push('D'); else recent.push('L');
                    }
                  }
                }
                const winRate = team?.last5?.winRate ?? 0;
                // Compute points precisely from recent results: W=3, D=1, L=0.
                // Avoid using rounded PPG * count which can misestimate with partial data.
                const points = recent.reduce((acc, r) => acc + (r === 'W' ? 3 : r === 'D' ? 1 : 0), 0);
                return { recentResults: recent, currentStreak: seqStr, winRate: winRate, pointsEarned: points, ppgSeries: team?.last5?.ppgSeries } as any;
              };
              const homeEntry = safe.find(t => Number(t?.teamId) === homeId) || safe.find(t => t?.teamName?.toLowerCase?.() === homeName.toLowerCase()) || null;
              const awayEntry = safe.find(t => Number(t?.teamId) === awayId) || safe.find(t => t?.teamName?.toLowerCase?.() === awayName.toLowerCase()) || null;
              this.homeForm = homeEntry ? toSummary(homeEntry, homeName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = awayEntry ? toSummary(awayEntry, awayName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
            },
            error: (err) => {
              console.error('[PlayedMatches] getH2HFormByIds failed', err);
              this.homeForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
            }
          });
        } else {
          console.warn('[PlayedMatches] Could not resolve team IDs in league for', { leagueId: this.leagueId, home: homeName, away: awayName });
          this.showTeamHint = true;
          this.homeForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
          this.awayForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
        }
      }
    }

    // Load H2H matches respecting orientation
    this.matchService.getH2HMatches(homeName, awayName).subscribe({
      next: list => { this.h2hMatches = list ?? []; if (this.predictiveOn) this.computeGDFromMatches(); },
      error: () => { this.h2hMatches = []; if (this.predictiveOn) this.computeGDFromMatches(); }
    });

    // Load total H2H count regardless of orientation
    this.matchService.getH2HCountAnyOrientation(homeName, awayName).subscribe({
      next: c => { this.h2hAnyCount = (typeof c === 'number') ? c : 0; },
      error: () => { this.h2hAnyCount = 0; }
    });

    // Load H2H matches across both orientations
    this.matchService.getH2HMatchesAnyOrientation(homeName, awayName).subscribe({
      next: list => { this.h2hMatchesAll = list ?? []; },
      error: () => { this.h2hMatchesAll = []; }
    });
  }

  clearH2H() {
    this.h2hSelected = false;
    this.h2hHome = '';
    this.h2hAway = '';
    this.h2hMatches = [];
    this.h2hMatchesAll = [];
    this.homeForm = null;
    this.awayForm = null;
    this.gdAggregate = null;
    this.gdAverage = null;
    this.gdInsufficient = true;
    this.showTeamHint = false;
    this.showDataHint = false;
    this.h2hAnyCount = null;
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
  letterClass(r: string | null | undefined): string {
    const v = (r || '').toUpperCase();
    if (v === 'W') return 'letter-win';
    if (v === 'D') return 'letter-draw';
    if (v === 'L') return 'letter-loss';
    return '';
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

import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchService, TeamBreakdownDto, H2HSuggestion, H2HMatchDto, FormSummaryDto } from '../services/match.service';
import { ConfigService, FlagsDto } from '../services/config.service';
import { TeamService, TeamSuggestion } from '../services/team.service';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, takeUntil } from 'rxjs';
import { LeagueContextService } from '../services/league-context.service';
import { PoissonService, Predictions } from '../services/poisson.service';

@Component({
  selector: 'app-played-matches-summary',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="container">
      <h1>Fixtures Analysis</h1>

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
        <div class="section-desc">Find a team to view their overall Fixtures Analysis stats across the entire dataset.</div>
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
          <div class="stat-label">Matches involved (from Fixtures Analysis):</div>
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
            <div>Over 1.5: {{ breakdown?.over15 ?? 0 }} <span class="pct">{{ pct(breakdown?.over15) }}%</span></div>
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
            <div class="h2h-option" (click)="runHeadToHeadSearch(s.teamA, s.teamB)">{{ s.teamA }} vs {{ s.teamB }}</div>
            <div class="h2h-option" (click)="runHeadToHeadSearch(s.teamB, s.teamA)">{{ s.teamB }} vs {{ s.teamA }}</div>
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
              <div [ngClass]="h2hClass('over15')">Over 1.5: {{ homeBreakdown?.over15 ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.over15, homeBreakdown?.total) }}%</span></div>
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
              <div [ngClass]="h2hClass('over15', false)">Over 1.5: {{ awayBreakdown?.over15 ?? 0 }} <span class="pct">{{ pct2(awayBreakdown?.over15, awayBreakdown?.total) }}%</span></div>
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
          <div class="breakdown-title">{{ h2hHome }} — Last 5 <span *ngIf="homeSeasonResolved">({{ homeSeasonResolved }})</span>
                      <span class="hint" *ngIf="homeMatchesAvailableText"> — {{ homeMatchesAvailableText }}</span>
                    </div>
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
          <div class="breakdown-title">{{ h2hAway }} — Last 5 <span *ngIf="awaySeasonResolved">({{ awaySeasonResolved }})</span>
                      <span class="hint" *ngIf="awayMatchesAvailableText"> — {{ awayMatchesAvailableText }}</span>
                    </div>
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

      <!-- Analyse this fixture button and predictions -->
      <div class="profile-card" *ngIf="h2hSelected">
        <div class="profile-header">
          <div class="breakdown-title">Analyse this fixture</div>
          <div style="flex:1"></div>
          <button
            data-test="analyse-button"
            class="clear"
            [disabled]="!(h2hHome && h2hHome.length >= 3 && h2hAway && h2hAway.length >= 3)"
            (click)="analyseFixture()"
            title="Compute Poisson predictions for this H2H"
          >Analyse this fixture</button>
          <button
            *ngIf="predictions"
            class="clear"
            style="margin-left:8px;background:#2563eb;"
            (click)="downloadAsPDF()"
            title="Download analysis as PDF with watermark"
          >Download as PDF</button>
        </div>
        <div class="section-desc">Computes win/draw/loss, BTTS, Over 1.5 and Over 2.5 probabilities using a Poisson model based on the head-to-head goal averages and each team's overall sample size.</div>

        <ng-container *ngIf="predictions; else noPreds">
          <div class="kpi-card" data-test="predictions-table">
            <div class="kpi-title">Fixture Analysis Results</div>
            <table class="h2h-table">
              <thead>
                <tr>
                  <th>Outcome</th>
                  <th>Probability</th>
                </tr>
              </thead>
              <tbody>
                <tr><td>{{ h2hHome }} Win</td><td>{{ predictions?.teamAWin }}%</td></tr>
                <tr><td>Draw</td><td>{{ predictions?.draw }}%</td></tr>
                <tr><td>{{ h2hAway }} Win</td><td>{{ predictions?.teamBWin }}%</td></tr>
                <tr><td>Both Teams To Score (BTTS)</td><td>{{ predictions?.btts }}%</td></tr>
                <tr><td>Over 2.5 Goals</td><td>{{ predictions?.over25 }}%</td></tr>
                <tr><td>Over 3.5 Goals</td><td>{{ predictions?.over35 | number:'1.0-0' }}%</td></tr>
                <tr><td>Over 1.5 Goals</td><td>{{ predictions?.over15 }}%</td></tr>
              </tbody>
            </table>
            <div class="hint">Expected goals used: λ({{ h2hHome }}) = {{ predictions?.lambdaA }}, λ({{ h2hAway }}) = {{ predictions?.lambdaB }}</div>
            <div *ngIf="predictions?.isLimitedData" class="alert alert-warning" style="margin-top:8px; color:#f59e0b;">Limited H2H data; scores based on league averages.</div>

            <div class="kpi-title" style="margin-top:12px;">Most Probable Correct Scores</div>
            <table class="h2h-table" *ngIf="predictions?.correctScores?.length">
              <thead>
                <tr>
                  <th>Score</th>
                  <th>Probability</th>
                  <th style="width:50%"> </th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let cs of getCorrectScoresWithDisplay()">
                  <td>{{ cs.score }}</td>
                  <td>{{ cs.displayProb | number:'1.1-1' }}%</td>
                  <td>
                    <div class="bar" [style.width.%]="barWidth(cs.displayProb)"></div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </ng-container>
        <ng-template #noPreds>
          <div class="hint">Select a valid H2H pair and click "Analyse this fixture" to see predictions.</div>
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
    .bar { height:5px; background:#007bff; border-radius:2px; }
    :host { display: block; }
    body { background: #0a0f1a; }
  `]
})
export class PlayedMatchesSummaryComponent implements OnInit, OnDestroy {
  private matchService = inject(MatchService);
  private teamService = inject(TeamService);
  private configService = inject(ConfigService);
  private route = inject(ActivatedRoute);

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
  h2hHomeId: number | null = null;
  h2hAwayId: number | null = null;
  h2hMatches: H2HMatchDto[] = [];
  h2hMatchesAll: H2HMatchDto[] = [];
  h2hAnyCount: number | null = null;
  homeForm: FormSummaryDto | null = null;
  awayForm: FormSummaryDto | null = null;
  // New: resolved season and messages from backend Last 5 response
  homeSeasonResolved: string | null = null;
  awaySeasonResolved: string | null = null;
  homeMatchesAvailableText: string | null = null;
  awayMatchesAvailableText: string | null = null;
  homeWarnings: string[] = [];
  awayWarnings: string[] = [];
  // UI hints
  showTeamHint = false;
  showDataHint = false;
  // league/season context (fallbacks for H2H form call)
  leagueId: number | null = null;
  seasonName: string = ((window as any).__SEASON_NAME__ as string) || '2025/2026';
  // GD summary (client-side computation for UI, oriented to h2hHome)
  gdAggregate: number | null = null;
  gdAverage: number | null = null;
  gdInsufficient = true;

  homeCount = 0; awayCount = 0;
  loadingHomeCount = false; loadingAwayCount = false;
  homeBreakdown: TeamBreakdownDto | null = null; awayBreakdown: TeamBreakdownDto | null = null;
  loadingHomeBreakdown = false; loadingAwayBreakdown = false;

  // Predictions via Poisson
  predictions: Predictions | null = null;
  private poisson = inject(PoissonService);
    private leagueContext = inject(LeagueContextService);

  // Expose Math if needed in template calculations
  Math = Math;

  getCorrectScoresWithDisplay(): Array<{ score: string; probability: number; displayProb: number }> {
    const cs = this.predictions?.correctScores || [];
    if (!cs || cs.length === 0) return [] as any;
    const sum = cs.reduce((s, c) => s + (c?.probability || 0), 0);
    const cap = 25;
    const scale = sum > 0 ? Math.min(1, cap / sum) : 1;
    return cs.map(c => ({ score: c.score, probability: c.probability, displayProb: +(c.probability * scale).toFixed(1) }));
  }

  barWidth(p: number): number {
    const w = (p || 0) * 5; // scale up for visibility
    return w > 100 ? 100 : w;
  }

  analyseFixture(): void {
    if (!(this.h2hHome && this.h2hAway)) {
      this.predictions = null;
      return;
    }
    const teamA = { id: this.h2hHomeId, name: this.h2hHome, matchesInvolved: this.homeCount || (this.homeBreakdown as any)?.total || 1 };
    const teamB = { id: this.h2hAwayId, name: this.h2hAway, matchesInvolved: this.awayCount || (this.awayBreakdown as any)?.total || 1 };
    const h2hData = (this.h2hMatchesAll && this.h2hMatchesAll.length > 0) ? this.h2hMatchesAll : this.h2hMatches;
    const preds = this.poisson.calculatePredictions(teamA as any, teamB as any, h2hData as any[], {});
    this.predictions = preds;
    if ((preds as any)?.usedFallback) {
      const msg = 'Limited H2H data; using league averages for Poisson model.';
      console.warn('[PlayedMatches][AnalyseFixture]', msg, { h2hCount: h2hData?.length ?? 0, home: this.h2hHome, away: this.h2hAway });
      this.showDataHint = true;
      try { (window as any).alert?.(msg); } catch {}
    }
  }

  private destroy$ = new Subject<void>();
  private query$ = new Subject<string>();
  private h2hQuery$ = new Subject<string>();

  // Deep-link buffering to avoid early execution before flags/data are ready
  private _pendingHome: string | null = null;
  private _pendingAway: string | null = null;
  private _deepLinkHandled: boolean = false;

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
    // Initialize league context
    this.leagueId = this.leagueContext.getCurrentLeagueId();
    this.leagueContext.leagueId$.pipe(takeUntil(this.destroy$)).subscribe(id => { this.leagueId = id ?? null; });

    // Track flags readiness to ensure deep-link triggers after predictive flag is known
    let flagsReady = false;
    const tryProcessDeepLink = () => {
      if (!flagsReady) return;
      if (this._pendingHome && this._pendingAway && !this._deepLinkHandled) {
        this._deepLinkHandled = true;
        this.runHeadToHeadSearch(this._pendingHome, this._pendingAway);
      }
    };

    // Load flags
    this.configService.getFlags().pipe(takeUntil(this.destroy$)).subscribe({
      next: (f) => {
        this.predictiveOn = !!f?.predictiveH2HPhase1Enabled;
        flagsReady = true;
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
        // Process any pending deep-link now that flags are ready
        tryProcessDeepLink();
      },
      error: () => { this.predictiveOn = false; flagsReady = true; tryProcessDeepLink(); }
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

    // Auto-H2H from query params (e.g., ?h2hHome=Nantes&h2hAway=Lorient)
    this.route.queryParams
      .pipe(takeUntil(this.destroy$))
      .subscribe(params => {
        const lidParam = params?.['leagueId'];
        const lid = (lidParam != null) ? Number(lidParam) : NaN;
        if (!Number.isNaN(lid) && lid > 0) {
          // Set league context from navigation (Fixtures tab)
          this.leagueContext.setCurrentLeagueId(lid);
          this.leagueId = lid;
          console.log('[LeagueContext] Set leagueId from fixture:', lid);
        }
        const home = params?.['h2hHome'];
        const away = params?.['h2hAway'];
        if (home && away && typeof home === 'string' && typeof away === 'string') {
          // Buffer params and process after flags are ready to avoid running before dataset/flags load
          this._pendingHome = home;
          this._pendingAway = away;
          tryProcessDeepLink();
        }
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
      error: () => { this.breakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 }; this.loadingBreakdown = false; }
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

  private async promptSelectCandidate(teamName: string, candidates: TeamSuggestion[]): Promise<TeamSuggestion | null> {
    try {
      const key = `team_disambig:${(teamName || '').trim().toLowerCase()}`;
      // Cache hit
      const cached = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null;
      if (cached) {
        const cache = JSON.parse(cached);
        const match = (candidates || []).find(c => c.id === cache.id || c.leagueId === cache.leagueId);
        if (match) return match;
      }
      // Build options text
      const lines = (candidates || []).map((c, idx) => `${idx + 1}) ${c.name} – ${c.leagueId != null ? 'League ' + c.leagueId : 'Unknown league'}`);
      const chosen = window.prompt(`Multiple teams named "${teamName}" found.\nSelect the correct one by number:\n\n${lines.join('\n')}`, '1');
      const num = chosen ? parseInt(chosen, 10) : NaN;
      if (!Number.isNaN(num) && num >= 1 && num <= candidates.length) {
        const sel = candidates[num - 1];
        try {
          if (typeof localStorage !== 'undefined') localStorage.setItem(key, JSON.stringify({ id: sel.id, leagueId: sel.leagueId }));
        } catch {}
        return sel;
      }
    } catch (e) {
      console.warn('[PlayedMatches] Disambiguation prompt failed', e);
    }
    return null;
  }

  // Unified wrapper to ensure both manual search and deep-link follow the same workflow
  runHeadToHeadSearch(home: string, away: string) {
    const h = (home ?? '').toString().trim();
    const a = (away ?? '').toString().trim();
    if (!h || !a) {
      this.showTeamHint = true;
      return;
    }
    // Pre-fill the query input so user sees what was auto-searched, preserving orientation
    this.h2hQuery = `${h} vs ${a}`;
    // Delegate to the internal worker which performs full fetching and calculations
    this.selectH2H(h, a);
  }

  async selectH2H(home: any, away: any) {
    this.h2hAnyCount = null;
    this.h2hHomeId = null; this.h2hAwayId = null;
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
    console.log('[PlayedMatches] Extracted names for league', this.leagueId, ':', { homeName, awayName });

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
      error: () => { this.homeBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 }; this.loadingHomeBreakdown = false; }
    });

    this.matchService.getPlayedTotalByTeamName(awayName).subscribe({
      next: v => { this.awayCount = v ?? 0; this.loadingAwayCount = false; },
      error: () => { this.awayCount = 0; this.loadingAwayCount = false; }
    });
    this.matchService.getResultsBreakdownByTeamName(awayName).subscribe({
      next: b => { this.awayBreakdown = b; this.loadingAwayBreakdown = false; },
      error: () => { this.awayBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 }; this.loadingAwayBreakdown = false; }
    });

    // Load last-5 form for each team (only if feature flag ON)
    if (this.predictiveOn) {
      this.homeForm = null; this.awayForm = null;

      // Resolve league/season context
      let lid: number | null = (typeof this.leagueId === 'number' && this.leagueId > 0) ? this.leagueId : (this.leagueContext.getCurrentLeagueId() ?? null);
      let inferred = false;
      if (lid == null) {
        // Attempt to infer leagueId from team identities when user came via manual search
        try {
          const homeRes = await this.teamService.findByNameWithDisambiguation(homeName).toPromise();
          let homeInfo: any = homeRes?.team ?? null;
          if ((!homeInfo || !homeInfo.leagueId) && homeRes?.conflict && Array.isArray(homeRes.candidates)) {
            homeInfo = await this.promptSelectCandidate(homeName, homeRes.candidates);
          }

          const awayRes = await this.teamService.findByNameWithDisambiguation(awayName).toPromise();
          let awayInfo: any = awayRes?.team ?? null;
          if ((!awayInfo || !awayInfo.leagueId) && awayRes?.conflict && Array.isArray(awayRes.candidates)) {
            awayInfo = await this.promptSelectCandidate(awayName, awayRes.candidates);
          }

          const hL = (homeInfo && typeof (homeInfo as any).leagueId === 'number') ? (homeInfo as any).leagueId as number : null;
          const aL = (awayInfo && typeof (awayInfo as any).leagueId === 'number') ? (awayInfo as any).leagueId as number : null;
          if (hL && aL && hL === aL) {
            lid = hL;
            inferred = true;
            console.log('[PlayedMatches] Inferred leagueId from team selection:', lid);
            // Persist into global context so other services pick it up
            this.leagueContext.setCurrentLeagueId(lid);
            this.leagueId = lid;
          } else {
            console.warn('[PlayedMatches] Could not infer a common leagueId from selected teams', { homeInfo, awayInfo });
          }
        } catch (e) {
          console.warn('[PlayedMatches] League inference by team names failed', e);
        }
      }

      let seasonId: number | null = (window as any).__SEASON_ID__ ?? null;
      if (seasonId == null || typeof seasonId !== 'number') {
        if (typeof lid === 'number') {
          try {
            // If league was inferred, request latest season by passing empty seasonName to trigger backend fallback
            const seasonNameForFetch = inferred ? '' : this.seasonName;
            const sid = await this.matchService.getSeasonId(lid, seasonNameForFetch as any).toPromise();
            if (typeof sid === 'number') seasonId = sid;
          } catch (e) {
            console.warn('[PlayedMatches] seasonId resolution failed', e);
          }
        } else {
          console.warn('[PlayedMatches] Missing active leagueId; cannot resolve seasonId.');
        }
      }
      if (seasonId == null || typeof seasonId !== 'number') {
        // Attempt name-based autoSeason flow when seasonId is unavailable
        if (typeof lid === 'number') {
          console.warn('[PlayedMatches] No valid seasonId; using autoSeason name-based flow.');
          this.matchService.getH2HFormByNamesWithAutoSeason(homeName, awayName, lid!, this.seasonName, 5).subscribe({
            next: list => {
              const safe: any[] = Array.isArray(list) ? list : [];
              const mapTeam = (team: any, label: string): FormSummaryDto => {
                const seqStr: string = team?.last5?.streak || '0';
                const recent: string[] = [];
                const matches = team?.matches || [];
                for (let i = 0; i < Math.min(5, matches.length); i++) {
                  const m = matches[i];
                  const rs = (m?.result || '').split('-');
                  if (rs.length === 2) {
                    const hg = parseInt(rs[0], 10); const ag = parseInt(rs[1], 10);
                    if (!Number.isNaN(hg) && !Number.isNaN(ag)) {
                      const my = (m?.homeTeam || '').localeCompare(label, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? hg : ((m?.awayTeam || '').localeCompare(label, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? ag : hg);
                      const opp = (m?.homeTeam || '').localeCompare(label, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? ag : ((m?.awayTeam || '').localeCompare(label, undefined, { sensitivity: 'accent', usage: 'search' }) === 0 ? hg : ag);
                      recent.push(my > opp ? 'W' : (my === opp ? 'D' : 'L'));
                    }
                  }
                }
                const winRate = team?.last5?.winRate ?? 0;
                const points = recent.reduce((acc, r) => acc + (r === 'W' ? 3 : r === 'D' ? 1 : 0), 0);
                return { recentResults: recent, currentStreak: seqStr, winRate, pointsEarned: points, ppgSeries: team?.last5?.ppgSeries } as any;
              };
              const hEntry = safe.find(t => (t?.teamName || '').toLowerCase() === homeName.toLowerCase()) || safe[0] || null;
              const aEntry = safe.find(t => (t?.teamName || '').toLowerCase() === awayName.toLowerCase()) || safe[1] || null;
              this.homeForm = hEntry ? mapTeam(hEntry, homeName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = aEntry ? mapTeam(aEntry, awayName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              // Map season context and messages
              this.homeSeasonResolved = hEntry?.seasonResolved || null;
              this.awaySeasonResolved = aEntry?.seasonResolved || null;
              this.homeMatchesAvailableText = hEntry?.matchesAvailable || null;
              this.awayMatchesAvailableText = aEntry?.matchesAvailable || null;
            },
            error: (err) => {
              console.error('[PlayedMatches] getH2HFormByNamesWithAutoSeason failed', err);
              this.showDataHint = true;
              this.homeForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
            }
          });
        } else {
          console.warn('[PlayedMatches] No valid seasonId and no leagueId; cannot fetch Last-5.');
          this.showDataHint = true;
        }
      } else {
        // Resolve team IDs scoped to league
        let homeId: number | null = null;
        let awayId: number | null = null;
        try {
          // Pass lid only if available; service can also read from context
          homeId = await this.teamService.getScopedTeamId(homeName, typeof lid === 'number' ? lid : undefined).toPromise();
          awayId = await this.teamService.getScopedTeamId(awayName, typeof lid === 'number' ? lid : undefined).toPromise();
        } catch (e) {
          console.warn('[PlayedMatches] league-scoped team id resolution failed', e);
        }

        if (typeof homeId === 'number' && typeof awayId === 'number') {
          console.debug('[PlayedMatches] Resolved IDs', { homeId, awayId, seasonId });
          this.h2hHomeId = homeId; this.h2hAwayId = awayId;
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
                const points = recent.reduce((acc, r) => acc + (r === 'W' ? 3 : r === 'D' ? 1 : 0), 0);
                return { recentResults: recent, currentStreak: seqStr, winRate: winRate, pointsEarned: points, ppgSeries: team?.last5?.ppgSeries } as any;
              };
              const homeEntry = safe.find(t => Number(t?.teamId) === homeId) || safe.find(t => t?.teamName?.toLowerCase?.() === homeName.toLowerCase()) || null;
              const awayEntry = safe.find(t => Number(t?.teamId) === awayId) || safe.find(t => t?.teamName?.toLowerCase?.() === awayName.toLowerCase()) || null;
              this.homeForm = homeEntry ? toSummary(homeEntry, homeName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = awayEntry ? toSummary(awayEntry, awayName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              // Map season context and messages
              this.homeSeasonResolved = homeEntry?.seasonResolved || null;
              this.awaySeasonResolved = awayEntry?.seasonResolved || null;
              this.homeMatchesAvailableText = homeEntry?.matchesAvailable || null;
              this.awayMatchesAvailableText = awayEntry?.matchesAvailable || null;
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
  private pctFor(b?: TeamBreakdownDto | null, key?: 'wins'|'draws'|'losses'|'btts'|'over25'|'over15'): number {
    if (!b || !key) return 0;
    const total = b.total ?? 0;
    const val = (b as any)[key] ?? 0;
    if (!total || total <= 0) return 0;
    return Math.round((val / total) * 100);
  }

  h2hClass(key: 'wins'|'draws'|'losses'|'btts'|'over25'|'over15', isHome: boolean = true): string | undefined {
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

  // Build payload for PDF export
  private buildPdfPayload(): any {
    const teamA = {
      name: this.h2hHome,
      matchesInvolved: this.homeCount || this.homeBreakdown?.total || 0,
      wins: this.homeBreakdown?.wins || 0,
      draws: this.homeBreakdown?.draws || 0,
      losses: this.homeBreakdown?.losses || 0,
      btts: this.homeBreakdown?.btts || 0,
      over15: this.homeBreakdown?.over15 || 0,
      over25: this.homeBreakdown?.over25 || 0,
    };
    const teamB = {
      name: this.h2hAway,
      matchesInvolved: this.awayCount || this.awayBreakdown?.total || 0,
      wins: this.awayBreakdown?.wins || 0,
      draws: this.awayBreakdown?.draws || 0,
      losses: this.awayBreakdown?.losses || 0,
      btts: this.awayBreakdown?.btts || 0,
      over15: this.awayBreakdown?.over15 || 0,
      over25: this.awayBreakdown?.over25 || 0,
    };
    const history = (this.h2hMatches || []).map(m => ({ year: m.year, date: m.date, match: `${m.homeTeam} vs ${m.awayTeam}`, result: m.result }));
    const allOrientations = (this.h2hMatchesAll || []).map(m => ({ year: m.year, date: m.date, match: `${m.homeTeam} vs ${m.awayTeam}`, result: m.result }));

    const predictions = this.predictions ? {
      win: this.predictions.teamAWin,
      draw: this.predictions.draw,
      loss: this.predictions.teamBWin,
      btts: this.predictions.btts,
      over15: this.predictions.over15,
      over25: this.predictions.over25,
      over35: this.predictions.over35,
      correctScores: (this.predictions.correctScores || []).map(cs => ({ score: cs.score, probability: cs.probability }))
    } : null;

    const insights = this.buildInsightsText();
    const goalDifferential = this.gdAggregate ?? 0;
    const averageGD = this.gdAverage ?? 0;
    return {
      totalMatches: this.total,
      teamA,
      teamB,
      h2h: {
        insights,
        goalDifferential,
        averageGD,
        last5TeamA: {
          streak: this.homeForm?.currentStreak || '0',
          winRate: this.homeForm?.winRate || 0,
          points: this.homeForm?.pointsEarned || 0,
          recent: this.homeForm?.recentResults || []
        },
        last5TeamB: {
          streak: this.awayForm?.currentStreak || '0',
          winRate: this.awayForm?.winRate || 0,
          points: this.awayForm?.pointsEarned || 0,
          recent: this.awayForm?.recentResults || []
        },
        history,
        allOrientations
      },
      predictions
    };
  }

  downloadAsPDF(): void {
    if (!this.predictions) { return; }
    const payload = this.buildPdfPayload();
    try { console.log('[Download] Sending payload:', payload); } catch {}
    this.matchService.generateAnalysisPdf(payload).subscribe({
      next: (resp: any) => {
        const blob = resp?.body instanceof Blob ? resp.body as Blob : new Blob([resp], { type: 'application/pdf' });
        const cd = resp?.headers?.get ? resp.headers.get('Content-Disposition') : null;
        let filename = 'analysis.pdf';
        if (cd && /filename=([^;]+)/i.test(cd)) {
          const m = cd.match(/filename=([^;]+)/i);
          if (m && m[1]) filename = m[1].replace(/"/g, '').trim();
        }
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        setTimeout(() => { document.body.removeChild(a); window.URL.revokeObjectURL(url); }, 0);
      },
      error: (err: any) => {
        console.error('[PlayedMatches] PDF generation failed', err);
        try { (window as any).alert?.('PDF generation failed. Try again or check backend logs.'); } catch {}
      }
    });
  }
}

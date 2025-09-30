import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchService, TeamBreakdownDto, H2HSuggestion, H2HMatchDto, FormSummaryDto } from '../services/match.service';
import { ConfigService, FlagsDto } from '../services/config.service';
import { TeamService, TeamSuggestion } from '../services/team.service';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, takeUntil } from 'rxjs';
import { LeagueContextService } from '../services/league-context.service';
import { Predictions } from '../services/poisson.service';
import { MatchAnalysisService, MatchAnalysisRequest, MatchAnalysisResponse } from '../services/match-analysis.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';

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
          <div class="breakdown-row" [class.loading]="loadingBreakdown" *ngIf="!loadingBreakdown">
            <div>Current streak: {{ formatStreak(breakdown?.currentStreakType, breakdown?.currentStreakCount) }}
              <span class="muted" *ngIf="breakdown?.longestStreakCount && breakdown?.longestStreakType"> • Longest: {{ formatLongestStreak(breakdown?.longestStreakType, breakdown?.longestStreakCount) }}</span>
            </div>
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
      <div class="hint" *ngIf="h2hSelected" style="margin: 8px 0; color:#9aa0a6;">
        Color legend: Green = W longest-ever, Orange = D longest-ever, Red = L longest-ever. Current streaks are shown for reference only.
      </div>
      <div class="profiles-grid" *ngIf="h2hSelected">
        <div class="profile-card" [ngStyle]="profileHighlightStyle(true)">
          <div class="profile-header">
            <div class="team-name" [ngStyle]="teamPillStyle(true)">{{ h2hHome }}</div>
            <button class="clear" (click)="clearH2H()">Clear H2H</button>
          </div>
          <div class="section-desc">Summary of all played matches for {{ h2hHome }} across Chambuavismart (all seasons and leagues). These figures are not limited to this H2H pairing.</div>
          <!-- Same/Different longest-ever streak banner for explicit confirmation -->
          <div class="hint" *ngIf="hasBothLongestTypes()">
            {{ isSameOverallLongest() ? 'Same type of longest-ever streak matchup' : 'Different type of longest-ever streak matchup' }}
            <span class="muted"> — {{ (homeBreakdown?.longestStreakType || '?') | uppercase }} vs {{ (awayBreakdown?.longestStreakType || '?') | uppercase }}</span>
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
            <div class="section-desc">Win/Draw/Loss share across all played matches. BTTS counts games where both teams scored; Over 2.5 counts games with 3 or more total goals.</div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown; else loadingHomeBdTpl">
              <div [ngClass]="h2hClass('wins')">Wins: {{ homeBreakdown?.wins ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.wins, homeBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('draws')">Draws: {{ homeBreakdown?.draws ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.draws, homeBreakdown?.total) }}%</span></div>
              <div [ngClass]="h2hClass('losses')">Losses: {{ homeBreakdown?.losses ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.losses, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div>BTTS: {{ homeBreakdown?.btts ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.btts, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div>Over 1.5: {{ homeBreakdown?.over15 ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.over15, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div>Over 2.5: {{ homeBreakdown?.over25 ?? 0 }} <span class="pct">{{ pct2(homeBreakdown?.over25, homeBreakdown?.total) }}%</span></div>
            </div>
            <div class="breakdown-row" [class.loading]="loadingHomeBreakdown" *ngIf="!loadingHomeBreakdown">
              <div>Current streak: {{ formatStreak(homeBreakdown?.currentStreakType, homeBreakdown?.currentStreakCount) }}
                <span class="muted" *ngIf="homeBreakdown?.longestStreakCount && homeBreakdown?.longestStreakType"> • Longest: {{ formatLongestStreak(homeBreakdown?.longestStreakType, homeBreakdown?.longestStreakCount) }}</span>
              </div>
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
        <div class="profile-card" [ngStyle]="profileHighlightStyle(false)">
          <div class="profile-header">
            <div class="team-name" [ngStyle]="teamPillStyle(false)">{{ h2hAway }}</div>
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
            <div class="breakdown-row" [class.loading]="loadingAwayBreakdown" *ngIf="!loadingAwayBreakdown">
              <div>Current streak: {{ formatStreak(awayBreakdown?.currentStreakType, awayBreakdown?.currentStreakCount) }}
                <span class="muted" *ngIf="awayBreakdown?.longestStreakCount && awayBreakdown?.longestStreakType"> • Longest: {{ formatLongestStreak(awayBreakdown?.longestStreakType, awayBreakdown?.longestStreakCount) }}</span>
              </div>
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
          <div class="section-desc" *ngIf="homeIsFallback; else homeScopedNote">Recent form for {{ h2hHome }} — last up to 5 played matches from {{ homeSourceLeague || 'all competitions' }} (fallback due to limited data in {{ homeSeasonResolved || 'this competition' }}). Points use W=3, D=1, L=0.</div>
          <ng-template #homeScopedNote>
            <div class="section-desc">Recent form for {{ h2hHome }} — last up to 5 played matches in {{ homeSeasonResolved || 'this competition' }}. Points use W=3, D=1, L=0.</div>
          </ng-template>
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
            <div title="Number of consecutive results from the most recent match going backwards (e.g., 3W means 3 straight wins).">Streak: <strong>{{ formatStreak(homeForm?.currentStreak) }}</strong></div>
            <div title="Percentage of wins within the last up to 5 played matches considered.">Win rate: <strong>{{ homeForm?.winRate ?? 0 }}%</strong></div>
            <div title="Total points from those matches using W=3, D=1, L=0.">Points: <strong>{{ homeForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
          <div class="hint" *ngIf="homeForm">
            <span *ngIf="homeIsFallback; else homeScopedExplain">Computed from the last up to 5 played matches across all competitions because {{ h2hHome }} has limited data in {{ homeSeasonResolved || 'this competition' }}.</span>
            <ng-template #homeScopedExplain>Computed from the last up to 5 played matches in {{ homeSeasonResolved || 'this competition' }}.</ng-template>
          </div>
        </div>
        <div class="profile-card">
          <div class="breakdown-title">{{ h2hAway }} — Last 5 <span *ngIf="awaySeasonResolved">({{ awaySeasonResolved }})</span>
                      <span class="hint" *ngIf="awayMatchesAvailableText"> — {{ awayMatchesAvailableText }}</span>
                    </div>
          <div class="section-desc" *ngIf="awayIsFallback; else awayScopedNote">Recent form for {{ h2hAway }} — last up to 5 played matches from {{ awaySourceLeague || 'all competitions' }} (fallback due to limited data in {{ awaySeasonResolved || 'this competition' }}). Points use W=3, D=1, L=0.</div>
          <ng-template #awayScopedNote>
            <div class="section-desc">Recent form for {{ h2hAway }} — last up to 5 played matches in {{ awaySeasonResolved || 'this competition' }}. Points use W=3, D=1, L=0.</div>
          </ng-template>
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
            <div title="Number of consecutive results from the most recent match going backwards (e.g., 3W means 3 straight wins).">Streak: <strong>{{ formatStreak(awayForm?.currentStreak) }}</strong></div>
            <div title="Percentage of wins within the last up to 5 played matches considered.">Win rate: <strong>{{ awayForm?.winRate ?? 0 }}%</strong></div>
            <div title="Total points from those matches using W=3, D=1, L=0.">Points: <strong>{{ awayForm?.pointsEarned ?? 0 }}</strong></div>
          </div>
          <div class="hint" *ngIf="awayForm">
            <span *ngIf="awayIsFallback; else awayScopedExplain">Computed from the last up to 5 played matches across all competitions because {{ h2hAway }} has limited data in {{ awaySeasonResolved || 'this competition' }}.</span>
            <ng-template #awayScopedExplain>Computed from the last up to 5 played matches in {{ awaySeasonResolved || 'this competition' }}.</ng-template>
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
        <div class="hint" *ngIf="(h2hMatchesAll?.length || 0) > 0">
          Total meetings (all orientations): <strong>{{ h2hMatchesAll.length }}</strong>
        </div>
        <div class="breakdown-row h2h-all" *ngIf="(h2hMatchesAll?.length || 0) > 0">
          <div class="h2h-all-left">
            <div>{{ h2hHome }} wins: <strong>{{ h2hAllWinsHomeTeam }}</strong></div>
            <div>Draws: <strong>{{ h2hAllDraws }}</strong></div>
            <div>{{ h2hAway }} wins: <strong>{{ h2hAllWinsAwayTeam }}</strong></div>
          </div>
          <div class="pill-group">
            <span class="pill pill-home">{{ h2hHome }}: {{ h2hAllWinsHomePct }}%</span>
            <span class="pill pill-draw">Draw: {{ h2hAllDrawsPct }}%</span>
            <span class="pill pill-away">{{ h2hAway }}: {{ h2hAllWinsAwayPct }}%</span>
          </div>
        </div>
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
          <div class="hint">Select a valid H2H pair and click "Analyse this fixture" to see predictions.</div>
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
    /* H2H All Orientations percentage pills on the far right */
    .breakdown-row.h2h-all { justify-content: space-between; align-items: center; }
    .h2h-all-left { display:flex; gap:16px; flex-wrap:wrap; }
    .pill-group { margin-left:auto; display:flex; gap:8px; }
    .pill { display:inline-block; padding:4px 10px; border-radius:999px; font-weight:800; font-size:12px; border:1px solid transparent; }
    .pill-home { background:#065f46; color:#10b981; border-color:#10b981; }
    .pill-draw { background:#1f2937; color:#cbd5e1; border-color:#9ca3af; }
    .pill-away { background:#0f2a52; color:#60a5fa; border-color:#3b82f6; }
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
  // Aggregates for All Orientations section
  h2hAllTotal: number = 0;
  h2hAllWinsHomeTeam: number = 0; // wins credited to the team labeled in h2hHome
  h2hAllWinsAwayTeam: number = 0; // wins credited to the team labeled in h2hAway
  h2hAllDraws: number = 0;
  h2hAllWinsHomePct: number = 0;
  h2hAllWinsAwayPct: number = 0;
  h2hAllDrawsPct: number = 0;
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
  // Resolved numeric season id (when using ID-based flow)
  seasonIdResolved: number | null = null;

  homeCount = 0; awayCount = 0;
  loadingHomeCount = false; loadingAwayCount = false;
  homeBreakdown: TeamBreakdownDto | null = null; awayBreakdown: TeamBreakdownDto | null = null;
  loadingHomeBreakdown = false; loadingAwayBreakdown = false;
  homeIsFallback: boolean = false; awayIsFallback: boolean = false;
  homeSourceLeague: string | null = null; awaySourceLeague: string | null = null;

  // PDF context for filename
  sourceContext: string = 'analysis';
  fixtureDateParam: string | null = null;

  // Predictions via backend MatchAnalysis (mapped to local shape)
  predictions: Predictions | null = null;
  private matchAnalysis = inject(MatchAnalysisService);
  private leagueContext = inject(LeagueContextService);
  private colorCache = inject(AnalysisColorCacheService);

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

  // Ensure oriented H2H is fetched only after IDs are available; fallback to names otherwise
  private fetchOrientedH2H(homeName: string, awayName: string): void {
    if (typeof this.h2hHomeId === 'number' && typeof this.h2hAwayId === 'number') {
      console.debug('[PlayedMatches] Fetching oriented H2H across all seasons by IDs', { homeId: this.h2hHomeId, awayId: this.h2hAwayId });
      this.matchService.getH2HMatchesByIdsAllSeasons(this.h2hHomeId, this.h2hAwayId, 200).subscribe({
        next: list => { this.h2hMatches = list ?? []; if (this.predictiveOn) this.computeGDFromMatches(); },
        error: () => { this.h2hMatches = []; if (this.predictiveOn) this.computeGDFromMatches(); }
      });
    } else {
      console.debug('[PlayedMatches] Fetching oriented H2H by names (IDs not yet resolved)', { homeName, awayName });
      this.matchService.getH2HMatches(homeName, awayName).subscribe({
        next: list => { this.h2hMatches = list ?? []; if (this.predictiveOn) this.computeGDFromMatches(); },
        error: () => { this.h2hMatches = []; if (this.predictiveOn) this.computeGDFromMatches(); }
      });
    }
  }

  barWidth(p: number): number {
    const w = (p || 0) * 5; // scale up for visibility
    return w > 100 ? 100 : w;
  }

  private computeH2HAllStats(): void {
    const list = Array.isArray(this.h2hMatchesAll) ? this.h2hMatchesAll : [];
    const hn = (this.h2hHome || '').trim().toLowerCase();
    const an = (this.h2hAway || '').trim().toLowerCase();
    let homeWins = 0, awayWins = 0, draws = 0, total = 0;
    const parse = (s: string): { hg: number; ag: number } | null => {
      if (!s || typeof s !== 'string') return null;
      const m = s.match(/\s*(\d+)\s*[-:]\s*(\d+)\s*/);
      if (!m) return null;
      const hg = parseInt(m[1], 10);
      const ag = parseInt(m[2], 10);
      if (Number.isNaN(hg) || Number.isNaN(ag)) return null;
      return { hg, ag };
    };
    for (const m of list) {
      const res = parse((m as any)?.result || '');
      if (!res) { continue; }
      const homeTeamName = ((m as any)?.homeTeam || '').toString().trim().toLowerCase();
      const awayTeamName = ((m as any)?.awayTeam || '').toString().trim().toLowerCase();
      if (!homeTeamName || !awayTeamName) { continue; }
      total++;
      if (res.hg === res.ag) {
        draws++;
      } else if (res.hg > res.ag) {
        // Home side won
        if (homeTeamName === hn) homeWins++; else if (homeTeamName === an) awayWins++;
      } else {
        // Away side won
        if (awayTeamName === hn) homeWins++; else if (awayTeamName === an) awayWins++;
      }
    }
    this.h2hAllTotal = total;
    this.h2hAllWinsHomeTeam = homeWins;
    this.h2hAllWinsAwayTeam = awayWins;
    this.h2hAllDraws = draws;
    if (total > 0) {
      this.h2hAllWinsHomePct = Math.round((homeWins / total) * 100);
      this.h2hAllWinsAwayPct = Math.round((awayWins / total) * 100);
      this.h2hAllDrawsPct = Math.round((draws / total) * 100);
    } else {
      this.h2hAllWinsHomePct = this.h2hAllWinsAwayPct = this.h2hAllDrawsPct = 0;
    }
  }

  analyseFixture(): void {
    if (!(this.h2hHome && this.h2hAway) || !this.leagueId) {
      this.predictions = null;
      return;
    }
    const req: MatchAnalysisRequest = {
      leagueId: this.leagueId,
      // seasonId omitted to allow backend to use current season
      homeTeamId: this.h2hHomeId ?? undefined,
      awayTeamId: this.h2hAwayId ?? undefined,
      homeTeamName: this.h2hHome,
      awayTeamName: this.h2hAway,
      analysisType: 'fixtures'
    } as any;
    this.matchAnalysis.analyze(req).pipe(takeUntil(this.destroy$)).subscribe({
      next: (resp: MatchAnalysisResponse) => {
        // Map backend response to local predictions shape expected by UI
        const p: Predictions = {
          teamAWin: resp?.winProbabilities?.homeWin ?? 0,
          draw: resp?.winProbabilities?.draw ?? 0,
          teamBWin: resp?.winProbabilities?.awayWin ?? 0,
          btts: resp?.bttsProbability ?? 0,
          over25: resp?.over25Probability ?? 0,
          // Derive lambda from xG for display; clamp to 2 decimals
          lambdaA: +(resp?.expectedGoals?.home ?? 0).toFixed(2) as any,
          lambdaB: +(resp?.expectedGoals?.away ?? 0).toFixed(2) as any,
          // We don't get over15/over35 from backend Poisson; approximate for UI continuity
          over15: Math.min(100, Math.max(0, (resp?.over25Probability ?? 0) + 18)),
          over35: Math.min(100, Math.max(0, (resp?.over25Probability ?? 0) - 18)),
          isLimitedData: false,
          correctScores: []
        } as any;
        this.predictions = p;
        this.showDataHint = false;
      },
      error: (err) => {
        console.error('[PlayedMatches][AnalyseFixture] Backend analysis failed', err);
        this.predictions = null;
        this.showDataHint = true;
      }
    });
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
        // Optional context for PDF naming
        const from = params?.['from'] || params?.['source'];
        const fdate = params?.['fixtureDate'] || params?.['date'] || params?.['kickoff'];
        if (typeof from === 'string' && from) this.sourceContext = from;
        if (typeof fdate === 'string' && fdate) this.fixtureDateParam = fdate;
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
    console.log('[PlayedMatches][Breakdown][REQ]', { name: s.name });
    this.matchService.getResultsBreakdownByTeamName(s.name).subscribe({
      next: b => {
        this.breakdown = b;
        this.loadingBreakdown = false;
        const hasStreak = !!b && typeof b.longestStreakCount === 'number' && (b.longestStreakCount as any) > 0 && !!b.longestStreakType;
        if (hasStreak) {
          console.log('[PlayedMatches][Breakdown][RESP]', { name: s.name, total: b.total, wins: b.wins, draws: b.draws, losses: b.losses, btts: b.btts, over15: b.over15, over25: b.over25, longest: `${b.longestStreakType}:${b.longestStreakCount}` });
        } else {
          const reason = (b?.total ?? 0) <= 0 ? 'NO_MATCHES' : 'NOT_COMPUTED_OR_ZERO';
          console.warn('[PlayedMatches][Breakdown][RESP][NO_STREAK]', { name: s.name, total: b?.total ?? 0, reason, payload: b });
        }
      },
      error: (err) => {
        console.error('[PlayedMatches][Breakdown][ERR]', { name: s.name, error: err });
        this.breakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 } as any;
        this.loadingBreakdown = false;
      }
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
    console.log('[H2H][HomeBreakdown][REQ]', { name: homeName });
    this.matchService.getResultsBreakdownByTeamName(homeName).subscribe({
      next: b => {
        this.homeBreakdown = b; this.loadingHomeBreakdown = false;
        const has = !!b && (b.longestStreakCount as any) > 0 && !!b.longestStreakType;
        if (has) console.log('[H2H][HomeBreakdown][RESP]', { name: homeName, longest: `${b.longestStreakType}:${b.longestStreakCount}` });
        else console.warn('[H2H][HomeBreakdown][RESP][NO_STREAK]', { name: homeName, total: b?.total ?? 0, payload: b });
        this.tryPersistH2HColors();
      },
      error: () => { this.homeBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 } as any; this.loadingHomeBreakdown = false; }
    });

    this.matchService.getPlayedTotalByTeamName(awayName).subscribe({
      next: v => { this.awayCount = v ?? 0; this.loadingAwayCount = false; },
      error: () => { this.awayCount = 0; this.loadingAwayCount = false; }
    });
    console.log('[H2H][AwayBreakdown][REQ]', { name: awayName });
    this.matchService.getResultsBreakdownByTeamName(awayName).subscribe({
      next: b => {
        this.awayBreakdown = b; this.loadingAwayBreakdown = false;
        const has = !!b && (b.longestStreakCount as any) > 0 && !!b.longestStreakType;
        if (has) console.log('[H2H][AwayBreakdown][RESP]', { name: awayName, longest: `${b.longestStreakType}:${b.longestStreakCount}` });
        else console.warn('[H2H][AwayBreakdown][RESP][NO_STREAK]', { name: awayName, total: b?.total ?? 0, payload: b });
        this.tryPersistH2HColors();
      },
      error: () => { this.awayBreakdown = { total: 0, wins: 0, draws: 0, losses: 0, btts: 0, over25: 0, over15: 0 } as any; this.loadingAwayBreakdown = false; }
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
            if (typeof sid === 'number') { seasonId = sid; this.seasonIdResolved = sid; }
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
                const backendRecent: string[] | undefined = team?.last5?.recent;
                let recent: string[] = Array.isArray(backendRecent) ? backendRecent : [];
                if (!Array.isArray(backendRecent) || backendRecent.length === 0) {
                  // Fallback: derive from matches list if backend didn't provide recent array
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
                }
                const winRate = team?.last5?.winRate ?? 0;
                const streak: string = team?.last5?.streak || this.computeCompactStreak(recent);
                const points = recent.reduce((acc, r) => acc + (r === 'W' ? 3 : r === 'D' ? 1 : 0), 0);
                return { recentResults: recent, currentStreak: streak, winRate, pointsEarned: points, ppgSeries: team?.last5?.ppgSeries } as any;
              };
              const hEntry = safe.find(t => (t?.teamName || '').toLowerCase() === homeName.toLowerCase()) || safe[0] || null;
              const aEntry = safe.find(t => (t?.teamName || '').toLowerCase() === awayName.toLowerCase()) || safe[1] || null;
              this.homeForm = hEntry ? mapTeam(hEntry, homeName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = aEntry ? mapTeam(aEntry, awayName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              // Map season context, messages and fallback flags
              this.homeSeasonResolved = hEntry?.seasonResolved || null;
              this.awaySeasonResolved = aEntry?.seasonResolved || null;
              this.homeMatchesAvailableText = hEntry?.matchesAvailable || null;
              this.awayMatchesAvailableText = aEntry?.matchesAvailable || null;
              this.homeIsFallback = !!(hEntry?.last5?.fallback);
              this.awayIsFallback = !!(aEntry?.last5?.fallback);
              this.homeSourceLeague = hEntry?.sourceLeague || null;
              this.awaySourceLeague = aEntry?.sourceLeague || null;
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
          this.seasonIdResolved = seasonId;
          console.debug('[PlayedMatches] calling getH2HFormByIds', { homeId, awayId, seasonId });
          this.matchService.getH2HFormByIds(homeId, awayId, seasonId, 5).subscribe({
            next: list => {
              const safe = Array.isArray(list) ? list : [];
              // After IDs are confirmed via Last-5 response, fetch oriented H2H with IDs
              this.fetchOrientedH2H(homeName, awayName);
              const toSummary = (team: any, teamLabel: string): FormSummaryDto => {
                const backendRecent: string[] | undefined = team?.last5?.recent;
                let recent: string[] = Array.isArray(backendRecent) ? backendRecent : [];
                if (!Array.isArray(backendRecent) || backendRecent.length === 0) {
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
                }
                const winRate = team?.last5?.winRate ?? 0;
                const streak: string = team?.last5?.streak || this.computeCompactStreak(recent);
                const points = recent.reduce((acc, r) => acc + (r === 'W' ? 3 : r === 'D' ? 1 : 0), 0);
                return { recentResults: recent, currentStreak: streak, winRate: winRate, pointsEarned: points, ppgSeries: team?.last5?.ppgSeries } as any;
              };
              const homeEntry = safe.find(t => Number(t?.teamId) === homeId) || safe.find(t => t?.teamName?.toLowerCase?.() === homeName.toLowerCase()) || null;
              const awayEntry = safe.find(t => Number(t?.teamId) === awayId) || safe.find(t => t?.teamName?.toLowerCase?.() === awayName.toLowerCase()) || null;
              this.homeForm = homeEntry ? toSummary(homeEntry, homeName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              this.awayForm = awayEntry ? toSummary(awayEntry, awayName) : { recentResults: [], currentStreak: '0', winRate: 0, pointsEarned: 0 };
              // Map season context, messages and fallback flags
              this.homeSeasonResolved = homeEntry?.seasonResolved || null;
              this.awaySeasonResolved = awayEntry?.seasonResolved || null;
              this.homeMatchesAvailableText = homeEntry?.matchesAvailable || null;
              this.awayMatchesAvailableText = awayEntry?.matchesAvailable || null;
              this.homeIsFallback = !!(homeEntry?.last5?.fallback);
              this.awayIsFallback = !!(awayEntry?.last5?.fallback);
              this.homeSourceLeague = homeEntry?.sourceLeague || null;
              this.awaySourceLeague = awayEntry?.sourceLeague || null;
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

    // Defer oriented H2H fetch until IDs are resolved; see fetchOrientedH2H()
    this.fetchOrientedH2H(homeName, awayName);

    // Load total H2H count regardless of orientation
    this.matchService.getH2HCountAnyOrientation(homeName, awayName).subscribe({
      next: c => { this.h2hAnyCount = (typeof c === 'number') ? c : 0; },
      error: () => { this.h2hAnyCount = 0; }
    });

    // Load H2H matches across both orientations
    this.matchService.getH2HMatchesAnyOrientation(homeName, awayName).subscribe({
      next: list => { this.h2hMatchesAll = list ?? []; try { this.tryPersistH2HColors(); } catch {} try { this.computeH2HAllStats(); } catch {} },
      error: () => { this.h2hMatchesAll = []; try { this.tryPersistH2HColors(); } catch {} try { this.computeH2HAllStats(); } catch {} }
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
    this.homeMatchesAvailableText = null;
    this.awayMatchesAvailableText = null;
    this.homeSeasonResolved = null;
    this.awaySeasonResolved = null;
    this.homeIsFallback = false;
    this.awayIsFallback = false;
    // Reset H2H All Orientations aggregates
    this.h2hAllTotal = 0;
    this.h2hAllWinsHomeTeam = 0;
    this.h2hAllWinsAwayTeam = 0;
    this.h2hAllDraws = 0;
    this.h2hAllWinsHomePct = 0;
    this.h2hAllWinsAwayPct = 0;
    this.h2hAllDrawsPct = 0;
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
 
  formatLongestStreak(type?: string | null, count?: number | null): string {
    const c = count ?? 0;
    if (!type || c <= 0) return '—';
    const label = type === 'W' ? 'Wins' : (type === 'D' ? 'Draws' : 'Losses');
    return `${label}: ${c}`;
  }
 
  // Overloads: format current streak either as (type,count) => labeled, or as compact string like "3W"
  formatStreak(type?: string | null, count?: number | null): string;
  formatStreak(s: string | null | undefined): string;
  formatStreak(a?: string | null, b?: number | null): string {
    // If called with a compact string like "3W"
    if (arguments.length === 1) {
      const s = a as (string | null | undefined);
      if (!s) return '—';
      return s;
    }
    // Called with (type, count)
    return this.formatLongestStreak(a, b);
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

  // Highlight profile card if longest-ever streak is strong (>5)
  profileHighlightStyle(isHome: boolean): {[k: string]: string} {
    const b = isHome ? this.homeBreakdown : this.awayBreakdown;
    const t = (b?.longestStreakType || '').toUpperCase();
    const c = b?.longestStreakCount || 0;
    if (!t || !c || c <= 5) return {};
    // Use a thick colored ring (inset box-shadow) instead of background to avoid affecting text readability
    let edge = '';
    if (t === 'W') { edge = 'rgba(16,160,16,0.55)'; }
    else if (t === 'L') { edge = 'rgba(204, 43, 59, 0.55)'; }
    else if (t === 'D') { edge = 'rgba(255, 165, 0, 0.55)'; }
    if (!edge) return {};
    // 6px inset ring; no background fill. This does not distort green/red/orange stat texts.
    return { boxShadow: `inset 0 0 0 6px ${edge}` };
  }

  // Compute a display color for team pill based on longest-ever streak from Fixtures Analysis
  private computeColorFromBreakdown(b?: TeamBreakdownDto | null): string | null {
    const t = (b?.longestStreakType || '').toUpperCase();
    // Always map to one of three colors based on streak type regardless of count
    if (!t) return null;
    if (t === 'W') return '#16a34a';   // Green
    if (t === 'L') return '#ef4444';   // Red
    if (t === 'D') return '#f59e0b';   // Orange
    return null;
  }

  teamPillStyle(isHome: boolean): { [k: string]: string } {
    const b = isHome ? this.homeBreakdown : this.awayBreakdown;
    const color = this.computeColorFromBreakdown(b);
    if (!color) return {};
    return {
      background: color,
      color: '#ffffff',
      padding: '2px 6px',
      borderRadius: '6px',
      border: '1px solid rgba(255,255,255,0.15)'
    };
  }

  // Explicit classification helpers: compare overall longest-ever streak types for both teams
  hasBothLongestTypes(): boolean {
    const ht = (this.homeBreakdown?.longestStreakType || '').toUpperCase();
    const at = (this.awayBreakdown?.longestStreakType || '').toUpperCase();
    return !!ht && !!at;
  }
  isSameOverallLongest(): boolean {
    if (!this.hasBothLongestTypes()) return false;
    const ht = (this.homeBreakdown?.longestStreakType || '').toUpperCase();
    const at = (this.awayBreakdown?.longestStreakType || '').toUpperCase();
    return ht === at;
  }

  // Persist H2H team colors into local cache so Home page can render colored pills for today's fixtures
  private tryPersistH2HColors(): void {
    try {
      if (!this.h2hSelected) return;
      if (!this.h2hHome || !this.h2hAway) return;
      if (this.loadingHomeBreakdown || this.loadingAwayBreakdown) return;
      const computedHome = this.computeColorFromBreakdown(this.homeBreakdown);
      const computedAway = this.computeColorFromBreakdown(this.awayBreakdown);
      const lid = this.leagueId ?? undefined;

      // No Indigo fallback; only three colors are allowed. Persist or clear accordingly.
      const homeColor = computedHome;
      const awayColor = computedAway;

      let changed = false;
      if (homeColor) { this.colorCache.setTeamColor(this.h2hHome, homeColor, lid); changed = true; }
      else { this.colorCache.removeTeamColor(this.h2hHome, lid); changed = true; }
      if (awayColor) { this.colorCache.setTeamColor(this.h2hAway, awayColor, lid); changed = true; }
      else { this.colorCache.removeTeamColor(this.h2hAway, lid); changed = true; }

      // Persist longest streak count always (if available)
      const sc = this.homeBreakdown?.longestStreakCount || null;
      this.colorCache.setTeamStreakCount(this.h2hHome, sc, lid);
      const sc2 = this.awayBreakdown?.longestStreakCount || null;
      this.colorCache.setTeamStreakCount(this.h2hAway, sc2, lid);

      // New: compute double-green flag (Green + H2H wins > 70%) if H2H matches are available
      const isGreen = (c: string | null): boolean => {
        if (!c) return false;
        const s = c.toLowerCase();
        return s.includes('#16a34a') || s.includes('green');
      };
      const calcWinPct = (team: string): number | null => {
        const list = Array.isArray(this.h2hMatchesAll) ? this.h2hMatchesAll : [];
        if (!list.length) return null;
        let wins = 0; let total = 0;
        for (const m of list) {
          const res = (m?.result || '').split('-');
          if (res.length !== 2) continue;
          const hg = parseInt(res[0], 10); const ag = parseInt(res[1], 10);
          if (Number.isNaN(hg) || Number.isNaN(ag)) continue;
          const homeT = (m as any).homeTeam || '';
          const awayT = (m as any).awayTeam || '';
          const isTeamHome = homeT.localeCompare(team, undefined, { sensitivity: 'accent', usage: 'search' }) === 0;
          const isTeamAway = awayT.localeCompare(team, undefined, { sensitivity: 'accent', usage: 'search' }) === 0;
          if (!isTeamHome && !isTeamAway) continue;
          total++;
          const teamGoals = isTeamHome ? hg : ag;
          const oppGoals = isTeamHome ? ag : hg;
          if (teamGoals > oppGoals) wins++;
        }
        if (total === 0) return null;
        return wins / total;
      };
      if (isGreen(homeColor)) {
        const p = calcWinPct(this.h2hHome);
        this.colorCache.setDoubleGreen(this.h2hHome, !!(p !== null && p >= 0.7), lid);
      } else {
        this.colorCache.setDoubleGreen(this.h2hHome, false, lid);
      }
      if (isGreen(awayColor)) {
        const p = calcWinPct(this.h2hAway);
        this.colorCache.setDoubleGreen(this.h2hAway, !!(p !== null && p >= 0.7), lid);
      } else {
        this.colorCache.setDoubleGreen(this.h2hAway, false, lid);
      }

      // New: compute Draw-heavy D flag: if either team pill is Orange AND H2H draws >= 40% across any orientation, mark BOTH teams
      const isOrange = (c: string | null): boolean => {
        if (!c) return false;
        const s = c.toLowerCase().trim();
        // Accept common representations for our Orange pill
        // - Hex: #f59e0b
        // - Name: orange
        // - RGB: 255,165,0 (allow optional spaces)
        if (s.includes('#f59e0b') || s.includes('orange')) return true;
        const m = s.match(/rgba?\((\d+)[ ,]+(\d+)[ ,]+(\d+)/);
        if (m) {
          const r = parseInt(m[1], 10), g = parseInt(m[2], 10), b = parseInt(m[3], 10);
          // Close to (255,165,0) range
          if (r >= 230 && g >= 130 && g <= 200 && b <= 40) return true;
        }
        return false;
      };
      const calcDrawPct = (): number | null => {
        const list = Array.isArray(this.h2hMatchesAll) ? this.h2hMatchesAll : [];
        if (!list.length) return null;
        let draws = 0; let total = 0;
        for (const m of list) {
          const res = (m?.result || '').split('-');
          if (res.length !== 2) continue;
          const hg = parseInt(res[0], 10); const ag = parseInt(res[1], 10);
          if (Number.isNaN(hg) || Number.isNaN(ag)) continue;
          total++;
          if (hg === ag) draws++;
        }
        if (total === 0) return null;
        return draws / total;
      };
      const drawPct = calcDrawPct();
      let flagsChanged = false;
      const sameColor = !!homeColor && !!awayColor && homeColor === awayColor;
      const shouldMarkD = !!(drawPct !== null && (
        (drawPct >= 0.4 && (isOrange(homeColor) || isOrange(awayColor))) ||
        (drawPct > 0.3 && sameColor)
      ));
      // Persist for BOTH teams within the league context
      this.colorCache.setDrawHeavyD(this.h2hHome, shouldMarkD, lid); flagsChanged = true;
      this.colorCache.setDrawHeavyD(this.h2hAway, shouldMarkD, lid); flagsChanged = true;

      if (changed || flagsChanged) {
        console.log('[FixturesAnalysis] Updated team colors', { leagueId: lid, home: this.h2hHome, homeColor, away: this.h2hAway, awayColor });
        try { window.dispatchEvent(new CustomEvent('fixtures:colors-updated', { detail: { leagueId: lid, home: this.h2hHome, away: this.h2hAway } })); } catch {}
        // Also notify parent (Home) if we are running inside the modal iframe
        try { if (window.parent && window.parent !== window) { window.parent.postMessage({ type: 'fixtures:colors-updated', leagueId: lid, home: this.h2hHome, away: this.h2hAway }, '*'); } } catch {}
      }
    } catch (e) {
      console.warn('[FixturesAnalysis] Failed to persist team colors', e);
    }
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
  private computeCompactStreak(recent: string[] | null | undefined): string {
    const arr = Array.isArray(recent) ? recent : [];
    if (arr.length === 0) return '0';
    const first = (arr[0] || '').toUpperCase();
    if (!['W','D','L'].includes(first)) return '0';
    let count = 1;
    for (let i = 1; i < arr.length; i++) {
      const v = (arr[i] || '').toUpperCase();
      if (v !== first) break;
      count++;
    }
    return `${count}${first}`;
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
      longestStreakType: this.homeBreakdown?.longestStreakType || null,
      longestStreakCount: this.homeBreakdown?.longestStreakCount || 0,
      formattedLongestStreak: this.formatLongestStreak(this.homeBreakdown?.longestStreakType, this.homeBreakdown?.longestStreakCount),
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
      longestStreakType: this.awayBreakdown?.longestStreakType || null,
      longestStreakCount: this.awayBreakdown?.longestStreakCount || 0,
      formattedLongestStreak: this.formatLongestStreak(this.awayBreakdown?.longestStreakType, this.awayBreakdown?.longestStreakCount),
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
      // Include optional context for backend PDF filename logic
      source: this.sourceContext || 'analysis',
      fixtureDate: this.fixtureDateParam || null,
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
        const blob = resp?.body instanceof Blob ? (resp.body as Blob) : new Blob([resp], { type: 'application/pdf' });
        const cd = resp?.headers?.get ? resp.headers.get('Content-Disposition') : null;
        let filename: string | null = null;
        // 1) Try to parse from Content-Disposition (server-provided)
        if (cd && /filename\*=UTF-8''([^;]+)/i.test(cd)) {
          try {
            const m = cd.match(/filename\*=UTF-8''([^;]+)/i);
            if (m && m[1]) filename = decodeURIComponent(m[1].trim());
          } catch {}
        }
        if (!filename && cd && /filename=([^;]+)/i.test(cd)) {
          const m = cd.match(/filename=([^;]+)/i);
          if (m && m[1]) filename = m[1].replace(/"/g, '').trim();
        }
        // 2) Client-side fallback: build descriptive filename if header missing or generic
        const looksGeneric = (s: string | null) => !s || /^analysis(\.|$)/i.test(s) || /analysis\s*result/i.test(s);
        if (looksGeneric(filename)) {
          const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
          const now = new Date();
          const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
          const stamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}.${pad(now.getMinutes())}`;
          const clean = (s: string) => (s || '').replace(/[\\/:*?"<>|]+/g, ' ').replace(/[\n\r]+/g, ' ').trim().replace(/\s+/g, ' ');
          const home = clean(this.h2hHome || 'Team A');
          const away = clean(this.h2hAway || 'Team B');
          let base = `${home} VS ${away} - Analysis ${stamp}`;
          // Append fixture date if initiated from Fixtures or Home Today's fixtures and we have a date
          try {
            const src = (this.sourceContext || '').toLowerCase();
            const fdate = this.fixtureDateParam;
            if (fdate && (src === 'fixtures' || src === 'home' || src === 'home-today' || src === 'today')) {
              const d = new Date(fdate);
              if (!isNaN(d.getTime())) {
                const dStr = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
                base += ` - Fixture ${dStr}`;
              }
            }
          } catch {}
          filename = base.replace(/\u00A0/g, ' ').trim().replace(/\s+/g, ' ').replace(/\s/g, '_') + '.pdf';
        }
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename || 'analysis.pdf';
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

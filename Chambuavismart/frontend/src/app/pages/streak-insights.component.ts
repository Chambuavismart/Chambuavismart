import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface TeamOption {
  id: number;
  name: string;
  leagueId?: number;
  leagueName?: string;
}

interface StreakTimelineItem {
  matchId: number;
  date: string | null;
  time: string | null;
  league: string | null;
  season: string | null;
  homeTeam: string | null;
  awayTeam: string | null;
  homeGoals: number | null;
  awayGoals: number | null;
  home: boolean;
  opponent: string | null;
  outcome: 'W' | 'D' | 'L' | null;
  activeStreakType: 'W' | 'D' | 'L' | null;
  activeStreakCount: number;
  longestToDateType: 'W' | 'D' | 'L' | null;
  longestToDateCount: number;
  opponentLongestToDateType: 'W' | 'D' | 'L' | null;
  opponentLongestToDateCount: number;
  matchupSummary: string | null;
}

interface StreakSummaryStats {
  winPercent: number;
  drawPercent: number;
  lossPercent: number;
  over15Percent: number;
  over25Percent: number;
  bttsPercent: number;
  // New: absolute counts used for these percentages
  total?: number;
  winCount?: number;
  drawCount?: number;
  lossCount?: number;
  over15Count?: number;
  over25Count?: number;
  bttsCount?: number;
  // New: most common winning scoreline within this bucket (from selected team perspective)
  mostCommonWinScoreline?: string | null;
  mostCommonWinCount?: number | null;
}

interface ScorelineStat { scoreline: string | null; count: number | null; }

interface StreakSummaryResponse {
  sameStreak: StreakSummaryStats;
  differentStreak: StreakSummaryStats;
  fixtureContext: 'same_streak' | 'different_streak' | null;
  upcoming?: {
    date: string | null;
    homeTeam: string | null;
    awayTeam: string | null;
    league?: string | null;
    season?: string | null;
  } | null;
  // Each team’s overall longest-ever streak type and its date range
  selectedTeamType?: 'W' | 'D' | 'L' | null;
  selectedTypeFrom?: string | null;
  selectedTypeTo?: string | null;
  selectedTeamCount?: number | null;
  opponentTeamType?: 'W' | 'D' | 'L' | null;
  opponentTypeFrom?: string | null;
  opponentTypeTo?: string | null;
  opponentTeamCount?: number | null;
  // Most common win scoreline by opponent longest-ever type
  winScorelineVsW?: ScorelineStat | null;
  winScorelineVsD?: ScorelineStat | null;
  winScorelineVsL?: ScorelineStat | null;
  // New: most common draw and loss scorelines by opponent longest-ever type
  drawScorelineVsW?: ScorelineStat | null;
  drawScorelineVsD?: ScorelineStat | null;
  drawScorelineVsL?: ScorelineStat | null;
  lossScorelineVsW?: ScorelineStat | null;
  lossScorelineVsD?: ScorelineStat | null;
  lossScorelineVsL?: ScorelineStat | null;
  // New: overall most common scorelines for Same/Different buckets
  sameTopScorelines?: ScorelineStat[] | null;
  differentTopScorelines?: ScorelineStat[] | null;
  // New: most common scoreline per subcategory (A vs B overall types)
  scorelineWvW?: ScorelineStat | null;
  scorelineDvD?: ScorelineStat | null;
  scorelineLvL?: ScorelineStat | null;
  scorelineWvD?: ScorelineStat | null;
  scorelineDvW?: ScorelineStat | null;
  scorelineWvL?: ScorelineStat | null;
  scorelineLvW?: ScorelineStat | null;
  scorelineDvL?: ScorelineStat | null;
  scorelineLvD?: ScorelineStat | null;
}

@Component({
  standalone: true,
  selector: 'app-streak-insights',
  imports: [CommonModule, FormsModule],
  template: `
  <div class="page">
    <h1>Streak Insights</h1>

    <div class="row">
      <div class="search">
        <label for="homeInput">Home team</label>
        <input id="homeInput" name="home" type="text" [(ngModel)]="homeQuery" (ngModelChange)="onHomeQueryChange($event)" placeholder="Type at least 3 letters..."/>
        <ul class="dropdown" *ngIf="showHomeDropdown()">
          <li *ngFor="let t of homeSuggestions" (click)="selectHome(t)">{{ t.name }}<span class="muted" *ngIf="t.leagueName"> • {{ t.leagueName }}</span></li>
        </ul>
      </div>
      <div class="search">
        <label for="awayInput">Away team</label>
        <input id="awayInput" name="away" type="text" [(ngModel)]="awayQuery" (ngModelChange)="onAwayQueryChange($event)" placeholder="Type at least 3 letters..."/>
        <ul class="dropdown" *ngIf="showAwayDropdown()">
          <li *ngFor="let t of awaySuggestions" (click)="selectAway(t)">{{ t.name }}<span class="muted" *ngIf="t.leagueName"> • {{ t.leagueName }}</span></li>
        </ul>
      </div>
    </div>

    <div class="summary" *ngIf="selectedHome() || selectedAway()">
      <div class="pill" *ngIf="selectedHome()">Home: <strong>{{ selectedHome()?.name }}</strong><span *ngIf="selectedHome()?.leagueName" class="muted"> ({{ selectedHome()?.leagueName }})</span></div>
      <div class="pill" *ngIf="selectedAway()" style="margin-left:8px;">Away: <strong>{{ selectedAway()?.name }}</strong><span *ngIf="selectedAway()?.leagueName" class="muted"> ({{ selectedAway()?.leagueName }})</span></div>
      <button class="simulate" (click)="simulateMatchup()" [disabled]="!selectedHome() || !selectedAway()">Simulate this matchup</button>
      <div class="progress-inline" *ngIf="simulateInProgress()">
        <div class="progress small">
          <div class="progress-bar" [style.width.%]="simulateProgress()"></div>
        </div>
        <span class="progress-label">{{ fmt(simulateProgress()) }}%</span>
      </div>
    </div>

    <div *ngIf="loading()" class="loading">
      <div class="progress">
        <div class="progress-bar" [style.width.%]="teamProgress()"></div>
      </div>
      <div class="progress-label">Loading team data {{ fmt(teamProgress()) }}%</div>
    </div>
    <div *ngIf="!loading() && timeline().length === 0 && selectedHome()" class="empty">No played matches found for this team.</div>

    <div class="table-wrap" *ngIf="timeline().length > 0">
      <table class="tbl">
        <thead>
          <tr>
            <th>#</th>
            <th>Date</th>
            <th>League</th>
            <th>Home</th>
            <th>Away</th>
            <th>Score</th>
            <th>Outcome</th>
            <th>Team Streak (before match)</th>
            <th>Team Longest Streak</th>
            <th>Opponent Longest Streak</th>
            <th>Streak Matchup</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of timeline(); let i = index">
            <td class="num">{{ i + 1 }}</td>
            <td>{{ r.date }}</td>
            <td>{{ r.league }}</td>
            <td [class.me]="r.home">{{ r.homeTeam }}</td>
            <td [class.me]="!r.home">{{ r.awayTeam }}</td>
            <td>{{ (r.homeGoals ?? '-') + ' - ' + (r.awayGoals ?? '-') }}</td>
            <td><span class="badge" [class.w]="r.outcome==='W'" [class.d]="r.outcome==='D'" [class.l]="r.outcome==='L'">{{ r.outcome || '-' }}</span></td>
            <td>
              <span *ngIf="r.activeStreakType; else noneA">{{ r.activeStreakCount }}{{ r.activeStreakType }}</span>
              <ng-template #noneA>–</ng-template>
            </td>
            <td>
              <ng-container *ngIf="summary() as s">
                <span *ngIf="s.selectedTeamType; else noneB">{{ (s.selectedTeamCount ?? 0) + (s.selectedTeamType || '') }}</span>
              </ng-container>
              <ng-template #noneB>–</ng-template>
            </td>
            <td>
              <span *ngIf="r.opponentLongestToDateType; else noneC">{{ r.opponentLongestToDateCount }}{{ r.opponentLongestToDateType }}</span>
              <ng-template #noneC>–</ng-template>
            </td>
            <td>
              {{ r.matchupSummary || '–' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="summary-cards" *ngIf="summary() as s">
      <div class="info muted" style="grid-column: 1 / -1; font-size: 12px; margin-bottom: 4px;">
        Note: Percentages below are computed using each team's overall longest-ever streak type across all matches. Current streaks are shown for reference only.
      </div>
      <div class="card">
        <div class="card-title has-tip" [attr.data-tip]="tip('sameCard')">Same Streak Matches
          <span class="muted" *ngIf="s.sameStreak?.total != null">— {{ s.sameStreak?.total }} matches</span>
          <span class="pill-badge" *ngIf="s.fixtureContext==='same_streak'" [ngStyle]="streakPillStyle(s)" style="margin-left:8px;">Same</span>
        </div>
        <div class="section">
          <div class="section-title has-tip" [attr.data-tip]="tip('results')">Results</div>
          <ul class="kv">
            <li>Wins: <strong>{{ fmt(s.sameStreak.winPercent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.winCount != null"> ({{ s.sameStreak.winCount }})</span></li>
            <li>Draws: <strong>{{ fmt(s.sameStreak.drawPercent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.drawCount != null"> ({{ s.sameStreak.drawCount }})</span></li>
            <li>Losses: <strong>{{ fmt(s.sameStreak.lossPercent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.lossCount != null"> ({{ s.sameStreak.lossCount }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section">
          <div class="section-title has-tip" [attr.data-tip]="tip('goals')">Goals & BTTS</div>
          <ul class="kv">
            <li>Over 1.5 Goals: <strong>{{ fmt(s.sameStreak.over15Percent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.over15Count != null"> ({{ s.sameStreak.over15Count }})</span></li>
            <li>Over 2.5 Goals: <strong>{{ fmt(s.sameStreak.over25Percent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.over25Count != null"> ({{ s.sameStreak.over25Count }})</span></li>
            <li>BTTS: <strong>{{ fmt(s.sameStreak.bttsPercent) }}%</strong><span class="muted" *ngIf="s.sameStreak?.bttsCount != null"> ({{ s.sameStreak.bttsCount }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section" *ngIf="s.sameTopScorelines && s.sameTopScorelines.length">
          <div class="section-title has-tip" [attr.data-tip]="tip('topScorelines')">Top scorelines (all results)</div>
          <ul class="kv">
            <li *ngFor="let it of s.sameTopScorelines">{{ it.scoreline }}<span class="muted" *ngIf="it.count != null"> ({{ it.count }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section" *ngIf="s.sameStreak?.mostCommonWinScoreline">
          <div class="section-title has-tip" [attr.data-tip]="tip('winMode')">Most common winning scoreline</div>
          <div class="kv-inline">{{ s.sameStreak.mostCommonWinScoreline }}<span class="muted" *ngIf="s.sameStreak?.mostCommonWinCount != null"> ({{ s.sameStreak.mostCommonWinCount }})</span></div>
        </div>
      </div>
      <div class="card">
        <div class="card-title has-tip" [attr.data-tip]="tip('diffCard')">Different Streak Matches
          <span class="muted" *ngIf="s.differentStreak?.total != null">— {{ s.differentStreak?.total }} matches</span>
          <span class="pill-badge" *ngIf="s.fixtureContext==='different_streak'" [ngStyle]="streakPillStyle(s, true)" style="margin-left:8px;">Different</span>
        </div>
        <div class="section">
          <div class="section-title has-tip" [attr.data-tip]="tip('results')">Results</div>
          <ul class="kv">
            <li>Wins: <strong>{{ fmt(s.differentStreak.winPercent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.winCount != null"> ({{ s.differentStreak.winCount }})</span></li>
            <li>Draws: <strong>{{ fmt(s.differentStreak.drawPercent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.drawCount != null"> ({{ s.differentStreak.drawCount }})</span></li>
            <li>Losses: <strong>{{ fmt(s.differentStreak.lossPercent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.lossCount != null"> ({{ s.differentStreak.lossCount }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section">
          <div class="section-title has-tip" [attr.data-tip]="tip('goals')">Goals & BTTS</div>
          <ul class="kv">
            <li>Over 1.5 Goals: <strong>{{ fmt(s.differentStreak.over15Percent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.over15Count != null"> ({{ s.differentStreak.over15Count }})</span></li>
            <li>Over 2.5 Goals: <strong>{{ fmt(s.differentStreak.over25Percent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.over25Count != null"> ({{ s.differentStreak.over25Count }})</span></li>
            <li>BTTS: <strong>{{ fmt(s.differentStreak.bttsPercent) }}%</strong><span class="muted" *ngIf="s.differentStreak?.bttsCount != null"> ({{ s.differentStreak.bttsCount }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section" *ngIf="s.differentTopScorelines && s.differentTopScorelines.length">
          <div class="section-title has-tip" [attr.data-tip]="tip('topscore')">Top scorelines (all results)</div>
          <ul class="kv">
            <li *ngFor="let it of s.differentTopScorelines">{{ it.scoreline }}<span class="muted" *ngIf="it.count != null"> ({{ it.count }})</span></li>
          </ul>
        </div>
        <div class="divider"></div>
        <div class="section" *ngIf="s.differentStreak?.mostCommonWinScoreline">
          <div class="section-title has-tip" [attr.data-tip]="tip('winScoreline')">Most common winning scoreline</div>
          <div class="kv-inline">{{ s.differentStreak.mostCommonWinScoreline }}<span class="muted" *ngIf="s.differentStreak?.mostCommonWinCount != null"> ({{ s.differentStreak.mostCommonWinCount }})</span></div>
        </div>
      </div>
    </div>

    <div class="subcategory" *ngIf="summary() as s">
      <div class="card">
        <div class="card-title has-tip" [attr.data-tip]="tip('subcats')">Scorelines by Streak-Type Subcategory</div>
        <div class="pill-grid">
          <div class="pill-section has-tip" [attr.data-tip]="tip('wvw')" *ngIf="s.scorelineWvW">
            <span class="pill-badge" style="background:#243b55; color:#d0e2ff;">W vs W</span>
            <span class="kv-inline">{{ s.scorelineWvW?.scoreline }}<span class="muted" *ngIf="s.scorelineWvW?.count != null"> ({{ s.scorelineWvW?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('dvd')" *ngIf="s.scorelineDvD">
            <span class="pill-badge" style="background:#243b55; color:#d0e2ff;">D vs D</span>
            <span class="kv-inline">{{ s.scorelineDvD?.scoreline }}<span class="muted" *ngIf="s.scorelineDvD?.count != null"> ({{ s.scorelineDvD?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('lvl')" *ngIf="s.scorelineLvL">
            <span class="pill-badge" style="background:#243b55; color:#d0e2ff;">L vs L</span>
            <span class="kv-inline">{{ s.scorelineLvL?.scoreline }}<span class="muted" *ngIf="s.scorelineLvL?.count != null"> ({{ s.scorelineLvL?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('wvd')" *ngIf="s.scorelineWvD">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">W vs D</span>
            <span class="kv-inline">{{ s.scorelineWvD?.scoreline }}<span class="muted" *ngIf="s.scorelineWvD?.count != null"> ({{ s.scorelineWvD?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('dvw')" *ngIf="s.scorelineDvW">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">D vs W</span>
            <span class="kv-inline">{{ s.scorelineDvW?.scoreline }}<span class="muted" *ngIf="s.scorelineDvW?.count != null"> ({{ s.scorelineDvW?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('wvl')" *ngIf="s.scorelineWvL">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">W vs L</span>
            <span class="kv-inline">{{ s.scorelineWvL?.scoreline }}<span class="muted" *ngIf="s.scorelineWvL?.count != null"> ({{ s.scorelineWvL?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('lvw')" *ngIf="s.scorelineLvW">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">L vs W</span>
            <span class="kv-inline">{{ s.scorelineLvW?.scoreline }}<span class="muted" *ngIf="s.scorelineLvW?.count != null"> ({{ s.scorelineLvW?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('dvl')" *ngIf="s.scorelineDvL">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">D vs L</span>
            <span class="kv-inline">{{ s.scorelineDvL?.scoreline }}<span class="muted" *ngIf="s.scorelineDvL?.count != null"> ({{ s.scorelineDvL?.count }})</span></span>
          </div>
          <div class="pill-section has-tip" [attr.data-tip]="tip('lvd')" *ngIf="s.scorelineLvD">
            <span class="pill-badge" style="background:#5b2a00; color:#ffd6a1;">L vs D</span>
            <span class="kv-inline">{{ s.scorelineLvD?.scoreline }}<span class="muted" *ngIf="s.scorelineLvD?.count != null"> ({{ s.scorelineLvD?.count }})</span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="conclusion" *ngIf="summary() as s">
      <div class="card">
        <div class="card-title has-tip" [attr.data-tip]="tip('conclusion')">Conclusion</div>
        <div class="kv-table">
          <div class="k has-tip" [attr.data-tip]="tip('fixture')">Fixture</div>
          <div class="v">
            <ng-container *ngIf="s.upcoming as u; else noUpcoming">
              {{ u.homeTeam }} vs {{ u.awayTeam }}<span *ngIf="u.date"> on {{ u.date }}</span>
            </ng-container>
            <ng-template #noUpcoming>
              {{ simulated() ? 'Simulated matchup' : (selectedHome()?.name || 'This team') + (selectedAway()?.name ? (' vs ' + selectedAway()?.name) : '') }}
            </ng-template>
          </div>

          <div class="k has-tip" [attr.data-tip]="tip('matchup')">Matchup</div>
          <div class="v">
            <span class="pill-badge" [ngStyle]="streakPillStyle(s)">
              {{ s.fixtureContext==='same_streak' ? 'Same Streak' : 'Different Streak' }}
            </span>
          </div>

          <div class="k has-tip" [attr.data-tip]="tip('longestTypes')">Longest types</div>
          <div class="v">
            <ng-container *ngIf="(s.selectedTeamCount ?? null) != null && s.selectedTeamType; else dashA">
              {{ s.selectedTeamCount }}{{ s.selectedTeamType }}
            </ng-container>
            <ng-template #dashA>-</ng-template>
            <span style="opacity:0.75; padding: 0 6px;">VS</span>
            <ng-container *ngIf="(s.opponentTeamCount ?? null) != null && s.opponentTeamType; else dashB">
              {{ s.opponentTeamCount }}{{ s.opponentTeamType }}
            </ng-container>
            <ng-template #dashB>-</ng-template>
          </div>

          <ng-container *ngIf="s.fixtureContext==='same_streak'; else diffRows">
            <div class="k has-tip" [attr.data-tip]="tip('under25Same')">Under 2.5</div>
            <div class="v"><strong>{{ fmt(100 - s.sameStreak.over25Percent) }}%</strong></div>
            <div class="k has-tip" [attr.data-tip]="tip('drawsSame')">Draws</div>
            <div class="v"><strong>{{ fmt(s.sameStreak.drawPercent) }}%</strong></div>
            <div class="k has-tip" [attr.data-tip]="tip('outlookSame')">Outlook</div>
            <div class="v">Low-scoring, tight contest</div>
          </ng-container>
          <ng-template #diffRows>
            <div class="k has-tip" [attr.data-tip]="tip('over25Diff')">Over 2.5</div>
            <div class="v"><strong>{{ fmt(s.differentStreak.over25Percent) }}%</strong></div>
            <div class="k has-tip" [attr.data-tip]="tip('winsDiff')">Wins</div>
            <div class="v"><strong>{{ fmt(s.differentStreak.winPercent) }}%</strong></div>
            <div class="k has-tip" [attr.data-tip]="tip('outlookDiff')">Outlook</div>
            <div class="v">More goals, decisive result</div>
          </ng-template>

          <ng-container *ngIf="hasAnyScorelineRow(s)">
            <div class="k">Scorelines</div>
            <div class="v">
              <div class="scorelines-3col">
                <!-- Wins column (left) -->
                <div class="col">
                  <div class="col-title">Wins</div>
                  <div class="row has-tip" [attr.data-tip]="tip('winVsW')" *ngIf="s.winScorelineVsW?.scoreline">
                    <span>vs W</span>
                    <span>{{ s.winScorelineVsW?.scoreline }}<span class="muted" *ngIf="s.winScorelineVsW?.count != null"> ({{ s.winScorelineVsW?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('winVsD')" *ngIf="s.winScorelineVsD?.scoreline">
                    <span>vs D</span>
                    <span>{{ s.winScorelineVsD?.scoreline }}<span class="muted" *ngIf="s.winScorelineVsD?.count != null"> ({{ s.winScorelineVsD?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('winVsL')" *ngIf="s.winScorelineVsL?.scoreline">
                    <span>vs L</span>
                    <span>{{ s.winScorelineVsL?.scoreline }}<span class="muted" *ngIf="s.winScorelineVsL?.count != null"> ({{ s.winScorelineVsL?.count }})</span></span>
                  </div>
                </div>
                
                <!-- Draws column (middle) -->
                <div class="col">
                  <div class="col-title">Draws</div>
                  <div class="row has-tip" [attr.data-tip]="tip('drawVsW')" *ngIf="s.drawScorelineVsW?.scoreline">
                    <span>vs W</span>
                    <span>{{ s.drawScorelineVsW?.scoreline }}<span class="muted" *ngIf="s.drawScorelineVsW?.count != null"> ({{ s.drawScorelineVsW?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('drawVsD')" *ngIf="s.drawScorelineVsD?.scoreline">
                    <span>vs D</span>
                    <span>{{ s.drawScorelineVsD?.scoreline }}<span class="muted" *ngIf="s.drawScorelineVsD?.count != null"> ({{ s.drawScorelineVsD?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('drawVsL')" *ngIf="s.drawScorelineVsL?.scoreline">
                    <span>vs L</span>
                    <span>{{ s.drawScorelineVsL?.scoreline }}<span class="muted" *ngIf="s.drawScorelineVsL?.count != null"> ({{ s.drawScorelineVsL?.count }})</span></span>
                  </div>
                </div>
                
                <!-- Losses column (right) -->
                <div class="col">
                  <div class="col-title">Losses</div>
                  <div class="row has-tip" [attr.data-tip]="tip('lossVsW')" *ngIf="s.lossScorelineVsW?.scoreline">
                    <span>vs W</span>
                    <span>{{ s.lossScorelineVsW?.scoreline }}<span class="muted" *ngIf="s.lossScorelineVsW?.count != null"> ({{ s.lossScorelineVsW?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('lossVsD')" *ngIf="s.lossScorelineVsD?.scoreline">
                    <span>vs D</span>
                    <span>{{ s.lossScorelineVsD?.scoreline }}<span class="muted" *ngIf="s.lossScorelineVsD?.count != null"> ({{ s.lossScorelineVsD?.count }})</span></span>
                  </div>
                  <div class="row has-tip" [attr.data-tip]="tip('lossVsL')" *ngIf="s.lossScorelineVsL?.scoreline">
                    <span>vs L</span>
                    <span>{{ s.lossScorelineVsL?.scoreline }}<span class="muted" *ngIf="s.lossScorelineVsL?.count != null"> ({{ s.lossScorelineVsL?.count }})</span></span>
                  </div>
                </div>
              </div>
            </div>
          </ng-container>
        </div>
      </div>
    </div>
  </div>
  `,
  styles: [`
    .page { max-width: 1100px; margin: 0 auto; padding: 16px; color: #e6eef8; background:#0a0a0a; font-family: Inter, Roboto, Arial, sans-serif; }
    h1 { margin: 0 0 12px; font-size: 22px; color:#ffffff; }
    .row { display:grid; grid-template-columns: 1fr 1fr; gap:12px; align-items: start; margin-bottom: 8px; }
    .search { position: relative; }
    .search label { display:block; font-size: 12px; color:#9fb3cd; margin-bottom:4px; }
    .search input { width:100%; padding: 10px 12px; border-radius: 8px; background:#2a2a2a; border:1px solid #404040; color:#ffffff; }
    .search input:focus { border-color:#007bff; box-shadow: 0 0 0 3px rgba(0,123,255,0.12); outline:none; }
    .dropdown { position:absolute; left:0; right:0; background:#121212; border:1px solid #232323; border-radius:8px; margin-top:6px; max-height: 260px; overflow:auto; padding:6px 0; box-shadow: 0 10px 30px rgba(0,0,0,.45); z-index: 20; }
    .dropdown li { list-style:none; padding:8px 12px; cursor:pointer; color:#e0e0e0; }
    .dropdown li:hover { background:#1f1f1f; }
    .muted { color:#cbd5e1; }
    .summary { margin: 12px 0; display:flex; align-items:center; gap:8px; flex-wrap: wrap; }
    .simulate { margin-left: auto; padding:10px 14px; border-radius:8px; border:1px solid #3b82f6; background:#0b3a75; color:#f8fafc; cursor:pointer; font-weight:600; }
    .simulate[disabled] { opacity:0.6; cursor:not-allowed; }
    .pill { display:inline-block; padding:6px 10px; border-radius: 999px; background:#101010; border:1px solid #2b2b2b; color:#f1f5f9; }
    .table-wrap { overflow:auto; margin-top: 12px; }
    table.tbl { width:100%; border-collapse: collapse; background:#121212; border:1px solid #232323; border-radius:8px; font-size:14px; }
    table.tbl th { padding: 12px; text-align:left; background:#1a1a1a; color:#e2e8f0; position:sticky; top:0; font-weight:800; font-size:13px; }
    table.tbl th:first-child { width: 48px; }
    table.tbl td { padding: 10px 12px; border-top: 1px solid #1f2937; color:#f1f5f9; }
    td.num { text-align: right; color:#cbd5e1; }
    tr:nth-child(even) td { background:#0f0f0f; }
    td .badge { display:inline-block; min-width:26px; text-align:center; padding:3px 8px; border-radius:6px; border:1px solid #334155; background:#0f172a; color:#e6eef8; font-weight:800; }
    td .badge.w { background:#114b2b; color:#86efac; border-color:#1f7a4c; }
    td .badge.d { background:#303030; color:#e2e8f0; border-color:#525252; }
    td .badge.l { background:#4b1111; color:#fecaca; border-color:#7f1d1d; }
    .me { font-weight: 800; color:#ffffff; }
    .loading, .empty { margin-top: 10px; color:#cbd5e1; }
    .progress { width: 260px; height: 10px; background: #1f2937; border:1px solid #334155; border-radius: 999px; overflow: hidden; margin-top: 6px; }
    .progress.small { width: 160px; height: 8px; }
    .progress-bar { height: 100%; background: linear-gradient(90deg, #3b82f6 0%, #22c55e 100%); transition: width 180ms ease; }
    .progress-inline { display:flex; align-items:center; gap:8px; margin-left: 8px; }
    .progress-label { font-size: 12px; color: #cbd5e1; }
    .summary-cards { display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin-top:12px; }
    .card { background:#121212; border:1px solid #30363d; border-radius:10px; padding:14px; }
    .card-title { font-weight:900; color:#ffffff; margin-bottom:8px; font-size:16px; letter-spacing: 0.2px; }
    .section-title { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:#93c5fd; margin-bottom:6px; font-weight:800; }
    .divider { height:1px; background:#1f2937; margin:10px 0; border-radius:1px; }
    .kv { list-style:none; padding:0; margin:0; display:grid; gap:6px; color:#e6eef8; font-size:14px; }
    .kv li strong { color:#ffffff; font-weight:900; }
    /* Improve readability of paragraphs and prevent overlap/wrapping issues */
    .card p { line-height: 1.5; overflow-wrap: anywhere; word-break: break-word; color:#e6eef8; }
    /* Ensure the “Longest types…” metadata sits on its own line and is readable */
    .conclusion .muted { display: inline-block; margin: 6px 0 4px; line-height: 1.4; color:#e5e7eb; }
    .kv-table { display:grid; grid-template-columns: 160px 1fr; gap:8px 12px; font-size:14px; }
    .kv-table .k { color:#93c5fd; text-transform:uppercase; font-size:11px; letter-spacing:0.6px; align-self:center; }
    .kv-table .v { color:#e6eef8; }
    .kv-table .v strong { color:#ffffff; font-weight:900; }
    /* Pill badge used for matchup coloring */
    .pill-badge { display:inline-block; padding:6px 10px; border-radius:999px; border:1px solid #2b2b2b; font-weight:900; letter-spacing:0.3px; color:#ffffff; background:#1f2937; min-width: 132px; text-align:center; }
    .has-tip { cursor: help; position: relative; }
    /* Styled tooltips using data-tip attribute */
    .has-tip[data-tip]:hover::after {
      content: attr(data-tip);
      position: absolute;
      top: 100%;
      left: 0;
      transform: translateY(8px);
      background: #0b3b74;
      color: #e6f2ff;
      padding: 10px 12px;
      border: 1px solid #1e40af;
      border-radius: 8px;
      font-size: 13.5px;
      line-height: 1.35;
      z-index: 9999;
      white-space: normal;
      max-width: 360px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.35);
    }
    .has-tip[data-tip]:hover::before {
      content: '';
      position: absolute;
      top: calc(100% + 2px);
      left: 14px;
      border-width: 6px;
      border-style: solid;
      border-color: #0b3b74 transparent transparent transparent;
      z-index: 10000;
    }
    .pill-grid { display:grid; grid-template-columns: 1fr 1fr 1fr; gap:10px; }
    .pill-section { display:flex; align-items:center; gap:8px; background:#0f0f0f; border:1px solid #1f2937; border-radius:999px; padding:6px 10px; }
    @media (max-width: 720px) { .kv-table { grid-template-columns: 120px 1fr; } .pill-grid { grid-template-columns: 1fr; } }
    /* Three-column scoreline layout inside Conclusion */
    .scorelines-3col { display:grid; grid-template-columns: 1fr 1fr 1fr; gap:10px; }
    .scorelines-3col .col { background:#0f0f0f; border:1px solid #1f2937; border-radius:8px; padding:8px; }
    .scorelines-3col .col-title { font-size:11px; text-transform:uppercase; letter-spacing:0.6px; color:#93c5fd; font-weight:800; margin-bottom:6px; }
    .scorelines-3col .row { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:6px 0; border-top:1px solid #1f2937; }
    .scorelines-3col .row:first-of-type { border-top:none; }
    .conclusion { margin-top:12px; }
    @media (max-width: 720px) { .summary-cards { grid-template-columns: 1fr; } .row { grid-template-columns: 1fr; } .scorelines-3col { grid-template-columns: 1fr; } }
  `]
})
export class StreakInsightsComponent {
  private http = inject(HttpClient);

  // Explanatory tooltips for summary sections (use {TEAM} to inject the selected team name)
  tips: Record<string, string> = {
    sameCard: "Matches where both teams' overall longest-ever streak types are the same (W/W, D/D, or L/L).",
    diffCard: "Matches where teams' overall longest-ever streak types differ (e.g., W vs D, W vs L, D vs L).",
    results: "Distribution of results for {TEAM} within this matchup bucket.",
    goals: "Share of matches in this bucket that exceeded 1.5 or 2.5 total goals, and where both teams scored (BTTS).",
    topScorelines: "Most frequent full-time scorelines in this bucket from {TEAM}’s perspective (team goals first).",
    winMode: "Scoreline {TEAM} most often wins by within this bucket.",
    subcats: "Most frequent scoreline for each specific pairing of overall longest-ever streak types (e.g., W vs W, W vs D).",
    wvw: "Both teams have Win as their overall longest-ever streak type.",
    dvd: "Both teams have Draw as their overall longest-ever streak type.",
    lvl: "Both teams have Loss as their overall longest-ever streak type.",
    wvd: "{TEAM} longest type = Win, opponent longest type = Draw.",
    dvw: "{TEAM} longest type = Draw, opponent longest type = Win.",
    wvl: "{TEAM} longest type = Win, opponent longest type = Loss.",
    lvw: "{TEAM} longest type = Loss, opponent longest type = Win.",
    dvl: "{TEAM} longest type = Draw, opponent longest type = Loss.",
    lvd: "{TEAM} longest type = Loss, opponent longest type = Draw.",
    // Conclusion section tooltips
    conclusion: "Quick take summarizing the matchup context and key probabilities for {TEAM}.",
    fixture: "The upcoming fixture if available; otherwise the currently selected teams (for simulation).",
    matchup: "Whether the teams' overall longest-ever streak types are the same or different. This determines which bucket the below stats come from.",
    longestTypes: "Each team’s overall longest-ever streak to date — shown as count plus type. {TEAM} first, then the opponent.",
    under25Same: "Estimated share of Same Streak matchups that finish with under 2.5 total goals.",
    drawsSame: "Draw rate for {TEAM} in Same Streak matchups.",
    outlookSame: "Same Streak contexts tend to be tighter: fewer goals and more balanced outcomes.",
    over25Diff: "Estimated share of Different Streak matchups that finish with over 2.5 total goals.",
    winsDiff: "Win rate for {TEAM} in Different Streak matchups.",
    outlookDiff: "Different Streak contexts tend to be more open: more goals and clearer winners.",
    winVsW: "When the opponent’s overall longest-ever type is Win, this is the scoreline {TEAM} most often wins by.",
    winVsD: "When the opponent’s overall longest-ever type is Draw, this is the scoreline {TEAM} most often wins by.",
    winVsL: "When the opponent’s overall longest-ever type is Loss, this is the scoreline {TEAM} most often wins by.",
    drawVsW: "When the opponent’s overall longest-ever type is Win, this is the draw scoreline that occurs most often for {TEAM}.",
    drawVsD: "When the opponent’s overall longest-ever type is Draw, this is the draw scoreline that occurs most often for {TEAM}.",
    drawVsL: "When the opponent’s overall longest-ever type is Loss, this is the draw scoreline that occurs most often for {TEAM}.",
    lossVsW: "When the opponent’s overall longest-ever type is Win, this is the scoreline {TEAM} most often loses by.",
    lossVsD: "When the opponent’s overall longest-ever type is Draw, this is the scoreline {TEAM} most often loses by.",
    lossVsL: "When the opponent’s overall longest-ever type is Loss, this is the scoreline {TEAM} most often loses by."
  };

  // Alias map to support older template keys
  tipAliases: Record<string, string> = {
    topscore: 'topScorelines',
    winScoreline: 'winMode'
  };

  // Resolve a user-friendly selected team name for tooltip texts
  private primarySelectedTeamName(): string {
    return this.selectedHome()?.name || this.selectedAway()?.name || 'selected team';
  }

  // Build the tooltip text with the selected team name injected
  tip(key: string): string {
    const mapKey = (this.tipAliases[key] || key) as keyof typeof this.tips;
    const tmpl = this.tips[mapKey] || '';
    return tmpl.replaceAll('{TEAM}', this.primarySelectedTeamName());
  }

  // Format a percentage value (clamped 0–100, rounded). Returns '-' for invalid inputs.
  fmt(v: number | null | undefined): string {
    if (v == null || isNaN(Number(v))) return '-';
    const n = Math.round(Math.min(100, Math.max(0, Number(v))));
    return String(n);
    }

    // Helper: whether any of the scoreline rows (win/draw/loss vs W/D/L) exists
    hasAnyScorelineRow(s: StreakSummaryResponse | null | undefined): boolean {
      if (!s) return false;
      return !!(
        s.winScorelineVsW?.scoreline || s.winScorelineVsD?.scoreline || s.winScorelineVsL?.scoreline ||
        s.drawScorelineVsW?.scoreline || s.drawScorelineVsD?.scoreline || s.drawScorelineVsL?.scoreline ||
        s.lossScorelineVsW?.scoreline || s.lossScorelineVsD?.scoreline || s.lossScorelineVsL?.scoreline
      );
    }

    // Home selector state
  homeQuery = '';
  homeSuggestions: TeamOption[] = [];
  selectedHome = signal<TeamOption | null>(null);
  private dropOpenHome = signal<boolean>(false);
  private homeDebounce?: any;

  // Away selector state
  awayQuery = '';
  awaySuggestions: TeamOption[] = [];
  selectedAway = signal<TeamOption | null>(null);
  private dropOpenAway = signal<boolean>(false);
  private awayDebounce?: any;

  // Data & UI state
  simulated = signal<boolean>(false);
  timeline = signal<StreakTimelineItem[]>([]);
  loading = signal<boolean>(false);
  summary = signal<StreakSummaryResponse | null>(null);
  private timelineCache = new Map<string, StreakTimelineItem[]>();

  // Progress state
  teamProgress = signal<number>(0);
  simulateInProgress = signal<boolean>(false);
  simulateProgress = signal<number>(0);
  private teamProgTimer?: any;
  private simProgTimer?: any;

  showHomeDropdown = computed(() => this.dropOpenHome() && this.homeSuggestions.length > 0);
  showAwayDropdown = computed(() => this.dropOpenAway() && this.awaySuggestions.length > 0);

  // --- Progress helpers ---
  private startTeamProgress() {
    if (this.teamProgTimer) clearInterval(this.teamProgTimer);
    this.teamProgress.set(5);
    this.teamProgTimer = setInterval(() => {
      const v = this.teamProgress();
      if (v >= 90) return; // cap while loading
      this.teamProgress.set(Math.min(90, v + 3));
    }, 180);
  }
  private completeTeamProgress() {
    if (this.teamProgTimer) { clearInterval(this.teamProgTimer); this.teamProgTimer = undefined; }
    this.teamProgress.set(100);
    setTimeout(() => this.teamProgress.set(0), 600);
  }
  private failTeamProgress() {
    if (this.teamProgTimer) { clearInterval(this.teamProgTimer); this.teamProgTimer = undefined; }
    this.teamProgress.set(0);
  }

  private startSimulateProgress() {
    if (this.simProgTimer) clearInterval(this.simProgTimer);
    this.simulateInProgress.set(true);
    this.simulateProgress.set(8);
    this.simProgTimer = setInterval(() => {
      const v = this.simulateProgress();
      if (v >= 85) return;
      // progress slower as it gets higher
      const step = v < 40 ? 6 : v < 65 ? 4 : 2;
      this.simulateProgress.set(Math.min(85, v + step));
    }, 180);
  }
  private completeSimulateProgress() {
    if (this.simProgTimer) { clearInterval(this.simProgTimer); this.simProgTimer = undefined; }
    this.simulateProgress.set(100);
    setTimeout(() => {
      this.simulateInProgress.set(false);
      this.simulateProgress.set(0);
    }, 700);
  }
  private failSimulateProgress() {
    if (this.simProgTimer) { clearInterval(this.simProgTimer); this.simProgTimer = undefined; }
    this.simulateInProgress.set(false);
    this.simulateProgress.set(0);
  }

  onHomeQueryChange(v: string) {
    this.homeQuery = v;
    this.simulated.set(false);
    this.summary.set(null);
    if (this.homeDebounce) clearTimeout(this.homeDebounce);
    if (!v || v.trim().length < 3) {
      this.homeSuggestions = [];
      this.dropOpenHome.set(false);
      return;
    }
    this.homeDebounce = setTimeout(() => this.searchHomeTeams(v.trim()), 220);
  }

  onAwayQueryChange(v: string) {
    this.awayQuery = v;
    this.simulated.set(false);
    this.summary.set(null);
    if (this.awayDebounce) clearTimeout(this.awayDebounce);
    if (!v || v.trim().length < 3) {
      this.awaySuggestions = [];
      this.dropOpenAway.set(false);
      return;
    }
    this.awayDebounce = setTimeout(() => this.searchAwayTeams(v.trim()), 220);
  }

  private searchHomeTeams(q: string) {
    this.http.get<TeamOption[]>(`/api/teams/search?query=${encodeURIComponent(q)}`)
      .subscribe(list => {
        this.homeSuggestions = list || [];
        this.dropOpenHome.set(true);
      }, _ => {
        this.homeSuggestions = [];
        this.dropOpenHome.set(false);
      });
  }

  private searchAwayTeams(q: string) {
    this.http.get<TeamOption[]>(`/api/teams/search?query=${encodeURIComponent(q)}`)
      .subscribe(list => {
        this.awaySuggestions = list || [];
        this.dropOpenAway.set(true);
      }, _ => {
        this.awaySuggestions = [];
        this.dropOpenAway.set(false);
      });
  }

  selectHome(t: TeamOption) {
    this.selectedHome.set(t);
    this.homeQuery = t.name;
    this.dropOpenHome.set(false);
    this.simulated.set(false);
    this.summary.set(null);
    // Load home team's timeline by default
    this.loadTimeline(t.name);
    // Also load single-team summary (until user simulates)
    if (t.id != null) {
      this.loadTeamSummary(t.id);
    }
  }

  selectAway(t: TeamOption) {
    this.selectedAway.set(t);
    this.awayQuery = t.name;
    this.dropOpenAway.set(false);
    this.simulated.set(false);
    // Keep any existing summary unless simulating or replacing with home-only
  }

  simulateMatchup() {
    const h = this.selectedHome();
    const a = this.selectedAway();
    if (!h || !a || h.id == null || a.id == null) { return; }
    this.simulated.set(true);
    this.summary.set(null);
    this.startSimulateProgress();
    this.http.get<StreakSummaryResponse>(`/api/teams/streak-summary/simulate?homeId=${encodeURIComponent(String(h.id))}&awayId=${encodeURIComponent(String(a.id))}`)
      .subscribe(res => {
        this.summary.set(res || null);
        this.completeSimulateProgress();
      }, _ => {
        this.summary.set(null);
        this.failSimulateProgress();
      });
  }

  private loadTimeline(teamName: string) {
    const key = teamName.trim().toLowerCase();
    const cached = this.timelineCache.get(key);
    if (cached) {
      this.timeline.set(cached);
      this.teamProgress.set(100);
      setTimeout(() => this.teamProgress.set(0), 400);
      return;
    }
    this.loading.set(true);
    this.timeline.set([]);
    this.startTeamProgress();
    // Request timeline with server-side cap to ensure fast response
    this.http.get<StreakTimelineItem[]>(`/api/matches/streak-insights/by-team-name?name=${encodeURIComponent(teamName)}&limit=180`)
      .subscribe(rows => {
        const data = rows || [];
        this.timeline.set(data);
        this.timelineCache.set(key, data);
        this.loading.set(false);
        this.completeTeamProgress();
      }, _ => {
        this.timeline.set([]);
        this.loading.set(false);
        this.failTeamProgress();
      });
  }

  private loadTeamSummary(teamId: number) {
    this.http.get<StreakSummaryResponse>(`/api/teams/${teamId}/streak-summary`)
      .subscribe(res => {
        this.summary.set(res || null);
      }, _ => {
        this.summary.set(null);
      });
  }

  // Compute dynamic pill style for matchup coloring
  streakPillStyle(s: StreakSummaryResponse | null | undefined, forceDifferent?: boolean): { [k: string]: string } {
    const neutral = { background: '#1f2937', color: '#ffffff', borderColor: '#2b2b2b' };
    if (!s) return neutral;
    const typeColor = (t: 'W' | 'D' | 'L' | null | undefined): string => {
      switch (t) {
        case 'W': return '#16a34a'; // green
        case 'L': return '#dc2626'; // red
        case 'D': return '#f59e0b'; // orange
        default: return '#1f2937'; // neutral dark
      }
    };

    const leftType = s.selectedTeamType || null; // Home on left
    const rightType = s.opponentTeamType || null; // Away on right

    // If we are not forcing a different-streak rendering, keep solid color for same-streak
    if (!forceDifferent && s.fixtureContext === 'same_streak' && leftType && rightType && leftType === rightType) {
      const solid = typeColor(leftType);
      return { background: solid, color: '#ffffff', borderColor: '#2b2b2b' };
    }

    // Otherwise, render split (or neutral if no types)
    const left = typeColor(leftType);
    const right = typeColor(rightType);
    if (left === '#1f2937' && right === '#1f2937') return neutral;
    return {
      background: `linear-gradient(to right, ${left} 0%, ${left} 50%, ${right} 50%, ${right} 100%)`,
      color: '#ffffff',
      borderColor: '#2b2b2b'
    };
  }
}

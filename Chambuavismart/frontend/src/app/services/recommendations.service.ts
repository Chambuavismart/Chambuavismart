import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface StreakInsightDTO {
  teamName?: string | null;
  pattern?: string | null; // e.g., "3W"
  instances?: number | null;
  nextWinPct?: number | null;
  nextDrawPct?: number | null;
  nextLossPct?: number | null;
  over15Pct?: number | null;
  over25Pct?: number | null;
  over35Pct?: number | null;
  bttsPct?: number | null;
  summaryText?: string | null;
}

export interface RecommendationSummaryDTO {
  fixtureId?: number | null;
  leagueId: number | null;
  seasonId?: number | null;
  leagueName?: string | null;
  homeTeam?: string | null;
  awayTeam?: string | null;
  outcomeLean: 'HOME' | 'DRAW' | 'AWAY' | 'UNKNOWN';
  outcomeConfidence: number; // 0-100
  correctScoreShortlist: string[];
  bttsRecommendation: string; // Yes/No/Lean
  overUnderRecommendation: string; // e.g., "Over 2.5"/"Under 2.5"/"2.5 borderline"
  overUnderProbability?: number | null;
  correctScoreContext?: string | null;
  confidenceBreakdownText?: string | null;
  fixtureConfidenceComponent?: number | null;
  streakConfidenceComponent?: number | null;
  adjustmentConfidence?: number | null;
  homeStreakSampleLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
  awayStreakSampleLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
  homeStreakInstances?: number | null;
  awayStreakInstances?: number | null;
  homeStreakNote?: string | null;
  awayStreakNote?: string | null;
  divergenceWarning: boolean;
  divergenceNote?: string | null;
  rationale?: string[];
  // New explainability fields
  analysisMatchesCount?: number | null;
  fixtureAnalysisFactors?: string[];
  streakInsightFactors?: string[];
  // Include raw streaks for richer display if needed
  homeStreak?: StreakInsightDTO | null;
  awayStreak?: StreakInsightDTO | null;
}

@Injectable({ providedIn: 'root' })
export class RecommendationsService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/recommendations`;

  getFixtureRecommendation(params: {
    fixtureId?: number | null;
    leagueId: number;
    seasonId?: number | null;
    homeTeamId?: number | null;
    awayTeamId?: number | null;
    leagueName?: string | null;
    homeTeamName?: string | null;
    awayTeamName?: string | null;
  }): Observable<RecommendationSummaryDTO> {
    const url = new URL(`${this.baseUrl}/fixture`, window.location.origin);
    const usp = new URLSearchParams();
    if (params.fixtureId != null) usp.set('fixtureId', String(params.fixtureId));
    usp.set('leagueId', String(params.leagueId));
    if (params.seasonId != null) usp.set('seasonId', String(params.seasonId));
    if (params.homeTeamId != null) usp.set('homeTeamId', String(params.homeTeamId));
    if (params.awayTeamId != null) usp.set('awayTeamId', String(params.awayTeamId));
    if (params.leagueName) usp.set('leagueName', params.leagueName);
    if (params.homeTeamName) usp.set('homeTeamName', params.homeTeamName);
    if (params.awayTeamName) usp.set('awayTeamName', params.awayTeamName);
    usp.set('_ts', String(Date.now()));
    return this.http.get<RecommendationSummaryDTO>(`${this.baseUrl}/fixture?${usp.toString()}`);
  }
}

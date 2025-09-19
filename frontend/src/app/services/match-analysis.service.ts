import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay } from 'rxjs';
import { getApiBase } from './api-base';

export interface MatchAnalysisRequest {
  leagueId: number;
  seasonId?: number; // optional: backend falls back to current season if omitted
  homeTeamId?: number;
  awayTeamId?: number;
  // Optional for convenience when IDs are unknown
  homeTeamName?: string;
  awayTeamName?: string;
  // Differentiates backend logic between tabs
  analysisType?: 'fixtures' | 'match';
  // Optional: force backend to recompute and overwrite cache
  refresh?: boolean;
}

export interface StreakInsightDto {
  teamName?: string;
  pattern?: string;
  instances?: number;
  nextWinPct?: number;
  nextDrawPct?: number;
  nextLossPct?: number;
  over15Pct?: number;
  over25Pct?: number;
  over35Pct?: number;
  bttsPct?: number;
  summaryText?: string;
}

export interface MatchAnalysisResponse {
  homeTeam: string;
  awayTeam: string;
  league: string;
  winProbabilities: { homeWin: number; draw: number; awayWin: number };
  bttsProbability: number;
  over25Probability: number;
  expectedGoals: { home: number; away: number };
  confidenceScore: number;
  advice: string;
  // Optional summaries for UI visualization
  formSummary?: { homeWin: number; draw: number; awayWin: number; btts: number; over25: number };
  h2hSummary?: { lastN: number; ppgHome: number; ppgAway: number; bttsPct: number; over25Pct: number; matches?: H2HMatchItem[] };
  headToHeadMatches?: { date: string; competition: string; homeTeam: string; awayTeam: string; homeGoals: number; awayGoals: number; }[];
  homeStreakInsight?: StreakInsightDto;
  awayStreakInsight?: StreakInsightDto;
}

export interface H2HMatchItem {
  date: string; // ISO yyyy-MM-dd
  home: string;
  away: string;
  score: string; // e.g., "2-1"
}

@Injectable({ providedIn: 'root' })
export class MatchAnalysisService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/match-analysis`;
  private cache = new Map<string, Observable<MatchAnalysisResponse>>();

  private keyOf(req: MatchAnalysisRequest): string {
    const key = JSON.stringify({
      leagueId: req.leagueId,
      seasonId: req.seasonId ?? null,
      homeTeamId: req.homeTeamId ?? null,
      awayTeamId: req.awayTeamId ?? null,
      homeTeamName: req.homeTeamName?.trim().toLowerCase() ?? null,
      awayTeamName: req.awayTeamName?.trim().toLowerCase() ?? null,
      analysisType: req.analysisType ?? 'match'
    });
    return key;
  }

  analyze(req: MatchAnalysisRequest): Observable<MatchAnalysisResponse> {
    if (req.refresh) {
      return this.http.post<MatchAnalysisResponse>(`${this.baseUrl}/analyze`, req);
    }
    const key = this.keyOf(req);
    const existing = this.cache.get(key);
    if (existing) return existing;
    const obs = this.http
      .post<MatchAnalysisResponse>(`${this.baseUrl}/analyze`, req)
      .pipe(shareReplay(1));
    this.cache.set(key, obs);
    return obs;
    }
}

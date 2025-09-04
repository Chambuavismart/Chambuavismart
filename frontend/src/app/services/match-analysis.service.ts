import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MatchAnalysisRequest {
  leagueId: number;
  seasonId: number; // required: analyze for selected season only
  homeTeamId?: number;
  awayTeamId?: number;
  // Optional for convenience when IDs are unknown
  homeTeamName?: string;
  awayTeamName?: string;
  // Optional: force backend to recompute and overwrite cache
  refresh?: boolean;
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
  h2hSummary?: { lastN: number; ppgHome: number; ppgAway: number; bttsPct: number; over25Pct: number };
}

@Injectable({ providedIn: 'root' })
export class MatchAnalysisService {
  private http = inject(HttpClient);
  private baseUrl = '/api/match-analysis';

  analyze(req: MatchAnalysisRequest): Observable<MatchAnalysisResponse> {
    return this.http.post<MatchAnalysisResponse>(`${this.baseUrl}/analyze`, req);
    }
}

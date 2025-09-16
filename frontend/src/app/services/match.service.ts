import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';
import { LeagueContextService } from './league-context.service';

export interface TeamBreakdownDto { total: number; wins: number; draws: number; losses: number; btts: number; over25: number; over15: number; }
export interface H2HSuggestion { teamA: string; teamB: string; }
export interface H2HMatchDto { year: number | null; date: string | null; homeTeam: string | null; awayTeam: string | null; result: string; season: string | null; }
export interface FormSummaryDto { recentResults: string[]; currentStreak: string; winRate: number; pointsEarned: number; ppgSeries?: number[]; }
export interface H2HFormTeamDto { teamId: string; teamName: string; last5: { streak: string; winRate: number; pointsPerGame: number; bttsPercent: number; over25Percent: number; fallback?: boolean; ppgSeries?: number[]; }; matches: { year: number | null; date: string | null; homeTeam: string | null; awayTeam: string | null; result: string; }[]; seasonResolved?: string | null; matchesAvailable?: string | null; warnings?: string[]; note?: string | null; sourceLeague?: string | null; }

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private leagueContext = inject(LeagueContextService);
  private baseUrl = `${getApiBase()}/matches`;

  private withLeague(params: any = {}): any {
    const leagueId = this.leagueContext.getCurrentLeagueId();
    if (leagueId) params.leagueId = String(leagueId);
    return params;
  }

  getTotalPlayedMatches(): Observable<number> {
    // Add a cache-busting param to avoid stale proxy/browser caches returning 0
    const ts = Date.now().toString();
    return this.http.get<number>(`${this.baseUrl}/played/total`, { params: { _ts: ts } });
    // Note: total across DB; not league-scoped.
  }

  getPlayedTotalByTeam(teamId: number): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/team/${teamId}/total`);
  }

  getPlayedTotalByTeamName(name: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/team/by-name/total`, { params: this.withLeague({ name }) });
  }

  getResultsBreakdownByTeamName(name: string): Observable<TeamBreakdownDto> {
    return this.http.get<TeamBreakdownDto>(`${this.baseUrl}/played/team/by-name/breakdown`, { params: this.withLeague({ name }) });
  }

  suggestH2H(query: string): Observable<H2HSuggestion[]> {
    return this.http.get<H2HSuggestion[]>(`${this.baseUrl}/h2h/suggest`, { params: this.withLeague({ query }) });
  }

  getH2HMatches(home: string, away: string): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches`, { params: this.withLeague({ home, away }) });
  }

  // New: Preferred ID-based H2H retrieval scoped to a season
  getH2HMatchesByIds(homeId: number, awayId: number, seasonId: number, limit: number = 10): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches`, { params: { homeId, awayId, seasonId, limit } as any });
  }

  // New: Oriented H2H across ALL seasons by IDs (backend ignores season when IDs are provided)
  getH2HMatchesByIdsAllSeasons(homeId: number, awayId: number, limit: number = 200): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches`, { params: { homeId, awayId, limit } as any });
  }

  // Total H2H count regardless of orientation
  getH2HCountAnyOrientation(teamA: string, teamB: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/h2h/count-any-orientation`, { params: this.withLeague({ teamA, teamB }) });
  }

  // All-orientations H2H match list (by names)
  getH2HMatchesAnyOrientation(teamA: string, teamB: string, limit: number = 200): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches-any-orientation`, { params: this.withLeague({ teamA, teamB, limit }) as any });
  }

  getFormByTeamName(name: string): Observable<FormSummaryDto> {
    return this.http.get<FormSummaryDto>(`${this.baseUrl}/form/by-name`, { params: this.withLeague({ name }) });
  }

  // Helper: resolve seasonId by leagueId + seasonName
  getSeasonId(leagueId: number, seasonName: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/season-id`, { params: { leagueId, seasonName } as any });
  }

  // Preferred: H2H forms by IDs
  getH2HFormByIds(homeId: number, awayId: number, seasonId: number, limit: number = 5): Observable<H2HFormTeamDto[]> {
    return this.http.get<H2HFormTeamDto[]>(`${this.baseUrl}/h2h/form`, { params: { homeId, awayId, seasonId, limit } as any });
  }

  // Name-based with autoSeason support when seasonId is unknown
  getH2HFormByNamesWithAutoSeason(home: string, away: string, leagueId: number, seasonName?: string, limit: number = 5): Observable<H2HFormTeamDto[]> {
    const params: any = { home, away, leagueId, autoSeason: 'true', limit };
    if (seasonName != null && seasonName !== '') params.seasonName = seasonName;
    return this.http.get<H2HFormTeamDto[]>(`${this.baseUrl}/h2h/form`, { params });
  }

  // PDF generation: posts analysis payload and gets a PDF Blob
  generateAnalysisPdf(payload: any) {
    const url = `${this.baseUrl}/generate-analysis-pdf`;
    return this.http.post(url, payload, { responseType: 'blob', observe: 'response' });
  }
}

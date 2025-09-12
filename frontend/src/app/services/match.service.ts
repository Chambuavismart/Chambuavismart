import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface TeamBreakdownDto { total: number; wins: number; draws: number; losses: number; btts: number; over25: number; }
export interface H2HSuggestion { teamA: string; teamB: string; }
export interface H2HMatchDto { year: number | null; date: string | null; homeTeam: string | null; awayTeam: string | null; result: string; season: string | null; }
export interface FormSummaryDto { recentResults: string[]; currentStreak: string; winRate: number; pointsEarned: number; ppgSeries?: number[]; }
export interface H2HFormTeamDto { teamId: string; teamName: string; last5: { streak: string; winRate: number; pointsPerGame: number; bttsPercent: number; over25Percent: number; }; matches: { year: number | null; date: string | null; homeTeam: string | null; awayTeam: string | null; result: string; }[] }

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/matches`;

  getTotalPlayedMatches(): Observable<number> {
    // Add a cache-busting param to avoid stale proxy/browser caches returning 0
    const ts = Date.now().toString();
    return this.http.get<number>(`${this.baseUrl}/played/total`, { params: { _ts: ts } });
  }

  getPlayedTotalByTeam(teamId: number): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/team/${teamId}/total`);
  }

  getPlayedTotalByTeamName(name: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/team/by-name/total`, { params: { name } });
  }

  getResultsBreakdownByTeamName(name: string): Observable<TeamBreakdownDto> {
    return this.http.get<TeamBreakdownDto>(`${this.baseUrl}/played/team/by-name/breakdown`, { params: { name } });
  }

  suggestH2H(query: string): Observable<H2HSuggestion[]> {
    return this.http.get<H2HSuggestion[]>(`${this.baseUrl}/h2h/suggest`, { params: { query } });
  }

  getH2HMatches(home: string, away: string): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches`, { params: { home, away } });
  }

  // New: Preferred ID-based H2H retrieval scoped to a season
  getH2HMatchesByIds(homeId: number, awayId: number, seasonId: number, limit: number = 10): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches`, { params: { homeId, awayId, seasonId, limit } as any });
  }

  // Total H2H count regardless of orientation
  getH2HCountAnyOrientation(teamA: string, teamB: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/h2h/count-any-orientation`, { params: { teamA, teamB } });
  }

  // All-orientations H2H match list (by names)
  getH2HMatchesAnyOrientation(teamA: string, teamB: string, limit: number = 200): Observable<H2HMatchDto[]> {
    return this.http.get<H2HMatchDto[]>(`${this.baseUrl}/h2h/matches-any-orientation`, { params: { teamA, teamB, limit } as any });
  }

  getFormByTeamName(name: string): Observable<FormSummaryDto> {
    return this.http.get<FormSummaryDto>(`${this.baseUrl}/form/by-name`, { params: { name } });
  }

  // Helper: resolve seasonId by leagueId + seasonName
  getSeasonId(leagueId: number, seasonName: string): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/season-id`, { params: { leagueId, seasonName } as any });
  }

  // Preferred: H2H forms by IDs
  getH2HFormByIds(homeId: number, awayId: number, seasonId: number, limit: number = 5): Observable<H2HFormTeamDto[]> {
    return this.http.get<H2HFormTeamDto[]>(`${this.baseUrl}/h2h/form`, { params: { homeId, awayId, seasonId, limit } as any });
  }
}

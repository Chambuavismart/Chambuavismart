import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface TeamBreakdownDto { total: number; wins: number; draws: number; losses: number; btts: number; over25: number; }
export interface H2HSuggestion { teamA: string; teamB: string; }
export interface H2HMatchDto { year: number | null; date: string | null; homeTeam: string | null; awayTeam: string | null; result: string; }
export interface FormSummaryDto { recentResults: string[]; currentStreak: string; winRate: number; pointsEarned: number; ppgSeries?: number[]; }

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/matches`;

  getTotalPlayedMatches(): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/total`);
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

  getFormByTeamName(name: string): Observable<FormSummaryDto> {
    return this.http.get<FormSummaryDto>(`${this.baseUrl}/form/by-name`, { params: { name } });
  }
}

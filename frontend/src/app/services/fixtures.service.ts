import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type FixtureStatus = 'UPCOMING' | 'LIVE' | 'FINISHED';

export interface FixtureDTO {
  id: number;
  round: string;
  dateTime: string; // ISO string
  homeTeam: string;
  awayTeam: string;
  homeScore: number | null;
  awayScore: number | null;
  status: FixtureStatus;
}

export interface LeagueWithUpcomingDTO {
  leagueId: number;
  leagueName: string;
  upcomingCount: number;
}

export interface LeagueFixturesResponse {
  leagueId: number;
  leagueName: string;
  fixtures: FixtureDTO[];
}

@Injectable({ providedIn: 'root' })
export class FixturesService {
  private http = inject(HttpClient);
  private baseUrl = '/api/fixtures';

  getLeagues(): Observable<LeagueWithUpcomingDTO[]> {
    return this.http.get<LeagueWithUpcomingDTO[]>(`${this.baseUrl}/leagues`);
  }

  getLeagueFixtures(leagueId: number, upcomingOnly = false): Observable<LeagueFixturesResponse> {
    const params = upcomingOnly ? '?upcomingOnly=true' : '';
    return this.http.get<LeagueFixturesResponse>(`${this.baseUrl}/${leagueId}${params}`);
  }

  // New: fixtures by date (grouped by league)
  getFixturesByDate(date: string, season?: string): Observable<LeagueFixturesResponse[]> {
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      throw new Error('getFixturesByDate: invalid date. Expected YYYY-MM-DD');
    }
    const formattedDate = date; // already YYYY-MM-DD; no timezone
    const params = new URLSearchParams({ date: formattedDate });
    if (season && season.trim()) params.set('season', season.trim());
    const url = `${this.baseUrl}/by-date?${params.toString()}`;
    // Temporary debug logging to verify alignment with backend
    // eslint-disable-next-line no-console
    console.debug('[FixturesService] GET', url);
    return this.http.get<LeagueFixturesResponse[]>(url);
  }

  // New: available dates for calendar dots
  getAvailableDates(year: number, month: number, season?: string): Observable<string[]> {
    if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
      throw new Error('getAvailableDates: invalid year/month');
    }
    const params = new URLSearchParams({ year: String(year), month: String(month) });
    if (season && season.trim()) params.set('season', season.trim());
    return this.http.get<string[]>(`${this.baseUrl}/available-dates?${params.toString()}`);
  }
}

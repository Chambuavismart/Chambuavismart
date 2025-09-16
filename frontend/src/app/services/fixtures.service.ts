import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

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
  leagueCountry: string;
  season: string;
  upcomingCount: number;
}

export interface LeagueFixturesResponse {
  leagueId: number;
  leagueName: string;
  leagueCountry?: string;
  fixtures: FixtureDTO[];
}

@Injectable({ providedIn: 'root' })
export class FixturesService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/fixtures`;

  getLeagues(): Observable<LeagueWithUpcomingDTO[]> {
    const url = `${this.baseUrl}/leagues?_ts=${Date.now()}`;
    return this.http.get<LeagueWithUpcomingDTO[]>(url);
  }

  getLeagueFixtures(leagueId: number, upcomingOnly = false): Observable<LeagueFixturesResponse> {
    const qp: string[] = [];
    if (upcomingOnly) qp.push('upcomingOnly=true');
    qp.push(`_ts=${Date.now()}`); // cache buster to ensure fresh data
    const qs = qp.length ? `?${qp.join('&')}` : '';
    return this.http.get<LeagueFixturesResponse>(`${this.baseUrl}/${leagueId}${qs}`);
  }

  // New: fixtures by date (grouped by league)
  getFixturesByDate(date: string, season?: string, refresh?: boolean): Observable<LeagueFixturesResponse[]> {
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      throw new Error('getFixturesByDate: invalid date. Expected YYYY-MM-DD');
    }
    const formattedDate = date; // already YYYY-MM-DD; no timezone
    const params = new URLSearchParams({ date: formattedDate, _ts: String(Date.now()) });
    if (season && season.trim()) params.set('season', season.trim());
    if (refresh) params.set('refresh', 'true');
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
    params.set('_ts', String(Date.now()));
    return this.http.get<string[]>(`${this.baseUrl}/available-dates?${params.toString()}`);
  }
}

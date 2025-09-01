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
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface AdminSeasonItem { id: number; name: string; startDate?: string | null; endDate?: string | null; }
export interface AdminLeagueSummaryDto {
  leagueId: number;
  name: string;
  category?: string | null;
  country: string;
  seasons: AdminSeasonItem[];
  currentSeasonId?: number | null;
  currentSeasonName?: string | null;
  lastUpdatedAt?: string | null;
}

export interface DeleteLeagueResult {
  matchesDeleted: number;
  fixturesDeleted: number;
  seasonsDeleted: number;
  leagueDeleted: boolean;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/admin/leagues`;

  getLeaguesSummary(): Observable<AdminLeagueSummaryDto[]> {
    return this.http.get<AdminLeagueSummaryDto[]>(`${this.baseUrl}/summary`);
  }

  deleteLeague(leagueId: number): Observable<DeleteLeagueResult> {
    return this.http.delete<DeleteLeagueResult>(`${this.baseUrl}/${leagueId}`);
  }
}

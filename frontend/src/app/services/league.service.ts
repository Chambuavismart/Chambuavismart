import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

// Keep existing LeagueDto for compatibility with MatchUploadComponent
export interface LeagueDto {
  id: number;
  name: string;
  country: string;
  season: string;
}

// Provide the expected alias 'League' used by other components
export type League = LeagueDto;

// Backend DTO for league table rows
export interface LeagueTableEntryDTO {
  position: number;
  teamId: number;
  teamName: string;
  mp: number;
  w: number;
  d: number;
  l: number;
  gf: number;
  ga: number;
  gd: number;
  pts: number;
}

// Backend DTO for form guide rows
export interface FormGuideRowDTO {
  teamId: number;
  teamName: string;
  mp: number; // number of matches considered (limited window)
  totalMp?: number; // total matches so far in league (per scope)
  w: number;
  d: number;
  l: number;
  gf: number;
  ga: number;
  gd: number;
  pts: number;
  ppg: number;
  lastResults: string[];
  bttsPct: number;
  over15Pct: number;
  over25Pct: number;
  over35Pct: number;
}

@Injectable({ providedIn: 'root' })
export class LeagueService {
  private http = inject(HttpClient);

  // Existing endpoint used elsewhere
  getLeagues(): Observable<LeagueDto[]> {
    return this.http.get<LeagueDto[]>(`/api/league`);
  }

  // New lightweight list for incremental dropdown
  getAllLeagues(): Observable<Pick<LeagueDto, 'id' | 'name'>[]> {
    return this.http.get<LeagueDto[]>(`/api/leagues/list`);
  }

  // Details for autofill in incremental mode
  getLeagueDetails(leagueId: string | number): Observable<LeagueDto> {
    return this.http.get<LeagueDto>(`/api/leagues/${leagueId}`);
  }

  // Fetch league table for a given league (optional season param)
  getLeagueTable(leagueId: number, season?: string): Observable<LeagueTableEntryDTO[]> {
    let params = new HttpParams();
    if (season) params = params.set('season', season);
    return this.http.get<LeagueTableEntryDTO[]>(`/api/league/${leagueId}/table`, { params });
  }

  // Fetch form guide rows
  getFormGuide(leagueId: number, limit: number | 'all', scope: 'overall' | 'home' | 'away'): Observable<FormGuideRowDTO[]> {
    let params = new HttpParams()
      .set('limit', String(limit))
      .set('scope', scope);
    return this.http.get<FormGuideRowDTO[]>(`/api/form-guide/${leagueId}`, { params });
  }
}

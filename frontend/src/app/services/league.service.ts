import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

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
// Note: ppg and the percentage fields below are now recency-weighted values provided by backend
export interface FormGuideRowDTO {
  teamId: number;
  teamName: string;
  mp: number; // number of matches considered (limited window)
  totalMp?: number; // total matches so far in league (per scope)
  w: number; // raw counts (unweighted)
  d: number; // raw counts (unweighted)
  l: number; // raw counts (unweighted)
  gf: number;
  ga: number;
  gd: number;
  pts: number; // raw points (unweighted)
  ppg: number; // recency-weighted PPG
  lastResults: string[];
  bttsPct: number; // recency-weighted BTTS%
  over15Pct: number; // recency-weighted Over 1.5%
  over25Pct: number; // recency-weighted Over 2.5%
  over35Pct: number; // recency-weighted Over 3.5%
  // Optional: recency-weighted home/away splits
  weightedHomePPG?: number;
  weightedAwayPPG?: number;
  weightedHomeBTTSPercent?: number;
  weightedAwayBTTSPercent?: number;
  weightedHomeOver15Percent?: number;
  weightedAwayOver15Percent?: number;
  weightedHomeOver25Percent?: number;
  weightedAwayOver25Percent?: number;
  weightedHomeOver35Percent?: number;
  weightedAwayOver35Percent?: number;
  // Optional: matches considered for splits (for fallback logic)
  weightedHomeMatches?: number;
  weightedAwayMatches?: number;
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

  // Fetch league table for a given league and season (season required)
  getLeagueTable(leagueId: number, seasonId: number): Observable<LeagueTableEntryDTO[]> {
    const params = new HttpParams().set('seasonId', String(seasonId));
    const url = `/api/league/${leagueId}/table`;
    console.debug('[LeagueTable][HTTP] GET', url, params.toString());
    return this.http.get<LeagueTableEntryDTO[]>(url, { params }).pipe(
      tap(_ => console.debug('[LeagueTable][HTTP] Response OK for', url))
    );
  }

  // Fetch form guide rows (season-specific)
  getFormGuide(leagueId: number, seasonId: number, limit: number | 'all', scope: 'overall' | 'home' | 'away'): Observable<FormGuideRowDTO[]> {
    let params = new HttpParams()
      .set('limit', String(limit))
      .set('scope', scope);
    if (seasonId != null) {
      params = params.set('seasonId', String(seasonId));
    }
    const url = `/api/form-guide/${leagueId}`;
    console.debug('[FormGuide][HTTP] GET', url, params.toString());
    return this.http.get<FormGuideRowDTO[]>(url, { params }).pipe(
      tap(_ => console.debug('[FormGuide][HTTP] Response OK for', url))
    );
  }
}

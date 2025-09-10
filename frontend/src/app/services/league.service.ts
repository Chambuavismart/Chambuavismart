import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { getApiBase } from './api-base';
import { Observable } from 'rxjs';

// Core League entity (matches backend model)
export interface League {
  id: number;
  name: string;
  country: string;
  season: string;
}
// Backward-compat alias used by MatchUploadComponent
export type LeagueDto = League;

// DTOs expected by Form Guide and League Table pages
export interface FormGuideRowDTO {
  teamId: number;
  teamName: string;
  mp?: number;         // matches in window
  totalMp?: number;    // total matches so far (per scope)
  w: number;
  d: number;
  l: number;
  gf: number;
  ga: number;
  gd: number;
  pts: number;
  ppg: number;         // may be weighted average
  lastResults?: string[];
  lastResultsDetails?: string[];
  bttsPct?: number;
  over15Pct?: number;
  over25Pct?: number;
  over35Pct?: number;
  // Weighted home/away splits (optional to allow partial data)
  weightedHomeGoalsFor?: number;
  weightedHomeGoalsAgainst?: number;
  weightedAwayGoalsFor?: number;
  weightedAwayGoalsAgainst?: number;
  weightedHomePPG?: number;
  weightedAwayPPG?: number;
  weightedHomeBTTSPercent?: number;
  weightedAwayBTTSPercent?: number;
  weightedHomeOver25Percent?: number;
  weightedAwayOver25Percent?: number;
  weightedHomeOver15Percent?: number;
  weightedAwayOver15Percent?: number;
  weightedHomeOver35Percent?: number;
  weightedAwayOver35Percent?: number;
  weightedHomeMatches?: number;
  weightedAwayMatches?: number;
}

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

@Injectable({ providedIn: 'root' })
export class LeagueService {
  private http = inject(HttpClient);

  // New generic: all leagues
  getAll(): Observable<League[]> {
    const base = getApiBase();
    return this.http.get<League[]>(`${base}/leagues`);
  }

  // Back-compat aliases used by various pages
  getLeagues(): Observable<League[]> { return this.getAll(); }
  getAllLeagues(): Observable<League[]> { return this.getAll(); }

  // League detail by id
  getLeagueDetails(id: number | string): Observable<League> {
    const base = getApiBase();
    return this.http.get<League>(`${base}/leagues/${id}`);
    // Note: backend also exposes GET /api/league, but details endpoint is under /api/leagues/{id}
  }

  // Form Guide API
  getFormGuide(
    leagueId: number,
    seasonId: number,
    limit: number | 'all',
    scope: 'overall' | 'home' | 'away'
  ): Observable<FormGuideRowDTO[]> {
    const params = new URLSearchParams();
    params.set('seasonId', String(seasonId));
    params.set('limit', String(limit));
    params.set('scope', scope);
    const base = getApiBase();
    const url = `${base}/form-guide/${leagueId}?${params.toString()}`;
    return this.http.get<FormGuideRowDTO[]>(url);
  }

  // League Table API (season-scoped)
  getLeagueTable(leagueId: number, seasonId: number): Observable<LeagueTableEntryDTO[]> {
    const base = getApiBase();
    const url = `${base}/league/${leagueId}/table?seasonId=${seasonId}`;
    return this.http.get<LeagueTableEntryDTO[]>(url);
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LeagueTableEntryDTO {
  position: number;
  teamId: number;
  teamName: string;
  mp: number; w: number; d: number; l: number;
  gf: number; ga: number; gd: number; pts: number;
}

export interface League {
  id: number;
  name: string;
  country: string;
  season: string;
}

@Injectable({ providedIn: 'root' })
export class LeagueService {
  private baseUrl = '/api/league';
  constructor(private http: HttpClient) {}

  getLeagues(): Observable<League[]> {
    return this.http.get<League[]>(`${this.baseUrl}`);
  }

  getLeagueTable(leagueId: number): Observable<LeagueTableEntryDTO[]> {
    return this.http.get<LeagueTableEntryDTO[]>(`${this.baseUrl}/${leagueId}/table`);
  }
}

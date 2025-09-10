import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface Season {
  id: number;
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  metadata?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SeasonService {
  private http = inject(HttpClient);

  listSeasons(leagueId: number): Observable<Season[]> {
    const base = getApiBase();
    return this.http.get<Season[]>(`${base}/leagues/${leagueId}/seasons`);
  }
}

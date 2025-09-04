import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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
    return this.http.get<Season[]>(`/api/leagues/${leagueId}/seasons`);
  }
}

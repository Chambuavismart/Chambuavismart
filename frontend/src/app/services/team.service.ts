import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface TeamSuggestion { id: number; name: string; country?: string; }

@Injectable({ providedIn: 'root' })
export class TeamService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/teams`;

  searchTeams(query: string): Observable<TeamSuggestion[]> {
    return this.http.get<TeamSuggestion[]>(`${this.baseUrl}/search`, { params: { query } });
  }
}

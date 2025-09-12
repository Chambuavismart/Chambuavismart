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
    return this.http.get<TeamSuggestion[]>(`${this.baseUrl}/search`, { params: { query } as any });
  }

  // League-scoped search variant (optional leagueId)
  searchTeamsScoped(query: string, leagueId?: number): Observable<TeamSuggestion[]> {
    if (typeof query !== 'string' || !query || query.trim().length < 2) {
      console.error('[TeamService] Invalid search query:', query);
      return new Observable<TeamSuggestion[]>((sub) => { sub.next([]); sub.complete(); });
    }
    const q = query.trim();
    const url = typeof leagueId === 'number'
      ? `${this.baseUrl}/search?query=${encodeURIComponent(q)}&leagueId=${leagueId}`
      : `${this.baseUrl}/search?query=${encodeURIComponent(q)}`;
    console.log('[TeamService] GET', url);
    return this.http.get<TeamSuggestion[]>(url);
  }

  // Resolve a single team by exact name or alias (server should handle alias resolution)
  findByName(name: string, leagueId?: number): Observable<TeamSuggestion | null> {
    if (typeof name !== 'string' || !name || name.trim().length < 2) {
      console.error('[TeamService] Invalid team name for by-name:', name);
      return new Observable<TeamSuggestion | null>((sub) => { sub.next(null); sub.complete(); });
    }
    const n = name.trim();
    const url = typeof leagueId === 'number'
      ? `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}&leagueId=${leagueId}`
      : `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}`;
    console.log('[TeamService] GET', url);
    return this.http.get<TeamSuggestion | null>(url);
  }

  // Convenience: get a teamId scoped to a league, with fallback to search
  getScopedTeamId(teamName: string, leagueId: number): Observable<number | null> {
    return new Observable<number | null>((subscriber) => {
      console.debug('[TeamService] getScopedTeamId start', { teamName, leagueId });
      if (typeof teamName !== 'string' || !teamName || teamName.trim().length < 2) {
        console.error('[TeamService] Invalid team name for lookup:', teamName);
        subscriber.next(null); subscriber.complete();
        return;
      }
      const tn = teamName.trim();
      this.findByName(tn, leagueId).subscribe({
        next: (t) => {
          if (t?.id) {
            console.debug('[TeamService] by-name resolved', t);
            subscriber.next(t.id); subscriber.complete();
          }
          else {
            console.debug('[TeamService] by-name empty; falling back to search');
            this.searchTeamsScoped(tn, leagueId).subscribe({
              next: (list) => {
                console.debug('[TeamService] search result', list);
                subscriber.next(list && list.length ? list[0].id : null); subscriber.complete();
              },
              error: (e) => { console.warn('[TeamService] search error', e); subscriber.next(null); subscriber.complete(); }
            });
          }
        },
        error: (e) => {
          console.warn('[TeamService] by-name error; falling back to search', e);
          this.searchTeamsScoped(tn, leagueId).subscribe({
            next: (list) => { console.debug('[TeamService] search result (fallback)', list); subscriber.next(list && list.length ? list[0].id : null); subscriber.complete(); },
            error: (err) => { console.warn('[TeamService] search error (fallback)', err); subscriber.next(null); subscriber.complete(); }
          });
        }
      });
    });
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';
import { LeagueContextService } from './league-context.service';

export interface TeamSuggestion { id: number; name: string; country?: string; leagueId?: number; }

export interface TeamByNameResult {
  team?: TeamSuggestion | null;
  candidates?: TeamSuggestion[];
  conflict?: boolean;
  notFound?: boolean;
  status?: number;
}

export interface LastMatchBrief {
  date: string; // ISO date (YYYY-MM-DD)
  season?: string | null;
  opponent: string;
  result: 'W' | 'D' | 'L';
  scoreLine: string; // e.g. 2-1
}

export interface OutcomeStreakDTO {
  outcome: 'W' | 'D' | 'L';
  length: number;
  startDate: string; // ISO date
  endDate: string;   // ISO date
}

export interface TopOutcomeStreaksResponse {
  teamName: string;
  consideredMatches: number;
  topStreaks: OutcomeStreakDTO[];
}

@Injectable({ providedIn: 'root' })
export class TeamService {
  private http = inject(HttpClient);
  private leagueContext = inject(LeagueContextService);
  private baseUrl = `${getApiBase()}/teams`;

  searchTeams(query: string): Observable<TeamSuggestion[]> {
    const leagueId = this.leagueContext.getCurrentLeagueId();
    const params: any = { query } as any;
    if (leagueId) params.leagueId = String(leagueId);
    return this.http.get<TeamSuggestion[]>(`${this.baseUrl}/search`, { params });
  }

  // League-scoped search variant (optional leagueId)
  searchTeamsScoped(query: string, leagueId?: number): Observable<TeamSuggestion[]> {
    if (typeof query !== 'string' || !query || query.trim().length < 2) {
      console.error('[TeamService] Invalid search query:', query);
      return new Observable<TeamSuggestion[]>((sub) => { sub.next([]); sub.complete(); });
    }
    const q = query.trim();
    const lid = typeof leagueId === 'number' ? leagueId : (this.leagueContext.getCurrentLeagueId() ?? undefined);
    const url = typeof lid === 'number'
      ? `${this.baseUrl}/search?query=${encodeURIComponent(q)}&leagueId=${lid}`
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
    const lid = typeof leagueId === 'number' ? leagueId : (this.leagueContext.getCurrentLeagueId() ?? undefined);
    const url = typeof lid === 'number'
      ? `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}&leagueId=${lid}`
      : `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}`;
    console.log('[TeamService] GET', url);
    return this.http.get<TeamSuggestion | null>(url);
  }

  // New: by-name with disambiguation handling (captures 409 Conflict candidates)
  findByNameWithDisambiguation(name: string, leagueId?: number): Observable<TeamByNameResult> {
    return new Observable<TeamByNameResult>((subscriber) => {
      if (typeof name !== 'string' || !name || name.trim().length < 2) {
        console.error('[TeamService] Invalid team name for by-name(disambiguation):', name);
        subscriber.next({ team: null, notFound: true, status: 400 });
        subscriber.complete();
        return;
      }
      const n = name.trim();
      const lid = typeof leagueId === 'number' ? leagueId : (this.leagueContext.getCurrentLeagueId() ?? undefined);
      const url = typeof lid === 'number'
        ? `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}&leagueId=${lid}`
        : `${this.baseUrl}/by-name?name=${encodeURIComponent(n)}`;
      console.log('[TeamService] GET', url);
      this.http.get<TeamSuggestion>(url, { observe: 'response' as const }).subscribe({
        next: (resp) => {
          subscriber.next({ team: resp.body ?? null, status: resp.status });
          subscriber.complete();
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409 && err.error && err.error.candidates) {
            const candidates: TeamSuggestion[] = (err.error.candidates || []) as TeamSuggestion[];
            subscriber.next({ candidates, conflict: true, status: 409 });
            subscriber.complete();
          } else if (err.status === 404) {
            subscriber.next({ team: null, notFound: true, status: 404 });
            subscriber.complete();
          } else {
            console.warn('[TeamService] by-name unexpected error', err);
            subscriber.next({ team: null, status: err.status });
            subscriber.complete();
          }
        }
      });
    });
  }

  getPriorOutcomes(teamName: string, leagueId?: number): Observable<any> {
    const tn = (teamName || '').trim();
    if (!tn) {
      return new Observable<any>((sub) => { sub.next(null); sub.complete(); });
    }
    const lid = typeof leagueId === 'number' ? leagueId : (this.leagueContext.getCurrentLeagueId() ?? undefined);
    const url = typeof lid === 'number'
      ? `${this.baseUrl}/${encodeURIComponent(tn)}/prior-outcomes?leagueId=${lid}`
      : `${this.baseUrl}/${encodeURIComponent(tn)}/prior-outcomes`;
    return this.http.get<any>(url);
  }

  // Last played match summary by team name
  getLastPlayedByName(teamName: string): Observable<{ team: string; priorResult: string; priorScoreLine: string; opponent: string; date: string } | null> {
    const tn = (teamName || '').trim();
    if (!tn) {
      return new Observable(sub => { sub.next(null); sub.complete(); });
    }
    const url = `${this.baseUrl}/${encodeURIComponent(tn)}/last-played`;
    return this.http.get<any>(url);
  }

  // New: last-N played matches (most recent first)
  getLastPlayedListByName(teamName: string, limit = 2): Observable<LastMatchBrief[]> {
    const tn = (teamName || '').trim();
    if (!tn) {
      return new Observable<LastMatchBrief[]>(sub => { sub.next([]); sub.complete(); });
    }
    const url = `${this.baseUrl}/${encodeURIComponent(tn)}/last-played-list?limit=${Math.max(1, Math.min(limit, 20))}`;
    return this.http.get<LastMatchBrief[]>(url);
  }

  // Top-2 longest W/D/L streaks over most recent 40 played matches for a team name
  getLast40TopStreaksByName(teamName: string): Observable<TopOutcomeStreaksResponse | null> {
    const tn = (teamName || '').trim();
    if (!tn) {
      return new Observable<TopOutcomeStreaksResponse | null>(sub => { sub.next(null); sub.complete(); });
    }
    const url = `${this.baseUrl}/${encodeURIComponent(tn)}/last40-top-streaks`;
    return this.http.get<TopOutcomeStreaksResponse>(url);
  }

  // Convenience: get a teamId scoped to a league, with fallback to search
  getScopedTeamId(teamName: string, leagueId?: number): Observable<number | null> {
    return new Observable<number | null>((subscriber) => {
      const activeLeagueId = typeof leagueId === 'number' ? leagueId : (this.leagueContext.getCurrentLeagueId() ?? undefined);
      console.debug('[TeamService] getScopedTeamId start', { teamName, leagueId: activeLeagueId });
      if (typeof teamName !== 'string' || !teamName || teamName.trim().length < 2) {
        console.error('[TeamService] Invalid team name for lookup:', teamName);
        subscriber.next(null); subscriber.complete();
        return;
      }
      const tn = teamName.trim();
      this.findByName(tn, activeLeagueId as number).subscribe({
        next: (t) => {
          if (t?.id) {
            console.debug('[TeamService] by-name resolved', t);
            subscriber.next(t.id); subscriber.complete();
          }
          else {
            console.debug('[TeamService] by-name empty; falling back to search');
            this.searchTeamsScoped(tn, activeLeagueId as number).subscribe({
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
          this.searchTeamsScoped(tn, activeLeagueId as number).subscribe({
            next: (list) => { console.debug('[TeamService] search result (fallback)', list); subscriber.next(list && list.length ? list[0].id : null); subscriber.complete(); },
            error: (err) => { console.warn('[TeamService] search error (fallback)', err); subscriber.next(null); subscriber.complete(); }
          });
        }
      });
    });
  }
}

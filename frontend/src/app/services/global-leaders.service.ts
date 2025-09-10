import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, shareReplay } from 'rxjs/operators';

export interface GlobalLeader {
  teamId: number;
  teamName: string;
  teamSlug?: string;
  teamLogoUrl?: string;
  statPct: number;
  matchesPlayed: number;
  statCount: number;
  category: string;
  rank: number;
}

@Injectable({ providedIn: 'root' })
export class GlobalLeadersService {
  private cache = new Map<string, Observable<GlobalLeader[]>>();

  constructor(private http: HttpClient) {}

  getLeaders(category: string, limit = 5, minMatches = 3, scope: 'overall'|'home'|'away' = 'overall', lastN: number = 0): Observable<GlobalLeader[]> {
    const key = `${category}:${limit}:${minMatches}:${scope}:${lastN}`;
    if (this.cache.has(key)) return this.cache.get(key)!;

    const obs = this.http
      .get<GlobalLeader[]>(`/api/global-leaders`, { params: { category, limit: String(limit), minMatches: String(minMatches), scope, lastN: String(lastN) } })
      .pipe(
        shareReplay(1),
        catchError(err => {
          console.error('GlobalLeaders fetch failed', err);
          return of([]);
        })
      );

    this.cache.set(key, obs);
    return obs;
  }

  clearCache(category?: string) {
    if (!category) {
      this.cache.clear();
    } else {
      [...this.cache.keys()].forEach(k => { if (k.startsWith(category + ':')) this.cache.delete(k); });
    }
  }
}

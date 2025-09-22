import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LeagueContextService {
  private readonly STORAGE_KEY = 'activeLeagueId';
  private currentLeagueId: number | null = null;
  private leagueIdSubject = new BehaviorSubject<number | null>(null);

  // Observable for reactive components
  leagueId$ = this.leagueIdSubject.asObservable();

  constructor() {
    const saved = typeof localStorage !== 'undefined' ? localStorage.getItem(this.STORAGE_KEY) : null;
    if (saved) {
      const id = Number(saved);
      if (!Number.isNaN(id) && id > 0) {
        this.currentLeagueId = id;
        this.leagueIdSubject.next(id);
        console.log('[LeagueContext] Initialized with saved leagueId:', id);
      }
    }
  }

  setCurrentLeagueId(id: number | null): void {
    if (id === null || id === undefined || Number.isNaN(Number(id)) || Number(id) <= 0) {
      console.warn('[LeagueContext] Ignoring invalid leagueId:', id);
      return;
    }
    const n = Number(id);
    this.currentLeagueId = n;
    this.leagueIdSubject.next(n);
    try {
      if (typeof localStorage !== 'undefined') localStorage.setItem(this.STORAGE_KEY, String(n));
    } catch {}
    console.log('[LeagueContext] Active leagueId:', n);
  }

  getCurrentLeagueId(): number | null {
    return this.currentLeagueId;
  }
}

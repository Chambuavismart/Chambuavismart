import { Injectable } from '@angular/core';

export type TeamColor = string; // CSS color, e.g. rgba(...) or hex

export interface TeamColorEntry {
  teamKey: string; // normalized team key
  color: TeamColor;
  leagueId?: number | null;
  updatedAt: number; // epoch ms
  // New: mark when a team has the special double-green status (Green color + H2H wins > 50%)
  doubleGreen?: boolean;
  // New: mark when fixture has draw-heavy H2H (>=40% draws) combined with Orange pill on either side
  drawHeavyD?: boolean;
}

// Lightweight localStorage-backed cache for team highlight colors decided in Fixtures Analysis tab
@Injectable({ providedIn: 'root' })
export class AnalysisColorCacheService {
  private readonly LS_KEY = 'fixturesAnalysis.teamColors.v1';
  private readonly LS_VERSION_KEY = 'fixturesAnalysis.teamColors.version';
  private map = new Map<string, TeamColorEntry>();

  constructor() {
    this.load();
  }

  private normTeam(team: string | null | undefined): string {
    return (team || '').trim().toLowerCase();
  }
  private key(team: string, leagueId?: number | null): string {
    const t = this.normTeam(team);
    const l = (leagueId ?? undefined) !== undefined ? String(leagueId) : '';
    return l ? `${t}#${l}` : t;
  }

  setTeamColor(team: string, color: TeamColor, leagueId?: number | null): void {
    const k = this.key(team, leagueId);
    const prev = this.map.get(k);
    const entry: TeamColorEntry = { teamKey: k, color, leagueId, updatedAt: Date.now(), doubleGreen: prev?.doubleGreen };
    this.map.set(k, entry);
    this.save();
  }

  getTeamColor(team: string, leagueId?: number | null): TeamColor | null {
    // Prefer league-specific, then global
    const k1 = this.key(team, leagueId);
    const k2 = this.key(team, undefined);
    const e = this.map.get(k1) || this.map.get(k2);
    return e?.color || null;
  }

  removeTeamColor(team: string, leagueId?: number | null): void {
    const k = this.key(team, leagueId);
    const g = this.key(team, undefined);
    let changed = false;
    if (this.map.has(k)) { this.map.delete(k); changed = true; }
    if (this.map.has(g)) { this.map.delete(g); changed = true; }
    if (changed) this.save();
  }

  // New: set or clear the doubleGreen flag
  setDoubleGreen(team: string, value: boolean, leagueId?: number | null): void {
    const k = this.key(team, leagueId);
    const e = this.map.get(k) || { teamKey: k, color: '' as TeamColor, leagueId, updatedAt: Date.now() } as TeamColorEntry;
    e.doubleGreen = value || undefined;
    e.updatedAt = Date.now();
    this.map.set(k, e);
    this.save();
  }

  // New: query the doubleGreen flag
  isDoubleGreen(team: string, leagueId?: number | null): boolean {
    const k1 = this.key(team, leagueId);
    const k2 = this.key(team, undefined);
    const e = this.map.get(k1) || this.map.get(k2);
    return !!(e && e.doubleGreen);
  }

  // New: set or clear the draw-heavy D flag
  setDrawHeavyD(team: string, value: boolean, leagueId?: number | null): void {
    const k = this.key(team, leagueId);
    const e = this.map.get(k) || { teamKey: k, color: '' as TeamColor, leagueId, updatedAt: Date.now() } as TeamColorEntry;
    e.drawHeavyD = value || undefined;
    e.updatedAt = Date.now();
    this.map.set(k, e);
    this.save();
  }

  // New: query the draw-heavy D flag
  hasDrawHeavyD(team: string, leagueId?: number | null): boolean {
    const k1 = this.key(team, leagueId);
    const k2 = this.key(team, undefined);
    const e = this.map.get(k1) || this.map.get(k2);
    return !!(e && e.drawHeavyD);
  }

  // Optional: clear old entries > N days
  purgeOlderThan(days: number = 30): void {
    const cutoff = Date.now() - days * 86400000;
    let changed = false;
    for (const [k, v] of this.map) {
      if ((v.updatedAt || 0) < cutoff) { this.map.delete(k); changed = true; }
    }
    if (changed) this.save();
  }

  private load(): void {
    try {
      const raw = localStorage.getItem(this.LS_KEY);
      if (!raw) return;
      const arr = JSON.parse(raw) as TeamColorEntry[];
      if (Array.isArray(arr)) {
        this.map.clear();
        for (const e of arr) {
          if (!e || !e.teamKey || !e.color) continue;
          this.map.set(e.teamKey, e);
        }
      }
    } catch {}
  }
  private save(): void {
    try {
      const arr = Array.from(this.map.values());
      localStorage.setItem(this.LS_KEY, JSON.stringify(arr));
      // Bump a version key so other documents (and polling fallbacks) can detect changes
      try { localStorage.setItem(this.LS_VERSION_KEY, String(Date.now())); } catch {}
    } catch {}
  }
}

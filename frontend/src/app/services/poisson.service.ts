import { Injectable } from '@angular/core';

export interface PoissonTeamInfo {
  name: string;
  matchesInvolved: number; // total matches across dataset
}

export interface H2HGoalLike {
  homeTeam: string;
  awayTeam: string;
  // any of these names can be present in the dataset; we will try to map gracefully
  homeGoals?: number | null;
  awayGoals?: number | null;
  home_score?: number | null;
  away_score?: number | null;
  result?: string | null; // optional "x-y" string; we can parse as fallback
}

export interface Predictions {
  teamAWin: number;
  teamBWin: number;
  draw: number;
  btts: number;
  over15: number;
  over25: number;
  lambdaA: number;
  lambdaB: number;
}

@Injectable({ providedIn: 'root' })
export class PoissonService {
  private factorial(n: number): number {
    if (n <= 1) return 1;
    // iterative to avoid recursion overhead on larger k
    let f = 1;
    for (let i = 2; i <= n; i++) f *= i;
    return f;
  }

  private poisson(lambda: number, k: number): number {
    return (Math.exp(-lambda) * Math.pow(lambda, k)) / this.factorial(k);
  }

  private parseGoals(m: H2HGoalLike, isHome: boolean): number {
    const g1 = isHome ? m.homeGoals : m.awayGoals;
    const g2 = isHome ? m.home_score : m.away_score;
    if (g1 !== undefined && g1 !== null) return g1;
    if (g2 !== undefined && g2 !== null) return g2;
    // fallback parse from result like "3-1"
    if (m.result && /\d+\s*-\s*\d+/.test(m.result)) {
      const [h, a] = m.result.split('-').map(x => parseInt(x.trim(), 10));
      return isHome ? (isFinite(h) ? h : 0) : (isFinite(a) ? a : 0);
    }
    return 0;
  }

  private averageGoalsForTeam(team: string, h2h: H2HGoalLike[]): { for: number; against: number } {
    if (!h2h || h2h.length === 0) return { for: 0, against: 0 };
    let gf = 0, ga = 0, n = 0;
    for (const m of h2h) {
      const home = m.homeTeam === team;
      const away = m.awayTeam === team;
      if (!(home || away)) continue;
      const gFor = this.parseGoals(m, home);
      const gAgainst = this.parseGoals(m, !home);
      gf += gFor;
      ga += gAgainst;
      n++;
    }
    if (n === 0) return { for: 0, against: 0 };
    return { for: gf / n, against: ga / n };
  }

  calculatePredictions(
    teamA: PoissonTeamInfo | null | undefined,
    teamB: PoissonTeamInfo | null | undefined,
    h2hData: H2HGoalLike[] | null | undefined,
    allMatchesData?: unknown // reserved for future extensions
  ): Predictions {
    const leagueAvg = 1.4; // per team

    const tA: PoissonTeamInfo = {
      name: teamA?.name || 'Team A',
      matchesInvolved: Math.max(1, (teamA?.matchesInvolved ?? 0))
    };
    const tB: PoissonTeamInfo = {
      name: teamB?.name || 'Team B',
      matchesInvolved: Math.max(1, (teamB?.matchesInvolved ?? 0))
    };

    const h2h = Array.isArray(h2hData) ? h2hData : [];

    // H2H averages for each team
    const aAvg = this.averageGoalsForTeam(tA.name, h2h).for || leagueAvg;
    const bAvg = this.averageGoalsForTeam(tB.name, h2h).for || leagueAvg;

    // Expected goals lambdas (weighted toward each team's dataset size)
    const denom = (tA.matchesInvolved + tB.matchesInvolved) || 1;
    const lambdaA = ((aAvg * tA.matchesInvolved) + (leagueAvg * tB.matchesInvolved)) / denom;
    const lambdaB = ((bAvg * tB.matchesInvolved) + (leagueAvg * tA.matchesInvolved)) / denom;

    // Scorelines 0..5
    let aWin = 0, bWin = 0, draw = 0;
    let p00 = 0, p10 = 0, p01 = 0, p11 = 0;

    const cacheA: number[] = [];
    const cacheB: number[] = [];
    for (let i = 0; i <= 5; i++) cacheA[i] = this.poisson(lambdaA, i);
    for (let j = 0; j <= 5; j++) cacheB[j] = this.poisson(lambdaB, j);

    for (let a = 0; a <= 5; a++) {
      for (let b = 0; b <= 5; b++) {
        const p = cacheA[a] * cacheB[b];
        if (a > b) aWin += p;
        else if (a < b) bWin += p; else draw += p;
        if (a === 0 && b === 0) p00 = p;
        if (a === 1 && b === 0) p10 = p;
        if (a === 0 && b === 1) p01 = p;
        if (a === 1 && b === 1) p11 = p;
      }
    }

    const btts = (1 - Math.exp(-lambdaA)) * (1 - Math.exp(-lambdaB));
    const over15 = 1 - (p00 + p10 + p01);
    const over25 = 1 - (p00 + p10 + p01 + p11);

    return {
      teamAWin: Math.round(aWin * 100),
      teamBWin: Math.round(bWin * 100),
      draw: Math.round(draw * 100),
      btts: Math.round(btts * 100),
      over15: Math.round(over15 * 100),
      over25: Math.round(over25 * 100),
      lambdaA: Math.round(lambdaA * 100) / 100,
      lambdaB: Math.round(lambdaB * 100) / 100,
    };
  }
}

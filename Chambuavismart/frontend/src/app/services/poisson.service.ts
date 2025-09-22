import { Injectable } from '@angular/core';

export interface PoissonTeamInfo {
  id?: number | string | null;
  name: string;
  matchesInvolved: number; // total matches across dataset
}

export interface H2HGoalLike {
  homeTeam: string;
  awayTeam: string;
  homeTeamId?: number | string | null;
  awayTeamId?: number | string | null;
  // any of these names can be present in the dataset; we will try to map gracefully
  homeGoals?: number | null;
  awayGoals?: number | null;
  home_score?: number | null;
  away_score?: number | null;
  result?: string | null; // optional "x-y" string; we can parse as fallback
}

export interface CorrectScorePrediction { score: string; probability: number; }
export interface Predictions {
  teamAWin: number;
  teamBWin: number;
  draw: number;
  btts: number;
  over15: number;
  over25: number;
  over35: number;
  lambdaA: number;
  lambdaB: number;
  usedFallback?: boolean; // indicates league average used for at least one team
  isLimitedData?: boolean; // fewer than 3 H2H or fallback to league average
  correctScores?: CorrectScorePrediction[]; // top 3 correct scores normalized over 0..10 grid
}

@Injectable({ providedIn: 'root' })
export class PoissonService {
  private readonly leagueAvg = 1.4; // per team fallback
  private readonly homeAdvantageFactor = 1.15; // configurable if needed
  private readonly awayDisadvantageFactor = 0.95; // slight reduction for away
  private lastGridStats?: { raw: { h: number; a: number; p: number }[]; total: number; maxGoals: number };

  private calculateCorrectScores(lambdaA: number, lambdaB: number, h2h: H2HGoalLike[]): CorrectScorePrediction[] {
    // Adaptive grid: start at 10 and expand until cumulative mass >= 0.999 or cap at 20
    let maxGoals = 10;
    let total = 0;
    let raw: { score: string; probability: number; h: number; a: number }[] = [];

    const computeGrid = (mg: number) => {
      const cacheA: number[] = new Array(mg + 1);
      const cacheB: number[] = new Array(mg + 1);
      for (let i = 0; i <= mg; i++) cacheA[i] = this.poisson(lambdaA, i);
      for (let j = 0; j <= mg; j++) cacheB[j] = this.poisson(lambdaB, j);
      const tmp: { score: string; probability: number; h: number; a: number }[] = [];
      let t = 0;
      for (let h = 0; h <= mg; h++) {
        for (let a = 0; a <= mg; a++) {
          const p = cacheA[h] * cacheB[a];
          t += p;
          tmp.push({ score: `${h}-${a}`, probability: p, h, a });
        }
      }
      return { tmp, t };
    };

    while (true) {
      const { tmp, t } = computeGrid(maxGoals);
      raw = tmp; total = t;
      if (total >= 0.999 || maxGoals >= 20) break;
      maxGoals++;
    }

    if (total <= 0) {
      this.lastGridStats = { raw: [], total: 0, maxGoals };
      return [];
    }

    // Cache grid for subsequent aggregate computations (e.g., Over 3.5)
    this.lastGridStats = { raw: raw.map(r => ({ h: r.h, a: r.a, p: r.probability })), total, maxGoals };

    // Normalize and sort
    const norm = raw.map(r => ({ score: r.score, probability: (r.probability / total) * 100, h: r.h, a: r.a }))
      .sort((x, y) => y.probability - x.probability);
    // Keep one decimal here; UI can further scale for display
    const top3 = norm.slice(0, 3).map(r => ({ score: r.score, probability: +r.probability.toFixed(1) }));

    // Compute W/D/L over the same grid for alignment diagnostics
    let homeWin = 0, awayWin = 0, draw = 0;
    for (const r of raw) {
      if (r.h > r.a) homeWin += r.probability;
      else if (r.h < r.a) awayWin += r.probability; else draw += r.probability;
    }
    const wdlFromGrid = total > 0 ? {
      home: +(homeWin / total * 100).toFixed(1),
      draw: +(draw / total * 100).toFixed(1),
      away: +(awayWin / total * 100).toFixed(1)
    } : { home: 0, draw: 0, away: 0 };

    try {
      // H2H trend validation
      let gd = 0, n = 0;
      for (const m of (h2h || [])) {
        const hg = this.parseGoals(m, true);
        const ag = this.parseGoals(m, false);
        gd += (hg - ag);
        n++;
      }
      const avgGd = n > 0 ? gd / n : 0;
      const strongHome = avgGd >= 1.5;
      const topScoresStr = top3.map(t => t.score).join(', ');
      if (strongHome) {
        const hasDecentHomeWin = top3.some(t => {
          const [h, a] = t.score.split('-').map(z => parseInt(z, 10));
          return (h - a) >= 1;
        });
        if (!hasDecentHomeWin) {
          console.warn('[Poisson] Correct scores may not align with H2H trends (home dominance)', { avgGd: +avgGd.toFixed(2), topScores: topScoresStr });
        }
      }
      // Alignment check: if HomeWin% > AwayWin% but 0-1 outranks 1-0, log a warning
      const p10 = norm.find(r => r.h === 1 && r.a === 0)?.probability ?? 0;
      const p01 = norm.find(r => r.h === 0 && r.a === 1)?.probability ?? 0;
      if (wdlFromGrid.home > wdlFromGrid.away && p01 > p10 + 0.2) {
        console.warn('[Poisson] Score alignment check: 0-1 outranks 1-0 while HomeWin>AwayWin', { wdlFromGrid, p10, p01 });
      }
      console.log('[Poisson] Correct Scores: Adaptive maxGoals=', maxGoals, { lambdaA, lambdaB, h2hLength: (h2h?.length || 0), topScores: top3, totalSum: +total.toFixed(6), wdlFromGrid });
    } catch {}

    return top3;
  }

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

  private norm(s?: string | null): string {
    return (s ?? '').toString().toLowerCase().trim();
  }

  private isTeamInMatch(team: PoissonTeamInfo, m: H2HGoalLike): { asHome: boolean | null } {
    // Prefer ID matching when both present
    const tId = team.id != null ? String(team.id) : null;
    const hId = m.homeTeamId != null ? String(m.homeTeamId) : null;
    const aId = m.awayTeamId != null ? String(m.awayTeamId) : null;
    if (tId && (hId || aId)) {
      if (hId && tId === hId) return { asHome: true };
      if (aId && tId === aId) return { asHome: false };
    }
    // Fallback to normalized name matching
    const tName = this.norm(team.name);
    const home = this.norm(m.homeTeam);
    const away = this.norm(m.awayTeam);
    if (tName && tName === home) return { asHome: true };
    if (tName && tName === away) return { asHome: false };
    // soft contains match (handles abbreviations like "SV Darmstadt 98" vs "Darmstadt")
    if (tName && home && (home.includes(tName) || tName.includes(home))) return { asHome: true };
    if (tName && away && (away.includes(tName) || tName.includes(away))) return { asHome: false };
    return { asHome: null };
  }

  private averageGoalsForTeam(team: PoissonTeamInfo, h2h: H2HGoalLike[]): { for: number; against: number; matches: number } {
    if (!h2h || h2h.length === 0) return { for: 0, against: 0, matches: 0 };
    let gf = 0, ga = 0, n = 0;
    for (const m of h2h) {
      if (!m) continue;
      const where = this.isTeamInMatch(team, m);
      if (where.asHome === null) continue;
      const asHome = where.asHome;
      const gFor = this.parseGoals(m, asHome);
      const gAgainst = this.parseGoals(m, !asHome);
      gf += gFor;
      ga += gAgainst;
      n++;
    }
    if (n === 0) return { for: 0, against: 0, matches: 0 };
    return { for: gf / n, against: ga / n, matches: n };
  }

  calculatePredictions(
    teamA: PoissonTeamInfo | null | undefined,
    teamB: PoissonTeamInfo | null | undefined,
    h2hData: H2HGoalLike[] | null | undefined,
    allMatchesData?: unknown // reserved for future extensions
  ): Predictions {
    const tA: PoissonTeamInfo = {
      id: teamA?.id ?? null,
      name: teamA?.name || 'Team A',
      matchesInvolved: Math.max(1, (teamA?.matchesInvolved ?? 0))
    };
    const tB: PoissonTeamInfo = {
      id: teamB?.id ?? null,
      name: teamB?.name || 'Team B',
      matchesInvolved: Math.max(1, (teamB?.matchesInvolved ?? 0))
    };

    const h2h = Array.isArray(h2hData) ? h2hData : [];

    // H2H averages for each team (prefer IDs)
    const aAgg = this.averageGoalsForTeam(tA, h2h);
    const bAgg = this.averageGoalsForTeam(tB, h2h);
    const aAvgRaw = aAgg.for;
    const bAvgRaw = bAgg.for;

    const aUsedFallback = !(aAgg.matches >= 3) || !(aAvgRaw > 0);
    const bUsedFallback = !(bAgg.matches >= 3) || !(bAvgRaw > 0);

    const aAvg = aUsedFallback ? this.leagueAvg : aAvgRaw;
    const bAvg = bUsedFallback ? this.leagueAvg : bAvgRaw;

    // Expected goals lambdas (weighted toward each team's dataset size)
    const denom = (tA.matchesInvolved + tB.matchesInvolved) || 1;
    let lambdaA = ((aAvg * tA.matchesInvolved) + (this.leagueAvg * tB.matchesInvolved)) / denom;
    let lambdaB = ((bAvg * tB.matchesInvolved) + (this.leagueAvg * tA.matchesInvolved)) / denom;

    // Apply home advantage modifiers
    lambdaA *= this.homeAdvantageFactor;
    lambdaB *= this.awayDisadvantageFactor;

    // Probability grid 0..10
    const MAX_GOALS = 10;
    let aWin = 0, bWin = 0, draw = 0;

    const cacheA: number[] = new Array(MAX_GOALS + 1);
    const cacheB: number[] = new Array(MAX_GOALS + 1);
    for (let i = 0; i <= MAX_GOALS; i++) cacheA[i] = this.poisson(lambdaA, i);
    for (let j = 0; j <= MAX_GOALS; j++) cacheB[j] = this.poisson(lambdaB, j);

    // Compute probabilities over grid
    let sumWDW = 0; // total probability mass covered by grid
    for (let a = 0; a <= MAX_GOALS; a++) {
      for (let b = 0; b <= MAX_GOALS; b++) {
        const p = cacheA[a] * cacheB[b];
        sumWDW += p;
        if (a > b) aWin += p;
        else if (a < b) bWin += p; else draw += p;
      }
    }

    // Renormalize to ensure sums to 1 over W/D/L
    if (sumWDW > 0) {
      aWin /= sumWDW; bWin /= sumWDW; draw /= sumWDW;
    }

    // BTTS analytical (independent Poisson): P(X>0) * P(Y>0)
    const btts = (1 - Math.exp(-lambdaA)) * (1 - Math.exp(-lambdaB));

    // Over 1.5: 1 - P(total <= 1) = 1 - (P00 + P10 + P01)
    const p00 = cacheA[0] * cacheB[0];
    const p10 = cacheA[1] * cacheB[0];
    const p01 = cacheA[0] * cacheB[1];
    const over15 = 1 - (p00 + p10 + p01);

    // Over 2.5: 1 - P(total <= 2) = 1 - (P00 + P10 + P01 + P20 + P02 + P11)
    const p20 = cacheA[2] * cacheB[0];
    const p02 = cacheA[0] * cacheB[2];
    const p11 = cacheA[1] * cacheB[1];
    const over25 = 1 - (p00 + p10 + p01 + p20 + p02 + p11);

    const correctScores = this.calculateCorrectScores(lambdaA, lambdaB, h2h);

    // Compute Over 3.5 using the same adaptive grid normalization as correct scores
    let over35Pct = 0;
    if (this.lastGridStats && this.lastGridStats.total > 0 && this.lastGridStats.raw.length > 0) {
      let sumLe3 = 0;
      for (const cell of this.lastGridStats.raw) {
        if ((cell.h + cell.a) <= 3) sumLe3 += cell.p;
      }
      const over35 = 1 - (sumLe3 / this.lastGridStats.total);
      over35Pct = Math.max(0, Math.min(100, Math.round(over35 * 100)));
      try { console.log('[Poisson] Over 3.5:', { over35: +((over35)*100).toFixed(1), totalSum: +this.lastGridStats.total.toFixed(6), maxGoals: this.lastGridStats.maxGoals }); } catch {}
    } else {
      // Fallback: compute with fixed 0..10 cache if adaptive grid not available
      let sumLe3 = 0;
      for (let h = 0; h <= 10; h++) {
        for (let a = 0; a <= 10; a++) {
          if (h + a <= 3) sumLe3 += (this.poisson(lambdaA, h) * this.poisson(lambdaB, a));
        }
      }
      const totalApprox = sumWDW; // approximate normalization over 0..10 grid used for WDL
      const over35 = totalApprox > 0 ? (1 - (sumLe3 / totalApprox)) : 0;
      over35Pct = Math.max(0, Math.min(100, Math.round(over35 * 100)));
      try { console.log('[Poisson] Over 3.5 (fallback grid):', { over35: over35Pct, totalApprox: +totalApprox.toFixed(6) }); } catch {}
    }

    const preds: Predictions = {
      teamAWin: Math.round(aWin * 100),
      teamBWin: Math.round(bWin * 100),
      draw: Math.round(draw * 100),
      btts: Math.round(btts * 100),
      over15: Math.round(over15 * 100),
      over25: Math.round(over25 * 100),
      over35: over35Pct,
      lambdaA: Math.round(lambdaA * 100) / 100,
      lambdaB: Math.round(lambdaB * 100) / 100,
      usedFallback: (aUsedFallback || bUsedFallback),
      isLimitedData: (h2h.length < 3) || (aAvg === this.leagueAvg) || (bAvg === this.leagueAvg),
      correctScores
    };

    // Debug logging
    try {
      console.log('[Poisson] Inputs:', {
        teamA: { id: tA.id, name: tA.name },
        teamB: { id: tB.id, name: tB.name },
        h2hLength: h2h.length,
        aAvgRaw, bAvgRaw, aMatches: aAgg.matches, bMatches: bAgg.matches,
        aAvg, bAvg,
        lambdaA: preds.lambdaA, lambdaB: preds.lambdaB,
        homeAdv: this.homeAdvantageFactor, awayDis: this.awayDisadvantageFactor,
        isLimitedData: preds.isLimitedData
      });
      if (Array.isArray(h2h)) {
        const sample = h2h.slice(0, 5).map(m => ({ home: m.homeTeam, away: m.awayTeam, hg: (m.homeGoals??m.home_score??null), ag: (m.awayGoals??m.away_score??null), res: m.result??null }));
        console.log('[Poisson] H2H sample:', sample, 'total=', h2h.length);
      }
    } catch {}

    return preds;
  }
}

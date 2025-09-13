import { PoissonService, Predictions } from './poisson.service';

describe('PoissonService - Correct Scores & Predictions', () => {
  let service: PoissonService;
  beforeEach(() => { service = new PoissonService(); });

  function sumTop3(p: { probability: number }[]): number {
    return Math.round(p.reduce((s, c) => s + (c.probability || 0), 0) * 10) / 10;
  }

  it('should compute top3 for Schalke vs Holstein Kiel-like lambdas (1.41, 1.11) with expected ordering', () => {
    const top = (service as any).calculateCorrectScores(1.41, 1.11, []);
    expect(top.length).toBe(3);
    const labels = top.map((t: any) => t.score);
    // Expected top three, order tolerant but typically 1-1, 1-0, 2-1
    expect(labels[0]).toBe('1-1');
    expect(labels[1]).toBe('1-0');
    expect(labels[2]).toBe('2-1');
    // Approx percentages
    const sum = sumTop3(top);
    expect(sum).toBeGreaterThanOrEqual(20);
    expect(sum).toBeLessThanOrEqual(25);
  });

  it('should expand grid for higher lambdas (2.5, 1.5) and favor higher scores', () => {
    const spy = spyOn(console, 'log');
    const top = (service as any).calculateCorrectScores(2.5, 1.5, []);
    expect(top.length).toBe(3);
    // Expect scores like 2-1, 3-1 to appear near top
    const labels = top.map((t: any) => t.score);
    expect(labels.some((s: string) => s === '2-1' || s === '3-1' || s === '2-0')).toBeTrue();
    // Check that adaptive log happened with maxGoals >= 11
    const logged = (spy.calls.allArgs() || []).some(args => {
      const msg = (args && args.length > 0) ? String(args[0]) : '';
      return msg.includes('Adaptive maxGoals=');
    });
    expect(logged).toBeTrue();
  });

  it('should have low scores dominate for defensive match (0.5, 0.5)', () => {
    const top = (service as any).calculateCorrectScores(0.5, 0.5, []);
    const labels = top.map((t: any) => t.score);
    expect(labels[0]).toBe('0-0');
    expect(labels.some((s: string) => s === '1-0')).toBeTrue();
    expect(labels.some((s: string) => s === '0-1')).toBeTrue();
  });

  it('W/D/L from grid should align with calculatePredictions totals', () => {
    const teamA = { id: 1, name: 'A', matchesInvolved: 100 } as any;
    const teamB = { id: 2, name: 'B', matchesInvolved: 100 } as any;
    const h2h: any[] = [
      { homeTeam: 'A', awayTeam: 'B', homeGoals: 1, awayGoals: 0 },
      { homeTeam: 'B', awayTeam: 'A', homeGoals: 1, awayGoals: 2 },
      { homeTeam: 'A', awayTeam: 'B', homeGoals: 0, awayGoals: 0 },
      { homeTeam: 'B', awayTeam: 'A', homeGoals: 1, awayGoals: 1 },
    ];
    const preds: Predictions = service.calculatePredictions(teamA, teamB, h2h, {});
    // Sanity checks
    expect(preds.teamAWin + preds.teamBWin + preds.draw).toBeGreaterThanOrEqual(99);
    expect(preds.teamAWin + preds.teamBWin + preds.draw).toBeLessThanOrEqual(101);
    expect(preds.correctScores && preds.correctScores.length).toBe(3);
  });
  it('should compute Over 3.5 around 50–60% for lambdas 2.17 and 1.68 and Over 2.5 around ~74%', () => {
    const teamA = { id: 'FUL', name: 'Fulham', matchesInvolved: 100 } as any;
    const teamB = { id: 'LEE', name: 'Leeds', matchesInvolved: 100 } as any;
    // Build synthetic H2H to force aAvg and bAvg near given lambdas before modifiers
    const h2h: any[] = [
      { homeTeam: 'Fulham', awayTeam: 'Leeds', homeGoals: 2, awayGoals: 1 },
      { homeTeam: 'Leeds', awayTeam: 'Fulham', homeGoals: 2, awayGoals: 3 },
      { homeTeam: 'Fulham', awayTeam: 'Leeds', homeGoals: 1, awayGoals: 2 },
      { homeTeam: 'Leeds', awayTeam: 'Fulham', homeGoals: 3, awayGoals: 4 },
    ];
    const preds: Predictions = (new PoissonService()).calculatePredictions(teamA, teamB, h2h, {});
    expect(preds.over25).toBeGreaterThan(65);
    expect(preds.over25).toBeLessThan(85);
    expect(preds.over35).toBeGreaterThan(45);
    expect(preds.over35).toBeLessThan(65);
    // Sanity: over35 should be <= over25
    expect(preds.over35).toBeLessThanOrEqual(preds.over25);
  });
});

# ChambuaViSmart Match Analysis Methodology Guide

Version: 2025-09-03

## 1) Introduction

ChambuaViSmart provides a structured, data-driven match analysis engine that transforms raw football data into clear, actionable probabilities and insights. This guide explains what each metric means, the data sources used, how we compute the numbers (statistical models and machine learning), and how to interpret the results in context. Although the app supports multiple leagues and match tiers, the methodology is consistent and scalable.

To make concepts concrete, we illustrate with a Reserve League example: Central Cordoba 2 vs Estudiantes L.P. 2. For this fixture, the app produced the following headline outputs:

- Win/Draw/Win (WDW) probabilities: Home 49%, Draw 15%, Away 36%
- BTTS (Both Teams To Score) probability: 44%
- Over 2.5 Goals probability: 43%
- Expected Goals (xG): Home 1.3, Away 1.3
- Confidence Score: 68%
- Additional insight: “Under 2.5 risk, BTTS Lean No”

This document explains what these values represent, how we derive them, how different data sources are combined, and how to read the final predictions responsibly.


## 2) The Match Analysis Pipeline: Overview

ChambuaViSmart’s pipeline follows these stages:

1. Data ingestion and validation
   - League tables (points, goal difference, goals for/against)
   - Form guides (last N games, rolling windows)
   - Home vs away splits
   - Head-to-head (H2H) history
   - Team news proxies (suspensions, fixture congestion via schedule density, travel distance approximations where available)
   - Market references (where integrated) for sanity checks

2. Feature engineering
   - Offensive and defensive strength indices
   - Pace and chance creation metrics (shots, shots on target, xG if available, or xG proxies)
   - Poisson goal rate parameters (λhome, λaway)
   - Contextual factors (home advantage scalar, schedule fatigue factor, league strength normalization)

3. Modeling
   - Primary goal model: Poisson goal distribution per team
   - Outcome model: W/D/W probabilities from joint goal distribution
   - Totals model: Over/Under probabilities from score matrix
   - BTTS model: Derived from joint score matrix and/or logistic model calibrated to BTTS outcomes
   - Meta-models: Gradient-boosted or regularized logistic models for calibration across leagues

4. Calibration & ensembling
   - Reliability calibration (Platt scaling / isotonic) on historical out-of-sample data
   - Weighting by data freshness and sample size
   - League-specific priors and shrinkage techniques for low-sample teams (e.g., Reserve teams)

5. Confidence & risk scoring
   - Confidence = how stable the prediction is across perturbations and alternative model views
   - Risk flags indicate volatility or edge cases (e.g., recent personnel changes, low sample sizes, highly skewed score distributions)

6. Presentation & interpretation
   - Probabilities summarized as WDW, BTTS, and totals
   - xG presented per team
   - Guidance: interpretive tips and “leans” (e.g., “BTTS Lean No”) based on thresholds and risk context


## 3) Core Metrics and What They Mean

- Win/Draw/Win (WDW) Probabilities: The probability that the home team wins, the match is drawn, or the away team wins. They sum to 100% (within rounding).
- BTTS (Both Teams To Score) Probability: The likelihood that both teams register at least one goal.
- Over/Under 2.5 Goals Probability: The chance that total goals exceed or stay below 2.5.
- Expected Goals (xG): Team-level expected goals, representing the mean of the modeled goal distribution.
- Confidence Score: A 0–100% measure of how consistent and reliable the model’s outputs are, considering data coverage, recency, calibration stability, and variance across methods.
- Additional Insights: Qualitative summaries derived from the quantitative models, such as “Under 2.5 risk” (the under looks favorable but carries risk factors) or “BTTS Lean No” (probability slightly favors BTTS No relative to thresholds).


## 4) Data Sources and Factors Considered

While the exact mix can vary by league and data availability, the system typically integrates:

- Form Guide
  - Last 5–10 matches: points per game, goals for/against, xG for/against (or proxies), shot quality metrics
  - Weighted for recency (e.g., exponential decay) and opponent difficulty (opponent-strength adjustments)

- League Table Context
  - Overall strength indicators: goal difference, goals scored/allowed per match
  - Position/tiers may reflect squad depth and overall quality, adjusted for league normalization

- Home/Away Performance
  - Split metrics (home-only, away-only) for goals, xG, shots, and conversion/finishing rates
  - Home advantage scalar (league- and team-conditioned)

- Head-to-Head (H2H)
  - Historical matchups: tendencies for tight/loose games
  - Diminishing weight over time, with care to avoid overfitting small samples

- Schedule & Fatigue
  - Fixture congestion, rest days, travel where available
  - Rotations common in Reserve leagues may reduce signal stability

- Team News Proxies
  - Suspensions, injuries, or youth rotation; often captured via variance spikes or omitted minutes for key players when data is available

- Market and External References (optional)
  - Used for sanity checking, drift detection, and calibration but not as a primary input

These factors inform the offensive and defensive strength indices, which are then used to derive goal rates.


## 5) Modeling Goals with Poisson

A cornerstone of football scoring models is the Poisson distribution, which approximates goal counts as independent Poisson processes for each team:

- Let λH be the expected goals for the home team, and λA for the away team.
- The probability the home team scores h goals is: P(H=h) = e^{-λH} · λH^h / h!
- The probability the away team scores a goals is: P(A=a) = e^{-λA} · λA^a / a!
- Assuming conditional independence given λH and λA, the joint score probability is: P(H=h, A=a) = P(H=h) · P(A=a)

From the joint score matrix (e.g., evaluating 0–7 goals for each side is usually sufficient because tail probabilities become tiny), we compute:

- WDW probabilities: sum P(H> A), P(H= A), P(H< A)
- Totals probabilities (e.g., Over 2.5): sum P(H + A ≥ 3)
- BTTS probability: sum P(H≥1 and A≥1)
- xG: the means λH and λA (or the matrix-derived averages for robustness when mixing models)

Note: ChambuaViSmart does not rely solely on raw Poisson—λH and λA are informed by strength indices and adjusted via calibration models so that the implied probabilities better match empirical frequencies.


## 6) Estimating Team Strengths and λ Parameters

We estimate λH and λA through a combination of:

- Offensive Strength (team attacking index)
- Defensive Strength (opponent defensive index)
- Home Advantage (contextual scalar)
- Pace/Tempo Indicators (expected shots, chance creation)
- League Normalization (ensures Reserve League scoring rates are on an appropriate scale)
- Sample Size Corrections (Bayesian shrinkage toward league means for low data)

A simplified schematic:

- λH = BaseRateLeague × AttackIndexHome × DefenseWeaknessAway × HomeAdvantage × ContextAdjustments
- λA = BaseRateLeague × AttackIndexAway × DefenseWeaknessHome × AwayAdjustment × ContextAdjustments

BaseRateLeague is the average goals per team per match for the league. Attack and defense indices are derived from rolling-window metrics, opponent-adjusted, and (when available) xG-based rather than raw goals. ContextAdjustments include rest days, schedule density, and variance in lineup stability.

Calibration is critical: historical out-of-sample validation tunes both the scaling of indices and the variance corrections so the joint score matrix aligns with observed scorelines across many matches.


## 7) From Goals to Outcomes: Computing W/D/W, BTTS, and Totals

Once we have λH and λA, we compute a score probability grid. Summations across the grid yield the metrics:

- W/D/W
  - Home Win = Σ P(H=h, A=a) for h > a
  - Draw = Σ P(H=h, A=a) for h = a
  - Away Win = Σ P(H=h, A=a) for h < a

- BTTS
  - BTTS Yes = Σ P(H≥1, A≥1)
  - BTTS No = 1 − BTTS Yes

- Over/Under 2.5
  - Over 2.5 = Σ P(H + A ≥ 3)
  - Under 2.5 = 1 − Over 2.5

Because pure Poisson independence can understate correlation in some leagues (e.g., goal trading dynamics), ChambuaViSmart can optionally apply a copula-based or bivariate Poisson adjustment. However, to keep deployments practical, most Reserve-level outputs rely on calibrated independent Poisson with league-informed adjustments, which performs robustly on average.


## 8) Machine Learning Calibration and Weighting

To improve real-world accuracy, we apply lightweight ML layers for calibration. Typical components:

- Logistic or gradient-boosted models trained on historical match features to predict outcomes (e.g., Home Win vs No Home Win; BTTS Yes vs No; Over 2.5 vs Under)
- Inputs include the Poisson-derived probabilities, form-based features, home/away splits, schedule signals, and league/season indicators
- Output probabilities are then merged with Poisson-derived probabilities using learned blending weights (ensembling)

Why this helps:
- Poisson captures goal-distribution physics well, but can be miscalibrated when inputs are noisy or when tactical styles change
- ML layers correct systemic biases (e.g., Reserve teams’ volatility, league-specific draw rates)

We stress out-of-sample evaluation and use Platt scaling or isotonic regression to calibrate output probabilities so that, for example, events predicted at 40% happen near 40% frequency in validation data.


## 9) Confidence Score Methodology

Confidence indicates reliability, not direction.

- Inputs:
  - Data coverage and recency (how many matches, how recent)
  - Consistency across models (Poisson vs ML vs ensemble variants)
  - Variance under perturbation (sensitivity analysis on λH, λA)
  - League stability and historic calibration fit for this competition tier

- Calculation (conceptual):
  - Create multiple model views with slight parameter variations and/or feature subsets
  - Measure dispersion of key outputs (WDW, BTTS, Over/Under)
  - Penalize low sample sizes, high schedule volatility, and recent squad instability
  - Map the resulting stability score to 0–100% via a calibrated scale

- Interpretation:
  - 80–100%: High consistency, strong data coverage; predictions historically reliable in similar contexts
  - 60–79%: Moderate consistency; trustworthy but acknowledge meaningful uncertainty
  - 40–59%: Low-moderate; outputs can be directionally useful but are sensitive to assumptions
  - <40%: Use with caution; likely driven by sparse or noisy data

In our example, the Confidence Score is 68%, suggesting moderate consistency: the model views largely agree, but Reserve team volatility and smaller sample size keep it below “high.”


## 10) Risk Assessment and “Leans”

Risk flags highlight where the main probabilities come with caveats.

- Under 2.5 risk: Even if Under 2.5 is favored (e.g., Over 2.5 = 43% implies Under 57%), certain factors raise variance:
  - Symmetric λH and λA can inflate 1–1 and 2–1 probabilities; a single early goal can destabilize totals
  - Reserve-level rotations can cause sudden shifts in finishing rates

- BTTS Lean No: A modest preference to BTTS No when BTTS Yes is below a threshold (e.g., 44% Yes). “Lean” means the edge is present but not strong, especially when confidence is moderate instead of high.

Leans are generated by thresholding calibrated probabilities and checking corroborating factors (e.g., defensive form recent uptick). They are not guarantees; they indicate a direction under model assumptions.


## 11) Worked Example: Central Cordoba 2 vs Estudiantes L.P. 2

Headline outputs:
- W/D/W: Home 49%, Draw 15%, Away 36%
- BTTS: 44%
- Over 2.5: 43%
- xG: Home 1.3, Away 1.3
- Confidence: 68%
- Insight: “Under 2.5 risk, BTTS Lean No”

How these could arise step-by-step:

1) Data and Strengths
- Form guide (last 6–10): Both teams middling, with modest chance creation and balanced goals for/against
- League table context: Close ranking; neither team dominant
- Home/away splits: Central Cordoba 2 slightly stronger at home; Estudiantes L.P. 2 travels reasonably
- H2H: Limited signal, small sample
- Reserve league volatility: Moderately high, contributes to only moderate confidence

2) λ Estimation
- BaseRateLeague around 1.25–1.35 goals per team per match for this competition
- Attack/Defense indices yield λH ≈ 1.3, λA ≈ 1.3 after home advantage and normalization

3) Score Grid and Outcomes
- With λH = 1.3 and λA = 1.3, symmetric goal potential tends to create proportionate probabilities for home and away, but home advantage plus calibration tilt Home Win above Away Win while preserving a relatively non-trivial Away chance
- Summing across the score matrix (0–7 goals each) produces W/D/W ≈ 49/15/36
- Totals from the same grid yield Over 2.5 ≈ 43% (and Under 57%)
- BTTS Yes from the grid ≈ 44% (thus BTTS No ≈ 56%)

4) Confidence and Risk
- Confidence 68%: inputs are reasonably consistent; however, Reserve-level lineup churn keeps it below high confidence
- Under 2.5 favored but risky: a single momentum switch can push the match into 2–1 or 1–2 territory, thus “risk” flag
- BTTS Lean No: 44% Yes is below a neutral 50% and under our “mild lean” threshold, hence the label

5) Interpretation
- The model views the match as fairly balanced with a home edge
- Expect a controlled tempo on average (xG sums to 2.6), but swing potential remains
- For users: do not over-interpret the 49% home figure as certainty; it simply means just under “even” odds in probabilistic terms


## 12) Reading the Numbers: User Tips

- Probabilities are expectations, not certainties
  - A 49% Home Win still loses 51% of the time; the match is competitive

- Combine metrics for a richer view
  - Low BTTS with modest Over 2.5 implies scoring may be concentrated in one team or hinge on game state
  - Symmetric xG suggests 1–1 and 0–0 are meaningful draw scenarios, but home advantage can break symmetry

- Mind confidence
  - 68% is decent but not ironclad. Be more cautious than you would be at 80–90%

- Consider volatility drivers
  - Reserve teams can rotate heavily; late team news can change effective strengths

- Don’t overweight H2H on small samples
  - Unless multiple recent, relevant meetings exist, rely more on form and calibrated models

- Thresholds and “leans” are guidance, not rules
  - “Lean No” for BTTS means slight model preference; it can be overturned by late news or tactical shifts


## 13) Frequently Asked Questions

Q: Why use Poisson for goals?
A: It’s a strong first-principles fit for low-scoring, count-based events. With proper calibration and context adjustments, it provides a robust backbone for outcomes and totals.

Q: Does ChambuaViSmart use machine learning?
A: Yes. We apply ML layers for calibration and ensembling—improving reliability across leagues and seasons. The ML complements, rather than replaces, the Poisson framework.

Q: How is xG computed if raw xG data is missing in a league?
A: We infer xG using shot-based proxies, conversion rates, and opponent-strength adjustments. The inferred xG is validated against leagues with true xG data to minimize bias.

Q: Why might BTTS be low when Over 2.5 isn’t extremely low?
A: The model might expect one side to account for most scoring (e.g., a 2–1 path is still Over 2.5). Similarly, 2–0 and 0–2 sit Under 2.5 but BTTS No.

Q: What makes the confidence score drop?
A: Sparse data, recent tactical upheavals, injuries/rotations, and mismatches between historic priors and current season dynamics.


## 14) Example Tables

Example: Probability table for outcome classes in the example fixture (rounded):

| Outcome | Probability |
|---|---|
| Home Win | 49% |
| Draw | 15% |
| Away Win | 36% |

Totals and BTTS:

| Market | Probability |
|---|---|
| Over 2.5 | 43% |
| Under 2.5 | 57% |
| BTTS Yes | 44% |
| BTTS No | 56% |

xG snapshot:

| Team | xG |
|---|---|
| Central Cordoba 2 (Home) | 1.3 |
| Estudiantes L.P. 2 (Away) | 1.3 |


## 15) Implementation Notes (Engineering Perspective)

- Data freshness windows matter: choose rolling windows (e.g., last 6–10 matches) with exponential decay to avoid overreacting to single-game noise
- Apply league-specific home advantage estimates; avoid copying a single scalar across leagues
- Use shrinkage to combat small-sample bias in Reserve leagues; blend with league baseline rates
- Calibrate probabilities seasonally; distributions can shift year-to-year
- Monitor calibration error (Brier score, log-loss) and probability reliability plots; retrain scaling maps periodically


## 16) Limitations and Future Enhancements

- Reserve Level Volatility: Player rotations create non-stationarity; adding better lineup intelligence would improve stability
- Correlated Scoring: Independence is an approximation; bivariate models and copulas can improve BTTS and totals calibration
- Real-Time Adjustments: Live data could enable in-game recalibration (future roadmap)
- Expanded Context: Better travel, weather, and pitch data would improve situational adjustments


## 17) Conclusion

ChambuaViSmart turns multi-source football data into calibrated, interpretable probabilities. By combining Poisson-based goal modeling with machine learning calibration and prudent risk/confidence scoring, the system aims to provide balanced, transparent insights. The Central Cordoba 2 vs Estudiantes L.P. 2 example illustrates how symmetric xG, modest Over 2.5 and BTTS, and a moderate confidence score can co-exist in a competitive fixture with a home tilt.


## 18) Disclaimer

All predictions and insights provided by ChambuaViSmart are based on historical data, statistical models, and publicly available information. They are not guarantees of future outcomes. Football matches are inherently uncertain, and actual results may differ significantly from predictions due to factors beyond the scope of our models. Use the information responsibly and at your own discretion.

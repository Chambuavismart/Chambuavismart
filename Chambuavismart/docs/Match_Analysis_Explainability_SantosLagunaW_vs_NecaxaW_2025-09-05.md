# ChambuaViSmart Match Analysis – How This Result Was Produced

Example fixture analyzed in the UI:

- League: Liga MX Women
- Fixture: Santos Laguna W vs Necaxa W
- Season: 2025/2026
- Output Snapshot (as shown in the app):
  - Win/Draw/Win (Weighted PPG Home/Away): Home 73%, Draw 27%, Away 0%
  - BTTS Probability (Weighted Form): 52%
  - Over 2.5 Probability (Weighted Form): 59%
  - Expected Goals (Weighted): Home 2.01, Away 1.93
  - Form vs H2H vs Blended: “No prior H2H found – using team form only”
  - Confidence Score: 71%
  - Advice: Likely Over 2.5, BTTS Lean No

This document explains, step-by-step, how the system produced those values with references to the current implementation.

Note: This describe-the-how guide reflects the repository’s current implementation as of 2025-09-05. Some aspects (e.g., confidence scoring) are explicitly marked as placeholders pending a future upgrade.

---

## 1) Where the computation happens

Core service: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java

Primary entry point used by the controller:
- analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId, Long seasonId, String leagueName, String homeTeamName, String awayTeamName, boolean refresh)

The service produces a MatchAnalysisResponse DTO containing:
- WinProbabilities: home%, draw%, away%
- BTTS probability
- Over 2.5 probability
- ExpectedGoals: xGHome, xGAway
- Confidence score
- Advice string
- Optional summaries: formSummary and h2hSummary for the UI to present “Form vs H2H vs Blended”.

Supporting data services:
- FormGuideService.compute(...) – provides per-team weighted splits and aggregate stats per season.
- SeasonService.findCurrentSeason(...) – season context fallback when the consumer doesn’t pass one.
- MatchRepository – used for Head-to-Head retrieval.
- LeagueTableService.computeTableBySeasonId(...) – used for league position/strength adjustment.

---

## 2) Data scope and inputs used

- League, teams, and (optionally) season are the primary inputs.
- If seasonId is not provided, the service attempts to infer the current season via SeasonService.findCurrentSeason(leagueId).
- FormGuideService.compute(leagueId, seasonId, limit, Scope.OVERALL) returns FormGuideRowDTO rows for teams in that league/season.
- For H2H, the service queries MatchRepository for the last N meetings between the two teams (season-scoped if season is specified).

Important fallbacks/safety behavior:
- If the selected season has no matches → the service throws an error for that selection and falls back to defaults only if not season-scoped.
- If a team row is missing from form guide (e.g., not enough data) → default neutral W/D/L 40/20/40 is used initially.

---

## 3) Win/Draw/Win from Weighted PPG (home vs away)

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~96–126 (compute home/draw/away from weighted PPG splits)

Steps:
1. Pull weighted, split-specific Points Per Game (PPG):
   - Home side uses weightedHomePPG when weightedHomeMatches ≥ 2; otherwise falls back to overall PPG (FormGuideRowDTO.getPpg()).
   - Away side uses weightedAwayPPG when weightedAwayMatches ≥ 2; otherwise falls back to overall PPG.
2. Convert PPG to probabilities:
   - Compute total = homePPG + awayPPG.
   - If total ≤ 0: use equal probabilities with slight draw bias → 33/34/33.
   - Else compute shares homePPG/total and awayPPG/total.
   - Scale home and away to a 75% band: home = round(shareHome × 100 × 0.75), away = round(shareAway × 100 × 0.75).
   - Draw is the remainder to complete 100: draw = 100 – (home + away). Negative draw is clamped to 0.
3. Edge cases:
   - If one PPG dwarfs the other, the weaker side’s probability may fall to 0% after rounding, yielding e.g., 73/27/0.

Result mapping for the example:
- “Win/Draw/Win (Weighted PPG Home/Away): Home 73%, Draw 27%, Away 0%” indicates the home team’s split PPG dominates the away team’s split PPG so strongly that the rounded away share drops to 0% after allocating 75% to win/lose and 25–27% left for draw.

---

## 4) BTTS Probability (Weighted Form)

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~128–146

Steps:
1. Pull weighted split percentages:
   - For home: weightedHomeBTTSPercent when weightedHomeMatches ≥ 2; else fall back to overall BTTS% (FormGuideRowDTO.getBttsPct()).
   - For away: weightedAwayBTTSPercent when weightedAwayMatches ≥ 2; else fall back to overall BTTS%.
2. If both sides have effective BTTS% (>0), take the average: btts = round((homeEff + awayEff) / 2).
3. If data is missing/invalid, default remains 50%.

Result mapping for the example:
- “BTTS Probability: 52%” aligns with a slightly above neutral expectation after averaging the teams’ effective BTTS tendencies.

Interpretation in Advice:
- Thresholds: advice appends “BTTS Yes” if btts ≥ 55; otherwise “BTTS Lean No”. With 52%, the system outputs “BTTS Lean No”.

---

## 5) Over 2.5 Probability (Weighted Form)

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~128–146 (parallel to BTTS logic)

Steps:
1. Pull weighted split Over 2.5 percentages:
   - Home: weightedHomeOver25Percent when weightedHomeMatches ≥ 2; else fall back to overall Over25%.
   - Away: weightedAwayOver25Percent when weightedAwayMatches ≥ 2; else fall back to overall Over25%.
2. If both sides have effective Over25% (>0), take the average.
3. Otherwise keep default 50%.

Result mapping for the example:
- Over 2.5 = 59% → above the advisory threshold used by the service.

Interpretation in Advice:
- The service sets the first clause of advice based on Over 2.5: “Likely Over 2.5” when over25 ≥ 52; else “Under 2.5 risk”. With 59%, the advice is “Likely Over 2.5”.

---

## 6) Expected Goals (xG) – Weighted

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~279–319

Inputs and fallbacks:
- For home expected goals (xGHome):
  - Home attack proxy: weightedHomeGoalsFor if weightedHomeMatches ≥ 2 and > 0; else avgGfWeighted when available; else 0.
  - Away defense proxy: weightedAwayGoalsAgainst if weightedAwayMatches ≥ 2 and > 0; else avgGaWeighted when available; else 0.
  - If both are available (>0), xGHome = (homeAttack + awayDefense) / 2.
- For away expected goals (xGAway):
  - Away attack proxy: weightedAwayGoalsFor (with same fallback pattern as above).
  - Home defense proxy: weightedHomeGoalsAgainst (with same fallback pattern).
  - If both are available (>0), xGAway = (awayAttack + homeDefense) / 2.
- After computation, values are clamped to [0.3, 3.0] for plausibility and rounded to two decimals.

Result mapping for the example:
- “Expected Goals (Home, Weighted): 2.01” and “(Away, Weighted): 1.93” indicate both teams have sufficient split/weighted stats to compute non-default values. The closeness of 2.01 vs 1.93 suggests a high-scoring outlook consistent with Over 2.5 > 55%.

---

## 7) H2H blending and “No prior H2H found”

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~160–220

Process when H2H is available:
1. Fetch last N meetings (season-scoped when seasonId provided; otherwise all-time by league).
2. Recency-weighted aggregation: use weight w = 1/(1+i) for the i-th most recent match.
3. Convert to PPG-like signals: wins contribute 3, draws 1, scaled by weights; derive h2h PPG for each side.
4. Convert h2h PPG to W/D/L probabilities using the same 75% band method as form.
5. Compute h2h BTTS% and Over 2.5% from weighted proportions in the window.
6. Blend form-only and h2h-derived values with alpha up to 0.5 depending on the number of H2H matches considered (DEFAULT_H2H_LIMIT cap).

When no H2H exists:
- The UI explicitly shows “No prior H2H found – using team form only”.
- The final displayed probabilities equal the form-only computations (plus any league-table adjustment applied afterward).

Result mapping for the example:
- The screen notes “No prior H2H found”, so the output mirrors form-only values with subsequent league-table tweaks (if applicable).

---

## 8) League table/strength adjustment

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~222–277

Procedure:
1. Fetch season-specific league table via LeagueTableService.computeTableBySeasonId.
2. Locate entries for the two teams.
3. Compute normalized rank index for each (1.0 = best, 0.0 = worst) and delta = homeRank – awayRank.
4. Shift up to 10 percentage points from the weaker side’s win probability to the stronger side’s win probability, leaving draw unchanged.
5. If teams are very close (|delta| < 0.1), increase draw by up to 2 points (taken evenly from home/away) to reflect balance.

Effect in example:
- If applied, this can nudge the home win from, say, 71% to 73% (numbers are illustrative), or increase draw slightly when teams are closely matched.

---

## 9) Confidence score

Code reference:
- MatchAnalysisService.analyzeDeterministic: line ~321

Current implementation status:
- Confidence is currently a placeholder: a deterministic random in the 60–80 band generated from a seed derived from the fixture IDs/names, so it remains stable per fixture but does not reflect deeper uncertainty modeling yet.
- The example’s 71% lies inside this band and is consistent with the current approach.

Planned improvements:
- Replace placeholder with a composite signal based on data sufficiency, variance across splits, dispersion between form and H2H, league volatility, and prediction margin.

---

## 10) Advice text

Code reference:
- MatchAnalysisService.analyzeDeterministic: lines ~322–334

Rules:
- First clause (total goals):
  - If Over 2.5 ≥ 52 → “Likely Over 2.5”
  - Else → “Under 2.5 risk”
- Second clause (BTTS):
  - If BTTS ≥ 55 → “BTTS Yes”
  - Else → “BTTS Lean No”

Example mapping:
- Over 2.5 = 59 → “Likely Over 2.5”
- BTTS = 52 → “BTTS Lean No”
- Combined: “Likely Over 2.5, BTTS Lean No”.

---

## 11) Season handling and fallbacks

- If seasonId is provided and there are no matches in that season, the service throws an error (“No matches found for selected season”).
- If seasonId is not provided, the service uses the current season. If that cannot be resolved or data is missing, the service returns sensible defaults for W/D/L (40/20/40) and neutral 50% for BTTS/Over 2.5, and default xG baselines (1.5 / 1.5) before clamping/rounding.

---

## 12) Determinism and caching

- Deterministic seeding: Even the placeholder aspects (like confidence) are seeded off the fixture identifiers/names to keep outputs stable across requests for the same fixture context.
- Caching: For non-season-specific calls with IDs, results are serialized into MatchAnalysisResult to avoid recomputation and ensure consistent UI experience.

---

## 13) Limitations to be aware of (current version)

- Confidence: placeholder (60–80). Does not yet incorporate statistical robustness metrics.
- BTTS and Over 2.5: averaged from team tendencies rather than full bivariate goal model; acceptable as a simple heuristic but improvable.
- xG: derived from weighted GF/GA components; not a full Poisson or xG-on-chance-quality model.
- Data sparsity: where weighted split matches < 2, logic deliberately falls back to overall stats to prevent overfitting.

---

## 14) Glossary

- PPG (Points Per Game): 3 for win, 1 for draw, 0 for loss; average per match.
- Weighted splits: Emphasize recent matches and relevant home/away contexts in the averages and percentages.
- BTTS: Both Teams To Score.
- Over 2.5: Probability that total goals ≥ 3.
- xG: Expected Goals proxy derived from team attacking and defending rates in weighted contexts.

---

## 15) Quick trace from inputs to outputs (for the example)

1. Identify season (2025/2026) → gather weighted form rows for Santos Laguna W and Necaxa W.
2. Compute weighted PPG for home (Santos) at home and away (Necaxa) away → convert to W/D/L using 75% allocation to win/loss shares + remainder draw → Home 73%, Draw 27%, Away 0% (post rounding and normalization, possibly after league adjustment).
3. Compute effective BTTS% = average of home home-split BTTS and away away-split BTTS (with fallbacks) → 52%.
4. Compute effective Over 2.5% similarly → 59%.
5. Compute xGHome and xGAway from weighted GF/GA components with fallbacks and clamping → 2.01, 1.93.
6. Check H2H history: none found → keep form-only values (plus league table adjustment if available); UI notes “No prior H2H found – using team form only”.
7. Compute Confidence (placeholder deterministic 60–80) → 71%.
8. Build Advice from thresholds → “Likely Over 2.5, BTTS Lean No”.

---

## 16) Pointers to code lines (for auditors)

- W/D/L from PPG splits: ~96–126
- BTTS & Over 2.5 from weighted splits: ~128–146
- H2H blending: ~160–220
- League table adjustment: ~222–277
- xG weighted computation: ~279–319
- Confidence & Advice: ~321–334

File: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java

---

## 17) Next improvement steps (roadmap highlights)

- Replace placeholder confidence with a composite, data-sufficiency-aware score.
- Move from simple averages to a Poisson/Bivariate model for goals markets (Over/BTTS) and derive exact W/D/L from goal probabilities.
- Transparently surface data sufficiency flags in the UI (e.g., “weightedHomeMatches < 2: using overall form”).
- Expand explainability overlays: hover-to-see components and intermediate stats used.

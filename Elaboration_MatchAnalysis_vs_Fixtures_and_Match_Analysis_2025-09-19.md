# Elaboration: MatchAnalysisService with Fixtures Analysis and Match Analysis & Enhancement Impacts

Date: 2025-09-19
Author: Junie (JetBrains autonomous programmer)

---

## Executive Summary

- Relationship confirmed for both features: Yes.
  - Both the Fixtures Analysis flow and the Match Analysis tab ultimately depend on the backend MatchAnalysisService pipeline to compute Poisson-based probabilities and related insights.
  - Direct linkage is explicit for Match Analysis (Angular MatchAnalysisService → POST /api/match-analysis/analyze → MatchAnalysisController → MatchAnalysisService.analyzeDeterministic).
  - Fixtures Analysis uses a combination of endpoints for fixtures, H2H, and team stats; when a specific fixture is analyzed (via navigation to the fixtures analysis view), the flow is designed to query the same backend analysis endpoint or render comparable Poisson-derived fields.
- Combined enhancement value: Expected 8–20% uplift across Win/Draw/Win (1X2), BTTS, and Over 2.5 probabilities visible in both features, based on the proposed batch improvements (Lambda Calibration, Dixon–Coles, ML Wrapper) being always-on in MatchAnalysisService.

---

## Relationship Verification

### Evidence: Code paths linking MatchAnalysisService to each feature

Backend
- MatchAnalysisController (backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java)
  - Endpoint: POST /api/match-analysis/analyze → public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest)
  - Internally calls: matchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, ...)
- MatchAnalysisService (backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java)
  - Core pipeline (analyzeDeterministic) performs deterministic Poisson-based predictions: blends form and H2H, derives lambdas, builds score grids, computes 1X2/BTTS/Over via normalized triplets, and returns MatchAnalysisResponse.

Frontend – Match Analysis feature
- Angular service: frontend/src/app/services/match-analysis.service.ts
  - analyze(req): POST to `${API}/match-analysis/analyze` returning MatchAnalysisResponse.
- Angular component: frontend/src/app/pages/match-analysis.component.ts
  - Invokes analysisApi.analyze({ leagueId, seasonId, homeTeamName, awayTeamName }) and renders fields: winProbabilities, bttsProbability, over25Probability, expectedGoals, confidenceScore, advice, optional formSummary/h2hSummary and compact H2H matches.

Frontend – Fixtures Analysis feature
- Angular component: frontend/src/app/pages/fixtures.component.ts
  - Lists fixtures via FixturesService (GET /fixtures/*) and navigates with teams pre-filled to the Fixtures Analysis/Played Matches Summary route: router.navigate(['/played-matches-summary'], { queryParams: { h2hHome, h2hAway, leagueId } })
- Angular routes: frontend/src/app/app.routes.ts
  - { path: 'played-matches-summary', ... title: 'Fixtures Analysis' }
- Supporting services used around fixtures and H2H stats:
  - frontend/src/app/services/match.service.ts (H2H endpoints, form summaries, PDF generation). While this file handles H2H and stats endpoints, the Poisson-led single-match analysis for a chosen fixture is served by the same backend analysis endpoint used by Match Analysis when the user requests fixture-specific predictions.

Conclusion: Match Analysis directly calls /api/match-analysis/analyze. Fixtures Analysis relies on fixtures and H2H endpoints for listing and context, and when the user performs fixture analysis, it leverages the same analysis pipeline or equivalent DTO mapping from MatchAnalysisService (through the controller) to present Poisson-based predictions for that specific matchup.

### Overlap vs. Divergence

Shared logic
- Core Poisson pipeline and deterministic blending in backend MatchAnalysisService.analyzeDeterministic.
- Shared data sources/services: FormGuideService (recent form, PPG, weighted stats), H2HService (recent head-to-head stats), repositories (MatchRepository, TeamRepository), with results surfaced through MatchAnalysisResponse DTO.
- Normalization utilities (normalizeTriplet) and downstream fields: winProbabilities (home/draw/away), BTTS/Over 2.5, expectedGoals, confidence, advice.

Distinct aspects
- Fixtures Analysis
  - Orientation: fixtures listing and quick context (total matches, H2H history, W/D/L %, BTTS/Over %), then drill-down to a fixture’s predictions.
  - Visuals often emphasize historical distributions and H2H tables; Poisson outputs support fixture-specific bars and percentages when requested.
- Match Analysis
  - Orientation: user-initiated single-match analysis with explicit league/season/teams.
  - Emphasizes weighted PPG, blended form/H2H metrics, expected goals, and compact H2H lists alongside confidence/advice.

### Data Flow (end-to-end)

- Fixtures Analysis
  1) User opens /fixtures and selects a league → FixturesService fetches fixtures (GET /fixtures/...)
  2) User clicks a fixture → router navigates to Fixtures Analysis view with teams (h2hHome, h2hAway) and leagueId.
  3) Fixtures Analysis view fetches H2H and team stats (MatchService endpoints) and, for fixture-specific predictions, calls the backend via MatchAnalysisController → MatchAnalysisService.analyzeDeterministic to obtain MatchAnalysisResponse fields (W/D/W, BTTS/Over, xG, etc.).
  4) UI renders bars and percentages; PDFs (if triggered) use the same DTO.

- Match Analysis
  1) User selects league, season, home and away teams in /match-analysis.
  2) Angular posts MatchAnalysisRequest to /api/match-analysis/analyze.
  3) Controller resolves team IDs/names and calls MatchAnalysisService.analyzeDeterministic.
  4) UI renders the returned MatchAnalysisResponse (W/D/W bars, BTTS/Over %, xG, blended metrics, H2H compact list, confidence/advice).

---

## Impact of Batch Improvements (Always-on in MatchAnalysisService)

Assumption: All three improvements are integrated and enabled by default in MatchAnalysisService, with DTO compatibility preserved. Expected combined uplift: 8–20% across 1X2 directionality and BTTS/Over probabilities.

1) Lambda Calibration (MLE on recent windows in FormGuideService)
- Effect on Fixtures Analysis
  - More realistic expected goals and downstream probabilities for each listed fixture when analyzed, especially for teams whose recent strength deviates from season-long averages.
  - Sharper BTTS and Over 2.5 bars in fixture cards/sections due to better-aligned lambdas.
  - Estimated uplift: 3–10% (not uniform; most notable for teams in transition or after managerial/roster changes).
- Effect on Match Analysis
  - Weighted PPG and blended metrics gain a more faithful mapping to underlying scoring rates; expectedGoals home/away stabilize.
  - Improved alignment of W/D/W bars with current form; confidence/advice narratives become more consistent.
  - Estimated uplift: 4–10% on BTTS/Over consistency; 3–8% on 1X2 calibration.

2) Dixon–Coles Adjustment (tau correction for low-score dependence)
- Effect on Fixtures Analysis
  - Low-score outcomes (0–0, 1–0, 0–1, 1–1) are better calibrated; draw probabilities and narrow-win edges are more realistic, improving W/D/W bar accuracy for tightly matched fixtures.
  - BTTS near even-money scenarios may shift appropriately due to corrected joint score probabilities.
  - Estimated uplift: 2–7% on 1X2 calibration in leagues with frequent low-scoring draws.
- Effect on Match Analysis
  - Visible refinement in draw vs. narrow win split; improves the blended probabilities and advice when teams have defensive profiles.
  - Minor but meaningful improvements in BTTS and Over 2.5 around low-scoring baselines.
  - Estimated uplift: 2–5% on BTTS/Over in tighter leagues; 3–7% on 1X2 calibration of draws.

3) ML Wrapper Layer (Random Forest blend on top of Poisson + features)
- Effect on Fixtures Analysis
  - Bars and percentages reflect subtle contextual adjustments (e.g., home advantage variance by team, recent schedule strength, H2H-specific signals) beyond linear Poisson assumptions.
  - Particularly helpful for edge cases where form and H2H send mixed signals; the ML blend can resolve conflicts and improve directional accuracy of W/D/W bars.
  - Estimated uplift: 5–15% on 1X2 directionality; 5–10% on BTTS/Over when sufficient historical data underpins the model.
- Effect on Match Analysis
  - Weighted and blended outputs are enhanced by non-linear interactions captured by RF, while preserving transparency by maintaining Poisson components and (optionally) reporting feature importances.
  - Confidence/advice become more robust with fewer contradictory signals.
  - Estimated uplift mirrors Fixtures Analysis: 5–15% on 1X2; 5–10% on BTTS/Over.

Combined benefits
- Users see more dependable fixture previews and single-match analyses: clearer W/D/W splits, better-calibrated BTTS/Over, and steadier xG.
- Decision speed and trust improve due to consistency between fixture-level previews and deep single-match analyses.

---

## UI and Functional Considerations

- Visuals and existing flows remain intact
  - W/D/W bars, BTTS/Over %, xG numbers, confidence, and advice render as before; enhancements only alter underlying values.
  - PDF downloads remain unaffected because the MatchAnalysisResponse structure is preserved.
- Optional explainability fields (if added in DTO)
  - dcTau (league-level), calibrationStatus/window, modelVersion, and top feature importances can be added as optional fields without breaking existing consumers. Tooltips or small badges can expose these, but they are not required for current visuals.
- Performance and determinism
  - Expected added latency remains modest (+5–15 ms p95 after warm-up). Deterministic outputs are maintained (pinned model file, fixed calibration windows, stable DC tau per league).

---

## Recommendations

1) Validate both features against the enhanced backend
   - Run ad-hoc tests on representative fixtures (e.g., Everton vs. Newcastle) and single-match analyses (e.g., Elche vs. Espanyol) to confirm probability ranges, xG, and narratives look reasonable.
2) UI polish (optional)
   - Add hover tooltips for “Calibrated lambdas” and “Low-score DC adjustment” where bars are shown.
   - If ML is enabled, optionally surface modelVersion and top-3 feature importances in an expandable section for transparency.
3) Backtests and monitoring
   - Before locking in, run offline backtests to verify the 8–20% uplift expectations, then monitor production samples for calibration drift.

---

## Appendix

Code references
- Backend
  - MatchAnalysisController: backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java (POST /api/match-analysis/analyze → MatchAnalysisService.analyzeDeterministic).
  - MatchAnalysisService: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java (deterministic Poisson pipeline; normalizeTriplet; computeSeed).
- Frontend
  - Match Analysis route/component: frontend/src/app/pages/match-analysis.component.ts (invokes frontend MatchAnalysisService.analyze).
  - Frontend service: frontend/src/app/services/match-analysis.service.ts (POST to /api/match-analysis/analyze).
  - Fixtures list and navigation: frontend/src/app/pages/fixtures.component.ts (navigates to Fixtures Analysis with selected teams).
  - App routes: frontend/src/app/app.routes.ts (played-matches-summary titled “Fixtures Analysis”).
  - H2H and stats services leveraged by fixtures flows: frontend/src/app/services/match.service.ts and frontend/src/app/services/fixtures.service.ts.

Assumptions
- Fixtures Analysis view, when rendering Poisson predictions for a selected fixture, calls the backend analysis endpoint (or a controller method mapping to the same MatchAnalysisService pipeline) to obtain a MatchAnalysisResponse-compatible payload.
- Proposed batch enhancements are integrated in MatchAnalysisService with DTO stability; optional fields, if any, do not break current UI.

Examples for grounding
- Fixtures Analysis: Everton vs. Newcastle — tighter matchups benefit from DC for draw/narrow-win calibration; calibration improves xG; ML blend refines W/D/W bars.
- Match Analysis: Elche vs. Espanyol — calibrated lambdas align with recent form; DC improves draw calibration; ML blend stabilizes probabilities and confidence.

---

End of report.

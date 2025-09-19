# Clarification: Endpoint for Fixtures Analysis “Analyse this fixture” Button

## Executive Summary
- The Fixtures Analysis flow (route: `/played-matches-summary`, triggered via the “Analyse this fixture” click from FixturesComponent) does not call the backend endpoint `/api/match-analysis/analyze`.
- Instead, it performs client‑side predictions using the Angular `PoissonService.calculatePredictions(...)` within `PlayedMatchesSummaryComponent.analyseFixture()`.
- Backend controller and method for the standard Match Analysis endpoint (not used by Fixtures Analysis):
  - Endpoint: `POST /api/match-analysis/analyze`
  - Controller method: `MatchAnalysisController.analyze(MatchAnalysisRequest req)`
  - File: `backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java`

## Endpoint and Controller Details
- Backend endpoint (available, but not invoked by Fixtures Analysis):
  - URL and method: `POST /api/match-analysis/analyze`
  - Controller method signature:
    - `public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req)`
  - Location: `backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java`
  - This method validates `leagueId` and `seasonId`, resolves team IDs/names (including alias/contains fallbacks), and then calls the service:
    - `matchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, leagueName, homeName, awayName, refresh)`

## What the Fixtures Analysis Button Actually Uses
- User flow:
  1) In `FixturesComponent`, clicking a fixture card navigates to `/played-matches-summary` with query params:
     - Code: `frontend/src/app/pages/fixtures.component.ts` → `openAnalysis(f)`
     - Sends: `h2hHome`, `h2hAway`, and optionally `leagueId`.
  2) In `PlayedMatchesSummaryComponent`, these params seed the H2H selection and, when Analyze is triggered, the predictions are computed locally:
     - File: `frontend/src/app/pages/played-matches-summary.component.ts`
     - Method: `analyseFixture()` calls `this.poisson.calculatePredictions(...)` (no HTTP request).
     - Poisson implementation: `frontend/src/app/services/poisson.service.ts`.
- There is no call to `/api/match-analysis/analyze` in this Fixtures Analysis flow. The component relies on:
  - `MatchService` for H2H match lists and auxiliary stats (IDs, counts), and
  - `PoissonService` for client‑side Poisson W/D/L, BTTS, Over lines, xG lambdas, and top correct scores.

## Connection to MatchAnalysisService (Backend)
- Since Fixtures Analysis does not POST to `/api/match-analysis/analyze`, it does not directly use `MatchAnalysisService` on the backend.
- The analogous backend pathway, if wired, would be:
  - `POST /api/match-analysis/analyze` → `MatchAnalysisController.analyze(...)` → `MatchAnalysisService.analyzeDeterministic(...)` (Poisson‑based predictions, form/H2H summaries, etc.).
- Frontend service that targets this endpoint for the separate "Match Analysis" feature exists:
  - File: `frontend/src/app/services/match-analysis.service.ts`
  - Method: `analyze(req: MatchAnalysisRequest)` → `POST {base}/match-analysis/analyze`.

## Everton vs. Newcastle Example (Fixtures Analysis)
- In Fixtures view, clicking the Everton vs. Newcastle fixture:
  - Navigates to: `/played-matches-summary?h2hHome=Everton&h2hAway=Newcastle&leagueId=<id>`.
  - `PlayedMatchesSummaryComponent` resolves H2H via `MatchService`, then `analyseFixture()` computes predictions locally via `PoissonService.calculatePredictions(teamA, teamB, h2hData, {})`.
  - No network call is made to `/api/match-analysis/analyze` for this action.

## Implications for Enhancements
- Planned server‑side enhancements (Dixon‑Coles adjustments, lambda calibration, ML wrapper) applied only to the backend `MatchAnalysisService` will not automatically affect the Fixtures Analysis tab because it uses a separate client‑side Poisson path.
- Options:
  - Keep client‑side Poisson for responsiveness, but optionally fetch backend‑computed probabilities for accuracy when available.
  - Or rewire Fixtures Analysis to call `/api/match-analysis/analyze` (via `MatchAnalysisService` in the frontend) to benefit from backend improvements.

## Appendix: Code References
- Backend:
  - Controller endpoint: `backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java`
    - `@PostMapping("/analyze")`
    - `public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req)`
    - Calls `matchAnalysisService.analyzeDeterministic(..., seasonId, ...)`.
  - Service: `backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java`
    - `analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId, Long seasonId, String leagueName, String homeTeamName, String awayTeamName, boolean refresh)`
- Frontend:
  - Fixtures button: `frontend/src/app/pages/fixtures.component.ts` → `openAnalysis(f)` navigates to `/played-matches-summary` with `h2hHome`, `h2hAway`, `leagueId`.
  - Played Matches (Fixtures Analysis view): `frontend/src/app/pages/played-matches-summary.component.ts` → `analyseFixture()` calls `PoissonService.calculatePredictions(...)`.
  - Client Poisson: `frontend/src/app/services/poisson.service.ts` (computes W/D/L, BTTS, Over 1.5/2.5/3.5, lambdas, top correct scores).
  - Separate Match Analysis API client: `frontend/src/app/services/match-analysis.service.ts` → `POST /api/match-analysis/analyze` (not used by Fixtures Analysis).

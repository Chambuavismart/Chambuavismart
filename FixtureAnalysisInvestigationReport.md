# Fixture Analysis Investigation Report

Date: 2025-09-16

Scope: Investigate the Fixture Analysis feature in ChambuaViSmart, explain end-to-end flow, detail the computation/analysis mechanisms, assess reliability, and recommend non-code tweaks to improve prediction quality. No code changes were made.

---

## 1) End-to-End Flow Overview

This section describes how a user’s interaction in the Fixture Analysis tab translates into backend queries and final outputs (stats, Last 5, H2H history, predictions).

### 1.1 Frontend components and UX
- Primary component: PlayedMatchesSummaryComponent (frontend/src/app/pages/played-matches-summary.component.ts)
  - Shows total matches KPI, team search, H2H search with pair suggestions, and the “Analyse this fixture” flow.
  - Team search (overall stats):
    - User types a team name; suggestions appear from TeamService. Selecting a team triggers backend calls to fetch overall breakdown.
  - H2H search:
    - Debounced query calls the backend H2H suggestions endpoint and displays candidate pairs. Two clickable orientations are offered (A vs B and B vs A).
    - Once a pair is chosen, the component resolves team IDs and season context, then fetches:
      - Oriented H2H matches list
      - H2H “Last 5” form blocks for both teams (season-scoped)
  - Analyse button:
    - When a specific fixture is selected (with league + season resolution), clicking “Analyse this fixture” triggers a combined analysis request to the backend analyzer. Returned data include: Win/Draw/Loss probabilities, BTTS, Over 2.5, expected goals (xG), H2H summary, and advisories.

### 1.2 Backend endpoints and their roles (key ones)
- MatchController (/api/matches)
  - /played/team/by-name/breakdown
    - Returns overall counts across the entire dataset for a team name: total, wins, draws, losses, BTTS, Over 2.5, Over 1.5.
  - /h2h/suggest
    - Suggests pairs of teams that have actually played before, based on a substring query.
  - /h2h/matches
    - Returns oriented H2H history, prioritizing ID-based lookups across all seasons; falls back to name-based queries via H2HService. Supports a limit.
  - /season-id
    - Resolves seasonId from leagueId + seasonName, with fallback to latest season.
  - /h2h/form
    - Returns the two teams’ last-5 form tiles, season-scoped via FormGuideService. Accepts multiple input modes:
      1) Preferred: homeId, awayId, seasonId
      2) Name-based path with leagueId + seasonName, with optional autoSeason fallback to the latest season that actually has played matches.
- MatchAnalysisController (/api/match-analysis)
  - POST /analyze
    - Unified analyzer. Requires leagueId and seasonId; accepts either team IDs or names (with server-side best-effort ID resolution via teamRepository and teamAliasRepository). Produces deterministic analysis output via MatchAnalysisService.

### 1.3 Data flow and resolution logic
- Team name to ID resolution (multiple layers):
  - League-scoped normalized name exact match
  - Alias repository fallback
  - League-scoped “contains” fallback
  - For H2HService (non-league scoped paths), there is a global name/alias lookup that collects all candidate IDs to search by team ID sets across leagues if possible.
- Season scoping:
  - For last-5 form: uses FormGuideService scoped to a particular season (either chosen or auto-resolved). This ensures current-season “Last 5” tiles.
  - For oriented H2H matches: by design, the oriented history endpoint prioritizes all seasons (orientation preserved). SeasonId is ignored for the oriented history itself; it is used elsewhere for form/league-table context.
- Global fallback for Last 5:
  - When the user provides names plus leagueId/seasonName and the exact season isn’t found, the system can (if autoSeason=true) fall back to the latest season with played matches for that league.
- Prediction trigger:
  - In the UI, after selecting the teams and having a valid leagueId and seasonId, clicking “Analyse this fixture” calls POST /api/match-analysis/analyze. The backend computes probabilities and returns a comprehensive response including form/H2H summaries.

---

## 2) Analysis Mechanisms

This section breaks down how the key sections are computed, based on the current implementation.

### 2.1 Overall stats (Wins/Draws/Losses, BTTS, Over 1.5/2.5)
- Endpoint: GET /api/matches/played/team/by-name/breakdown
- Data source:
  - MatchRepository aggregation methods over all played matches in the database where the given team name appears (no season restriction for this overall profile).
- Aggregations:
  - Wins, draws, losses, BTTS counts, Over 2.5 and Over 1.5 counts.
  - Safety check: if wins + draws + losses != total (e.g., data anomalies), losses are recomputed as total − wins − draws.
- Scope: Entire database (cross-season, cross-league) for the named team’s played matches. This is a “global” team profile, not restricted to the selected H2H pair.

### 2.2 Last 5 form (recent matches, streak, win rate, points)
- Where computed: MatchController -> /h2h/form using FormGuideService
- Scoping:
  - Strictly season-scoped. The controller resolves seasonId as described above.
  - Limit defaults to 5 but can be up to 10.
- Computed fields per team (see buildTeamResponseById):
  - lastResults: sequence of W/D/L capped by the limit (usually 5)
  - currentStreak: computed from the start of the list (e.g., “3W”) for the recent run
  - winRate: percent over the “Last N” window
  - points: wins*3 + draws over the window
  - ppgSeries: progressive points-per-game over recent matches (descending chronological order)
- Fallbacks:
  - If names are used and the season name doesn’t match, optional autoSeason attempts to pick the latest season with played matches. Without any valid season, empty results are returned.

### 2.3 H2H history (oriented and any orientation)
- Oriented history: GET /api/matches/h2h/matches
  - Resolution strategy:
    1) If homeId and awayId are available, use matchRepository.findH2HByTeamIds(homeId, awayId) across all seasons (preferred path, orientation preserved).
    2) If that fails or IDs are missing, resolve names to IDs using H2HService:
       - resolveTeamSafely and resolveAllTeamIds gather candidate IDs based on global name/alias matches.
       - Query by team ID sets across leagues if available, else fallback to name-based exact/fuzzy queries (played-only, past or auto-corrected dates).
  - Output includes year, date, home name, away name, score, and season label (if available).
- Any-orientation count: GET /api/matches/h2h/count-any-orientation
  - Returns the total count of H2H matches regardless of home/away orientation for two names.

### 2.4 Predictions and probabilistic model
- Entry point: POST /api/match-analysis/analyze (MatchAnalysisController -> MatchAnalysisService.analyzeDeterministic)
- Key inputs:
  - leagueId and seasonId are required to anchor the analysis to a season context.
  - Team IDs or names (with best-effort resolution when only names are provided).
- Computation outline (MatchAnalysisService):
  1) Base Win/Draw/Loss from form and weighted splits
     - Fetch FormGuide rows for the league and season via FormGuideService (Scope.OVERALL, LAST N DEFAULT_FORM_LIMIT).
     - For each team, pick PPG for home/away split if enough split matches; otherwise fallback to overall PPG.
     - Convert the two PPG figures to a W/D/L distribution by scaling the home and away to 75% of the probability mass (draw gets the remainder; clamp and normalize safeguards applied).
     - Parallel computation for BTTS% and Over 2.5% by averaging the effective split percentages from each team with fallbacks to overall percentages when split data are thin.
  2) H2H blending (recency-weighted)
     - Retrieve H2H matches within the relevant league family or league, optionally season-scoped first; if missing, fallback to broader lookups including name/alias sets.
     - Consider up to DEFAULT_H2H_LIMIT most recent matches with recency weights w = 1/(1+i).
     - Convert weighted W/D/L points to H2H PPGs, then to a W/D/L distribution using the same 75% band rule; compute H2H BTTS% and Over 2.5%.
     - Blend factor alpha scales up to 0.5 (i.e., max 50% influence from H2H at the window cap). Final W/D/L, BTTS, Over 2.5 are blended and normalized.
  3) League-table strength adjustment
     - Compute normalized ranks for both teams from the season’s league table. Derive a delta and shift up to 10 points between home and away, preserving draw where possible; small deltas may marginally increase draw.
  4) Expected Goals (xG)
     - Compute xG per team from weighted split Goals For (attack) and Goals Against (defense) with per-team fallbacks to overall weighted averages when splits are insufficient.
     - xG is the mean of the relevant attack and opponent defense numbers, clamped to [0.3, 3.0] and rounded to 2 decimals.
  5) Output assembly
     - WinProbabilities {home, draw, away}
     - BTTS percentage
     - Over 2.5 percentage
     - xG {home, away}
     - Confidence (currently 60–80 band; pseudo-random but deterministic per fixture seed)
     - Advice text based on thresholds
     - Form and H2H summaries (window size, PPG, BTTS, Over 2.5, compact recent list)
  6) Insights and goal differential (feature flag)
     - If predictiveH2HPhase1 is enabled, MatchAnalysisService attaches a GoalDifferentialSummary from H2HService.computeGoalDifferentialByNames(home, away) and a narrative insights text from H2HService.generateInsightsText.
- Poisson usage for score probabilities
  - There is an internal Poisson implementation in MatchController (poisson(), factorial()) used by verifyCorrectScores() which suggests probable scorelines for a given pairing, separate from the unified analyzer. The analyzer itself (MatchAnalysisService) does not directly compute a full Poisson score matrix in the current code; instead, it returns W/D/L, BTTS, Over 2.5, and xG derived from form and split stats with H2H and league adjustments.

---

## 3) Reliability Assessment

### 3.1 Strengths
- Multi-signal blending:
  - Uses weighted-split form (home/away) PPG and percentages as primary inputs.
  - Augments with recency-weighted H2H up to 50% influence, improving signal when credible H2H samples exist.
  - Adjusts for relative league position/strength to offset form/H2H biases.
- Deterministic behavior:
  - A deterministic seed ensures consistent “confidence” ranges and other seeded values for the same fixture inputs.
- Season scoping discipline:
  - Last-5 tiles are strictly season-scoped, with auto-season fallback to “latest with played” to avoid empty UIs.
- Defensive coding and fallbacks:
  - Extensive null/empty checks and normalization/clamping prevent pathological outputs.
  - Name/alias/team-set resolution minimizes failure to locate teams across data sources.

### 3.2 Limitations
- Small-sample H2H volatility:
  - Even with a capped 50% blend, small H2H windows (e.g., 1–3 matches) can exert notable influence; weighted scheme helps but variance remains.
- Cross-league historical bias:
  - Global-oriented H2H queries may include cross-league matches or older seasons that do not reflect current team quality.
- No explicit incorporation of exogenous factors:
  - Injuries, suspensions, schedule congestion, travel, and weather are not modeled.
- BTTS/Over thresholds from split percentages:
  - A simple averaging of team split percentages ignores matchup interactions beyond league-table adjustment and H2H blending.
- xG as split-average mean:
  - Using the mean of attack GF and opponent defense GA is a reasonable first-order approximation but does not model unit-strength interaction (attack vs defense ratings) nor pace effects.
- Confidence score currently narrow and seeded:
  - 60–80 range with seeded pseudo-randomness may not reflect actual confidence dictated by sample sizes or variance.

### 3.3 Accuracy indicators and backtesting
- There is no explicit in-code backtesting harness or accuracy metrics reported in responses.
- Some tests (MatchAnalysisService* tests) validate internal arithmetic (blending, adjustments, thresholds) but do not evaluate prediction accuracy vs. holdout outcomes.
- Sample size heuristics exist (e.g., requiring 2+ split matches to use split metrics; H2H blending alpha scaling by window), but confidence intervals or uncertainty bands are not exposed.

### 3.4 Edge cases
- New competitions/seasons with few matches:
  - Form rows may be sparse; code falls back to overall PPG and 40/20/40 defaults.
- Teams with no H2H:
  - Analyzer simply skips H2H blending; oriented history endpoint will still attempt fuzzy/name fallbacks but may return empty.
- Season mismatch / invalid seasonName:
  - Auto-season can recover; otherwise last-5 tiles return empty, which the UI should handle.
- Goal data missing (null goals):
  - H2HService skips such matches for GD and insights computations.

---

## 4) Potential Tweaks and Improvements (no code changes now)

Below are conceptual improvements to enhance reliability and prediction quality, with brief feasibility notes.

### 4.1 Model improvements
- Calibrated Poisson for scorelines and derivative markets
  - Use xG as λ parameters to a bivariate goal model (with home-advantage factor) to produce a full score matrix, then derive W/D/L, BTTS, Over 1.5/2.5/3.5 with uncertainty estimates.
  - Feasibility: Medium. Inputs already include xG; needs correlation handling and validation.
- Dynamic weighting by recency and opponent strength
  - Weight form splits not only by recency but also by opponent strength (e.g., opponent table rank or rating) to reduce inflation from soft schedules.
  - Feasibility: Medium; table service exists; add a weighting transform before computing PPG/percentages.
- H2H prior as weak signal
  - Cap H2H alpha more strictly for N<3 and use it primarily to adjust BTTS/Over markets or specific matchup quirks (e.g., persistent low totals), not baseline W/D/L.
  - Feasibility: Low-to-Medium; alpha curve revision and rules around N.
- Home/away advantage parameterization
  - Introduce explicit home-advantage term when translating PPG to win probabilities and when computing xG.
  - Feasibility: Medium; requires estimation per league and season.
- Shrinkage toward league averages for small samples
  - When split matches are <k, shrink team-specific rates toward league-average rates to reduce variance.
  - Feasibility: Medium; leagueTableService and FormGuideService can provide contextual league averages.

### 4.2 Data and thresholds
- Minimum sample thresholds
  - Require a minimum of k split matches (e.g., k=4) before using split-specific BTTS/Over rates; otherwise revert to overall or league average with clear UI labels.
- Multi-season aggregation for Last-5 fallback (optional toggle)
  - When current season has <5 matches for a team early in season, optionally allow a multi-season blended last-5 with decay, labeled as such.
- Confidence scoring overhaul
  - Derive confidence from effective sample sizes, agreement between form- and H2H-derived probabilities, and spread in the score matrix.

### 4.3 UI/UX enhancements
- Surface uncertainty and sample context
  - Display badges such as “Low sample size” or “H2H N=2” and show confidence bands for markets.
- Explainability tooltips
  - Add “How we computed this” tooltips referencing form split counts, league adjustment magnitude, and H2H weight used.
- Visual H2H blending indicator
  - Show a small bar indicating the alpha blend used so users see the impact of H2H.

### 4.4 Feasibility summary
- Impact vs. effort (indicative):
  - Confidence scoring based on sample sizes: High impact, Low/Medium effort.
  - H2H alpha curve tightening for small N: Medium impact, Low effort.
  - Home/away explicit parameter: Medium/High impact, Medium effort.
  - Calibrated Poisson score matrix: High impact, Medium/High effort (needs validation & performance considerations).

---

## 5) Open Questions and Assumptions
- Poisson in the unified analyzer:
  - Current analyzer does not produce a full Poisson score matrix; Poisson utilities exist in MatchController.verifyCorrectScores(). Assumption: the main UI relies on MatchAnalysisService outputs for W/D/L, BTTS, Over 2.5, and xG.
- DEFAULT_FORM_LIMIT and DEFAULT_H2H_LIMIT values
  - Assumed to be moderate (e.g., 5–10 for form, ~10–20 for H2H) based on code patterns; exact constants should be confirmed for tuning alpha and recency weights.
- League-family resolution
  - Code suggests a capability to aggregate across league IDs that share name/country (league family). Confirmation needed for how many IDs are included and how historical changes are handled.
- Data quality checks
  - Assumes data normalization (AdminDiagnosticsController normalize/anomalies endpoints exist). Clarify current frequency and scope of normalization runs.
- Feature flag predictiveH2HPhase1
  - Assumption: Enabled by default in production. If disabled, GD/insights texts are suppressed or reduced to a generic message.

---

## 6) Summary
The Fixture Analysis feature integrates season-scoped form (with home/away splits), recency-weighted H2H blending, and league-table strength adjustments to generate probabilities for W/D/L, BTTS, Over 2.5, and expected goals. Oriented H2H history is fetched across all seasons, while “Last 5” tiles are season-scoped with robust fallbacks. The design emphasizes defensive handling and name/alias resolution to limit empty states.

To improve reliability and user trust, we suggest tightening H2H influence for small samples, adopting shrinkage to league averages for split metrics, upgrading the confidence score to reflect sample and consensus, and optionally incorporating a calibrated Poisson score matrix to support richer markets and uncertainty visualization. No code changes were made in this investigation.

# Post-Harmonization Analysis: Fixtures Analysis vs. Match Analysis

## Executive Summary
- Both Fixtures Analysis and Match Analysis now call the same backend endpoint: POST /api/match-analysis/analyze via MatchAnalysisController → MatchAnalysisService.analyzeDeterministic(...).
- They share the same response DTO (MatchAnalysisResponse), producing aligned fields: winProbabilities (W/D/W), bttsProbability, over25Probability, expectedGoals, formSummary, h2hSummary, confidence, and advice.
- They remain distinct in purpose and the inputs/weights used internally:
  - Fixtures Analysis: simpler Poisson-style assumptions using league-level/H2H goal averages and overall team stats for fast, broad coverage across lists of fixtures.
  - Match Analysis: single-match deep dive using weighted PPG (home/away splits) and blended recent-form + H2H metrics (e.g., last 6 H2H, recency weights), adding a confidence/advice overlay.
- User preference drivers: speed and broad overview (Fixtures) vs. depth and context-sensitive accuracy (Match Analysis).

## Shared and Distinct Aspects

### Shared
- Backend endpoint: /api/match-analysis/analyze (MatchAnalysisController → MatchAnalysisService).
- Common DTO: MatchAnalysisResponse with:
  - winProbabilities: {homeWin, draw, awayWin} that sum to 100.
  - bttsProbability, over25Probability (percent integers).
  - expectedGoals (xG-ish expected goals per team/total where provided), formSummary, h2hSummary.
  - confidence and advice strings.
- Data sources: recent form guide (FormGuideService), H2H data (MatchRepository), league/season context (SeasonService, LeagueTableService). Caching via MatchAnalysisResultRepository.
- UX outputs: W/D/W bars, BTTS/Over% gauges, xG indicators, short textual insights.

### Distinct
- Functional focus:
  - Fixtures Analysis (analysisType = "fixtures"): optimized for scanning many matches; uses simpler Poisson pipeline driven by H2H/league goal averages and overall team stats. Less granular weighting, prioritizes consistency and speed.
  - Match Analysis (analysisType = "match"): optimized for one match; uses weighted PPG (home/away splits), blended recent form + H2H (recency weighting, shorter H2H window like last ~6), and adds confidence + advice tuned to that blend.
- UI presentation:
  - Fixtures: list/table cards with compact metrics for many fixtures; quick bars/percentages; broader H2H history panel.
  - Match: a single-page deep card with detailed form/H2H summaries, confidence indicator, and compact H2H list focused on recent relevance.
- Analysis logic knobs (illustrative):
  - H2H window: Fixtures tends to use longer or average H2H goal rates; Match uses a shorter, recent H2H subset (e.g., last ~6) with recency weights.
  - Form blending: Fixtures leans on overall team stats; Match applies weighted PPG (home vs. away context) and recent-form emphasis.
  - Normalization: Both normalize W/D/W via normalizeTriplet(...) to sum 100, but inputs differ due to weighting choices.

## User Preferences
- Prefer Fixtures Analysis when:
  - You need a fast, comparable snapshot across many upcoming fixtures.
  - You want consistent, league-average-friendly predictions (lower variance), suitable for shortlisting.
  - You value seeing broader H2H context and straightforward BTTS/Over signals.
- Prefer Match Analysis when:
  - You are focusing on a single game and want context-sensitive adjustments (home/away PPG splits, recent-form tilt, recency-weighted H2H).
  - You need a confidence measure and prescriptive advice grounded in recent performance blend.
  - You expect asymmetries (e.g., strong home trend vs. weak away) to be explicitly captured.

## Inputs and Output Differences
- Inputs that differentiate behavior:
  - analysisType in MatchAnalysisRequest: "fixtures" vs. "match" determines which blending/weighting path the service takes.
  - H2H window/weights: shorter, recent-weighted for Match; broader/averaged for Fixtures.
  - Form weights: Match emphasizes weighted PPG (home for home team, away for away team) and recent matches; Fixtures uses overall team stats and simpler averages.
- Why outputs differ or align:
  - When teams’ recent PPG splits and recent H2H strongly diverge from long-run averages, Match Analysis will skew W/D/W and confidence more than Fixtures.
  - When both teams have steady form and H2H mirrors league averages, outputs from both features tend to align (similar W/D/W, BTTS/Over, and xG).

### Example: Lyon vs. Angers (2025/2026, Ligue 1)
Assumptions for illustration (based on harmonized logic; exact numbers depend on repository data on the day):
- Scenario: Lyon showing strong recent home PPG and Angers weaker recent away PPG; last ~6 H2H slightly favors Lyon with moderate goals.
- Expected outputs:
  - Fixtures Analysis (simpler Poisson, broader averages):
    - W/D/W: Slight-to-moderate tilt to Lyon, e.g., HomeWin modestly higher than AwayWin, Draw around mid-20s after normalization.
    - BTTS/Over: Percentages track league and long-run H2H goal rates; moderate BTTS, moderate Over 2.5.
    - xG: Near the averaged goal expectations from league/H2H rates.
  - Match Analysis (weighted/blended with PPG splits and recent H2H):
    - W/D/W: Higher Lyon HomeWin% than Fixtures due to strong home PPG and recency weights; AwayWin lower; Draw adjusted to maintain sum=100.
    - BTTS/Over: If recent form shows Lyon scoring efficiently and Angers creating less, Over may edge up while BTTS may slightly drop versus Fixtures.
    - xG: Slightly higher expected goals for Lyon relative to the Fixtures baseline, reflecting recent attacking form.
- Interpretation: A user comparing both would likely see Match Analysis push a stronger Lyon lean (higher HomeWin%) and a clearer advice/confidence message, while Fixtures provides a steadier, average-based view suitable for quick scanning.

## Appendix
- Code references:
  - Controller: backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java (POST /api/match-analysis/analyze)
  - Service: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java (analyzeDeterministic(...), normalizeTriplet(...))
  - Frontend routing/components:
    - Fixtures: frontend/src/app/pages/fixtures (FixturesComponent → PlayedMatchesSummaryComponent), calls endpoint with analysisType: "fixtures"
    - Match Analysis: frontend/src/app/pages/match-analysis (MatchAnalysisComponent), calls endpoint with analysisType: "match"
  - DTO: backend/src/main/java/com/chambua/vismart/dto/MatchAnalysisResponse.java
- Assumptions:
  - Sufficient recent matches in MatchRepository to compute form and H2H summaries.
  - The Poisson pipeline is deterministic, with normalization ensuring W/D/W sums to 100.
  - No Dixon-Coles, lambda calibration, or ML wrapper applied in this harmonized stage.

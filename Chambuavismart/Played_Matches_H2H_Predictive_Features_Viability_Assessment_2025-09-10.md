# Played Matches / Head-to-Head – Predictive-Support Features Viability Assessment (2025-09-10)

Author: Junie (Autonomous Programmer)

This assessment evaluates the feasibility of introducing predictive-support features in the Played Matches / Head-to-Head (H2H) area, leveraging the current backend and frontend capabilities of the ChambuaViSmart project.

Scope of requested features:
1) Historical Context (H2H & Goal Differentials)
2) Form & Momentum (Recent Results, Streaks, League Position)
3) Momentum/Trend Visualization (streak indicator or mini chart)
4) Predictive Impact Tagging (High/Medium/Low impact labeling)
5) Combined Insights Summary (auto-generated match insight panel)

---

## Executive Summary
- Short-term viable (low-to-moderate effort):
  - Historical Context (H2H & Goal Differentials)
  - Form & Momentum (Recent Results, Streaks)
  - Momentum/Trend Visualization (simple streak indicators / sparkline)
  - Combined Insights Summary (rule-based text panel)
- Longer-term or gated by dependencies:
  - League Position (needs stable league table endpoint + season scoping)
  - Predictive Impact Tagging (needs weighting clarity + governance, avoid overclaiming)

The codebase already includes H2H services, recent results breakdowns, team suggestion/search and H2H endpoints, plus tests and prior investigations. These can be leveraged to deliver a phased rollout rapidly, starting with non-controversial visuals and summaries and deferring algorithmic “impact” labels until governance and data quality checks are complete.

---

## What infrastructure already exists and can be leveraged

Backend (Java / Spring):
- H2HService and repository queries
  - H2HService.java already provides methods to retrieve H2H by team names/IDs.
  - MatchRepository exposes findH2HByTeamIds and findH2HByTeamIdSetsAllLeagues (recent work to support cross-season H2H per investigations).
  - MatchAnalysisResponse includes H2HSummary and H2HMatchItem for UI rendering: lastN, ppgHome/ppgAway, BTTS%, Over2.5%, match rows.
  - Tests: MatchAnalysisServiceH2HTest cover scenarios like standard H2H and fallback-to-form-only.
  - Several investigations (H2H_Disappearance_Investigation_Report_2025-09-06.md; H2H_Fallback_Contradiction_Root_Cause_Analysis_2025-09-06.md) document gaps and intended behavior (cross-season H2H, blending logic).
- Team repository/services
  - Team suggestions, team counts, and results breakdown (wins/draws/losses) for "Played Matches" are already wired (see TeamController, TeamRepository, and frontend bindings).
- MatchController endpoints
  - H2H suggestions (search), H2H matches by team names, and structured DTOs used by the frontend.

Frontend (Angular):
- Played Matches page has search, team profile, and H2H search and rendering (played-matches-summary.component.ts).
- Displays results breakdown and handles H2H list rendering paths.
- MatchService includes TeamBreakdownDto, H2HSuggestion, H2HMatchDto interfaces aligning with backend.
- Visual placeholders and layout exist for KPI cards, breakdowns, and H2H sections; adding indicators/mini charts is incremental.

Artifacts and documentation:
- Multiple MD reports in repo already analyze H2H path, cross-season requirements, and data separation by season/league — this shortens design time and clarifies decisions.

---

## Feature-by-feature viability

1) Historical Context (H2H & Goal Differentials)
- Short-term viability: High.
- What we can ship quickly:
  - Show last N H2H matches with scores and a computed goal differential summary (e.g., aggregate GD, avg GD per match).
  - Use existing H2HSummary.matches for display; compute GD either on backend (preferred for consistency) or frontend (if fields are available in H2HMatchDto).
- Existing leverage:
  - H2H services, DTOs, and frontend H2H rendering pipeline already exist.
- Dependencies/gaps:
  - Ensure cross-season H2H is consistently returned (recent fixes indicate progress, but verify repository queries and controller wiring across all leagues/seasons).
  - Confirm score fields or include them in DTO if missing for GD computation.

2) Form & Momentum (Recent Results, Streaks, League Position)
- Short-term viability: Partial (Recent Results, Streaks: Yes; League Position: Likely Medium-term).
- What we can ship quickly:
  - Recent Results and simple streak detection from existing team breakdowns or recent match feeds.
  - A lightweight streak computation: last 5 matches per team (W/D/L) with count of consecutive results.
- Existing leverage:
  - Played Matches page shows wins/draws/losses; Match endpoints already return recent matches for H2H.
- Dependencies/gaps:
  - League Position requires a reliable league table endpoint aligned to selected league and season (post season-separation). If this endpoint is not yet finalized, we should defer.
  - Need a standard “recent window” configuration (e.g., last 5 or last 10) used consistently in UI and backend.

3) Momentum/Trend Visualization (streak indicator or mini chart)
- Short-term viability: High for simple visuals.
- What we can ship quickly:
  - Compact W/D/L badges for last N matches.
  - A tiny sparkline-like bar/line showing points per game over last N.
- Existing leverage:
  - Angular component already has card sections for displaying stats; adding a small canvas/SVG/sparkline component is straightforward.
- Dependencies/gaps:
  - Decide whether to compute trend series on backend (stable) vs. frontend (faster to iterate). Starting frontend-first is acceptable for Phase 1, then backfill backend support.

4) Predictive Impact Tagging (High/Medium/Low impact labeling)
- Short-term viability: Medium to Low (governance and methodology risk).
- Notes:
  - Tagging requires a clear, reviewed methodology to map signals (form, H2H, league position) to “impact levels” without overstating predictive power.
  - Docs note that current MatchAnalysis may still be placeholder/randomized in areas (see docs/weighting-audit.md), implying we should avoid labeling that suggests rigorous predictive output until the methodology is hardened.
- Dependencies/gaps:
  - Define thresholds/weights and document them.
  - Add explainability strings (“Because recent PPG diff > X and H2H favors home, impact: Medium”).
  - Could start with rule-of-thumb heuristics and mark as beta/experimental.

5) Combined Insights Summary (auto-generated match insight panel)
- Short-term viability: High (rule-based, explainable summaries).
- What we can ship quickly:
  - Generate a concise paragraph combining: recent form comparison, H2H tilt, and streaks, with caveats for sample size.
- Existing leverage:
  - We have all raw ingredients: recent results, H2H lastN, BTTS/Over2.5, etc.
- Dependencies/gaps:
  - Ensure consistent cross-season H2H retrieval.
  - Add safety guards for small samples and contradictory signals.

---

## Dependencies and gaps to address
- Cross-season H2H consistency
  - Confirm MatchRepository queries and H2HService paths always gather cross-season matches for the same league family (per the recent investigations proposing a league family key).
- Score availability for GD
  - Ensure DTOs include goals (homeGoals, awayGoals) or a parseable score string to compute GD.
- Recent form window
  - Establish a config-driven lastN (e.g., 5) used across components.
- League table endpoint
  - To display League Position accurately per season, confirm availability and shape of table endpoint; otherwise, defer League Position UI until ready.
- Explainability and governance for “impact” labels
  - Document thresholds; label feature as beta initially to avoid misinterpretation.

---

## Suggested phased rollout order

Phase 1 – Visualization & Context (Low Risk, High Value)
- Historical Context: show last N H2H with goal differential summary.
- Form & Momentum: recent results array and W/D/L streak badges (last 5).
- Momentum/Trend Visualization: simple sparkline for recent PPG.
- Combined Insights Summary: rule-based explanatory paragraph with caveats.

Phase 2 – Strengthen Data Foundations
- Finalize league family resolution for cross-season H2H and verify across major leagues.
- Standardize config for windows (H2H lastN, form lastN) and surface in backend DTOs.
- Expose minimal series endpoints if moving trend computations to backend.

Phase 3 – League Position and Comparative Context
- Integrate League Position once league table endpoint is confirmed season-scoped and accurate.
- Add comparative badges (e.g., position delta last 5 matchdays) if data available.

Phase 4 – Predictive Impact Tagging (Beta)
- Introduce High/Medium/Low impact tags with fully documented heuristics.
- Add explainability tooltips: which signals drove the tag, sample size, and confidence.
- Monitor feedback and adjust thresholds.

---

## Short-term vs Long-term summary
- Short-term (can begin immediately):
  - H2H last N with GD summary
  - Recent results & streaks (last 5)
  - Simple trend visuals (sparkline/PPG mini-chart)
  - Combined Insights Summary (rule-based)
- Long-term (after dependencies resolved):
  - League Position integration
  - Predictive Impact Tagging with governance and explainability

---

## Implementation notes (non-coding guidance)
- Keep all new UI elements optional behind feature flags to mitigate risk during rollout.
- For sample-size caveats, display warnings when lastN < 3 for H2H.
- Prefer backend computation for consistent metrics but prototype in frontend to accelerate discovery.
- Reuse existing DTOs (MatchAnalysisResponse.H2HSummary, H2HMatchItem) and extend minimally if needed (e.g., include numeric goals fields).
- Maintain accessibility: indicators should include text alternatives.

---

## Decision points for the team
- Confirm whether we standardize on last 5 matches for form and last 6 for H2H (as seen in tests), or align to a single window.
- Approve deferring League Position until the league table endpoint is production-ready.
- Approve labeling “Predictive Impact” as Beta in Phase 4 with published heuristics.

---

## Conclusion
The system already contains most of the plumbing to deliver meaningful, low-risk predictive-support features in the H2H/Played Matches section. A phased approach that starts with contextual visualization and rule-based summaries is recommended for rapid value, while deferring league position and impact tagging until supporting data and methodology are mature.

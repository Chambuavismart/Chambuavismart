# Streak Insights & Fixtures Analysis — Longest‑Ever Streaks Implementation Report

Date: 2025-09-29
Owner: Junie (JetBrains Autonomous Programmer)

## Executive Summary
We have standardized streak logic across two key areas of Chambuavismart:

- Streak Insights: The summary percentages (Wins/Draws/Losses, Over 1.5, Over 2.5, BTTS) are now computed using each team’s overall longest‑ever streak type across all played matches, not their current streak at the time of each match.
- Fixtures Analysis (played matches summary / H2H view): Team pill colors (Green/Orange/Red) now reflect the team’s longest‑ever streak type across all played matches, not the current streak. Current streaks are displayed for reference only.

This alignment ensures both sections label an upcoming fixture’s context (Same‑Streak vs Different‑Streak) in a consistent manner and explain outcomes using the same underlying longest‑ever classification.

---

## What Changed (High Level)

- Backend (TeamController.getStreakSummary):
  - Classification for historical matches and upcoming fixture context now uses each team’s overall longest‑ever streak type (W/D/L), computed from all played matches in the database.
  - Opponent’s longest‑ever type is cached per opponent name for efficiency.
  - The method still builds pre‑match maps internally, but classification ignores these and relies on overall longest‑ever types only.

- Frontend — Streak Insights (streak-insights.component.ts):
  - Table headers updated:
    - “Active Streak (pre-match)” -> “Current Streak (reference)”
    - “Longest To‑Date” -> “Longest Ever”
    - “Opponent Longest To‑Date” -> “Opponent Longest Ever”
  - Added an informational note above the summary cards clarifying that the percentages use longest‑ever streak types and current streaks are shown for reference.

- Frontend — Fixtures Analysis (played-matches-summary.component.ts):
  - Added a color legend above the H2H profiles:
    - Green = W longest‑ever
    - Orange = D longest‑ever
    - Red = L longest‑ever
    - Current streaks are reference only
  - Color computation for team “pills” is mapped strictly from longest‑ever streak type, independent of current streak length.
  - The existing local cache persists these colors for use elsewhere (e.g., Today view), preserving consistency.

---

## How Streak Insights Works Now

1. Data retrieval
   - Endpoint: GET /api/matches/streak-insights/by-team-name?name={team}
   - Returns a chronological timeline of played matches for the selected team with:
     - outcome (W/D/L)
     - current streak (reference only)
     - longest ever up to now (displayed as “Longest Ever” in the table)
     - opponent’s longest ever (displayed)

2. Summary computation
   - Endpoint: GET /api/teams/{teamId}/streak-summary
   - For each played match of the selected team, we classify the matchup using:
     - A = selected team’s overall longest‑ever type (W/D/L) across all recorded played matches.
     - B = opponent’s overall longest‑ever type across all recorded played matches (looked up and cached by opponent name).
   - If A == B, the match is counted in the “Same Streak Matches” bucket; otherwise in “Different Streak Matches”.
   - For each bucket we compute percentages:
     - Wins, Draws, Losses
     - Over 1.5 goals, Over 2.5 goals
     - BTTS
   - These percentages no longer depend on the pre‑match current streak or the pre‑match longest‑to‑date; they use longest‑ever types only.

3. Upcoming fixture context
   - The same longest‑ever types (A, B) are used to label the next upcoming fixture as:
     - same_streak if A == B
     - different_streak otherwise
   - The summary’s “Conclusion” text references this classification.

4. UI clarifications
   - Table shows “Current Streak (reference)” to indicate that the current streak is not used in the summary calculations.
   - An info banner above the summary cards reiterates that the percentages are based on longest‑ever streak types.

---

## How Fixtures Analysis (Played Matches Summary / H2H) Works Now

1. Team selection and H2H view
   - When two teams are selected, the H2H profiles show team “pills” colored strictly by each team’s longest‑ever streak type across all played matches:
     - W longest‑ever -> Green (#16a34a)
     - D longest‑ever -> Orange (#f59e0b)
     - L longest‑ever -> Red (#ef4444)
   - The current streak is displayed in the stats section but is purely informational.

2. Local cache behavior
   - The AnalysisColorCacheService stores the resolved pill color (mapped from longest‑ever type) and (optionally) the longest streak count for each team. This allows other parts of the app (like the Today fixtures view) to consistently show the same colors.
   - Some heuristics (e.g., double‑green flag, draw‑heavy D) leverage H2H data but do not affect the core color mapping, which remains driven by longest‑ever type only.

3. UI clarifications
   - A legend above the profiles explains the color mapping and that current streaks are for reference only, aligning the communication with Streak Insights.

---

## Consistency Between Sections

- Both Streak Insights and Fixtures Analysis reference the same conceptual definition: the team’s longest‑ever streak type across all played matches.
- The Streak Insights summary buckets (Same/Different Streak) use overall longest‑ever types for both teams; the Fixtures Analysis pill colors derive from the same longest‑ever types.
- Therefore, for any upcoming fixture, the “type of longest streak shown” by Fixtures Analysis (via pill color legend) will agree with the Streak Insights classification used to compute summary percentages and to label the fixture context.

---

## Endpoints and Key Modules

- Backend
  - TeamController.getStreakSummary (GET /api/teams/{teamId}/streak-summary)
    - Computes overall longest‑ever type for selected team and opponents
    - Classifies historical matches and determines upcoming fixture context using those types
  - MatchController.getStreakInsightsByTeamName (GET /api/matches/streak-insights/by-team-name)
    - Provides match timeline with current streak (reference) and longest‑ever values for display

- Frontend
  - streak-insights.component.ts
    - Updated table headers and added info banner
    - Calls /api/teams/{id}/streak-summary and renders percentages
  - played-matches-summary.component.ts
    - Adds legend and maps team pill color from longest‑ever type only
  - analysis-color-cache.service.ts
    - Persists computed colors and optional streak counts for reuse

---

## Edge Cases and Notes

- Teams with no played matches: Summary will be empty; UI shows no data available.
- Opponents with missing or unknown longest‑ever type: Such matches are skipped in the summary computation (cannot classify Same/Different without both A and B).
- Name normalization: Opponent lookup uses case‑insensitive name keys; if inconsistent naming exists in the dataset, the opponent’s longest‑ever type may not be found in rare cases.
- Current streak lengths: Still computed and displayed in both sections for reference but do not influence summary buckets or color assignment.

---

## How to Verify (Manual)

1. Pick a team with a known strong winning longest‑ever streak (e.g., longest W ≥ 6).
   - Open Streak Insights:
     - Confirm the table headers show “Current Streak (reference)” and “Longest Ever”.
     - Confirm the info banner states that percentages are computed from longest‑ever streak types.
     - Note the “Conclusion” labels the upcoming fixture as same/different streak based on longest‑ever types.
   - Open Fixtures Analysis (H2H view) for that team vs any opponent:
     - Confirm the team pill is Green (W longest‑ever) regardless of current streak.
     - Confirm the legend clarifies colors and reference‑only current streak.

2. For a draw‑heavy team (D longest‑ever):
   - Streak Insights should count matches into Same/Different using D as the team’s type.
   - Fixtures Analysis should show Orange pill.

3. For a losing‑heavy team (L longest‑ever):
   - Streak Insights should classify using L for that team.
   - Fixtures Analysis should show Red pill.

4. Cross‑check consistency:
   - When Streak Insights says the upcoming fixture is a same‑streak matchup, the two H2H profile pills should be the same color (both W, both D, or both L).

---

## Rationale

- Using overall longest‑ever streak types reduces noise from transient current streaks and better captures each team’s historical tendency.
- Aligning Streak Insights and Fixtures Analysis ensures a cohesive narrative and avoids contradictory signals across pages.

---

## Appendix: Color Mapping

- W longest‑ever → Green (#16a34a)
- D longest‑ever → Orange (#f59e0b)
- L longest‑ever → Red (#ef4444)

These colors are applied only to the team pills in Fixtures Analysis and any downstream views that reuse the cached values. They do not affect other chart or text colors.

---

If you want this report linked from README.md or surfaced in the UI (Docs section), let me know and I can add cross‑links or a navigation entry.
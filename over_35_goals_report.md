# Over 3.5 Goals Probability – Implementation Report

## Changes Made

- Frontend (Angular)
  - PoissonService (frontend/src/app/services/poisson.service.ts)
    - Added Over 3.5 Goals probability computation using the same adaptive Poisson grid used for correct scores (0..10 expanding up to 20 until cumulative mass ≥ 0.999).
    - Implemented a lightweight cache (lastGridStats) populated inside calculateCorrectScores to expose the raw grid and total mass for the subsequent computation of aggregates such as Over 3.5.
    - Computed P(total ≤ 3) by summing probabilities for cells where h + a ≤ 3 on the same grid, then Over 3.5 = 1 − (sumLe3 / total). Returned as percentage (0 decimals) in Predictions.over35.
    - Extended Predictions interface to include over35: number.
    - Added logs: prints adaptive maxGoals and totalSum for correct scores, and prints Over 3.5 with totalSum.
    - Kept BTTS, Over 1.5, Over 2.5 logic unchanged for backward compatibility.
    - Ensured isLimitedData remains: (h2h.length < 3) || (aAvg === leagueAvg) || (bAvg === leagueAvg). Added more context logs including counts and an H2H sample to diagnose incorrect warnings.
  - PlayedMatchesSummaryComponent (frontend/src/app/pages/played-matches-summary.component.ts)
    - Updated "Fixture Analysis Results" table to include a new row: Over 3.5 Goals, displayed with 0 decimal places.
    - Confirmed the warning uses predictions.isLimitedData and added no UI breaking changes.
  - Unit Tests (frontend/src/app/services/poisson.service.spec.ts)
    - Added a test ensuring Over 3.5 falls in a plausible 50–60% range for λ_home = 2.17, λ_away = 1.68 scenario and that Over 2.5 is roughly in the mid-70s. Also asserts over35 ≤ over25.

- Backend (Spring Boot)
  - MatchController (/api/matches/verify-correct-scores)
    - Extended the verification endpoint response to include over35 computed on the same 0..10 grid normalization used for returning top correct scores. A new trailing object {"over35": value} is appended to the list.

## Fix for Incorrect H2H Warning

- The frontend warning now strictly follows the requirement: it only shows if either:
  - h2hData.length < 3, or
  - the per-team H2H averages fell back to the league average (1.4), indicating insufficient usable H2H orientation mapping.
- Added diagnostic logs to help identify potential H2H parsing/matching issues (IDs vs names), including a small H2H sample dump and the number of matches counted per team (aAgg.matches, bAgg.matches).

## Mathematical Notes

- Poisson Model:
  - P(Home goals = h) = e^(−λ_home) λ_home^h / h!
  - P(Away goals = a) = e^(−λ_away) λ_away^a / a!
  - P(Score h–a) = P(Home goals = h) × P(Away goals = a)
- Over 3.5:
  - P(Over 3.5) = 1 − P(Total ≤ 3)
  - P(Total ≤ 3) is calculated by summing the grid probabilities for the terms where h + a ≤ 3.
  - Normalization: Division by the same grid total mass (totalSum) used for correct scores, guaranteeing consistency across all displayed percentages.

## Tests Performed

- Unit tests (frontend):
  - Correct scores grid expansion behavior validated (existing tests).
  - New test validates Over 3.5 for a high-scoring example (Fulham vs Leeds-like lambdas) to be in the expected range and consistent with Over 2.5.

- Backend build: Successful compile of Spring Boot module after changes.

- Manual reasoning checks:
  - Verified that over35 ≤ over25 and that over35 is materially lower but in the 50–60% band when over25 ~74% for lambda pair (2.17, 1.68), aligning with football expectations.

## New Suggestions

- Add Over 4.5 probability using the same adaptive grid cache, following the same pattern as Over 3.5.
- Expose a detailed diagnostics panel (behind a toggle) showing:
  - maxGoals used, totalSum, and mass outside the grid (1−totalSum).
  - A small table of P(total = 0..6) to help analysts cross-check market lines.
- Consider adding server-side parity for Over 1.5/2.5/3.5 in the verification endpoint for full E2E checks.
- Provide a tooltip next to the warning that explains exactly which condition triggered it (e.g., “H2H usable matches counted for Team A: 2”).

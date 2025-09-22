# Poisson Model Fix Report

Date: 2025-09-13

## Changes Made

- Frontend Poisson Service (frontend/src/app/services/poisson.service.ts)
  - Added ID-aware and case-insensitive matching for H2H events.
    - Introduced optional team and match ID fields (teamA.id/teamB.id, homeTeamId/awayTeamId) with priority matching by ID and fallback to normalized names.
    - Implemented name normalization (lowercase + trim) and soft contains to handle aliases like "SV Darmstadt 98" vs "Darmstadt".
  - Hardened goal extraction from multiple fields (homeGoals/awayGoals, home_score/away_score, and result string parsing) with null-safe handling.
  - Fixed Over 2.5 probability formula: now uses 1 - (P00 + P10 + P01 + P20 + P02 + P11).
  - Expanded score grid from 0..5 to 0..10 and renormalized Win/Draw/Loss to ensure sums to 100%.
  - Added home advantage and away disadvantage modifiers (defaults: 1.15 and 0.95 respectively) applied to λA and λB.
  - Added debug logging of inputs and computed values.
  - Extended Predictions interface with optional usedFallback flag to inform UI when league averages were used.

- Fixture Analysis UI (frontend/src/app/pages/played-matches-summary.component.ts)
  - Passes team IDs (when available) to PoissonService.
  - Stores resolved H2H team IDs (h2hHomeId/h2hAwayId) and resets them on new selection.
  - Displays a user-facing warning if predictions used fallback averages: "Limited H2H data; using league averages." Also logs a detailed console warning.

## Tests Performed

- Unit-level sanity checks (manual via console):
  - Name normalization: Mocked cases where h2h contained "SV Darmstadt 98" and input team was "Darmstadt". Matching succeeded and goals were counted.
  - ID preference: When teamA.id/teamB.id matched homeTeamId/awayTeamId, matching occurred even if names differed in case/spacing.

- Probability calculations:
  - Over 2.5 with λA = 1.4, λB = 1.4 computed ~50–55% (previously ~65%).
  - Grid expansion 0..10 with renormalization: Win/Draw/Loss sum to 100%, asymmetric when λA ≠ λB.
  - Home advantage applied: with base aAvg=bAvg=1.4 and equal matches, λA > λB after modifiers.

- UI flow:
  - Analyse this fixture now produces asymmetric probabilities when H2H data exists.
  - Warning appears (alert + hint) when H2H data is scarce (e.g., <3 matches) or averages are zero, indicating fallback to league average.
  - Console logs show: inputs, h2h length, raw and final averages, λA, λB, and modifiers.

## Remaining Suggestions

- Backend: Include team IDs in H2HMatchDto (homeTeamId, awayTeamId) so frontend can avoid name-based fallback entirely.
- Config: Expose homeAdvantageFactor and awayDisadvantageFactor via ConfigService or environment for league-specific tuning.
- UI: Replace alert with a proper toast/notification component for a better UX.
- Stats: Consider incorporating recent form weighting or defensive strength (goals against) to refine λ beyond simple averages.
- Tests: Add automated unit tests for PoissonService to validate probability mass and key thresholds (e.g., Over 2.5 for common λ values).

# Correct Scores Feature – Implementation Report

Date: 2025-09-13

## Changes Made

### Frontend (Angular)

1. PoissonService (frontend/src/app/services/poisson.service.ts)
   - Added interface CorrectScorePrediction and extended Predictions to include:
     - correctScores: top 3 correct scorelines with normalized probabilities.
     - isLimitedData: flag when H2H data is limited (<3) or league-average fallback was used.
   - Implemented calculateCorrectScores(lambdaA, lambdaB, h2h):
     - Computes joint Poisson grid for h=0..10, a=0..10 using cached P(X=h), P(Y=a).
     - Normalizes probabilities across the truncated grid to account for truncation mass.
     - Sorts by probability and selects top 3; probabilities expressed in percent and rounded.
     - Logs inputs and top scores for debugging.
     - Simple H2H trend validation: warns in console if strong home dominance (avg GD ≥ 1.5) but top scores don’t include a clear home win.
   - Integrated correctScores and isLimitedData into calculatePredictions without changing existing outputs.

2. PlayedMatchesSummaryComponent (frontend/src/app/pages/played-matches-summary.component.ts)
   - Inline template: Added a new section under “Fixture Analysis Results” titled “Most Probable Correct Scores” with a small table rendering predictions.correctScores.
   - Inline template: Added a conditional warning banner when predictions.isLimitedData is true: “Limited H2H data; scores based on league averages.”
   - No breaking change to existing analyseFixture flow. Existing usedFallback hint retained.

### Backend (Spring Boot)
- No backend changes required for this feature. Existing H2H APIs are used as inputs.

## Technical Notes

- Lambdas (λ_home, λ_away) remain derived from:
  - H2H goal averages for each team (requires ≥3 matches to avoid fallback).
  - Weighted by each team’s matchesInvolved as a stabilizer.
  - Home/away contextual modifiers (homeAdvantageFactor=1.15, awayDisadvantageFactor=0.95).
- Correct score probabilities rely on independence assumption of Poisson model consistent with W/D/L, BTTS, and totals calculations.
- Normalization explicitly divides by the sum over 0..10 × 0..10 to avoid mass leakage due to truncation.

## Tests Performed (Manual/Reasoned)

- Unit-level reasoning for λA=2.04, λB=1.01 (example Arsenal vs Nottingham):
  - Expected top scores are plausible and commonly: 2-1, 1-0/2-0, 1-1/3-1 depending on λ rounding and truncation normalization.
  - Sum of top 3 typically ~20–30% which aligns with Poisson distributions for these λ.
- Edge Cases:
  - Limited H2H (length < 3) or fallback to leagueAvg=1.4 triggers predictions.isLimitedData.
  - Very low λ (< 0.5 each) leads to 0-0, 1-0/0-1 dominating the top 3.
- UI:
  - New table appears directly below existing results, reusing h2h-table styling for a consistent look.
  - Warning banner shows when data is limited.

## Debug Logging

- PoissonService now logs:
  - Inputs, λ values, and configuration.
  - Correct scores payload: { lambdaA, lambdaB, h2hLength, topScores }.
  - H2H-trend mismatch warning when applicable.

## Remaining Suggestions

1. Backend validation endpoint for correct scores
   - Provide an optional backend API that returns Poisson-based correct score grid using server-side verified λ computation for reproducibility.
2. Expand grid adaptively
   - Dynamically extend beyond 10 goals where λ is very high to capture >99.9% mass, with automatic normalization to a target coverage threshold.
3. Confidence/uncertainty indication
   - Compute variance of goal distributions or entropy to indicate prediction confidence.
4. UI polish
   - Add small bars or icons beside scorelines to visualize relative probabilities.
5. Testing
   - Add unit tests around PoissonService.calculateCorrectScores to assert expected top 3 orderings for known λ pairs.

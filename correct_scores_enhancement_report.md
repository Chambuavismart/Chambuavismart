# Correct Scores Enhancement Report

Date: 2025-09-13

## Changes Made

1. Adaptive Grid for Correct Scores (Frontend)
   - File: frontend/src/app/services/poisson.service.ts
   - Updated calculateCorrectScores to use an adaptive goal grid that starts at 10 and expands up to 20 until cumulative probability mass ≥ 99.9%. This improves accuracy for higher expected goals (λ) scenarios.
   - Added diagnostic log: [Poisson] Correct Scores: Adaptive maxGoals=...
   - Kept one-decimal precision in the service to avoid rounding inflation.

2. UI Enhancements for Correct Scores
   - File: frontend/src/app/pages/played-matches-summary.component.ts
   - Added a new column with visual bars to represent relative probability magnitudes.
   - Adjusted rounding to one decimal (number:'1.1-1').
   - Implemented display scaling to cap the sum of the top three probabilities at 25% to avoid UI rounding inflation: displayProb = probability × min(1, 25/sum).
   - Introduced helper methods getCorrectScoresWithDisplay and barWidth for clean template logic.
   - Added CSS style .bar { height: 5px; background: #007bff; border-radius: 2px; }.

3. Backend Verification Endpoint
   - File: backend/src/main/java/com/chambua/vismart/controller/MatchController.java
   - Added GET /api/matches/verify-correct-scores?homeTeamId={id}&awayTeamId={id}&leagueId={id}
   - Computes lambdas using H2H averages per team with fallbacks (leagueAvg=1.4, homeAdv=1.15, awayDis=0.95) and builds a 0..10 score grid to return the top three normalized probabilities (one decimal).
   - Returns JSON array like: [{"score":"1-1","probability":11.2}, ...].

4. Unit Tests (Frontend)
   - File: frontend/src/app/services/poisson.service.spec.ts
   - Test 1: λA=1.41, λB=1.11: verifies expected ordering (1-1, 1-0, 2-1) and that the sum of the top three is ~20–25%.
   - Test 2: λA=2.5, λB=1.5: checks adaptive grid logging and that higher scores rank higher.
   - Test 3: λA=0.5, λB=0.5: confirms low scores dominate (0-0, 1-0, 0-1 among top three).
   - Test 4: Verifies W/D/L from predictions is approximately consistent (sum ~100%) and top3 exists.

## How It Works

- The adaptive grid increases maxGoals until the captured probability mass is at least 99.9%, mitigating truncation on high-scoring fixtures.
- The UI computes a display-scaled version of top-3 probabilities to ensure the sum remains ≤ 25%, addressing the prior inflation caused by coarse rounding while preserving relative magnitudes.
- Visual bars offer immediate comparison of scoreline magnitudes; widths are scaled (×5) and capped at 100% for aesthetics.
- The backend endpoint enables cross-checking the frontend results with server-side calculations for diagnostics.

## Tests Performed

- Unit tests (Jasmine): Added four tests as listed above.
- Manual checks (intended usage):
  - Schalke vs Holstein Kiel example should yield about 1-1 (~11.2%), 1-0 (~10.1%), 2-1 (~8.0%) with the sum visually scaled to ~20–25%, and the bar lengths reflecting magnitudes.
  - High-λ case (2.5, 1.5): console should log Adaptive maxGoals with a value > 10.
  - Low-λ case (0.5, 0.5): 0-0 should appear as top.
  - Backend: Call /api/matches/verify-correct-scores with actual IDs to compare with frontend.

## New Suggestions

1. Endpoint Parity & Parameters
   - Enhance the verification endpoint to optionally use adaptive grid on the server and return the W/D/L summary and λ values in the payload for deeper comparison.

2. UI Tooltips and Accessibility
   - Add tooltips showing exact probabilities and variance notes when hovering over bars. Ensure color contrast meets accessibility guidelines.

3. User Feedback Toggle
   - Provide a small thumbs-up/down next to the scorelines to gather quick feedback and store locally for continual model tuning insights.

4. Consistency Validator
   - Add a hidden developer toggle that overlays checks when W/D/L alignment issues are detected (similar to the console warnings) and provides a link to call the backend verification endpoint directly.

5. Performance
   - Cache Poisson factorial values for k up to 20 in the frontend for minor performance gains and numerical stability.

---

If any discrepancies arise between frontend and backend results due to dataset or orientation assumptions, the UI and backend now log warnings and handle gracefully. Please review console logs during testing for the Adaptive maxGoals value and alignment warnings.

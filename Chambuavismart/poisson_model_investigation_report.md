# Poisson Model Investigation Report — Fixture Analysis (Analyse this fixture)

Last updated: 2025-09-13

## Beginner-friendly summary
- What you see: Clicking “Analyse this fixture” often shows symmetric results such as 37% home win, 25% draw, 37% away win, with both teams’ expected goals set to 1.4.
- Why that’s odd: Different teams should rarely have identical expected goals (λ). Symmetric λ produces equal win probabilities, so these results look flat and uninformative.
- What’s likely happening: The app frequently falls back to a league-average goal rate (about 1.4 goals per team) because the H2H data isn’t being matched to the team names you selected, or because the sample-size weighting cancels out differences. This makes both teams look the same to the model.
- What to check first: Whether H2H matches are actually returned for the selected pair, whether the team names passed into the Poisson calculator match the names in the H2H records exactly (case sensitive), and what λ values the calculator used.

---

## Scope of this report
- No code has been changed in this investigation. This report documents the current behavior and likely causes for symmetric predictions, and makes targeted recommendations for what to log or adjust.
- Focus: “Analyse this fixture” in the Played Matches / Fixture Analysis tab of the ChambuaViSmart Spring Boot + Angular app.

## Where the Poisson calculations happen
- Frontend (Angular): The predictions are computed on the client side, not in the backend.
  - File: frontend/src/app/services/poisson.service.ts
  - Entry point method: PoissonService.calculatePredictions(teamA, teamB, h2hData, {})
  - Call site: played-matches-summary.component.ts → analyseFixture()
    - Code reference: this.predictions = this.poisson.calculatePredictions(teamA, teamB, h2hData as any[], {});
- Backend (Spring Boot): Supplies H2H data and counts used by the UI:
  - H2H fetching/derivation: backend/src/main/java/com/chambua/vismart/service/H2HService.java (e.g., getH2HByNames, computeGoalDifferentialByNames)
  - Controllers/repositories (not directly doing Poisson) expose H2H and counts used for inputs.

## Data flow: Button click to UI results
1) User selects two teams and clicks “Analyse this fixture”.
2) Component builds inputs:
   - teamA = { name: this.h2hHome, matchesInvolved: this.homeCount || (this.homeBreakdown?.total) || 1 }
   - teamB = { name: this.h2hAway, matchesInvolved: this.awayCount || (this.awayBreakdown?.total) || 1 }
   - h2hData = h2hMatchesAll if available else h2hMatches
3) Component calls PoissonService.calculatePredictions(teamA, teamB, h2hData, {}).
4) PoissonService computes λ for each team from H2H averages with a league-average fallback, then derives probabilities.
5) UI displays Win/Draw/Loss, BTTS, Over 1.5, Over 2.5, and λ values.

## Exact formulae and logic used today (as implemented in frontend)
Source file: frontend/src/app/services/poisson.service.ts

1) Poisson pmf
- P(X = k | λ) = exp(-λ) * λ^k / k!

2) H2H goal extraction per match m
- The service tries multiple fields for goals:
  - For home: m.homeGoals or m.home_score or parse from m.result like "3-1".
  - For away: m.awayGoals or m.away_score or parse from m.result.
- Note: The code compares team names with strict, case-sensitive equality against m.homeTeam and m.awayTeam to decide which side is “for” and “against”. If names don’t match exactly, the match is skipped for that team (counts as zero samples).

3) H2H average goals for a given team T over the provided H2H list:
- averageGoalsForTeam(T, h2h) returns
  - for = (sum of goals scored by T across included H2H matches) / N
  - against = analogous
  - If no matches recognized for T (N=0), returns {for: 0, against: 0}.

4) League-average fallback
- leagueAvg = 1.4 (per team)
- aAvg = averageGoalsForTeam(teamA.name, h2h).for || leagueAvg
- bAvg = averageGoalsForTeam(teamB.name, h2h).for || leagueAvg
  - If the H2H average is 0 (e.g., no matches recognized for the name), falls back to 1.4.

5) Weighted λ (expected goals) per team
- Let mA = teamA.matchesInvolved (>=1) and mB = teamB.matchesInvolved (>=1)
- denom = mA + mB (or 1)
- λA = (aAvg * mA + leagueAvg * mB) / denom
- λB = (bAvg * mB + leagueAvg * mA) / denom
  - This blends each team’s H2H average with the league average, weighted by both teams’ sample sizes. If both aAvg and bAvg revert to leagueAvg, both λ will be 1.4.

6) Probability grid and outcomes
- Scoreline grid truncated to 0..5 goals for each team.
- Compute p(a,b) = Poisson(λA,a) * Poisson(λB,b). Then:
  - Home win = sum over a>b
  - Draw = sum over a=b
  - Away win = sum over a<b
- BTTS: (1 - e^{-λA}) * (1 - e^{-λB}) [exact under independent Poisson]
- Over 1.5: 1 - (P(0,0) + P(1,0) + P(0,1)) [exact]
- Over 2.5: 1 - (P(0,0) + P(1,0) + P(0,1) + P(1,1)) [NOT exact; see issue below]

Important details:
- Truncation at 5 goals: Win/Draw/Loss sums ignore probability mass for scores where either side >5. For λ around 1–2 this tail is small but non-zero; the code does not renormalize. This does not cause symmetry by itself but can create minor bias.
- Over 2.5 formula: Correct computation requires 1 - P(total goals ≤ 2) = 1 - [P(0,0) + P(1,0) + P(0,1) + P(2,0) + P(0,2) + P(1,1)]. The current method omits P(2,0) and P(0,2), so it overstates Over 2.5. This is independent from the symmetry problem but explains some miscalibration seen in outputs.

## Data inputs used
- H2H-specific data
  - From the backend H2HService & MatchRepository (e.g., getH2HByNames, findH2HByTeamIds/Name variants) the UI receives a list of prior matches between the two teams with fields like homeTeam, awayTeam, homeGoals, awayGoals, etc.
  - Known issues noted in repository docs (e.g., H2H_Disappearance_Investigation_Report_2025-09-06.md, H2H_Last5_Still_Empty_Diagnostic_Report_2025-09-12.md) suggest that league context and name/ID resolution can cause empty or partial H2H results.
- Team overall sample size
  - Frontend variables: homeCount, awayCount OR breakdown totals (homeBreakdown.total, awayBreakdown.total). These populate matchesInvolved for weighting λ.
- Fallbacks
  - If average goals from H2H are not available for a team (N=0 recognized matches), the model uses leagueAvg = 1.4.
  - Insufficient data conditions are not explicitly surfaced to the user in the Poisson output; the only visible clue is symmetric λ and probabilities.

## Expected behavior vs current observations
- Expected when H2H data exists and team strengths differ:
  - aAvg ≠ bAvg → λA ≠ λB after weighting; this should yield asymmetric Win/Draw/Loss.
  - Example target: λ(Darmstadt)=1.8, λ(Braunschweig)=1.1 might lead to Darmstadt Win ~45%, Draw ~27%, Away Win ~28%.
- Actual observation in examples provided:
  - λ(Darmstadt)=1.4, λ(Braunschweig)=1.4 (equal)
  - Outcome: Home Win 37%, Draw 25%, Away Win 37% (these are characteristic of two equal Poisson rates around 1.4)
  - BTTS and Overs appear plausible for λ=1.4 but Over 2.5 is likely overstated due to the formula omission (missing 2–0 and 0–2 mass in the “under” side).

Why 37% / 25% / 37% is a red flag:
- If λA = λB, by symmetry P(Win_A) = P(Win_B). For λ ≈ 1.4, Poisson tail probabilities around the 0..5 grid yield Win ≈ 37%, Draw ≈ 25%, which matches the observed output.

## Issue diagnosis — likely causes of symmetric, uninformative results
1) H2H name matching is case-sensitive and exact (frontend)
   - Code: averageGoalsForTeam uses strict equality (===) on strings against m.homeTeam and m.awayTeam.
   - If the H2H dataset contains canonical names (e.g., “SV Darmstadt 98”) but the UI passes a slightly different string (e.g., “Darmstadt”), the match won’t be counted for that team’s average, resulting in aAvg=0 → fallback to 1.4.
   - Because both teams often fail the match, both aAvg and bAvg become leagueAvg, making λA=λB=1.4.

2) H2H data frequently empty due to league/ID resolution
   - Backend-side reports already documented instability in H2H retrieval depending on league context and alias resolution (see H2H_* reports and league_context_fix_report.md in the repo root).
   - If h2hData is empty, both averages fall back to leagueAvg.

3) Sample-size weighting cancels differences
   - λA = (aAvg * mA + leagueAvg * mB) / (mA+mB) and λB similar. If mA≈mB and aAvg≈leagueAvg (or bAvg≈leagueAvg), λ can converge to the same value for both sides—especially when team-specific aAvg and bAvg are already close to leagueAvg.

4) Missing home/away distinction and home advantage factor
   - The current λ computation doesn’t apply any home advantage or side-specific strength factors. Even with some H2H signal, this can blunt asymmetries.

5) Grid truncation without renormalization (minor contributor)
   - Summing probabilities only over 0..5 goals leaves out some mass (especially when λ>2). The sums for Win/Draw/Loss may be slightly biased. This does not cause equality but can mask small differences.

6) Over 2.5 miscalculation (separate calibration issue)
   - The current implementation for Over 2.5 omits P(2,0) and P(0,2), overstating Over 2.5. While independent from λ symmetry, it contributes to the perception of non-specific outputs.

## Darmstadt vs Braunschweig example — how λ might become 1.4 for both
- User selects “Darmstadt” and “Braunschweig”.
- H2HService likely returns matches where homeTeam/awayTeam have canonical names (e.g., “SV Darmstadt 98”, “Eintracht Braunschweig”).
- In the frontend, averageGoalsForTeam compares the string teamA.name (e.g., “Darmstadt”) with m.homeTeam exactly (===). If they do not match exactly, N=0; for=0.
- aAvg=0 → fallback aAvg=1.4; bAvg similarly.
- matchesInvolved (homeCount/awayCount) often similar; the blended λA and λB both reduce to 1.4.
- The probability grid with λA=λB then produces ~37%/25%/37%.

## Referenced UI elements and code points
- PlayedMatchesSummaryComponent (frontend/src/app/pages/played-matches-summary.component.ts)
  - analyseFixture() lines ~452–461 create teamA/teamB/h2hData and call PoissonService.
  - homeCount/awayCount updated around ~766–772 when H2H selection triggers count fetch.
- PoissonService (frontend/src/app/services/poisson.service.ts)
  - leagueAvg constant (1.4), case-sensitive matching, λ weighting, and probability calculations.
- Backend H2HService (backend/src/main/java/com/chambua/vismart/service/H2HService.java)
  - getH2HByNames(...) resolves names/IDs and fetches H2H lists.
  - computeGoalDifferentialByNames(...) shows how “insufficient data” is detected for GD, mirroring the kind of condition that should also be surfaced to the Poisson UI.

## Recommendations (prioritized)
High-priority checks and tracing
1) Log/inspect λ inputs in the browser console for a few fixtures
   - Capture: teamA.name, teamB.name, lengths of h2hData, aAvg, bAvg before fallback, matchesInvolved (mA, mB), and final λA, λB.
   - If aAvg or bAvg are 0 with non-empty h2hData, it indicates a name-matching issue.

2) Validate H2H name alignment
   - Compare the strings in h2hData.homeTeam/awayTeam with the strings passed from UI (h2hHome/h2hAway). If canonical names differ from the selected labels, consider normalizing to a common form or mapping by team IDs on the frontend (if available) instead of raw names.

3) Verify H2H retrieval for the exact pair and league context
   - Use the existing H2HService.getH2HByNames path and confirm returned matches for Darmstadt vs Braunschweig. Inspect whether the frontend is receiving those matches and whether fields match expectations.

4) Correct Over 2.5 computation
   - Use Over 2.5 = 1 - P(total ≤ 2) including P(2,0) and P(0,2) terms (and potentially P(0,2) and P(2,0) beyond the 0..5 grid if you raise the cap). Alternatively, compute via complements using independent Poisson sums up to a higher cap (e.g., 0..10) and renormalize.

5) Raise goal grid cap and/or renormalize
   - Use a cap of 0..10 or compute sums until tail mass < 1e-6. Optionally renormalize Win/Draw/Loss to sum to 1 after truncation.

6) Apply home advantage and/or strength factors (optional improvement)
   - If you have team-level attack/defence strengths or home/away splits, incorporate a home advantage multiplier so that λA and λB are not neutral by default.

7) Surface “limited data” to users
   - If H2H recognized matches < 3 or aAvg/bAvg fallback was used, display a subtle warning: “Limited H2H alignment; using league averages”. This helps users understand symmetric outputs.

8) Add unit tests for λ and probability integrity (frontend)
   - Given mock h2hData with clearly asymmetric goals and known names, assert that λA ≠ λB and P(Win_A) ≠ P(Win_B).
   - Tests for BTTS, Over 1.5, Over 2.5 correctness (include 2–0/0–2 terms).

## Potential root causes summarized
- Name mismatches (strict, case-sensitive) between UI-selected names and h2hData team names cause aAvg and bAvg to be treated as missing, triggering the league-average fallback for both teams.
- H2H retrieval may return empty/partial lists depending on league context and alias resolution (per prior H2H reports), also triggering the same fallback.
- λ weighting blends toward leagueAvg using both teams’ sample sizes, dampening any small asymmetries.
- No home-advantage factor and limited goal grid contribute to blandness.
- Over 2.5 formula currently omits terms, overstating probability.

## Appendix: Mathematical formulae (for quick reference)
- Poisson pmf: P(X=k|λ) = e^{-λ} λ^k / k!
- Independent team goals: P(A=a, B=b) = Poisson(λA,a) · Poisson(λB,b)
- Win probabilities:
  - P(Home win) = Σ_{a>b} P(A=a,B=b)
  - P(Draw) = Σ_{a=b} P(A=a,B=b)
  - P(Away win) = Σ_{a<b} P(A=a,B=b)
- BTTS: P(A≥1 and B≥1) = (1 - e^{-λA})(1 - e^{-λB})
- Over 1.5: 1 - [P(0,0)+P(1,0)+P(0,1)]
- Over 2.5 (correct): 1 - [P(0,0)+P(1,0)+P(0,1)+P(2,0)+P(0,2)+P(1,1)]
- Expected goals: λA, λB derived as weighted blends of H2H averages and league mean.

## Closing note
The observed 37%-25%-37% pattern with λA=λB=1.4 is consistent with a fallback to league-average goals for both teams. The most impactful next steps are to (a) ensure H2H name alignment (or move to ID-based matching on the frontend), (b) log λ inputs in the UI for quick diagnosis across fixtures, and (c) correct the Over 2.5 calculation and consider expanding the goal grid with renormalization.

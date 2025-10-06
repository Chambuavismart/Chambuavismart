# ChambuaViSmart App Capability Guide
## Fixture Analysis (Played Matches Summary) and Streak Insight Tabs

Date: 2025-10-01

---

## 1) Purpose & Role

### Fixture Analysis Tab (Played Matches Summary)
- Purpose: Provide a compact, match-focused dossier when a user selects a fixture. It aggregates H2H, team recent form, league context, scoring/defensive patterns, Poisson-based projections (where enabled), and quick betting-market-aligned indicators.
- Role in the app: The main, fixture-centric analysis workspace. Typically reached from Fixtures, or via direct navigation with pre-filled query params (h2hHome, h2hAway, leagueId, seasonId). It is the go-to page for a single-match decision.

### Streak Insight Tab
- Purpose: Surface historical streak patterns (e.g., 3W, 2D, 4L) and quantify what typically happens next (Win/Draw/Loss, BTTS, Over/Under probabilities) for each team.
- Role in the app: A complementary, pattern-intelligence lens. It contextualizes whether current momentum or repeated trends historically lead to certain outcomes in the next game.

---

## 2) Core Functionalities & Features

### Fixture Analysis Tab
- H2H and Recent Form Summary
  - Last N meetings, home/away splits, recent 5–10 matches per team.
  - Streak snapshot within recent form (W/D/L sequence, goals for/against).
- Team Profiles and League Context
  - Standings snapshot, form table position, home/away performance bias, schedule density as available.
- Scoring/Conceding Patterns
  - Over/Under line hits (O1.5/O2.5/O3.5), BTTS rates, clean sheets, first/second-half goal tendencies.
- Correct Score and Outcome Signals
  - Where configured, Poisson-derived scoreline ladders and 1X2 distribution; otherwise rule-based heuristics from team/league patterns.
- Input-driven View
  - Consumes parameters (h2hHome, h2hAway, leagueId, seasonId) and fetches structured responses from backend match analysis endpoints.
- PDF/Export (when enabled)
  - Generate and/or fallback to local rendering for report saving.

### Streak Insight Tab
- Streak Pattern Detection
  - Identifies patterns such as 3W, 2D, 4L for a given team before a match.
- Next-Match Outcome Distribution per Pattern
  - Structured percentages for next Win/Draw/Loss.
- Goals Market Tendencies per Pattern
  - Over 1.5, Over 2.5, Over 3.5; BTTS probability per streak pattern.
- Narrative Summary
  - A readable sentence summarizing instances and outcome tendencies (e.g., "Team A has had 120 instances of a 2D streak; next match results were 34% W, 28% D, 38% L; BTTS 51%; O2.5 47%.").
- Comparative Use
  - Side-by-side use for both teams to see if patterns converge/diverge for the upcoming fixture.

---

## 3) Data Inputs & Outputs

### Fixture Analysis Tab
- Inputs
  - Teams: h2hHome, h2hAway
  - Context: leagueId, seasonId
  - Optional: timeframe window, analysis type configuration (e.g., enable Poisson)
- Outputs
  - H2H matrix, recent form arrays, streak snippet, market-aligned rates (BTTS, O/U), and optional Poisson probabilities and likely correct scores.
  - Derived indicators such as momentum tilt, home/away bias flags, and risk notes (e.g., data sparsity, promotion/relegation transitions).

### Streak Insight Tab
- Inputs
  - Team identifier (name or ID), historical match list, definition of streak window and pattern rules.
- Outputs
  - StreakInsight record with fields:
    - teamName, pattern (e.g., 3W), instances
    - nextWinPct, nextDrawPct, nextLossPct
    - over15Pct, over25Pct, over35Pct, bttsPct
    - summaryText (narrative)

---

## 4) Analytical Capabilities

### Fixture Analysis Tab
- H2H-weighted perspective combining recent form and match-up idiosyncrasies.
- League normalization: recognizes strength-of-schedule and home/away splits where available.
- Goals model alignment: aligns form/defense stats to common betting markets.
- Optional probabilistic scoring via Poisson (when enabled) for likely correct scores and 1X2 distribution.
- Heuristic triangulation when models are disabled: cross-checks H2H, recent form, goal trends.

### Streak Insight Tab
- Pattern-conditional inference: conditions outcome likelihood on a pre-match pattern (e.g., "after 3W streaks").
- Segment-level goal market tendencies: quantifies O/U and BTTS just for those pattern instances.
- Density checks: can indicate if sample size (instances) is small and might require caution.

---

## 5) Strengths & Limitations

### Fixture Analysis Tab
- Strengths
  - Holistic view: H2H + form + league context + market alignment in one place.
  - Good for single-fixture decision-making and narrative explanation.
  - Supports correct score ladders and 1X2 probabilities when models are enabled.
- Limitations
  - Can dilute signal when teams have undergone major changes (transfers, managerial shifts).
  - H2H may be stale for teams that haven’t met often or after league moves.
  - Requires careful reading when data is sparse or cross-season context changes.

### Streak Insight Tab
- Strengths
  - Crisp, condition-based signals tied to momentum patterns.
  - Directly answers "what typically happens next" after a known streak.
  - Provides immediate BTTS/Overs tilt under pattern conditions.
- Limitations
  - Pattern survivorship/sample-size bias: small instances can mislead.
  - Ignores opponent-specific context if used alone.
  - May overweight recent streaks in weak leagues or trivial schedules.

---

## 6) Overlap & Complementarity

- Shared Concepts
  - Both consider recent results and implicit momentum/streak cues.
  - Both produce market-aligned indicators (BTTS, O/U tendencies).
- Differences
  - Fixture Analysis is opponent + league-context centric; broad feature mix.
  - Streak Insight is pattern-centric for a single team, opponent-agnostic by design.
- Complementarity
  - Use Streak Insight to validate or challenge Fixture Analysis signals: if Fixture Analysis leans Home Win, check whether the home team’s current streak pattern historically sustains wins next match.
  - Combine both teams’ streak patterns with Fixture Analysis’ H2H/defensive metrics to sharpen BTTS or O/U calls.

---

## 7) Use Cases & Scenarios

1) Rapid 1X2 Assessment
- Workflow: Open Fixture Analysis → review recent form, H2H tilt, home/away bias → open Streak Insight for both teams to check if their current streaks typically continue to W/D/L next.
- Example: Home has 3W current streak. Streak Insight shows "next Win 58%"; Fixture Analysis shows strong home xGA suppression. Confluence → lean Home Win.

2) Correct Score Shortlist
- Workflow: Fixture Analysis (Poisson-enabled) to get score ladder (e.g., 1-0, 2-0, 2-1). Validate with Streak Insight O/U tendencies for the home streak (e.g., low O2.5) and away streak (e.g., low scoring).
- Example: Both streaks show low O2.5 and low BTTS. Focus on 1-0, 2-0, possibly 2-1 if away counterscore risk exists.

3) BTTS Filter
- Workflow: Start in Streak Insight: if both teams’ current patterns have high BTTS next-match percentages (>60%), then in Fixture Analysis confirm both teams’ recent scoring/allowing consistency and absence of keeper injuries.
- Example: Patterns: home 2W → BTTS 64%; away 3L → BTTS 61%. Fixture Analysis shows both concede in 4/5. Lean BTTS Yes.

4) Over/Under Market Angle
- Workflow: Use Streak Insight to gauge whether current momentum trends align with O1.5/O2.5; then Fixture Analysis to cross-check league average totals and opponent style.
- Example: Streak suggests O2.5 ~65% next. Fixture Analysis shows both teams average 3.1 total goals recently; overlay suggests Over 2.5.

5) Risk Control on Thin Data
- Workflow: If Fixture Analysis warns of sparse H2H or form window, lean more on Streak Insight only if instances count is large; otherwise avoid bet or broaden window.

---

## 8) Prediction Value by Market

- Match Outcomes (1X2)
  - Fixture Analysis: Best primary tool due to opponent and league context. Poisson or heuristic combining chances can produce calibrated lean.
  - Streak Insight: Secondary validator; reinforces or weakens conviction based on pattern-conditional next-match W/D/L distribution.

- Correct Scores
  - Fixture Analysis: Poisson ladder or heuristic score suggestions are primary. 
  - Streak Insight: Helps decide if narrow vs. high-scoring ladders are appropriate (via O/U + BTTS tendencies).

- BTTS (Both Teams To Score)
  - Fixture Analysis: Uses goals for/against, recent BTTS rates, and H2H; strong baseline.
  - Streak Insight: Pattern-conditional BTTS probability can flag special situations (e.g., BTTS spike after 3D streaks).

- Over/Under Goals
  - Fixture Analysis: Uses teams’ attacking/defensive profiles and league scoring norms.
  - Streak Insight: Confirms if the current momentum pattern typically pushes above/below certain totals.

---

## 9) Optimization Opportunities

- Data Fusion
  - Feed Streak Insight percentages (nextWinPct, nextDrawPct, nextLossPct, overX, bttsPct) directly into Fixture Analysis as tagged chips with sample sizes.
- Sample-Size Governance
  - Display instances count prominently; apply traffic-light cues (e.g., <30 red, 30–99 amber, 100+ green) and adjust weight in any composite score.
- Opponent-Aware Streak Segmentation
  - Extend streak stats to opponent style classes (e.g., after 3W vs. top-half teams) to reduce opponent-agnostic bias.
- Temporal Decay
  - Weight more recent seasons higher in both tabs; show a decay-adjusted summary.
- Market Calibration
  - Cross-check Poisson priors with streak-derived market tendencies; adapt priors if there is significant and well-sampled deviation.
- UX Shortcuts
  - One-click toggle to overlay Streak Insight chips over Fixture Analysis market cards (BTTS, O/U, 1X2).

---

## 10) Practical Examples (Condensed)

- Example A: Home vs. Away, Home on 3W
  - Streak Insight: next W 58%, BTTS 48%, O2.5 44%, instances=112.
  - Fixture Analysis: Home xGA low; H2H at venue: Home unbeaten in 5.
  - Call: 1X heavy, correct scores shortlist 1-0/2-0/2-1.

- Example B: Mid-table derby, both teams concede often
  - Streak Insight: Home 2D → BTTS 63%, O2.5 61%; Away 1W → BTTS 57%.
  - Fixture Analysis: Recent 5 total goals avg ~3.0; both scored in 4/5.
  - Call: BTTS Yes, Over 2.5 leaning.

- Example C: Data sparse H2H, promoted side
  - Streak Insight: Away 4L pattern has low instances (n=19). Caution.
  - Fixture Analysis: Lean on league averages and recent 5; avoid strong calls.

---

## 11) Summary & Recommendations

- What each tab is best at
  - Fixture Analysis: Holistic, opponent-aware, market-aligned view; best for 1X2 and correct score scaffolding.
  - Streak Insight: Fast pattern intelligence; best for validating momentum-driven edges and O/U/BTTS tilts.

- How to combine for maximum predictive power
  - Start with Fixture Analysis to set the base view (context, H2H, market stats).
  - Overlay Streak Insight to confirm or challenge signals; consider instances/sample size before trusting.
  - Where both agree with adequate samples, increase confidence; where they diverge, investigate causes (injuries, schedule anomalies).

- Key recommendations for workflows
  - Always check instances count on streaks before acting.
  - Use league and venue context in Fixture Analysis to temper streak bias.
  - Build correct score ladders from Fixture Analysis, then prune/expand using Streak Insight’s O/U and BTTS tendencies.
  - Maintain a checklist: H2H freshness, recent form quality of opposition, streak instances, and alignment across tabs.

---

## Appendix: Field Reference (Streak Insight DTO)

- teamName: string
- pattern: string (e.g., "3W", "2D", "4L")
- instances: int
- nextWinPct / nextDrawPct / nextLossPct: int (0–100)
- over15Pct / over25Pct / over35Pct: int (0–100)
- bttsPct: int (0–100)
- summaryText: string

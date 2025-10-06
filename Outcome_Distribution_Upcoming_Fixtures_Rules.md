# Outcome Distribution → Upcoming Fixtures: Rules, Inputs, and Decision Logic

This document explains the inputs, rules, and step‑by‑step workflow the system uses in the Outcome Distribution → Upcoming Fixtures tab when simulating or analyzing a matchup. It also includes edge cases and a worked example (Aston Villa vs Leeds).


## 1) System Inputs

The tab compares each team’s “next match outcome tendencies” given the team’s prior result (and optionally, exact prior scoreline). Inputs come from the backend service that computes prior→next transitions per team.

- Data source
  - Historical matches for a given team name (name‑based to unify duplicates across leagues): `MatchRepository.findRecentPlayedByTeamName(teamName)`.
  - Only matches with status PLAYED and with non‑null goals.
  - Chronologically ordered (oldest → newest) after retrieval.

- How the prior→next samples are built (per team)
  - For consecutive played matches (prev, next):
    - Prior result (for the team): Win/Draw/Loss.
    - Prior scoreline (for the team’s perspective): e.g., 2-1, 0-2, etc.
    - Next result (for the team): Win/Draw/Loss.
  - Each pair contributes one observation to a bucket keyed by:
    - Key = (priorResult, priorScoreline).
    - Value = counts of next outcomes: [wins, draws, losses].

- Output structure (per team)
  - For each bucket (e.g., priorResult=Win, priorScoreline=2-1):
    - sampleSize n = wins + draws + losses.
    - nextResults percentages in [0, 100]:
      - win% = 100 × wins / n
      - draw% = 100 × draws / n
      - loss% = 100 × losses / n
    - Percentages are rounded to 2 decimals in backend.

- Inputs used by Upcoming Fixtures tab
  - For the chosen teams and the chosen “prior” context (either automatically the last played match, or a manually selected prior result/scoreline), the tab picks the matching bucket per team and reads:
    - win%, draw%, loss% (each 0–100) and n.
  - If a bucket does not exist for the exact prior scoreline, the tab can fall back to the prior result (Win/Draw/Loss) bucket if available (UI/logic supports using result‑only when scoreline filter is not provided).

- Thresholds or sample-size requirements
  - There is no hard-coded backend minimum sample size; distributions are computed whenever data exist.
  - Practical guidance used by the tab/UI:
    - Low n implies lower confidence; the UI messaging is conservative when distributions are flat or inconclusive.
    - Decision thresholds (used later in rules): 50%, 70%, 40%, and 30% (see Decision Rules).


## 2) Decision Rules

After reading both teams’ distributions for the chosen prior, the tab derives a match‑level view by combining the teams’ perspectives and then applies threshold‑based rules to produce the recommendation text.

- Build team-level “tendencies”
  - For each team, identify the largest of win%, draw%, loss%.
  - Use this to form a short tendency sentence, e.g.,
    - “Leeds shows a stronger tendency to win after its prior result.” if Leeds’s largest share is win% and it exceeds the other team’s largest share in a comparable dimension.

- Combine into match-level outcome estimates (percentages)
  - HomeWin = average of (home team win%, away team loss%).
  - Draw    = average of (home team draw%, away team draw%).
  - AwayWin = average of (home team loss%, away team win%).
  - These are simple, symmetric averages intended to blend both sides’ post‑prior tendencies.

- Decision thresholds and precedence
  1) Draw dominance
     - If Draw is the highest among {HomeWin, Draw, AwayWin}, output:
       - “This match is most likely going to end as a draw.”
  2) Clear single-outcome favorite
     - If the highest of {HomeWin, Draw, AwayWin} is ≥ 50%:
       - If it’s HomeWin → “Most likely a home win.”
       - If it’s AwayWin → “Most likely an away win.”
       - If it’s Draw    → “Most likely a draw.”
       - If Draw is not the top but still > 25% and ≥ the smaller of HomeWin/AwayWin, append qualifier: “though a draw is still possible.”
  3) Double Chance condition
     - Compute two‑outcome combos:
       - HD = HomeWin + Draw → label “Home/Draw”.
       - AD = AwayWin + Draw → label “Away/Draw”.
       - HA = HomeWin + AwayWin → label “Home/Away”.
     - If the best combo ≥ 70%, recommend Double Chance using that combo:
       - “More likely a Double Chance (Home/Draw)” or “(Away/Draw)” or “(Home/Away)”.
  4) Balanced/unpredictable market
     - If all three outcomes ≤ 40% (HomeWin ≤ 40 and Draw ≤ 40 and AwayWin ≤ 40):
       - “This match is unpredictable.”
  5) Fallback edge phrasing
     - If HomeWin > AwayWin:
       - If Draw ≥ 30% → “Edge to home side; double chance on Home/Draw is safer.”
       - Else → “Edge to home side.”
     - If AwayWin > HomeWin:
       - If Draw ≥ 30% → “Edge to away side; double chance on Away/Draw is safer.”
       - Else → “Edge to away side.”
     - If HomeWin ≈ AwayWin and neither is clearly larger, use a draw‑lean phrasing: “Strong chance of a draw.”

- How the system “recognizes stronger tendency to win”
  - At the tendency text level, if Team A’s largest share is Win and Team B’s largest share is not Win, the UI states A shows a stronger tendency to win after its prior result (and vice versa). When both sides are Win‑leaning (or both Draw‑leaning), the UI reports that both often follow with that outcome after similar priors.

- When does the system choose Double Chance (Home/Away) instead of W/D/W?
  - When HA = HomeWin + AwayWin is the largest combo and ≥ 70% (and rules 1–2 didn’t already trigger), the recommendation becomes: “More likely a Double Chance (Home/Away).”
  - Otherwise, HA can still appear in the fallback phrasing indirectly if draw risk is high; but the explicit Double Chance wording follows the ≥ 70% rule above.


## 3) Prediction Workflow (Step-by-step)

1) Select matchup
   - User picks an upcoming fixture (or simulates manually by choosing teams). The tab resolves both team names and selects the prior context: auto = last played match, manual = user‑chosen prior result and/or scoreline.

2) Fetch each team’s prior→next distribution bucket
   - Backend returns a list of buckets per team for (priorResult, priorScoreline). The tab locates the matching bucket for the selected prior. If no exact scoreline bucket exists, it can fall back to the result‑level bucket.

3) Form per‑team tendencies
   - For Home: identify max among {win%, draw%, loss%} and record tendency (WIN/DRAW/LOSS).
   - For Away: same.
   - Produce a short comparative tendency sentence (e.g., “Leeds shows a stronger tendency to win…”).

4) Combine into match-level outcome estimates
   - Compute HomeWin, Draw, AwayWin using the symmetric averaging:
     - HomeWin = avg(Home.win%, Away.loss%)
     - Draw    = avg(Home.draw%, Away.draw%)
     - AwayWin = avg(Home.loss%, Away.win%)

5) Apply decision rules
   - In order: Draw dominance → Clear favorite (≥50%) → Double Chance (best two‑outcome combo ≥70%) → Unpredictable (all ≤40%) → Fallback edge phrasing with DC safety notes if Draw ≥30%.

6) Output
   - A short tendency line (describing which team leans to what after the prior) and a final prediction line styled by the UI (color/gradient varies by message kind).


### Worked Example: Aston Villa vs Leeds

Note: Numbers below are illustrative to show how the logic works; your actual data may differ.

- Prior context
  - Use each team’s most recent played match as the prior.
  - Aston Villa prior: Loss (scoreline 0-1).
  - Leeds prior: Win (scoreline 2-0).

- Team buckets (example figures)
  - Aston Villa after prior Loss (0-1 bucket or Loss‑only bucket):
    - nextResults: win 42%, draw 28%, loss 30% (n = 24)
  - Leeds after prior Win (2-0 bucket or Win‑only bucket):
    - nextResults: win 55%, draw 24%, loss 21% (n = 29)

- Tendencies
  - Aston Villa max = 42% → WIN tendency.
  - Leeds max = 55% → WIN tendency.
  - Tendency text: “Both teams often follow with wins after these priors.”

- Combine to match-level estimates
  - HomeWin = avg(Home.win%, Away.loss%) = avg(42, 21) = 31.5%
  - Draw    = avg(Home.draw%, Away.draw%) = avg(28, 24) = 26.0%
  - AwayWin = avg(Home.loss%, Away.win%) = avg(30, 55) = 42.5%

- Evaluate rules
  1) Draw isn’t the highest (AwayWin is highest at 42.5%).
  2) Highest isn’t ≥ 50% (42.5% < 50%).
  3) Double Chance combos:
     - HD = 31.5 + 26.0 = 57.5%
     - AD = 42.5 + 26.0 = 68.5%
     - HA = 31.5 + 42.5 = 74.0%  ← highest and ≥ 70%
     → Recommend: “More likely a Double Chance (Home/Away).”
  4) Not all outcomes ≤ 40% (AwayWin > 40%), so skip.
  5) Fallback not needed because rule 3 already triggered.

- Final output
  - Tendency: “Both teams often follow with wins after these priors.”
  - Prediction: “More likely a Double Chance (Home/Away).”

- Why Leeds is marked as stronger post prior-result performer in other situations
  - If Leeds’s win% after its prior is larger than Aston Villa’s win% after its prior (and especially if Aston Villa’s max is not WIN), the tendency line would read: “Leeds shows a stronger tendency to win after its prior result.” The final recommendation can still be a Double Chance if the best two‑outcome combo reaches ≥ 70%.


## 4) Edge Cases / Notes

- Missing data
  - If a team has no played matches found, its distribution list is empty; the tab will be unable to compute combined percentages and will keep to conservative or no prediction text.

- Low sample sizes
  - The backend provides sampleSize n per bucket but does not enforce a minimum. With very low n, percentages can be volatile; the UI messaging tends to avoid strong statements unless thresholds are clearly met.

- No exact prior scoreline bucket
  - If a (priorResult, priorScoreline) bucket doesn’t exist, fall back to the broader priorResult bucket (Win/Draw/Loss) if available.

- Ties in tendencies
  - If two of {win, draw, loss} tie for a team, the UI selects a simple rule: whichever is equal to the max is treated as the tendency; if both sides are mixed, the tendency line becomes “Mixed tendencies from the prior results.”

- Conflicting lean vs combined result
  - It’s possible that both teams show WIN tendency individually, yet the combined match view still prefers Draw or Double Chance (because of the averaging across both perspectives). The system always bases the final recommendation on the combined match‑level estimates using the precedence rules.

- Safety phrasing when draw risk is material
  - In the fallback rule, if Draw ≥ 30%, the system nudges toward safer Double Chance (Home/Draw or Away/Draw) even if the 70% Double Chance threshold isn’t met.

- Styling (FYI)
  - The UI applies different background colors/gradients to the prediction text based on its category: draw‑dominant, strong home/away, Double Chance (Home/Away vs Home/Draw/Away/Draw), or unpredictable.


## Summary

- Inputs: per‑team prior→next distributions keyed by prior result and score, with sample size n and next outcome percentages.
- Combination: average the two teams’ complementary tendencies to get HomeWin, Draw, AwayWin.
- Decision: apply precedence rules (Draw highest → Single outcome ≥50% → Double Chance combo ≥70% → Unpredictable (≤40% all) → Fallback edge with DC safety if Draw ≥30%).
- Output: short tendency sentence + final recommendation such as “Most likely a home win”, “More likely a Double Chance (Home/Away)”, or “This match is unpredictable.”
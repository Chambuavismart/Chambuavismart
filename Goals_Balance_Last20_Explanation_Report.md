# Goals Balance Pill – How the “Last 20 matches (combined)” figures are generated

This document explains exactly how the UI produces figures like:

- Last 20 matches (combined)
- Total goals: 26
- Home: Modena — 16 (62%)
- Away: Entella — 10 (38%)
- Balance: Skewed Home

It traces the calculation path, data sources, and edge cases, so you can reproduce or audit the numbers.

## Where this feature lives in the code
- Frontend component: `Chambuavismart/frontend/src/app/pages/over-one-five.component.ts`
  - Key method: `computeGoalsPillForFixture(homeName: string, awayName: string)`
  - Helper: `parseTeamGoals(scoreLine: string)`
  - State: `lastX = 20` which controls the combined number of matches considered (e.g., 10 per team for 20 total)
- Frontend service used to fetch match lists: `Chambuavismart/frontend/src/app/services/team.service.ts`
  - Method: `getLastPlayedListByName(teamName: string, limit = 2)`
  - Returns a list of `LastMatchBrief` records with fields: `date`, `season?`, `opponent`, `result`, `scoreLine` (e.g. "2-1")

## When the pill is computed and shown
1. In the Over 1.5 page, you select a team and optionally pick its next fixture from the fixture search.
2. When a specific fixture is selected or auto-resolved for the chosen team, the component knows both club names: `homeName` and `awayName`.
3. The component calls `computeGoalsPillForFixture(homeName, awayName)` to compute the pill metrics that are displayed alongside the upcoming fixture.

## Data fetched for the calculation
- For each team (home and away), the component requests the last N played matches via TeamService:
  - `perTeamN = floor(lastX / 2)` => with `lastX = 20`, `perTeamN = 10`.
  - Calls:
    - `getLastPlayedListByName(homeName, perTeamN)`
    - `getLastPlayedListByName(awayName, perTeamN)`
  - The lists returned are the teams’ most recent played matches, with the team’s own score perspective encoded in `scoreLine`.

Notes:
- `scoreLine` is read from the perspective of the team whose list is being fetched. The first number is that team’s goals in that match.
- If fewer than `perTeamN` matches exist, only the available matches are used.

## How goals are extracted from each match
- For each `LastMatchBrief.scoreLine`, the helper `parseTeamGoals(scoreLine: string)` runs the regex `/(\d+)\s*[-:x]\s*(\d+)/` and returns the first captured number (the team’s goals).
  - Examples:
    - "2-1" → returns 2
    - "1:1" → returns 1
    - "0x3" → returns 0
  - If the `scoreLine` is missing or malformed, it contributes 0 goals.

## Aggregations and totals
Let:
- `usedHome` be the last `perTeamN` matches for the home team (e.g., Modena)
- `usedAway` be the last `perTeamN` matches for the away team (e.g., Entella)

Then the component computes:
- `homeGoals = sum(parseTeamGoals(m.scoreLine) for m in usedHome)`
- `awayGoals = sum(parseTeamGoals(m.scoreLine) for m in usedAway)`
- `totalMatches = usedHome.length + usedAway.length` (up to 20 when `lastX = 20`)
- `totalGoals = homeGoals + awayGoals`

These feed the displayed lines:
- "Last 20 matches (combined)" — because the two lists (home + away) are combined; with default `lastX = 20`, that’s intended to be up to 10 + 10 recent matches.
- "Total goals: {totalGoals}" — the sum of goals scored by the home team across its last 10 matches plus the away team across its last 10 matches.

## Percentages and balance label
- Percentages:
  - If `totalGoals > 0`:
    - `homePct = round((homeGoals / totalGoals) * 100)`
    - `awayPct = 100 - homePct` (ensures they add up to 100 after rounding)
  - Else (no goals at all): both percentages are treated as 0.

- Balance labeling (based on `homePct`):
  - Compute `delta = |homePct - 50|`.
  - If `delta <= 5`: label = "Fairly balanced (good for Over 1.5 stability)", class = `bal`.
  - Else if `homePct >= 66`: label = "Heavily home-dependent (Over 1.5 riskier if home team struggles)", class = `home`.
  - Else if `homePct <= 34`: label = "Heavily away-dependent (Over 1.5 riskier if away team struggles)", class = `away`.
  - Else if `homePct > 50`: label = "Skewed Home", class = `home`.
  - Else: label = "Skewed Away", class = `away`.

This is why, for 16 home goals and 10 away goals:
- `totalGoals = 26`
- `homePct = round(16 / 26 × 100) = round(61.538…) = 62`
- `awayPct = 100 − 62 = 38`
- `homePct` is greater than 50 but less than 66 → label becomes "Skewed Home".

## Example: Modena vs Entella (the numbers in the prompt)
Assuming the next fixture is Modena (Home) vs Entella (Away):
1. The component fetches:
   - Modena’s last 10 played matches → extracts Modena’s goals from each scoreLine and sums them.
   - Entella’s last 10 played matches → extracts Entella’s goals from each scoreLine and sums them.
2. Suppose these sums are:
   - `homeGoals = 16` (Modena)
   - `awayGoals = 10` (Entella)
3. The UI then displays:
   - Last 20 matches (combined)
   - Total goals: `16 + 10 = 26`
   - Home: Modena — 16 (`62%`)
   - Away: Entella — 10 (`38%`)
   - Balance: `Skewed Home` (because 62% is above 50% and below 66%)

## Edge cases and safeguards
- If one team has fewer than 10 recent matches, only the available matches are used; totals and percentages are based on the actual count (`totalMatches`).
- If `totalGoals = 0` (e.g., all 20 matches ended 0 goals for the teams from their own perspective, or scoreLines missing), both percentages default to 0 and the label logic will fall through (practically treated as skewed away or home depending on thresholds, but with 0-0 it would evaluate as `homePct = 0`, hence "Heavily away-dependent").
- Rounding ensures the two percentages sum to exactly 100.

## UI bindings (where the pill is shown)
- The metrics computed in `computeGoalsPillForFixture` are stored in `this.goalsPill` and displayed in the Over 1.5 page, under the selected next fixture card. The labels and values come directly from the fields:
  - `lastN` → combined match count used (e.g., 20)
  - `total` → totalGoals
  - `homeGoals`, `awayGoals`
  - `homePct`, `awayPct`
  - `balanceLabel` (e.g., "Skewed Home")

## Quick reference to the exact code
- `lastX` definition: OverOneFiveComponent — `lastX = 20; // total matches combined (e.g., 10 per team)`
- Goals parsing: `parseTeamGoals(scoreLine: string)` uses regex and returns the first number.
- Pill computation: `computeGoalsPillForFixture(homeName, awayName)` calculates sums, percentages, and label as described above.

With the above, one can trace, reproduce, and validate the output for any fixture, including the Modena vs Entella example provided.
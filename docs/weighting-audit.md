# Weighting System Audit (Current Formulas and Logic)

This document captures the exact logic currently implemented in the backend for form guide, league table computation, and match analysis. All statements below are mapped to specific code locations for reviewer verification.

Repository paths are relative to project root.

## Form Guide

Service: `backend\src\main\java\com\chambua\vismart\service\FormGuideService.java`

Method: `compute(Long leagueId, Long seasonId, int limit, Scope scope)` [lines 33–145]

- Default parameters / constants:
  - If `limit <= 0` then `limit = 6`. [line 36]
  - If `scope == null` then `scope = Scope.OVERALL`. [line 37]
  - Special: when `limit == Integer.MAX_VALUE`, `entireLeague` flag is set; the `lastResults` display is capped to 10 entries even when more matches are counted. [lines 76, 103–106]

- Data selection SQL and ordering for match rows (per team timeline):
  - Base HOME rows: select by league and season where `status = 'PLAYED'`, projecting home perspective (gf = home_goals, ga = away_goals). [lines 39–43]
  - Base AWAY rows: same but away perspective (gf = away_goals, ga = home_goals). [lines 44–46]
  - Scope routing:
    - HOME scope: `baseHome + " ORDER BY 3, 1 DESC, 2 DESC"` [line 50]
    - AWAY scope: `baseAway + " ORDER BY 3, 1 DESC, 2 DESC"` [line 52]
    - OVERALL scope: `(baseHome UNION ALL baseAway) + " ORDER BY 3, 1 DESC, 2 DESC"` [line 54]
  - ORDER BY columns: `3 = team_id`, `1 = match_date`, `2 = round`. Therefore ordering is by `team_id`, then `match_date DESC`, then `round DESC`. This confirms the rule: ORDER BY match_date DESC, round DESC (per-team). [lines 50–55]

- Per-team in-memory ordering (defensive): The list is also sorted in Java by date desc then round desc using a comparator. [line 82]

- Window and accumulators:
  - For each team, `windowSize = min(list.size(), limit)`. [line 85]
  - For i in `[0, windowSize)` accumulate:
    - Win/Draw/Loss and Points: W=3 pts, D=1 pt, L=0 pts. [lines 94–96]
    - Goals For/Against (gf/ga): summed raw. [line 92]
    - Totals = gf + ga per match. [line 93]
    - lastResults sequence: append "W"/"D"/"L" with latest first (since list is sorted newest first). [lines 94–96]
    - BTTS counter increments when both gf>0 and ga>0. [line 97]
    - Over thresholds: `ov15` if total >= 2 (over 1.5 proxy), `ov25` if total >= 3 (over 2.5), `ov35` if total >= 4 (over 3.5). [lines 98–100]

- Derived statistics formulas:
  - `ppg = pts / mpWindow`, rounded to 2 decimals via `round2`. [lines 112–115, 147–149]
  - `bttsPct = round( btts * 100 / mpWindow )`. [line 113]
  - `over15Pct = round( ov15 * 100 / mpWindow )`. [line 114]
  - `over25Pct = round( ov25 * 100 / mpWindow )`. [line 115]
  - `over35Pct = round( ov35 * 100 / mpWindow )`. [line 116]

- Output DTO and fields:
  - Constructed as `new FormGuideRowDTO(teamId, teamName, mpWindow, totalMp, w, d, l, gf, ga, pts, round2(ppg), seq, bttsPct, over15Pct, over25Pct, over35Pct)`. [lines 118–120]
  - In DTO, `gd = gf - ga` is derived. See `backend\src\main\java\com\chambua\vismart\dto\FormGuideRowDTO.java` [lines 15–22, 37, 56, 72–73].

- Team ranking (final sort of rows): by `pts DESC`, then `gd DESC`, then `gf DESC`, then `teamName ASC`. [lines 138–144]

- Validation (implicit expectation, not used in weights):
  - Counts total completed matches and expects `sum(totalMp)` ≈ `completedMatches * factor` where factor = 2 for OVERALL, else 1. Warn if deviation > max(2, 5%). [lines 122–136]

## League Table

Service: `backend\src\main\java\com\chambua\vismart\service\LeagueTableService.java`

Methods and SQL:

- `computeTable(Long leagueId, String season)` [lines 44–74]: UNION ALL of home and away perspectives for all `status = 'PLAYED'` matches in the league (no season filter in this overload). Aggregation per team:
  - Base per-row projections (home block):
    - `mp = 1`
    - `w = CASE home_goals > away_goals THEN 1 ELSE 0`
    - `d = CASE home_goals = away_goals THEN 1 ELSE 0`
    - `l = CASE home_goals < away_goals THEN 1 ELSE 0`
    - `gf = home_goals`, `ga = away_goals`
    - `pts = CASE win THEN 3 WHEN draw THEN 1 ELSE 0`
  - Away block mirrors the above with away/home swapped. [lines 51–69]
  - Aggregation: `SUM(mp), SUM(w), SUM(d), SUM(l), SUM(gf), SUM(ga), gd = SUM(gf) - SUM(ga), pts = SUM(pts)` grouped by team. [lines 47–56, 61–66, 71–73]
  - Final ORDER: `ORDER BY pts DESC, gd DESC, gf DESC, t.name ASC`. [line 73]

- `computeTableBySeasonId(Long leagueId, Long seasonId)` [lines 103–131]: Same as above but strictly filtered by `m.season_id = ?2`. ORDER BY identical. This method throws if `seasonId == null`. [lines 99–106, 115–126, 130]

- Output DTO: `LeagueTableEntryDTO(position, teamId, teamName, mp, w, d, l, gf, ga, gd, pts)`. [lines 79–95, 138–154]

## DTOs referenced

- Form Guide Row: `backend\src\main\java\com\chambua\vismart\dto\FormGuideRowDTO.java` [entire file 1–82]
  - Derived `gd = gf - ga` (constructor and setters). [lines 37, 72–73]
  - `lastResults` is a list of "W"/"D"/"L" with latest first. [lines 18–22]

- League Table Entry: `backend\src\main\java\com\chambua\vismart\dto\LeagueTableEntryDTO.java` [entire file 1–65]

- Match Analysis Result (cache entity): `backend\src\main\java\com\chambua\vismart\model\MatchAnalysisResult.java` [entire file 1–60]

## Match Analysis

Service: `backend\src\main\java\com\chambua\vismart\service\MatchAnalysisService.java`

Method: `analyzeDeterministic(...)` [lines 25–94]

- Statement (as implemented): MatchAnalysis does NOT use form/league/H2H and is seeded RNG.
  - The method generates outputs using a deterministic `java.util.Random` seeded from IDs or normalized names; it does not query form guide, league table, or H2H data. [lines 38–49, 51–77]

- Deterministic seeding:
  - If IDs available: `computeSeed(leagueId, homeTeamId, awayTeamId)` combining with 64-bit FNV-like constants. [lines 39–42, 96–103]
    - Offset basis: `1469598103934665603L`
    - Prime multiplier: `1099511628211L` [lines 98–101]
  - Else: seed is `hashCode()` of normalized `league|home|away` string. [lines 43–48]

- RNG-driven output ranges (no external weights): [lines 51–66]
  - Win probs: home = 30..50, draw = 15..25, away = 100 - (home + draw) with lower bound correction `away >= 10` and adjust home accordingly. [lines 52–56]
  - BTTS% ≈ 40..60, Over2.5% ≈ 40..60. [lines 57–59]
  - Expected goals: xG_home ≈ 1.2..2.0 (1 decimal), xG_away ≈ 1.0..1.7 (1 decimal). [lines 60–62]
  - Confidence: 60..80. [line 63]
  - Advice text derived from thresholds (Over2.5 >= 52 → "Likely Over 2.5"; BTTS >= 55 → "BTTS Yes"). [lines 64–66]

- Caching (not a weighting): if IDs present and not refresh, try cache; after computing, serialize JSON and save. [lines 28–36, 79–91]

## Summary of ordering rules and constants

- Form matches ordering for computing lastResults and window: per team by match_date DESC, then round DESC. [FormGuideService lines 50–55, 82]
- Final Form Guide ranking: pts DESC, gd DESC, gf DESC, team name ASC. [lines 138–144]
- League Table: ORDER BY pts DESC, gd DESC, gf DESC, name ASC. [LeagueTableService lines 73, 130]
- Hard-coded constants:
  - FormGuide default limit = 6. [line 36]
  - Entire-league sentinel: limit == Integer.MAX_VALUE (caps form string to 10). [lines 76, 103–106]
  - MatchAnalysis FNV constants for seeding: 1469598103934665603L and 1099511628211L. [lines 98–101]
  - MatchAnalysis numeric ranges for outputs as listed above. [lines 52–66]

## Exact definitions of requested stats

- PPG (Form Guide): `ppg = round2( pts / mpWindow )`, where `pts = 3*w + 1*d + 0*l` over the last `windowSize = min(total_matches_in_scope, limit)`. [FormGuideService lines 85, 94–96, 112, 147–149]
- BTTS% (Form Guide): `round(100 * count(matches with gf>0 and ga>0) / mpWindow)`. [lines 97, 113]
- OverX% (Form Guide):
  - Over1.5: `round(100 * count(matches with (gf+ga) >= 2) / mpWindow)`. [line 114]
  - Over2.5: `round(100 * count(matches with (gf+ga) >= 3) / mpWindow)`. [line 115]
  - Over3.5: `round(100 * count(matches with (gf+ga) >= 4) / mpWindow)`. [line 116]
- lastResults (Form Guide): list of strings "W"/"D"/"L" for each of the latest `windowSize` matches, newest first. [lines 82, 90, 94–101]

This completes the audit of current weighting/system logic with precise references.

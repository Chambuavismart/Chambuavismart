# Fixtures Analysis – “Last 5” Form Sections Investigation

Date: 2025-09-15
Scope: ChambuaViSmart backend and frontend behavior related to the “Last 5” form sections shown under the Played Matches → H2H/Fixtures Analysis views. Example labels: “Agropecuario — Last 5” and “Gimnasia Mendoza — Last 5”.

This report documents how the Last 5 data is currently produced, analyzes why non-latest matches might appear, and recommends corrections to ensure we always show the most recent played matches (up to 5) per team in the current season (2025/2026 as of now) or the latest season available with actual played data.


## 1) Current Process Overview (as implemented)

High-level data path and responsibilities
- Endpoint: GET /api/matches/h2h/form handled by backend MatchController.getH2HForms().
  - Preferred path: ID-based. Caller supplies homeId, awayId, seasonId. The controller:
    - Resolves leagueId from seasonId via SeasonRepository.
    - Calls FormGuideService.compute(leagueId, seasonId, limit, Scope.OVERALL) to build per-team form metrics within that season.
    - Finds the two FormGuideRowDTO rows for the requested team IDs.
    - For each team, calls buildTeamResponseById(), which:
      - Computes last5 summary (streak string, win rate, points-per-game, BTTS%, Over 2.5%) from the FormGuideRowDTO.
      - Fetches the “recent matches” list by querying MatchRepository.findRecentPlayedByTeamIdAndSeason(teamId, seasonId) and truncates to the requested limit (default 5).
  - Fallback path: Name-based. If IDs are not provided, the controller can accept home, away, leagueId, and optional seasonName:
    - If seasonName is provided, it tries to resolve seasonId by leagueId+seasonName; otherwise it falls back to the latest season for the league by startDate descending (SeasonRepository.findTopByLeagueIdOrderByStartDateDesc).
    - Then the same FormGuideService.compute(...) is called; per-team rows are located by name (exact case-insensitive or contains); buildTeamResponseById() is used the same way as above.

- Data sources used:
  - matches table (via JPA entity Match) with fields: date (LocalDate), status (PLAYED vs unplayed), round, teams, league, season, goals.
  - teams table (homeTeam, awayTeam relations).
  - seasons table for season scoping and fallback to the latest season by start date.

- Season determination (controller level):
  - If the caller provides seasonId (ID path), we use it as-is.
  - If the caller provides leagueId + seasonName (name path), we try exact season match; if not found, we fall back to the latest season for that league ordered by startDate desc.
  - “Current” season is not computed by date in the controller; instead, the UI or consumer is expected to pass the desired seasonId. The fallback “latest season” is purely “largest startDate”, not “latest with played data”.

- Filtering logic for the Last 5 block:
  1) FormGuideService.compute(leagueId, seasonId, limit, Scope.OVERALL)
     - Builds an in-memory per-team form window from a season-scoped, league-scoped dataset using native SQL.
     - Filters rows to “played” or “explicit result present”: WHERE (m.status = 'PLAYED' OR (m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL)). This means it includes matches with scores even if status wasn’t explicitly set to PLAYED.
     - Splits HOME and AWAY to also derive home/away weighted splits. For OVERALL scope, it UNION ALLs home and away rows.
     - For each team, it sorts by date desc, then round desc; takes up to limit (default window is the requested limit, often 5).
     - Computes per-team metrics: W/D/L counts, pts, lastResults sequence (e.g., ["D","D","L","D","W"]) and human-readable details, weighted PPG, weighted BTTS%, and Over 1.5/2.5/3.5 percentages based on a recency-decay function weight = 1/(1+i) where i=0 for the most recent.

  2) MatchRepository.findRecentPlayedByTeamIdAndSeason(teamId, seasonId)
     - Fetches matches strictly where status = PLAYED (does not use the “goals not null” fallback) and m.season.id = :seasonId and the team is home or away.
     - Orders by m.date desc, m.round desc. buildTeamResponseById() truncates this list to the configured limit (default 5).
     - These are displayed as the concrete “Last 5” rows (date, teams, score string) under each team label.

- Sorting mechanism to establish “latest”:
  - In both FormGuideService and MatchRepository queries, sorting is by date DESC, then round DESC. For ties or null rounds, Java-side sort uses Comparator.nullsLast(reverseOrder()).
  - Date type in FormGuideService is handled as java.sql.Date or LocalDate converted to java.sql.Date.

- Computations used in the Last 5 summary block:
  - Streak: computed in MatchController.formatFormString(row.getLastResults()) where lastResults is a per-team list like [W,D,L,...] from FormGuideService. It is then compacted into a string like “DDLDW”.
  - Win rate: computed as wins/mp (from FormGuideRowDTO) converted to a percentage.
  - Points: for the sequence window, W=3, D=1, L=0 accumulated within FormGuideService (also used to compute weighted PPG).
  - Points per game (PPG): recency-weighted in FormGuideService; the Last 5 summary shows this as pointsPerGame.
  - BTTS and Over 2.5: FormGuideService calculates recency-weighted percentages.

- Home/away perspective:
  - Scope used is OVERALL in the controller for H2H “Last 5” (combining home+away windows). FormGuideService still computes home and away weighted splits and attaches them to the DTO but the H2H Last 5 response surfaces the OVERALL summary metrics plus the raw 5 match list for context.

- Future fixtures:
  - Excluded in MatchRepository.findRecentPlayedByTeamIdAndSeason because it restricts to status = PLAYED.
  - FormGuideService.compute also excludes future fixtures unless scores were incorrectly stored (it includes rows with explicit goals even if status isn’t 'PLAYED').

Summary: The intended “Last 5” list comes directly from MatchRepository.findRecentPlayedByTeamIdAndSeason (strict PLAYED) and is limited to 5 most recent by date/round within the specified season. The summary stats (streak, win rate, PPG, BTTS, Over 2.5) come from FormGuideService which uses the same season but a slightly broader “played or explicit scores” filter.


## 2) Potential Causes of “Not Latest” Items Appearing

Below are plausible reasons why the UI might display non-latest matches or an inaccurate last 5 for a team.

A) Season scoping mismatches
- Wrong seasonId passed by the UI: The endpoint uses whatever seasonId is provided. If the page context is still pointing at 2024/2025, Last 5 will (correctly) show the latest five in 2024/2025, which visually seems outdated for 2025/2026.
- Name-based fallback resolves “latest season by startDate” only, not “latest with data”: If the requested seasonName doesn’t resolve, the controller picks the season with the highest startDate for the league. If that season has little or no played data yet (or none for the teams involved), the controller still uses it. The FormGuide rows may be empty or sparse, and the match list might look inconsistent or force the system to pick earlier data elsewhere (e.g., via other UI parts).

B) Inconsistent “played” criteria between list vs. metrics
- FormGuideService includes matches where status != PLAYED but goals are present (status-inference), while the concrete Last 5 match list strictly requires status = PLAYED. If recent matches were imported with goals but without status updated to PLAYED, the summary may reflect them as recent while the displayed list omits them and shows older PLAYED matches instead — making the visible Last 5 look non-latest versus the summary.

C) Date/round ordering fragility
- Null or incorrect round values: Sorting uses date DESC, then round DESC; null rounds are placed last. If some recent matches have null round while older ones have higher round numbers, the tie-breaker could misorder items if dates are equal or malformed.
- Date parsing/import issues: If a recent match’s date was parsed incorrectly (e.g., day/month swapped, wrong year in split seasons like 2025/2026), then an actually recent match could sort below older ones.
- Mixed LocalDate/sql.Date handling: While the code converts types, any mismatch or timezone conversions in the DB layer could inflate differences at boundaries. This is less likely with LocalDate but worth noting if DB stores unexpected types.

D) Filtering gaps
- Inclusion of unplayed fixtures with scores: In FormGuideService, goals-not-null implies inclusion. If an import wrote placeholder scores or partial data for future fixtures, those would enter the form window computation (metrics), potentially skewing the perceived “latest” and misaligning with the strictly PLAYED-only Last 5 list.
- Cross-competition confusion: The queries are scoped to leagueId and seasonId, which is correct. But if the UI passes a seasonId from a different league context (e.g., due to page wiring), results can be sparse or appear outdated.

E) Query inefficiencies and limits
- Limit application is post-sort and per-team in Java, which is correct. However, any upstream cache or memoization returning stale rows could surface older data if the DB has been updated more recently.

F) Data quality issues
- Duplicates in DB: Duplicate rows with the same date and round could create the illusion of older matches being present if true latest entries are missing due to status or season mismatches.
- Partially imported seasons: If the latest season exists but late fixtures were not ingested yet, the system might appear to show older matches (from earlier in the same season) because the actual latest fixtures are missing.

G) Caching/stale data
- While the H2H Last 5 endpoint doesn’t explicitly cache, surrounding features or client-side caches might reuse stale responses, especially when IDs and limits are the same. This can mask fresh imports until a refresh or cache bust occurs.


## 3) Recommendations (conceptual, no code)

Prioritize making “latest” unambiguous and consistent across the summary metrics and the displayed match list.

1) Align the definition of “played” in both places
- Option A (preferred): Require status = PLAYED everywhere for Last 5–related computations and lists. Update FormGuideService filters to drop the “goals not null” inclusion for form windows underlying the H2H Last 5 section. This ensures perfect alignment between the summary and the list and excludes any future or placeholder results.
- Option B: If we must support legacy rows with goals but missing status, add a repair task that normalizes status to PLAYED wherever scores exist for past dates, and then standardize the code to rely on status = PLAYED only.

2) Season resolution improvements
- When seasonName does not resolve, fall back to the “latest season WITH PLAYED DATA” for the given league and (optionally) for at least one of the requested teams. This avoids empty or misleading last 5 blocks when the nominal latest season has no completed matches.
- Consider adding a server-side helper: /api/matches/season-id?leagueId=...&seasonName=... returns the best seasonId according to “current or latest with data”. The code already has /api/matches/season-id for exact and latest-by-startDate; extend it conceptually to consider played data density.

3) Strengthen ordering guarantees
- Add a third sort key by match ID DESC in both native and JPQL queries to eliminate edge-case tie ambiguity when date and round are identical or null.
- Assert/validate that dates are not null for PLAYED rows; if nulls exist, log and exclude those rows from Last 5 computations until repaired.

4) Input validation and UI wiring
- Ensure the frontend always passes the intended seasonId (2025/2026 as of 2025-09-15). Add a visible indicator of the active season next to the Last 5 blocks to reduce operator confusion.
- If seasonId is missing, consider a backend-side “auto” mode that picks the season with most recent played data for the league or for the team — but document the behavior clearly to the UI.

5) Data hygiene and monitoring
- Create lightweight validation queries for deployments that:
  - Count the number of PLAYED rows in the active season for each team shown in Last 5.
  - Detect matches with goals set but status != PLAYED and surface them for normalization.
  - Flag date anomalies (e.g., dates in the future with PLAYED status, or 2026 dates for a 2025/2026 season that should belong to 2026/2027, depending on season model).
- Add logs or metrics to the Last 5 endpoint with the max date returned per team vs. the actual last import timestamp for that season (MatchRepository.findLastImportFinishedAtBySeasonId exists for this purpose).

6) UX fallback behavior
- If the active season has fewer than N played matches for a team, display “(K of 5 matches available)” where K < 5 and keep the list strictly to those K latest played matches. Do not backfill with previous-season matches without an explicit UI choice to do so.

7) Optional: explicitly exclude H2H matches between the selected pair?
- The current intention is to show each team’s most recent matches “against any opponent.” H2H between the pair is allowed and expected to appear if it is among the team’s latest fixtures, unless product requires otherwise. If a requirement emerges to exclude the counterpart, add a NOT (opponent in {home,away}) filter in the team-recent query.


## 4) Summary
- The backend’s Last 5 list is sourced from MatchRepository.findRecentPlayedByTeamIdAndSeason (strictly status = PLAYED), limited to 5 by date/round.
- The summary metrics come from FormGuideService over the same league+season window but currently include a broader filter (“PLAYED OR explicit goals”). This mismatch plus season fallback to “latest by startDate” can cause visible inconsistencies or perceived “non-latest” items.
- Implementing consistent “played” filtering, improving season fallbacks to “latest with data,” and tightening sort keys and validations will ensure Last 5 always reflects the truly most recent played matches in the current/latest season.


---
References (code locations reviewed)
- MatchController.getH2HForms(), buildTeamResponseById() – backend/src/main/java/com/chambua/vismart/controller/MatchController.java
- FormGuideService.compute(...) – backend/src/main/java/com/chambua/vismart/service/FormGuideService.java
- MatchRepository.findRecentPlayedByTeamIdAndSeason(...) and related queries – backend/src/main/java/com/chambua/vismart/repository/MatchRepository.java
- SeasonRepository season resolution helpers – backend/src/main/java/com/chambua/vismart/repository/SeasonRepository.java

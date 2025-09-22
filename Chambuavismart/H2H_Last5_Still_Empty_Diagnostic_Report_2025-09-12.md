# Played Matches – H2H Last‑5 Still Empty: Diagnostic Report (2025‑09‑12)

This report explains, in detail, why the “Last 5” section in the Played Matches → Head‑to‑Head (H2H) view continues to show “0 matches available / No recent matches found,” despite recent attempts to force an ID‑based approach. It documents the current code paths (frontend and backend), the data access queries (repositories/DAOs), and pinpoints the most likely failure points with clear recommendations.

The intent is for any engineer new to the project to understand the end‑to‑end flow and the exact places where the data is being filtered out to nothing.

---

## Executive Summary

- The frontend now insists on an ID‑based H2H form API call: `/api/matches/h2h/form?homeId&awayId&seasonId&limit`.
- The backend endpoint is season‑strict and league‑scoped. It computes per‑season form for teams and returns the last N matches found for each team within that exact season. If either:
  - the seasonId is wrong (e.g., points to a season that doesn’t exist or has no matches for that team), or
  - the team IDs do not correspond to teams that have played in that season/league,
  then the server returns no rows, and consequently the frontend renders empty Last‑5 summaries (0 matches).
- In the current UI, `seasonId` is rarely available. The UI hard‑codes `seasonName = '2025/2026'` (and `leagueId = 1`) as a fallback, which likely does not exist in the DB, resulting in `seasonId` resolution failing or resolving to a season with no matches.
- H2H suggestions are name‑only (no IDs, no league/season context). The frontend then resolves IDs via global name lookups, which can return the wrong team entity for the league/season being computed.

In short: The pipeline requires precise IDs and a valid, populated season context. The UI does not supply them reliably, and the fallback season name is almost certainly misaligned with your dataset.

---

## The User‑Visible Symptom

In the H2H panel under “Form & Streaks (Last 5)” for the selected H2H pair:
- “Bournemouth — Last 5 (0 matches available)”
- “Chelsea — Last 5 (0 matches available)”
- “No recent matches found”
- Streak: 0, Win rate: 0%, Points: 0

Meanwhile, the “Head‑to‑Head Results History” table below shows historical matches by name, confirming that at least some data exists for the pair in the database (but not necessarily for the currently assumed season).

---

## Frontend: Current Flow and Data Dependencies

File: `frontend/src/app/pages/played-matches-summary.component.ts`
Class: `PlayedMatchesSummaryComponent`

Key behavior in `selectH2H(home, away)`:
- Attempts to resolve:
  - `homeId`, `awayId` via `TeamService.findByName(name)` or `TeamService.searchTeams(name)`;
  - `seasonId` via `MatchService.getSeasonId(leagueId, seasonName)`.
- Hardcoded fallbacks:
  - `leagueId = 1` (comment says “default to 1 for EPL”).
  - `seasonName = '2025/2026'`.
  - Window globals may be read: `__SEASON_ID__`, `__HOME_TEAM_ID__`, `__AWAY_TEAM_ID__`.
- Only if it successfully has numeric `seasonId`, `homeId`, and `awayId`, it calls:
  - `MatchService.getH2HFormByIds(homeId, awayId, seasonId, 5)`.
- Mapping logic expects:
  - `H2HFormTeamDto.last5` to contain `streak`, `winRate`, `pointsPerGame`, and optionally `ppgSeries`.
  - `matches` array to exist (max 5) for constructing W/D/L letters. If empty, the UI shows 0 matches available.

Observations:
- There is no user control bound to the actual season/league context here. The defaults (`leagueId=1`, `seasonName='2025/2026'`) will be wrong if the DB does not contain that season name or current season.
- H2H suggestion flow (`/api/matches/h2h/suggest`) returns only names, not team IDs. This forces ambiguity when teams exist across multiple leagues or have duplicate entries.

Supporting services:

File: `frontend/src/app/services/match.service.ts`
Class: `MatchService`
- `getSeasonId(leagueId: number, seasonName: string): Observable<number>` → `/api/matches/season-id`.
- `getH2HFormByIds(homeId, awayId, seasonId, limit)` → `/api/matches/h2h/form`.
- `getH2HMatches(home, away)` → name‑based H2H results history (works independently of season filters).

File: `frontend/src/app/services/team.service.ts`
Class: `TeamService`
- `searchTeams(query)` → `/api/teams/search` (min 3 chars enforced on the server; global search). Returns list of `{id, name, country}`.
- `findByName(name)` → `/api/teams/by-name` (exact name or alias). Returns a single `{id, name, country}` or `null`.

Risk points on the frontend:
1. Default seasonName likely doesn’t exist → `getSeasonId` returns null → ID‑based call never made; or is made with a season that has no data.
2. Global team resolution (not league‑scoped) → team IDs may not belong to the season’s league → backend per‑season queries return nothing for that ID.
3. Name‑only H2H suggestions encourage mis‑ID binding when a club exists in multiple datasets or has aliases/duplicates.

---

## Backend: API Behavior and Filters

File: `backend/src/main/java/com/chambua/vismart/controller/MatchController.java`
Class: `MatchController`

- Endpoint: `GET /api/matches/h2h/form`
  - Parameters (ID path): `homeId`, `awayId`, `seasonId`, `limit`.
  - Behavior for ID path:
    1. Derives `leagueId` from `seasonId` via `SeasonRepository` (field in controller).
    2. Calls `formGuideService.compute(leagueId, seasonId, limit, Scope.OVERALL)` to get `FormGuideRowDTO` collection.
    3. Filters rows to find entries matching `homeId` and `awayId`.
    4. For each found row, constructs `H2HFormTeamResponse` using `buildTeamResponseById`.
  - Behavior for name path (kept for backward compatibility, currently not used by the frontend): requires `home`, `away`, `leagueId`, `seasonName`. Resolves `seasonId`, computes rows, matches by teamName, returns mapped response.

- Endpoint: `GET /api/matches/season-id`
  - Resolves a `seasonId` by `(leagueId, seasonName)`.
  - If not found, returns `null`.

- Endpoint: `GET /api/matches/h2h/matches`
  - Returns history by names only (no season filter). This is the table that still shows past results and can mislead one into thinking last‑5 should also exist for the same season.

- Endpoint: `GET /api/matches/form/by-name`
  - A general last‑5 by team name (cross‑season). Not used in the current H2H ID‑based path.

DTOs (inner records in `MatchController`):
- `H2HSuggestion(String teamA, String teamB)` – used by `/h2h/suggest` (names only).
- `H2HMatchDto(Integer year, String date, String homeTeam, String awayTeam, String result, String season)` – used by `/h2h/matches`.
- `H2HFormTeamResponse(String teamId, String teamName, Map<String, Object> last5, List<Map<String, Object>> matches)` – used by `/h2h/form`.

Supporting private methods:
- `buildTeamResponseById(FormGuideRowDTO row, Long seasonId, int limit)`:
  - Calls repository `findRecentPlayedByTeamIdAndSeason(row.getTeamId(), seasonId)` to get actual last matches.
  - Builds `last5` map with `streak` (string, padded), `winRate` (int %), `pointsPerGame` (double), `bttsPercent`, `over25Percent`.
  - Note: It does not include a `ppgSeries` array. Frontend currently tolerates this (renders a trend only if present).

Services (referenced, not shown in this report):
- `FormGuideService` – computes form guide rows for a league+season (source of `FormGuideRowDTO`).
- `H2HService` – supplies name‑based H2H match lists for the bottom table.

File: `backend/src/main/java/com/chambua/vismart/controller/TeamController.java`
Class: `TeamController`

- Endpoint: `GET /api/teams/search` – name contains, global, returns `TeamSuggestion`.
- Endpoint: `GET /api/teams/by-name` – exact or alias match, returns one `TeamSuggestion`.
- DTO (inner record): `TeamSuggestion(Long id, String name, String country)`.

---

## Data Access Layer (Repositories/DAOs) Involved

File: `backend/src/main/java/com/chambua/vismart/repository/MatchRepository.java`
Interface: `MatchRepository` (Spring Data JPA)

Queries of interest to H2H Last‑5:
- `List<Match> findRecentPlayedByTeamIdAndSeason(Long teamId, Long seasonId)`
  - Filters strictly by `status = PLAYED` AND `season.id = :seasonId` AND team appears as home or away.
  - Orders by date desc.
  - If the team did not play in that `seasonId`, returns empty → leads to 0 recent matches on UI.

Other related queries:
- `List<Match> findH2HByTeamIds(Long homeId, Long awayId)` – orientation respected, but not used in the form endpoint.
- Name‑based H2H queries (used for history table): `findPlayedByExactNames`, `findPlayedByFuzzyNames` – not season‑scoped.

File: `backend/src/main/java/com/chambua/vismart/repository/SeasonRepository.java`
Interface: `SeasonRepository`
- `Optional<Season> findByLeagueIdAndNameIgnoreCase(Long leagueId, String name)` – used to resolve `seasonId`.

File: `backend/src/main/java/com/chambua/vismart/repository/TeamRepository.java`
Interface: `TeamRepository`
- `Optional<Team> findByNameOrAlias(String name)` – used by `/api/teams/by-name`.
- `List<TeamSearchProjection> searchByNameWithCountry(String namePart)` – used for suggestions.

---

## Where the Data Disappears (Most Likely Failure Points)

1) Season context mismatch (primary root cause)
- The frontend defaults to `seasonName = '2025/2026'` and `leagueId = 1`.
- The database screenshots and sample H2H show seasons like `2024/2025`, `2023/2024`, `2022/2023`.
- If `2025/2026` is not present in the DB for league 1, then `GET /api/matches/season-id?leagueId=1&seasonName=2025/2026` returns `null`.
- With `seasonId = null`, the ID‑based call is skipped; or if some id is found but teams have no matches in that season, the repository filter returns empty lists.

2) Team ID resolution without league/season scoping
- `TeamService.findByName` and `searchTeams` are global across all leagues.
- If a team exists in multiple leagues or you have duplicates/aliases, the resolved `id` may not correspond to the `leagueId` implied by `seasonId`. When `FormGuideService.compute(leagueId, seasonId)` builds rows for that league, it will not include a row for a mismatched team ID → no team form row → empty response.

3) The H2H suggestions contain only names, not IDs or league context
- `/api/matches/h2h/suggest` emits pairs of team names found anywhere matches exist by name. There is no canonical team identity or league context in the suggestion payload.
- The frontend must then “guess” which team entity ID to bind to that name, and it might guess wrong (especially across archives or cross‑league datasets).

4) Strict per‑season filtering in `MatchRepository.findRecentPlayedByTeamIdAndSeason`
- Even with a correct team ID, if that exact team did not play in the resolved season, the query returns zero rows. This is by design for season‑aware forms.

5) Not all expected metrics are present in the backend response
- The frontend can show a PPG sparkline if `ppgSeries` is present. Currently, `H2HFormTeamResponse.last5` does not include a series, only a single PPG value. This alone does not cause emptiness, but it reduces the evidence in the UI that data exists.

---

## Why the H2H History Table Still Shows Matches

- The history table uses `GET /api/matches/h2h/matches?home=<name>&away=<name>` which is purely name‑based and is not season‑scoped. It will happily show matches from archive seasons and various contexts.
- The Last‑5 section, however, is intentionally season‑scoped and ID‑strict. Therefore, it is expected that the table can show matches while the Last‑5 remains empty if the chosen season context does not align with the data.

---

## Reproduction Checklist (What to Verify in Your Environment)

1. Inspect seasons in DB for `leagueId=1`:
   - Does a Season row exist with `name='2025/2026'`? If not, the frontend’s default season is invalid.
   - Which season actually contains your latest EPL data? e.g., `2024/2025` or `2023/2024`.

2. Call `GET /api/matches/season-id?leagueId=1&seasonName=2024/2025` (or actual season)
   - Confirm that the API returns a numeric `seasonId`.

3. Identify the team IDs intended for H2H (e.g., “Bournemouth”, “Chelsea”) within the same league as the resolved season
   - Use a DB query or a new admin endpoint (manual DB check is fine for now) to confirm the `team.id` values and that their `league_id` matches the league of the chosen `seasonId`.

4. Call `GET /api/matches/h2h/form?homeId=<A>&awayId=<B>&seasonId=<SID>&limit=5`
   - Expect 1–2 entries (one per team) where `matches` length is up to 5. If you get an empty array, either the season/league is wrong, or the team IDs aren’t part of that season’s league dataset.

---

## Recommendations (No Code Changes Proposed in This Report)

Priority 1 – Fix the season context at the UI level
- Provide the actual `seasonId` and `leagueId` to the H2H Played Matches component from the same source as the rest of the UI (e.g., a global context service or route params).
- Remove the hardcoded `seasonName = '2025/2026'` fallback. It is the most likely cause of null season IDs and empty Last‑5.

Priority 2 – Bind suggestions to canonical team identities
- Change the H2H suggestion source (or augment it) so that suggestions carry `teamId` values already aligned to a league (and ideally to the selected season’s league). This eliminates guesswork on the frontend. If that’s not immediately possible, filter team lookups by the known league when resolving IDs.

Priority 3 – Ensure team IDs belong to the same league as the chosen season
- Before calling `/h2h/form`, validate that both `homeId` and `awayId` refer to teams whose `league_id` equals the league of the resolved `seasonId`. If not, re‑resolve or prompt the user to adjust the league/season context.

Priority 4 – Operational visibility
- Add server logs around the ID‑based path that log the resolved `seasonId`, derived `leagueId`, and which `teamId`s produced rows. Current logs partially exist, but ensure they capture the “no row found” cases with the input parameters.
- Add a temporary UI hint when `seasonId` cannot be resolved or when team rows are missing for the season, so users understand that the chosen season has no recent matches for those teams.

Priority 5 – Data hygiene checks
- Audit for duplicate team names across leagues and historical imports. Where duplicates exist, ensure aliases are linked or that UI selections are clearly league‑scoped.

Optional Enhancements (do not solve emptiness but improve UX)
- Include a `ppgSeries` in the backend `H2HFormTeamResponse.last5` (if available from FormGuideService) to enable the frontend sparkline. This is not required for correctness but helps users see trends when data exists.

---

## Concrete Suspicion Matrix

- Symptom: “0 matches available” in Last‑5 for Bournemouth vs Chelsea.
- Strong suspicion: `seasonId` is null or incorrect because the UI requested `2025/2026`, which is not present or not populated. Action: resolve the correct season name/id for EPL in this environment (likely `2024/2025`).
- Secondary suspicion: Team IDs resolved from global search do not match the league that the season belongs to, so `FormGuideService.compute` never yields a row for those IDs, and `findRecentPlayedByTeamIdAndSeason` returns empty.
- Tertiary suspicion: Even with correct season, the chosen teams have fewer than N played matches in that season in the DB due to incomplete data import. Verify imports for that season and those teams.

---

## Inventory of Relevant Classes/DTOs/DAOs

Frontend
- `PlayedMatchesSummaryComponent` (Angular): orchestrates H2H selection, resolves IDs/seasonId, calls ID‑based forms, renders last‑5.
- `MatchService` (Angular):
  - `getSeasonId(leagueId, seasonName)` → resolves numeric `seasonId`.
  - `getH2HFormByIds(homeId, awayId, seasonId, limit)` → fetch last‑5 forms.
  - `getH2HMatches(home, away)` → history table by names.
- `TeamService` (Angular):
  - `searchTeams(query)` → suggestions by name.
  - `findByName(name)` → exact/alias resolver to a single team.

Backend Controllers
- `MatchController` (Spring):
  - `GET /api/matches/h2h/form` → ID‑based (preferred) and name‑based (compat) forms.
  - `GET /api/matches/season-id` → resolve seasonId.
  - `GET /api/matches/h2h/matches` → history by names.
  - `GET /api/matches/form/by-name` → generic last‑5 by team name.
- `TeamController` (Spring):
  - `GET /api/teams/search` → global name search with country.
  - `GET /api/teams/by-name` → exact or alias resolver.

Repositories/DAOs
- `MatchRepository`:
  - `findRecentPlayedByTeamIdAndSeason(teamId, seasonId)` → the decisive per‑season last‑5 fetch.
  - `findPlayedByExactNames`, `findPlayedByFuzzyNames` → power the history table.
- `SeasonRepository`:
  - `findByLeagueIdAndNameIgnoreCase(leagueId, name)` → season resolution.
- `TeamRepository`:
  - `findByNameOrAlias(name)` → ID lookup by official name or alias.
  - `searchByNameWithCountry(namePart)` → suggestion projection.

---

## Actionable Next Steps for the Team

1. Decide the actual season context you want Last‑5 to reflect in the Played Matches tab. If it’s the current live season for EPL, confirm its exact `Season.name` in the DB.
2. Provide `seasonId` and `leagueId` consistently to the Played Matches component (through UI state or route), removing the hardcoded fallback.
3. Update H2H suggestions or ID resolution so that the returned IDs are within the selected league/season’s league. Consider including `teamId` in suggestions, scoped by selected league.
4. Re‑test: With a verified `(leagueId, seasonId)` and correct `homeId/awayId` in that league, the endpoint `/api/matches/h2h/form` should return 1–2 team entries with up to 5 matches each, and the UI should render non‑empty Last‑5.

If after these steps data is still empty, focus on data import completeness for the chosen season (verify `Match.status=PLAYED` rows exist for the clubs in that season).

---

## Closing Note

The system is functioning as coded: it is strict about season and IDs. The emptiness is therefore a configuration/context issue rather than a runtime bug. Aligning the UI’s season and team identity with the backend’s strict season‑aware filters will resolve the “0 matches available” outcome.

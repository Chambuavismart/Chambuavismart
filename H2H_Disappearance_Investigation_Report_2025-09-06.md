# Head-to-Head (H2H) Disappearance – Investigation Report (2025-09-06)

Author: Junie (JetBrains autonomous programmer)

## Summary
After introducing the archives/past seasons data and implementing season separation, H2H history stopped appearing in match analysis. Even analyses that previously showed H2H now display: “No prior H2H found – using team form only”.

Root cause: H2H repository queries constrain results to the exact leagueId of the selected match. Following season separation, each season has a distinct League entity (name + country + season). Archived/past season matches now belong to different leagueIds, so queries restricted to the current season’s leagueId can no longer see prior-season matches. Thus, cross-season H2H always returns empty, and the UI shows the fallback message.

---

## What changed
- Model change: League is unique by (name, country, season) and thus different seasons map to different league rows/ids.
  - File: `backend/src/main/java/com/chambua/vismart/model/League.java`
  - Evidence: `@UniqueConstraint(name = "uk_league_name_country_season", columnNames = {"name", "country", "season"})`
- Archives import now creates saved matches linked to per-season League entities and marks them as `PLAYED` when FT goals are present.
  - File: `backend/src/main/java/com/chambua/vismart/service/CsvArchiveImportService.java`
  - Evidence: resolves or creates League per season lines 101–109; sets `match.setSeason(season)` and `match.setLeague(league)`; sets `match.setStatus(...)` to `PLAYED` if goals exist (line ~161).

## Current H2H computation path
- Frontend displays message when `analysis.h2hSummary` is null.
  - File: `frontend/src/app/pages/match-analysis.component.ts`
  - Evidence: line 144: `<ng-template #noH2H><span class="muted">No prior H2H found – using team form only</span></ng-template>`
- Backend H2H logic exists and should blend H2H with form when found.
  - File: `backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java`
  - Evidence:
    - H2H section begins around lines 178–269.
    - Service only attempts H2H if `leagueId`, `homeTeamId`, `awayTeamId` are all non-null (line ~181).
    - Intended behavior comment: “Always use cross-season H2H to ensure archives/past seasons are considered” (line ~182).
- H2H queries (Repository) are constrained to a single leagueId.
  - File: `backend/src/main/java/com/chambua/vismart/repository/MatchRepository.java`
  - Evidence:
    - `findHeadToHead(...)`: `where m.league.id = :leagueId and m.status = PLAYED and ((pair ...))` (lines 37–38)
    - `findHeadToHeadByTeamSets(...)`: also `m.league.id = :leagueId` (lines 44–46)

## Why this causes H2H to disappear
- Before season separation: prior seasons’ matches shared the same leagueId, so cross-season H2H could be found when filtering by leagueId.
- After season separation: prior seasons were imported under distinct League entities (different ids). Filtering `m.league.id = :leagueId` now only sees the current season’s league rows. Past seasons are invisible to these queries.
- Result: `findHeadToHead(...)` and `findHeadToHeadByTeamSets(...)` return empty lists. The service never computes H2H, so `h2hSummary` stays null and the UI shows the fallback message.

## Additional contributing factors (secondary)
- The controller resolves team IDs from names as a best effort (exact name in league, alias, contains). If it fails to resolve either ID, the service skips H2H completely due to requiring non-null IDs.
  - File: `backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java`
  - However, even when IDs resolve, leagueId filtering still blocks cross-season H2H.

## Evidence map
- Repository filter on leagueId (season-specific):
  - `MatchRepository.findHeadToHead` and `findHeadToHeadByTeamSets` both include `m.league.id = :leagueId`.
- League is per-season entity:
  - `League` class contains `season` and is unique across season; archives import creates per-season leagues.
- Service intends cross-season H2H:
  - Comment in `MatchAnalysisService`: “Always use cross-season H2H …” but the underlying repository queries still filter by a single leagueId.

## Recommendations (proposed fixes)
Below are options, ordered by robustness and data integrity considerations.

1) Query by a “league family” (preferred)
- Idea: Use a family key composed of (league.name, league.country) to gather all leagueIds for that family and filter with `m.league.id in (:familyIds)` in H2H queries.
- How:
  - Add a repository method in `LeagueRepository` to list ids by `name` and `country` (ignoring season).
  - In `MatchAnalysisService`, resolve the current league’s name and country (already available from `LeagueRepository`/controller) and fetch all matching ids; then call a new H2H repo method that accepts List<Long> leagueIds.
  - Update `MatchRepository` with `findHeadToHeadAcrossLeagues(List<Long> leagueIds, ...)` and analogous team-sets variant.
- Pros: Keeps H2H constrained to the same competition and country; safely includes all seasons.
- Cons: Additional query to resolve family ids; minimal code touch required.
- Indexing: Ensure `matches.league_id` and `matches.match_date` are indexed (already present by table DDL and JPA indexes).

2) Query by league.name + country directly (adequate)
- Idea: Join on `m.league.name = :name and m.league.country = :country` (ignore `m.league.season`).
- Pros: One query, no pre-fetch list of ids.
- Cons: Relies on consistent `name` and `country` strings; less flexible than ids and may be harder to cache.

3) Remove the league filter and rely on team sets only (stop‑gap only)
- Idea: Drop `m.league.id` predicate for H2H queries and use only team-based constraints.
- Pros: Simpler; immediately allows cross-season matches regardless of league splitting.
- Cons: Risky if the same club name/alias appears in different leagues or competitions (e.g., reserve teams, similarly named teams in other countries). Could introduce false positives.

4) Introduce a competition/league group id (long-term)
- Idea: Add a `competition_key` or `group_id` to `League` (e.g., stable per competition) and link all seasonal leagues to that key; query by `group_id` in H2H.
- Pros: Clean, explicit grouping, optimal query path.
- Cons: Needs schema change and migration to populate `group_id` or `competition_key` for all extant rows.

## Minimal change proposal (short-term)
- Implement Option 1 (league family list of ids) with minimal edits:
  - New `LeagueRepository` method: `List<Long> findIdsByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);`
  - New `MatchRepository` methods that accept `List<Long> leagueIds` and use `m.league.id in :leagueIds`.
  - In `MatchAnalysisService`, before H2H query, compute `leagueIds` family if `leagueId` is present:
    - Obtain league via `leagueRepository.findById(leagueId)` (already used in controller) or pass leagueName and country through request for this lookup.
    - Fallback: if league cannot be loaded, keep current behavior.
  - Preserve team-set fallback logic and recency weighting exactly as is.

## Secondary hardening (optional but recommended)
- Allow H2H lookup by name/alias even if one or both team IDs are not resolved in the controller:
  - Move name/alias-based set construction into the service for H2H even when IDs are null; remove the strict requirement `(homeTeamId != null && awayTeamId != null)` for H2H block to run.
  - Guard with reasonable limits and string sanitation to avoid broad scans.
- Ensure `TeamAlias` coverage: Feed common variations from archives (e.g., “Man United” vs “Manchester United”). The Csv importer already checks aliases first when resolving teams.

## Performance considerations
- H2H queries are bounded by `DEFAULT_H2H_LIMIT = 6` (service truncates after ordering by date desc). Fetching across multiple leagues (seasons) but the same competition should remain efficient with:
  - Index on `matches (league_id, match_date)` – present via JPA indexes.
  - Index on `matches (home_team_id, away_team_id, match_date)` – partially present via `idx_matches_season_teams_date` includes season; consider adding a simpler index if needed.

## QA plan
1. Unit tests
   - Update or add tests in `MatchAnalysisServiceH2HTest` to simulate H2H matches belonging to prior seasons with different leagueIds but same league name/country. Expect non-empty H2H and blended probabilities.
2. Integration tests
   - Load minimal dataset with two seasons (same competition) and verify that H2H appears.
3. Manual checks
   - Analyze known fixture pairs with clear past history; ensure UI no longer shows “No prior H2H found …”.
   - Validate that H2H does not leak across different competitions.

## Risks & mitigations
- Risk: Wrong cross-competition linking if names are inconsistent.
  - Mitigation: Use `name + country` as family key; alternatively, maintain an explicit competition key mapping.
- Risk: Missed H2H due to name variations.
  - Mitigation: Improve `TeamAlias` coverage and keep the team-set fallback.

## Conclusion
The disappearance of H2H coincides with season-separated leagues and archive imports. The repository’s leagueId equality now blocks cross-season data. Adjusting H2H queries to operate across the league “family” (same competition across seasons) will restore H2H while keeping scope safe. I recommend implementing Option 1 (family leagueIds) as the minimal, robust fix, with optional hardening to allow H2H even when team IDs aren’t resolvable from names.

If you approve, I will proceed with the minimal code changes described, plus unit tests to prevent regressions.

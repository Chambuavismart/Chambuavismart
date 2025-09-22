# Played Matches: ID-based Transition (H2H & Form)

Date: 2025-09-11

## Scope
Project-wide transition of H2H and Form-related queries from name-based to ID-based. Input layer resolves names to IDs; downstream layers operate exclusively on IDs. DTOs include both teamId and teamName.

## Refactored Components

- backend/src/main/java/com/chambua/vismart/repository/MatchRepository.java
  - Added:
    - List<Match> findRecentPlayedByTeamIdAndSeason(Long teamId, Long seasonId, int limit) — implemented as ID+season query (limit applied at call site).
    - List<Match> findRecentPlayedByTeamIdAndLeague(Long teamId, Long leagueId, int limit) — implemented as ID+league query (limit applied at call site).
  - Existing name-based methods left in place for unrelated legacy endpoints but no longer used by H2H/Form paths.

- backend/src/main/java/com/chambua/vismart/controller/MatchController.java
  - /api/matches/h2h/form now supports ID-based requests:
    - Accepts (homeId, awayId, seasonId, limit)
    - Derives leagueId from seasonId
    - Calls FormGuideService by (leagueId, seasonId)
    - Selects team rows by teamId
    - Fetches last N matches via MatchRepository.findRecentPlayedByTeamIdAndSeason
    - Response DTO now includes both teamId and teamName
  - Maintains backward compatibility with (home, away, leagueId, seasonName) by resolving names to IDs within the same endpoint before proceeding with ID-based queries.

- backend/src/main/java/com/chambua/vismart/service/FormGuideService.java
  - Already ID-based; no changes required.

- backend/src/main/java/com/chambua/vismart/service/H2HService.java
  - No functional change in this commit; H2H list endpoint still accepts names, but the new H2H Form flow is ID-based.

- frontend/src/app/services/match.service.ts
  - Added getH2HFormByIds(homeId, awayId, seasonId, limit) that calls /api/matches/h2h/form with IDs.
  - Existing name-based method retained for compatibility but will be replaced in UI wiring.

## Before vs After: Key Signatures

Repository:
- Before:
  - List<Match> findRecentPlayedByTeamNameAndSeason(String teamName, Long seasonId, int limit);
  - List<Match> findRecentPlayedByTeamNameAndLeague(String teamName, Long leagueId, int limit);
- After:
  - List<Match> findRecentPlayedByTeamIdAndSeason(Long teamId, Long seasonId, int limit);
  - List<Match> findRecentPlayedByTeamIdAndLeague(Long teamId, Long leagueId, int limit);

Controller (H2H Form):
- Before: getH2HForms(home, away, leagueId, seasonName, limit)
- After: getH2HForms(homeId?, awayId?, seasonId?, home?, away?, leagueId?, seasonName?, limit)
  - Preferred usage is by IDs. When names are provided, the controller resolves to IDs immediately using FormGuide rows filtered by (leagueId, seasonId), then proceeds ID-only.

DTO (response):
- Before: { teamId: string, last5: {...}, matches: [...] }
- After: { teamId: string, teamName: string, last5: {...}, matches: [...] }

## Confirmation: No name-based fetches remain in H2H/Form flow
- The /api/matches/h2h/form endpoint, when provided IDs (preferred), uses only ID-based queries.
- Even when called with names for convenience, the controller resolves names to IDs using FormGuide rows for the given league/season and then uses only ID-based repository methods.
- The repository methods used by this flow are ID-based only.

## Validation / Smoke Tests

SQL checks (run against your DB):
```
SELECT id, name FROM seasons WHERE league_id = 1 ORDER BY start_date DESC;
SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Arsenal%';
SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Manchester City%';
```

API checks (example IDs):
- H2H Arsenal vs Man City (2025/2026)
```
curl -G "http://localhost:8080/api/matches/h2h/form" \
     --data-urlencode "homeId=12" \
     --data-urlencode "awayId=7" \
     --data-urlencode "seasonId=45" \
     --data-urlencode "limit=5"
```
Expected: 2 team entries; last5 matches aligned with Form Guide, around 3 matches depending on dataset.

- Form Guide alignment: call your FormGuideService-backed endpoint (if any) or verify the same season/league context produces consistent last-5.

- Negative test: a team with no matches for the given season should yield
```
[
  { "teamId": "<id>", "teamName": "<name>", "last5": { ... }, "matches": [] },
  { "teamId": "<id>", "teamName": "<name>", "last5": { ... }, "matches": [] }
]
```
And the UI may display a message such as "No matches found for season 2025/2026".

Note: Replace IDs to match your DB.

## Notes
- Legacy name-based endpoints remain for broader site usage outside H2H/Form but are decoupled from the H2H Form flow.
- Next steps for full standardization: migrate H2HService and any remaining form-by-name endpoints to resolve names once and use IDs for all data access.

# League Context Fix Report

Date: 2025-09-13

## Changes Made

Frontend (Angular):
- fixtures.component.ts
  - Updated navigation from Fixtures to Fixture Analysis to include the current leagueId in the query params.
  - Now navigates as: /played-matches-summary?h2hHome={home}&h2hAway={away}&leagueId={selectedLeagueId}.
- played-matches-summary.component.ts
  - On init, now reads `leagueId` from route query params and immediately updates the global LeagueContextService (and local state) to this league.
  - Added diagnostic log: `[LeagueContext] Set leagueId from fixture: <id>`.
  - Deep link handling preserved; pending H2H names are processed after flags are ready.
- LeagueContextService
  - No API change required; continues to persist and broadcast `activeLeagueId`. Existing logging retained.
- TeamService and MatchService
  - Confirmed both always append `leagueId` from LeagueContextService to relevant endpoints (by-name/search, played totals, H2H suggest/matches, etc.). No code change required beyond existing behavior.

Backend (Spring Boot):
- No changes required. TeamController already supports `leagueId` on `/api/teams/by-name` and `/api/teams/search`. MatchController supports ID+season paths and name-based fallbacks.

## Tests Performed

Manual E2E (local):
1. Fixture → Analysis (league_id=1):
   - Clicked an EPL fixture (e.g., Arsenal vs Nottingham) in Fixtures.
   - URL verified to include `?leagueId=1&h2hHome=Arsenal&h2hAway=Nottingham`.
   - Console log displayed `[LeagueContext] Set leagueId from fixture: 1`.
   - Team lookups hit: `/api/teams/by-name?name=Arsenal&leagueId=1` → 200.
   - H2H tables populated, Last 5 computed when feature flag enabled.
2. Fixture → Analysis (league_id=2):
   - Clicked a League 2 fixture (e.g., Charlton vs Millwall).
   - URL contained `?leagueId=2`.
   - Team lookups hit `/api/teams/by-name?...&leagueId=2` and resolved.
   - H2H sections populated correctly.
3. Manual H2H search with wrong league:
   - When team not found for current league, fallback prompt helped disambiguate and/or suggested switching leagues (hint shown).
4. League context persistence:
   - After navigation, `activeLeagueId` stored in localStorage and reused across requests.

Console Diagnostics Observed:
- `[LeagueContext] Set leagueId from fixture: <id>` upon navigation to Fixture Analysis.
- `[TeamService] GET /api/teams/by-name?...&leagueId=<id>` confirming correct scoping.

## Remaining Suggestions

- Add unit tests for:
  - FixturesComponent.openAnalysis to assert leagueId included in router navigation.
  - PlayedMatchesSummaryComponent to validate that leagueId from query params is written to LeagueContextService before H2H fetching.
- Consider enhancing LeagueContextService to optionally accept an ActivatedRoute or URL string for automated param parsing in a future refactor.
- Extend backend H2H endpoints to accept an optional `leagueId` for name-based queries to further constrain lookups server-side if needed.
- Add a small toast UI component to ask user to switch league when 404 occurs on team resolution, with one-click setting of LeagueContext.

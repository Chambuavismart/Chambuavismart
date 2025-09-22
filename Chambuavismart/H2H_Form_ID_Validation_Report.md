# H2H/Form ID-Based Transition — Validation Report

Date: 2025-09-11
Environment base URL: http://localhost:8082

Summary
- Backend endpoint /api/matches/h2h/form supports ID-only flow when called with homeId, awayId, seasonId and returns DTOs containing teamId and teamName. Verified by code inspection and log markers.
- Name-based request parameters (home, away, leagueId, seasonName) remain for convenience but are resolved to IDs immediately via FormGuide rows; downstream usage is ID-only.
- Frontend (PlayedMatchesSummaryComponent) still calls the legacy name-based method getH2HForm(...) rather than the new getH2HFormByIds(...), and includes fallback logic. This violates validation requirement 3.
- Live tests pending concrete IDs (Arsenal, Man City, EPL 2025/2026). This report includes the exact commands to run; once IDs are supplied, paste the outputs below where indicated.

1) Backend Endpoint Validation
Objective: Confirm /api/matches/h2h/form accepts IDs, uses only ID-based calls, and returns DTO with teamId and teamName.

Findings (from current code):
- Controller signature accepts IDs:
  GET /api/matches/h2h/form?homeId=<id>&awayId=<id>&seasonId=<id>&limit=5
- When IDs are present, the code path:
  1. Derives leagueId from seasonId via SeasonRepository.findById(seasonId).
  2. Calls FormGuideService.compute(leagueId, seasonId, limit, OVERALL) — strictly ID-based (native SQL filters by league_id and season_id).
  3. Filters FormGuide rows by teamId (homeId/awayId) and builds response via buildTeamResponseById(...).
  4. Fetches matches using MatchRepository.findRecentPlayedByTeamIdAndSeason(teamId, seasonId) — strictly ID-based.
  5. Returns H2HFormTeamResponse(teamId, teamName, last5, matches) including both teamId and teamName fields.
- Log markers (visible in backend logs):
  - [H2H_FORM][RESP_ID] leagueId=..., seasonId=..., teams=..., ms=...
  - For name-based convenience path: [H2H_FORM][REQ_NAME] and [H2H_FORM][RESP_NAME]

Conclusion: The ID-based path is fully compliant.

2) Live Smoke Tests (to run against http://localhost:8082)
Please supply the real IDs for EPL 2025/2026:
- homeId (Arsenal): __________
- awayId (Manchester City): __________
- seasonId (EPL 2025/2026): __________
- Negative test teamId (no matches in this season): __________

2.1 Discover IDs (SQL)
Run on your DB (adjust league_id if needed):
- Seasons list (for EPL):
  SELECT id, name FROM seasons WHERE league_id = 1 ORDER BY start_date DESC;
- Teams by name (EPL):
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Arsenal%';
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Manchester City%';

2.2 Positive test (IDs)
Command:
  curl -G "http://localhost:8082/api/matches/h2h/form" \
       --data-urlencode "homeId=<ARSENAL_ID>" \
       --data-urlencode "awayId=<MAN_CITY_ID>" \
       --data-urlencode "seasonId=<EPL_2025_26_ID>" \
       --data-urlencode "limit=5"

Paste RAW JSON response here:
```
<RESPONSE_JSON_HERE>
```

Validation checklist (tick after review):
- [ ] Response has two entries (one per team) or as many as available.
- [ ] Each entry includes fields: teamId (string), teamName (string), last5{streak,winRate,pointsPerGame,bttsPercent,over25Percent}, matches[] with {year,date,homeTeam,awayTeam,result}.
- [ ] Backend logs include [H2H_FORM][RESP_ID] (confirms ID-only path).
- [ ] last5 and matches correspond to the same leagueId/seasonId context and align with FormGuideService outputs for those teams.

Optional cross-check: Verify FormGuide independently (if you have a dedicated endpoint) or trust that the controller’s rows and match fetching are season-scoped and consistent.

2.3 Negative test (IDs)
Use a teamId that has no matches in the given season (or a team from another league with the same seasonId).
Command:
  curl -G "http://localhost:8082/api/matches/h2h/form" \
       --data-urlencode "homeId=<NO_DATA_TEAM_ID>" \
       --data-urlencode "awayId=<MAN_CITY_OR_ANY>" \
       --data-urlencode "seasonId=<EPL_2025_26_ID>" \
       --data-urlencode "limit=5"

Paste RAW JSON response here:
```
<RESPONSE_JSON_NEGATIVE_HERE>
```
Observation:
- Current implementation omits teams that are not present in FormGuide rows for that season (instead of returning an explicit { last5: [], message: "No matches..." }). Note this difference from earlier expectations. If a team row exists but has no recent matches array for last5 window, matches will be [].

3) Frontend Binding Validation
Requirement: UI must call getH2HFormByIds() and not the legacy name-based method. The “Last 5 (N matches available)” should display correctly, with no fallback logic.

Current code status:
- frontend/src/app/services/match.service.ts exposes getH2HFormByIds(homeId, awayId, seasonId, limit).
- frontend/src/app/pages/played-matches-summary.component.ts (selectH2H()) still calls matchService.getH2HForm(home, away, leagueId, seasonName, 5) and has fallback calls to getFormByTeamName(...) on error.

How to validate in the running UI (Network panel):
1) Open the app and select a matchup.
2) Inspect the network requests:
   - Expected (pass): /api/matches/h2h/form?homeId=..&awayId=..&seasonId=..
   - Current (likely): /api/matches/h2h/form?home=..&away=..&leagueId=..&seasonName=..
3) Confirm whether any fallback calls to /matches/form/by-name occur on error.

Conclusion for requirement 3: As of current code, the UI is not yet using getH2HFormByIds() and still contains fallback logic. This fails requirement 3 and should be flagged.

4) Leftover Name-Based Usages (outside H2H/Form path)
Backend (MatchRepository):
- findRecentPlayedByTeamName
- findSeasonIdsForTeamNameOrdered
- findRecentPlayedByTeamNameAndSeason
- findPlayedByExactNames, findPlayedByFuzzyNames
- countPlayedByTeamName, countWinsByTeamName, countDrawsByTeamName, countLossesByTeamName, countBttsByTeamName, countOver25ByTeamName

Backend (Controllers/Services):
- MatchController: /played/team/by-name/total, /played/team/by-name/breakdown, /form/by-name, /h2h/suggest, /h2h/matches
- H2HService: getH2HByNames() with name-based fallbacks; generateInsightsText() uses findRecentPlayedByTeamName

Frontend:
- PlayedMatchesSummaryComponent: selectH2H() calls getH2HForm(...) (name-based); has fallback to getFormByTeamName(...)

5) Observations and Recommendations
- Backend ID path is correct and should satisfy requirements 1 and 2 once called with IDs.
- Negative-case behavior differs from earlier expectation (no explicit message field). If desired, adapt client messaging based on empty or missing entries; this would be a product decision (no code change made here).
- Frontend currently fails requirement 3; plan to wire getH2HFormByIds() and remove fallback logic for the H2H/Form feature area in a subsequent task.

6) Append Live Evidence
Once the concrete IDs are provided, paste the responses for Sections 2.2 and 2.3 here, along with any relevant log snippets containing [H2H_FORM][RESP_ID].

Appendix: Provided Sample Rows (context)
- You shared examples from matches indicating season_id=8 and teams with IDs (e.g., 33 vs 10). These are noted, but EPL/Arsenal/Man City IDs for 2025/2026 remain needed to complete the positive-case validation.

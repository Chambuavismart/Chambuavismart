# Chambuavismart – Fixture Analysis Tab: Project Overview and Issue Analysis Report

Date: 2025-09-13

## Beginner-Friendly Summary
This project is a Spring Boot + Angular web app for football (soccer) analysis. The "Fixture Analysis" tab should let users:
- See overall counts of matches played across the database.
- Search for a team (min 3 letters) and view stats like wins/draws/losses, BTTS, Over 1.5/2.5.
- Compare two teams head-to-head (H2H), including last 5 results and form points (W=3, D=1, L=0).
- Use a Poisson-style model that relies on team stats and recent H2H data to predict outcomes.

The current problem: For some leagues, recent H2H matches do not show. The frontend tries to resolve team IDs by name (e.g., /api/teams/by-name?name=Everton&leagueId=2), gets 404, falls back to a search endpoint, and then still cannot finalize IDs. As a result, recent H2H and form sections are empty or incomplete. WebSocket logs show the Angular dev server reconnecting, which is usually harmless.

What to check first:
1) Make sure the teams actually exist in the database for the affected league (ID=2 in the logs) and that names/aliases are normalized correctly.
2) Verify the /api/teams/by-name endpoint returns exactly one match (200 OK) for the queried team and league, or returns helpful diagnostics when not found.
3) Confirm the frontend is using the right leagueId in the UI state and is picking a single team candidate from the search fallback.

---

## Project Overview

### High-Level Architecture
- Backend: Spring Boot (Java)
  - Controllers expose REST endpoints under /api (e.g., /api/teams, /api/matches).
  - Services implement domain logic (e.g., H2HService, FormGuideService).
  - Repositories are Spring Data JPA interfaces against the database (MatchRepository, TeamRepository).
- Frontend: Angular
  - Pages/Components: played-matches-summary.component.ts drives the "Fixture Analysis" tab.
  - Services: match.service.ts (match/H2H/form APIs), team.service.ts (team lookup), config/league-context services, poisson.service.ts (predictions).
- Data store: Relational database (JPA entities: Team, Match, League, Season, TeamAlias, etc.).

### Relevant Backend Components (observed in codebase)
- TeamController (backend/src/main/java/com/chambua/vismart/controller/TeamController.java)
  - GET /api/teams/search
    - Params: query, optional leagueId
    - Returns lightweight team suggestions with league info.
  - GET /api/teams/by-name
    - Params: name (required), optional leagueId, optional teamId
    - Resolves a single team by normalized name or alias; 404 if not found; 409 if multiple candidates.
    - Includes diagnostic logging and checks for space anomalies and trimmed-equality.
- TeamRepository
  - Normalization-aware lookups: findByNameOrAlias, findAllByNameOrAliasIgnoreCase, and league-scoped variants.
  - Search projections that include leagueId and leagueName for safer DTO mapping.
  - Diagnostic queries: countByTrimmedNameIgnoreCase*, countSpaceAnomalies().
- MatchRepository
  - H2H retrieval by team IDs and names (exact/fuzzy), including season- and league-scoped variants.
  - Recent matches by team ID or by name; helper queries for totals and statistics (wins/draws/losses/BTTS/Over 2.5/1.5).
- H2HService
  - Attempts to resolve teams from names (considering aliases) to IDs; tries set-based and single-ID H2H queries.
  - Falls back to name-based H2H queries if ID resolution fails.
  - Computes goal differential and generates insights, including last-5 form via repository calls.
- MatchController
  - Exposes match totals, team breakdowns, H2H suggestions, H2H match lists (by name or by IDs/season), last-5 form, and additional helper endpoints used by the frontend.

### Relevant Frontend Components
- played-matches-summary.component.ts
  - Central to the Fixture Analysis tab. Handles team search, H2H lookup, rendering of stats, and insights text.
  - Uses LeagueContextService to track the selected leagueId (e.g., [LeagueContext] Initialized with saved leagueId: 2 in logs).
  - Invokes TeamService to resolve team IDs by name and to fall back to /api/teams/search when /by-name returns 404 or 409.
  - Calls MatchService for totals (/matches/played/total), H2H matches (/matches/h2h/matches) and last-5 forms.
- match.service.ts
  - Wraps REST calls to /api/matches endpoints (played totals, H2H lists, counts, forms).
  - Helps pass the current leagueId via withLeague().
- team.service.ts (implied by imports and logs)
  - Wraps REST calls to /api/teams/by-name and /api/teams/search.

### Data Flow (UI → API → DB → UI)
1) User types team names into H2H inputs.
2) Frontend (TeamService)
   - GET /api/teams/by-name?name=<team>&leagueId=<id>
   - If 404 or 409: GET /api/teams/search?query=<team>&leagueId=<id>, then prompts the user to select a unique candidate to continue.
3) After resolving the two teams to IDs (or to final names), the frontend (MatchService)
   - GET /api/matches/h2h/matches with home/away names (or IDs+season in newer flow) to fetch recent H2H.
4) Backend (MatchController → H2HService/MatchRepository)
   - If IDs are available, uses H2H by IDs for accuracy. If names only, falls back to exact/fuzzy name queries.
5) UI renders up to 5 recent H2H matches and builds form summaries and Poisson predictions (which depend on recent match data).

---

## Functionality Analysis for Fixture Analysis Tab

### What the Tab Shows
- Total matches played across all leagues (MatchController.getTotalPlayedMatches → MatchRepository.countByResultIsNotNull or native fallback).
- Team stats by name (win/draw/loss, BTTS, Over 1.5/2.5), computed via repository count queries by team name.
- H2H search between two teams
  - Resolves both team names to IDs (preferred) or uses names directly.
  - Retrieves H2H matches (by IDs where possible; otherwise by names; orientation-aware or any-orientation depending on endpoint).
- Recent H2H matches (up to 5)
  - Displayed with date, home/away, result string; orientation consistently presented.
  - Points for form: W=3, D=1, L=0; derived for each side and summarized.
- Insights and Poisson
  - H2HService generates a text summary including goal differential, streaks, and points-per-game (PPG) trends.
  - Poisson predictions depend on recent H2H and broader team stats; accuracy degrades if H2H data is missing.

### Expected Behavior for Recent H2H Matches
- For a given pair (home, away) and selected league context, the app should show up to the 5 most recent played matches between them.
- If team IDs are resolvable, H2H is fetched via ID-based queries (more reliable when there are aliases/duplicates).
- If IDs cannot be resolved, name-based fallback should still return matches if the names match those stored on Match rows.

---

## Issue Diagnosis

### What the Logs Say
- [LeagueContext] Initialized with saved leagueId: 2
- Frontend attempts direct resolution:
  - GET http://localhost:8082/api/teams/by-name?name=Charlton&leagueId=2
  - GET http://localhost:8082/api/teams/by-name?name=Millwall&leagueId=2
  - GET http://localhost:8082/api/teams/by-name?name=Arsenal&leagueId=2 → 404 → fallback to /api/teams/search
  - GET http://localhost:8082/api/teams/by-name?name=Nottingham&leagueId=2 → 404 → fallback to /api/teams/search
  - GET http://localhost:8082/api/teams/by-name?name=Everton&leagueId=2 → 404 → fallback to /api/teams/search
  - GET http://localhost:8082/api/teams/by-name?name=Aston%20Villa&leagueId=2 → 404 → fallback to /api/teams/search
- Component message: [PlayedMatches] Could not resolve team IDs in league for Object
- Angular dev server reconnects WebSocket (ws://localhost:4200/ng-cli-ws) — typical during dev; not likely the root cause for backend 404s.

### What This Means in Code Terms
- TeamController.by-name is strict and designed to return:
  - 200 with a single TeamDto when a unique normalized match exists in the (leagueId, name/alias) scope.
  - 404 if no candidate is found (with diagnostics logged server-side).
  - 409 if multiple candidates match (e.g., duplicate data or ambiguous alias).
- The frontend expects /by-name to succeed per team. If it fails, it calls /search and should ask the user to choose a candidate. The log "Could not resolve team IDs in league for Object" indicates the fallback didn’t lead to a selected and confirmed team ID, leaving the component without IDs to proceed with ID-based H2H.
- H2HService can still fall back to name-based queries (MatchRepository.findPlayedByExactNames / findPlayedByFuzzyNames). If those are not returning matches either, it usually implies that the names supplied do not match the stored Match.homeTeam.name / awayTeam.name values in the DB for that league/season combination, or the league scoping on ID resolution is preventing selection.

### Why It’s League-Specific
Possible reasons the issue appears for leagueId=2 while working elsewhere:
1) Data/Normalization Inconsistency
   - Teams for league 2 might be stored with names requiring normalization or with aliases not recorded (e.g., "Oxford Utd" vs "Oxford United"). TeamNameNormalizer is used on both API and repository sides, but if the Match rows’ team names differ from Team names, or aliases are incomplete, /by-name will 404 while name-based H2H may also fail or be sparse.
   - Trailing/leading spaces or punctuation variants could cause normalized name mismatches. TeamController logs trimmedEqCount and spaceAnomalies to help diagnose.
2) Missing or Incorrect League Assignment
   - Teams in league 2 may not actually be assigned to leagueId=2 in the Team table, so league-scoped lookups return empty.
3) Duplicate or Ambiguous Entries
   - Multiple candidates (same normalized name or alias mapped to different teams) could cause 409 conflicts. If the frontend doesn’t complete the disambiguation, H2H proceeds without IDs and fails the last-5 fetch.
4) Frontend League Context Desync
   - If LeagueContextService says 2 but the backend/team data corresponds to a different league, by-name lookups with ?leagueId=2 will 404. The fallback /search might return candidates from other leagues, which the component then rejects for not matching the current league.
5) Partial Uploads or Season Separation Effects
   - For certain leagues, matches may be imported but teams/aliases weren’t aligned after “season separation” refactors. That yields teams that can be found globally but not in the league, or matches that reference teams not present under leagueId=2 in the Team table.

### Secondary Observations
- The Angular dev server WebSocket disconnections are unlikely to be the cause; they are common during development and only affect hot reloads.
- The favicon 404 is cosmetic.

---

## Recommendations and Next Steps

### 1) Backend Data Integrity Checks (High Priority)
- Verify Team Rows for League 2
  - Query: List team names and ids for leagueId=2. Confirm expected teams exist (Arsenal, Nottingham Forest, Everton, Aston Villa, etc.).
  - Check for normalization anomalies: trailing spaces, punctuation, unexpected abbreviations ("Utd" vs "United").
- Verify Team Aliases
  - Ensure common aliases (e.g., "Nottingham" for "Nottingham Forest", "Oxford Utd" for "Oxford United") exist in TeamAlias for league 2 teams.
- Cross-check Match Rows vs Team Names
  - For the problematic pairs, verify that Match.homeTeam.name and Match.awayTeam.name values exactly correspond to the Team entries (or that alias normalization bridges the difference). If Match data uses a name variant that doesn’t exist as the Team’s normalized name or alias, /by-name will fail and name-based H2H may also not match.
- Use TeamController Diagnostics
  - Hit /api/teams/by-name?name=<X>&leagueId=2 and inspect server logs for [Team][ByName] trimmedEqCount and spaceAnomalies. A nonzero trimmedEqCount with 404 suggests normalization mismatch (e.g., extra spaces or alias missing).

### 2) API Behavior Verification
- Manually test /api/teams/by-name for each failing case with leagueId=2:
  - Expected: 200 with a single TeamDto containing id, name, leagueId.
  - If 404: Immediately try /api/teams/search?query=<X>&leagueId=2 to see candidates and confirm whether the desired team is within league 2.
  - If 409: Multiple candidates returned; ensure frontend selection flow is working and confirms the chosen teamId.
- Validate that TeamController.toDto returns league info without triggering LazyInitialization (it currently includes leagueId and leagueName defensively).

### 3) Frontend Checks
- Confirm LeagueContext is set correctly when initiating H2H.
- Validate the fallback selection flow after /by-name failure:
  - The component should prompt the user to select exactly one candidate from /search and then store the resolved teamId.
  - The error log “[PlayedMatches] Could not resolve team IDs in league for Object” indicates the chosen candidate might not be within the selected league or that no candidate was chosen. Ensure the UI displays a clear selection dialog and blocks progression until both teams are confirmed.
- If available, prefer ID-based H2H endpoints via match.service.getH2HMatchesByIds/homeId+awayId (+seasonId when needed) for more reliable retrieval, especially when aliases exist.

### 4) League ID and Season Handling
- Ensure the right leagueId is applied consistently when resolving teams and when fetching H2H (some endpoints are league-scoped while others are global/name-based).
- If the analysis is intended to be season-specific, resolve seasonId explicitly (match.service.getSeasonId) and use the ID-based H2H-by-season endpoint for last-5 matches.

### 5) Logging and Error Handling Improvements
- Backend
  - Keep the existing logs in TeamController (/by-name and search) — they already provide trimmedEqCount and anomaly counts.
  - Consider adding league-scoped variants for findAllByNameOrAliasIgnoreCase to reduce false positives across leagues (if not already used in by-name path).
- Frontend
  - Add clear UI messages when a team is not found in the selected league and present the league of each search candidate to guide users.
  - Log the final resolved team IDs before making H2H calls to confirm the flow.

### 6) Database Maintenance
- Normalize and clean team names (trim spaces, standardize punctuation) and ensure TeamNameNormalizer aligns with how names are stored.
- Populate TeamAlias for known shorthand (e.g., Utd/United, Forest/Nottingham Forest) per league.
- Audit for duplicates within the same league and resolve them to avoid /by-name 409 conflicts.

### 7) Troubleshooting Checklist (Actionable)
- For a failing pair (e.g., Everton vs Aston Villa, leagueId=2):
  1. GET /api/teams/by-name?name=Everton&leagueId=2 → Is it 200? If 404, check logs for trimmedEqCount, anomalies.
  2. GET /api/teams/search?query=Everton&leagueId=2 → Does the correct team show with leagueId=2?
  3. Repeat for the away team.
  4. If both resolve, try ID-based H2H: GET /api/matches/h2h/matches?homeId=<id1>&awayId=<id2>&seasonId=<sid>&limit=5.
  5. If names still fail, try name-based any orientation: GET /api/matches/h2h/matches-any-orientation?teamA=Everton&teamB=Aston%20Villa&leagueId=2.
  6. If H2H by names returns 0 but you know matches exist, inspect Match rows for name variants and add necessary TeamAlias entries or standardize names.

---

## How the Poisson Model Depends on Data
- Inputs:
  - Team offensive/defensive rates derived from historical matches (wins/losses goals scored/conceded).
  - H2H recent form trends used to adjust expectations.
- Impact of Missing H2H Data:
  - If recent H2H is missing for a league, the model relies more on general team stats and becomes less reflective of the specific matchup.
  - Ensuring reliable ID-based H2H retrieval improves the Poisson estimates.

---

## Additional Notes and References
- Project documentation already includes multiple investigative reports (e.g., H2H_* and Played_Matches_* MDs) that trace similar issues. Cross-reference these when auditing league 2.
- Coding/Process Guidelines
  - See .junie/guidelines.md (if present) and prior reports in the repo root for conventions and testing practices.
- Dev Environment
  - WebSocket disconnects from webpack-dev-server are typical in dev; they do not directly affect backend lookup behavior.

---

## Suggested Minimal Repro Steps (No Code Changes)
1) Start backend on 8082 and frontend on 4200.
2) Set League to the problematic one (leagueId=2) in the UI.
3) Open browser dev tools → Network tab.
4) Attempt H2H: Arsenal vs Nottingham (as per logs).
5) Observe /api/teams/by-name calls → if 404, note server logs from TeamController.
6) Use /api/teams/search to confirm candidates and their leagueId.
7) If candidates exist but not in league 2, fix data (team league assignment or aliases). If none exist, load missing teams.
8) Once both sides resolve to IDs, confirm last-5 H2H renders and that Poisson predictions update.

---

## Conclusion
The failure to populate recent H2H matches in some leagues is most likely caused by data and normalization mismatches in team resolution under a given leagueId (e.g., leagueId=2). The backend is equipped with normalization-aware queries and diagnostics; the frontend has a fallback search path. Focus efforts on verifying league-scoped team presence, alias completeness, and consistency between Team names and Match row names. Once team resolution is reliable, the recent H2H and Poisson features should function as expected for the affected leagues.

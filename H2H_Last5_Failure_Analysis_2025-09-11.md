# Played Matches → H2H “Last 5” Failure Analysis (2025-09-11)

## Overview
- Intended behavior: The H2H “Last 5” block should fetch and display each team’s last N matches (default 5) scoped strictly to the current head‑to‑head search context — i.e., the same league and season that produced the H2H match list. This must mirror the Form Guide tab’s filters: leagueId + seasonId (or seasonName), scope=overall, limit=5. If fewer than 5 matches exist in the current season, show the available N with metrics (streak, win rate, PPG, BTTS%, O/U%), not fall back to prior seasons.
- What the latest patch was supposed to fix: Prior issues where the H2H Last 5 either used the wrong season (falling back silently) or stayed stuck on “Loading…”. The patch enforced strict season scoping on the backend endpoint `/api/matches/h2h/form` and adjusted the frontend to always render once a response is received, including partial counts (e.g., “3 matches available”).

## Observed Behavior
During testing after the patch:
- Some teams still show a streak that appears to come from a previous season (e.g., Manchester City showing LWLWD resembling 2024/2025 history).
- Some teams (e.g., Arsenal) show “No recent matches found” despite matches existing in the 2025/2026 season in the Form Guide tab.
- In the UI, the Last 5 count reflects the number of items we reconstructed from the `matches` array rather than the FormGuide’s own last-5 sequence, potentially causing mismatched sequences when the backend’s `rows` did not match the teamName reliably.

These symptoms indicate a remaining inconsistency between Played Matches H2H context and the Form Guide computation for the same league/season and team.

## Possible Root Causes

### Backend
1. League/Season mismatch at source inputs
   - The endpoint requires `leagueId` and `seasonName` from the H2H context. The frontend currently uses hardcoded defaults (`leagueId = 1`, `seasonName = '2025/2026'`). If the actual H2H selection is from a different league or if the Season name string doesn’t match exactly what’s stored (e.g., whitespace, different case, metadata like “2025-26”), `seasonRepository.findByLeagueIdAndNameIgnoreCase` may return empty, causing the endpoint to return an empty array. Result: frontend renders safe defaults → “No recent matches found”.
2. Team name matching differences
   - `MatchController.getH2HForms` matches `FormGuideRowDTO` by `equalsIgnoreCase` then by `contains`. If the imported team names in matches vary (suffixes like “FC”, punctuation, or diacritics), the relaxed `contains` may still fail or match the wrong entity in leagues with multiple similarly named clubs. This may produce either no row (→ no data) or a mismatched row (→ wrong streak).
3. `FormGuideService.compute` and recency window
   - The service correctly enforces `(m.league_id = ?1 AND m.season_id = ?2)` and orders by date desc then round desc, but it builds the `lastResults` by taking a `windowSize = min(list.size(), limit)`. If the `limit` is passed correctly (5), the data should be season-correct. However, if the frontend provides a wrong `seasonName` or wrong `leagueId`, the DAO will compute valid data but for the wrong scope, which then appears as “old season” in the UI.
4. Strict no-fallback behavior may cause partial empties
   - The patch intentionally removed fallbacks. If one of the teams doesn’t have any computed row (e.g., name could not be matched in the row list for that season), the endpoint returns only the other team’s response. The frontend expects two entries but converts the array with a naïve assumption of order and existence; this can mis-assign or show empty.
5. `buildTeamResponse` uses `findRecentPlayedByTeamNameAndSeason(teamName, seasonId)` then filters by league
   - If `teamName` doesn’t exactly match the stored team’s name for that season, this query may return 0 matches even though the Form Guide did include that team via teamId. The form guide rows are grouped by teamId coming from SQL joins. But the match list fetch is name-based, not id-based; this discrepancy can lead to a valid `last5` metrics but an empty `matches` list for recent reconstruction, leading the frontend to compute an empty `recentResults` and display “No recent matches found” or wrong points.

### Frontend
1. Hardcoded context for league/season
   - `leagueId: number = 1` and `seasonName: string = '2025/2026'` are hardcoded in `PlayedMatchesSummaryComponent`. If the user’s H2H selection pertains to a different league/season, the backend receives the wrong context, yielding either wrong-season rows or empties.
2. Response array ordering and mapping assumptions
   - The code assumes `[home, away]` order but then tries `safe.find(...)` for the first team and `safe[1]` for the second. If only one team matched in the backend (e.g., away team unmatched), we may assign the existing item to home and then compute away from `safe[1]` (undefined). This can show reconstructed results from the wrong perspective or default empties.
3. Reconstructing last-5 sequence from `matches` rather than trusting backend strings
   - The component builds `recentResults` by parsing `team.matches` results, oriented by `teamLabel`. If `matches` is empty (due to the name-based query mismatch on backend), the UI shows an empty sequence even when the backend’s `last5.streak`, `winRate`, `pointsPerGame` are non-zero for that season (since they came from FormGuide rows). The UI currently does not use an authoritative last-5 sequence from the backend; it attempts to infer it. Any discrepancy in `matches` retrieval leads to blank or mismatched UI sequences.
4. Stuck state eliminated but edge states still confusing
   - While “Loading…” is resolved, the “No recent matches found” appears when `recentResults.length === 0`. Because `recentResults` is reconstructed and depends on `matches`, users may see “No recent matches found” even when metrics exist.

### Context Discrepancies between Played Matches and Form Guide
- Form Guide relies on `leagueId` + `seasonId` being explicitly selected in that tab. In Played Matches H2H, the current implementation does not derive the actual `leagueId/seasonId` from the selected H2H match context; instead, it uses defaults. This is the most likely source of season mismatch.
- Form Guide shows data by teamId; H2H Last 5 reconstructs by team name for `matches`. This ID vs name mismatch creates inconsistent outcomes between tabs even when both target the same season.

## Verification Steps
To pinpoint the root cause(s), perform these checks:

1. Backend request/response logs
   - Confirm logs emitted by `MatchController.getH2HForms`:
     - [H2H_FORM][REQ] leagueId, seasonName, limit, home, away
     - [H2H_FORM][STRICT_NO_SEASON] if season not found
     - [H2H_FORM][RESP] leagueId, seasonId, teams, ms
   - Validate that `leagueId` and `seasonName` in requests match the Form Guide tab’s inputs during the same test (e.g., Arsenal vs Man City in EPL 2025/2026).

2. Compare FormGuide vs H2H compute inputs
   - Call `/api/form-guide/{leagueId}?seasonId=<id>&limit=5&scope=overall` and check that the `FormGuideRowDTO` list includes rows for both teams with `teamId` and `teamName` values. Then compare with `/api/matches/h2h/form?home=Arsenal&away=Manchester City&leagueId=...&seasonName=2025/2026&limit=5` logs to ensure the same league/season. If different, the frontend is sending incorrect context.

3. Validate season lookup
   - Query the DB (or repository) for `SeasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, "2025/2026")` to ensure a row exists and its `id` matches the Form Guide tab’s selected season. If not found, verify actual stored season names (e.g., `2025/26` vs `2025/2026`).

4. Team mapping audit
   - Inspect the `rows` returned by `formGuideService.compute` for the requested league/season and verify the exact `teamName` strings. Make sure they match the user-provided `homeName`/`awayName` values used in the H2H endpoint. Print a debug dump of the names where matching fails.

5. Matches retrieval by name vs season
   - For each team that matched a `FormGuideRowDTO`, run `matchRepository.findRecentPlayedByTeamNameAndSeason(teamName, seasonId)` directly and confirm it returns the last N matches for that specific season and league. If 0, try the same query by teamId (if an alternative repository method exists or via a custom check) to verify whether the issue is purely a name mismatch.

6. Frontend parameter provenance
   - Trace where `leagueId` and `seasonName` are set in `PlayedMatchesSummaryComponent`. Confirm whether they come from the H2H match context or are hardcoded defaults. If hardcoded, instrument the selection flow to log what league/season the H2H match list uses, then compare with what is sent to `/h2h/form`.

7. Frontend mapping integrity
   - Log the raw response from `/h2h/form` in the component and confirm:
     - Does the array have 1 or 2 entries?
     - Which `teamId` and `teamName` do they correspond to (if available)?
     - Is `matches` empty while `last5` shows non-zero metrics? If yes, backend name-based matches retrieval is failing.

## Recommendations (Next Steps, No Code)
1. Enforce consistent context inputs
   - Stop using hardcoded `leagueId` and `seasonName` in the Played Matches H2H component. Instead, derive leagueId + seasonId/seasonName from the H2H selection source (the same objects that power `/h2h/matches`). Validate the IDs against the Form Guide tab inputs by logging them on both tabs and visually comparing.

2. Align identifiers: prefer teamId over teamName for matches fetch
   - In the backend endpoint, after obtaining `FormGuideRowDTO` (which includes `teamId`), use teamId-based queries to fetch the team’s last matches for that season and league. This will eliminate name discrepancies. If a teamId query isn’t available for “recent by team and season,” plan to add it and deprecate the name-based variant in this flow.

3. Provide authoritative last-5 sequence from backend
   - Add a compact last-5 sequence in the endpoint response (e.g., `last5.sequence: ["W","D","W",...]`) derived directly from `FormGuideRowDTO.getLastResults()` for the selected season and limit. The frontend should display this sequence rather than reconstructing from `matches`.

4. Improve diagnostic logs temporarily
   - Backend: when no team row is matched, log the list of team names found in the `rows` for that season to spot near-miss names.
   - Backend: when `matches` for a matched team row are empty, log the exact `teamName` used in the name-based query and the `teamId` from the row to highlight the mismatch.
   - Frontend: log the request parameters and the raw response from `/h2h/form` to quickly identify missing/incorrect context or partial responses.

5. Validate season naming consistency
   - Check how seasons are stored in DB for the target league(s). Align the frontend-provided `seasonName` with the exact stored naming (e.g., `2025/26` vs `2025/2026`). If necessary, map from a canonical display name to DB name before making the request, or pass `seasonId` rather than name.

6. Cross-compare with Form Guide tab outputs
   - For a test pair (Arsenal vs Manchester City):
     - Record the leagueId and seasonId used by the Form Guide tab.
     - Call `/api/form-guide/{leagueId}?seasonId=<id>&limit=5` and snapshot the rows for both teams.
     - Call `/api/matches/h2h/form` with exactly the same league/season and team names, then compare `last5` metrics and sequences. Any discrepancy indicates mapping issues.

7. UX handling for partial responses
   - If only one team returns data (due to unmatched name), the UI should explicitly indicate which team had no data for the specified season, rather than a generic “No recent matches found”. Also display the league and season in the header for clarity (e.g., “Premier League 2025/2026”).

---

By following the verification steps, you can identify whether the failure stems primarily from incorrect league/season inputs (most likely due to hardcoded defaults), team name mismatches between tabs, or the frontend reconstructing sequences from an empty `matches` array despite valid backend metrics. The recommendations prioritize ID consistency (teamId, seasonId), authoritative sequences from backend, and aligned context sourcing, which together should resolve the observed inconsistencies without reintroducing forbidden season fallbacks.

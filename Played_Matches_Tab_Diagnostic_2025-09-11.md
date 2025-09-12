# Played Matches Tab – H2H vs Last 5 Diagnostic Investigation (2025-09-11)

Author: Junie (JetBrains Autonomous Programmer)
Environment: Local dev (Windows), repo at C:\Users\Michael\Desktop\Chambuavismart001
Scope window: Changes up to current session

---

## 1) What API requests are being sent by the Played Matches tab?

Based on the current frontend code (frontend/src/app/pages/played-matches-summary.component.ts and services/match.service.ts):

- H2H Results History (matches table)
  - Endpoint: GET /api/matches/h2h/matches
  - Params: { home: string, away: string }
  - Source: MatchService.getH2HMatches(home, away)
  - Trigger: selectH2H(home, away)

- Team Recent Form (Last 5)
  - Endpoint: GET /api/matches/h2h/form
  - Preferred param mode: IDs only
    - Params: { homeId: number, awayId: number, seasonId: number, limit: 5 }
    - Source: MatchService.getH2HFormByIds(homeId, awayId, seasonId, 5)
  - Name fallback mode (still supported by service, but component is effectively ID-only for Last 5):
    - Params: { home: string, away: string, leagueId: number, seasonName: string, limit: 5 }
    - Source: MatchService.getH2HForm(home, away, leagueId, seasonName, 5)
  - Trigger: selectH2H(home, away) when predictiveOn is true. The component attempts to read IDs from window context:
    - seasonId from (window as any).__SEASON_ID__
    - homeId from either selectedTeam.id (if selection matches) or (window as any).__HOME_TEAM_ID__
    - awayId from (window as any).__AWAY_TEAM_ID__
    - If any are missing or not numbers, the component does NOT call the endpoint and instead shows empty Last 5 states for both teams.

Conclusion on requests currently sent:
- H2H matches: always sent by team names.
- Last 5: only sent if all three IDs (homeId, awayId, seasonId) are available; otherwise no request is made and empty UI is shown.

---

## 2) Are teamId and seasonId passed correctly?

- For H2H matches: IDs are not used at all. The request uses only names (home, away). This is intentional for H2H blending across archives + current season and relies on backend H2HService’s name→ID resolution fallback.
- For Last 5: The component is ID-only. It will send homeId, awayId, seasonId if and only if these are present in the global window context (or selectedTeam.id for home) at the time of selection. There is no automatic derivation inside PlayedMatches from the H2H pair; therefore, if the page fails to provide __SEASON_ID__, __HOME_TEAM_ID__, and __AWAY_TEAM_ID__, the Last 5 call is not made.

Comparison with previous (working) version:
- Previously, Last 5 used name-based GET /api/matches/form/by-name?name=TEAM and would look across seasons, occasionally selecting a latest season via repository helper. It did not require IDs.
- Now, Last 5 strictly requires IDs+seasonId to ensure it is scoped to the latest/current season dataset. This is a behavioral change and can appear as a regression if the page does not supply IDs.

---

## 3) Returned payloads – Raw JSON shapes

- H2H Results History (/api/matches/h2h/matches)
  - Backend DTO structure (MatchController.H2HMatchDto):
    { year: number|null, date: string|null, homeTeam: string|null, awayTeam: string|null, result: string, season: string|null }
  - Note: The ‘season’ field was added in the current session; when Season has a name, it returns that; otherwise it may return the season id as a string; null for classic archive rows.

- Last 5 (/api/matches/h2h/form)
  - Backend response structure (H2HFormTeamResponse[]), as expected by MatchService.getH2HForm*():
    [
      {
        teamId: string,
        teamName: string,
        last5: { streak: string, winRate: number, pointsPerGame: number, bttsPercent: number, over25Percent: number },
        matches: [ { year: number|null, date: string|null, homeTeam: string|null, awayTeam: string|null, result: string } ]
      },
      { ... second team ... }
    ]
  - In the component, we reconstruct recentResults (W/D/L) from the matches array and derive pointsEarned from pointsPerGame.

Note: This report does not capture live JSON, but the shapes above are from current code definitions and controller DTOs.

---

## 4) If payloads are empty, likely reasons

- H2H Results History empty causes:
  - Name mismatch after all fallback paths (H2HService resolves team IDs by name/alias; then falls back to name-based queries). If both resolution and name-based queries find nothing, the response is an empty array.
  - Filtering: Only matches with status=PLAYED or with both goals present are considered in repository queries used by H2HService.

- Last 5 empty (or not shown) causes:
  - Most common: The component never calls /api/matches/h2h/form because IDs are missing. The code explicitly short-circuits when seasonId, homeId, or awayId are not numbers, and sets homeForm/awayForm to empty placeholders.
  - If the request is sent but returns empty: could be wrong IDs or seasonId not matching stored season data (e.g., seasonName vs actual, but in the ID-only call seasonId must be correct). Also possible: the specified season has no PLAYED matches for the team.

---

## 5) UI binding verification

- H2H table binding is straightforward:
  - h2hMatches: H2HMatchDto[] from getH2HMatches().
  - Template displays columns Year, Date, Home, Away, Result, Season.
  - Empty-state message: “No prior head-to-head matches”. Trigger: when h2hMatches?.length is falsy.

- Last 5 binding:
  - homeForm and awayForm are set from the /api/matches/h2h/form response via a mapping that reconstructs recentResults from matches.
  - Empty-state (“No recent matches found”) shows when homeForm (or awayForm) exists and recentResults.length === 0. Note the component may also simply not show the data if the endpoint is never called due to missing IDs; in that case, placeholders are set and the hint message appears.

- Data structure mismatches:
  - The component expects H2H form response as an array of H2HFormTeamDto. This matches the service typings.
  - The H2H matches endpoint returns H2HMatchDto[] (note: includes season now). The template uses h2hMatches directly; no mismatch like expecting matches[] vs h2hMatches[].

Conclusion: UI binding logic is consistent with the current DTOs. Empty states are driven either by empty arrays or by the early short-circuit when IDs are missing for Last 5.

---

## 6) Regression analysis – What changed that could cause H2H or Last 5 to “disappear”?

- Change 1: Last 5 switched from name-based to strict ID+seasonId-based fetching via /api/matches/h2h/form. If the surrounding page does not inject __HOME_TEAM_ID__, __AWAY_TEAM_ID__, and __SEASON_ID__ into window, the component will not issue the form request and will display empty Last 5 blocks. This is a likely regression trigger compared to the previous behavior that used /form/by-name and did not need IDs.

- Change 2: H2H matches endpoint itself remained name-driven but gained a season field in its DTO. There’s no regression here; instead, it enhances display. However, if the teams are typed in variants not present in DB (and aliases aren’t present), it can return empty. The H2HService still contains multiple fallbacks (IDs via alias resolution; exact name; fuzzy name), so this is less likely unless the input names are very off.

- Change 3: Frontend ID wiring for Last 5 is not yet complete in PlayedMatches. The component relies on global window vars and does not compute/resolve team IDs from the selected H2H pair. That increases the chance of missing IDs.

Summary: The disappearance of Last 5 is primarily due to the ID-only requirement without guaranteed context variables; H2H history disappearance would more likely be an input-name mismatch, not a DTO or binding mismatch.

---

## 7) Exact examples of requests (ready-to-run)

- H2H Results (names):
  curl -G "http://localhost:8080/api/matches/h2h/matches" --data-urlencode "home=Arsenal" --data-urlencode "away=Manchester City"

- Last 5 (IDs; recommended):
  curl -G "http://localhost:8080/api/matches/h2h/form" --data-urlencode "homeId=14" --data-urlencode "awayId=50" --data-urlencode "seasonId=1" --data-urlencode "limit=5"
  # Replace 14/50/1 with real IDs as per your DB.

- Last 5 (names+leagueId+seasonName; supported but not used by component currently):
  curl -G "http://localhost:8080/api/matches/h2h/form" --data-urlencode "home=Arsenal" --data-urlencode "away=Manchester City" --data-urlencode "leagueId=1" --data-urlencode "seasonName=2025/2026" --data-urlencode "limit=5"

---

## 8) Where does the breakdown occur? Mapping the chain

- For H2H Results History:
  UI → Request: names only → Backend H2HService → Repository queries (by ID sets, by single IDs, by name exact, by name fuzzy) → Response H2HMatchDto[].
  - If empty: most likely the names didn’t match any team/alias in DB; less likely a repository filter because queries accept both PLAYED or non-null goals. No DTO mismatch in frontend — the template uses returned array directly.

- For Last 5:
  UI → If IDs not present → no request made → homeForm/awayForm set to empty → UI shows "No recent matches found".
  UI → If IDs present → Request with IDs → Backend FormGuideService + repository findRecentPlayedByTeamIdAndSeason → Response array of two teams with last5 + matches.
  - If response empty despite IDs: seasonId likely incorrect or teams lack matches in that season.

Conclusion:
- Breakdown for Last 5 is most often before the request: missing IDs in the page context.
- Breakdown for H2H is less likely in DTO binding; if empty, it is early in the chain (name mismatch).

---

## 9) Recommendation: Where to fix

Priority order:
1) Frontend binding/context: Ensure the Played Matches tab always supplies homeId, awayId, and seasonId when requesting Last 5. Options:
   - Propagate IDs from the H2H suggestion selection (extend suggestion payload to include IDs) or resolve IDs immediately upon H2H selection via a TeamService lookup.
   - Source seasonId from a central Season context service rather than window variables.
2) Backend query logic (if we still see empty H2H Results with valid names):
   - Add more robust alias matching in TeamRepository for H2HService, or provide a direct endpoint that accepts team IDs for H2H matches (keeping name-based for convenience).
3) DTO mapping:
   - Consider including an explicit last5.recentResults array from the server in /h2h/form so the frontend doesn’t have to reconstruct W/D/L from matches.

Rationale:
- The observed regression results from the stricter, ID-only Last 5 path without guaranteed IDs from the UI. Fixing context/ID sourcing will restore functionality without relaxing season scoping.

---

## 10) Quick validation checklist (post-fix)

- When selecting an H2H pair, confirm the component logs and network tab show a GET /api/matches/h2h/form?homeId=..&awayId=..&seasonId=.. request.
- Verify response contains two array elements (both teams) with non-empty matches when applicable.
- Confirm H2H table still populates and now shows Season column values.
- Check empty states:
  - Case A (no current-season matches): Last 5 shows "No recent matches found"; H2H table may show archive-only rows.
  - Case D (no history at all): H2H shows "No prior head-to-head matches".

---

Appendix: Code references
- Frontend: played-matches-summary.component.ts (selectH2H, template bindings), match.service.ts (getH2HMatches, getH2HFormByIds, getH2HForm)
- Backend: MatchController.getH2HMatches (H2HMatchDto incl. season), H2HService.getH2HByNames (name→ID resolution and fallbacks), MatchRepository (findRecentPlayedByTeamIdAndSeason, name-based findRecentPlayedByTeamNameAndSeason)

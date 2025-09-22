# H2H Fallback Contradiction – Root Cause Analysis (2025-09-06)

Issue summary: In Match Analysis (e.g., Arsenal vs Manchester City), the H2H section shows the fallback message:

> “No prior head-to-head matches were found between {Home} and {Away} across available seasons.”

But the Form vs H2H vs Blended table immediately below shows H2H data populated for the last 6 matches (percentages and PPG). This proves that historical H2H matches are retrieved and processed by the backend. The contradiction is that the table is populated while the dedicated H2H block above shows the fallback.

This report explains likely causes, the data flow, where the divergence occurs, logic differences, and why the issue persists despite recent fixes. It concludes with specific “no‑code” next steps for alignment and verification.

---

## Observed behavior and code landmarks

Backend:
- MatchAnalysisController (POST /api/match-analysis/analyze) validates inputs and calls MatchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, leagueName, homeName, awayName, refresh).
- MatchAnalysisService integrates H2H into metrics:
  - Attempts strict ID-based H2H queries, then falls back to league-family and alias/name-based ID sets if needed.
  - When H2H matches are found, it computes H2H PPG, BTTS%, Over2.5% over a window (DEFAULT_H2H_LIMIT = 6) and sets h2hWindow/metrics.
  - DTO: MatchAnalysisResponse contains both h2hSummary (H2HSummary) and headToHeadMatches (flat list intended for UI), plus nested H2HSummary.matches (H2HMatchItem) for UI rendering.
- Repositories involved: MatchRepository, TeamRepository, TeamAliasRepository, LeagueRepository (for cross-league families).

Frontend:
- frontend/src/app/services/match-analysis.service.ts declares MatchAnalysisResponse with:
  - h2hSummary?: { lastN, ppgHome, ppgAway, bttsPct, over25Pct, matches?: H2HMatchItem[] }
  - headToHeadMatches?: { date, competition, homeTeam, awayTeam, homeGoals, awayGoals }[]
- The UI likely renders:
  - A metrics table (Form vs H2H vs Blended) driven by h2hSummary values (lastN, ppgHome, ppgAway, bttsPct, over25Pct), which are computed from any H2H retrieved.
  - A dedicated H2H list section driven by either h2hSummary.matches OR headToHeadMatches (raw rows). If the chosen property is empty/undefined, the UI prints the fallback string.

Related docs already in repo:
- H2H_Disappearance_Investigation_Report_2025-09-06.md (context and prior debugging hints)
- Match_Analysis_Explainability_* docs describe the H2H path and the fallback text.

---

## Root Cause Hypotheses

1) Split data sources: metrics vs raw list
- The metrics table uses h2hSummary (built from List<Match> h2hUsed). This is populated when any H2H matches are found (by IDs, across‑league, or name/alias fallbacks).
- The dedicated H2H list may be bound to a different property (headToHeadMatches) or expect H2HSummary.matches to be filled. If the service fills only the metrics (h2hSummary fields) but not the raw list, the UI would show numbers in the table but still display the “No prior H2H” fallback for the list.

2) Partial DTO population
- MatchAnalysisResponse provides multiple places to carry H2H rows: h2hSummary.matches and headToHeadMatches. If the backend currently computes metrics but does not convert the List<Match> to either of these raw-row DTOs, the UI list won’t have rows and will show the fallback.
- The code hints at intended support (H2HSummary.matches, HeadToHeadMatchDto) but there’s no strong indication in MatchAnalysisService that these are being set consistently alongside metrics.

3) Name/alias logic applied only to metrics path
- The recent fix added alias/name fallbacks in MatchAnalysisService to retrieve H2H when IDs are missing. That is clearly feeding the metrics computation.
- If the raw list UI relies on a separate endpoint or a separate mapper (e.g., H2HMatchesService or a different controller), that path may still use strict ID matching or be scoped to a single league without the cross‑league family logic. It would then return 0 rows while the metrics path (now robust) finds matches.

4) Season/league scoping mismatch
- Metrics code attempts cross-league family (via LeagueRepository.findIdsByNameIgnoreCaseAndCountryIgnoreCase) then strict league fallback, and also uses alias/name sets.
- A different path for the raw list might require exact season ID or exact league ID without cross‑league fallback, yielding zero rows despite matches existing in sibling leagues/seasons considered by metrics. The UI would then diverge.

5) Frontend binding mismatch
- The table may read h2hSummary values (present), while the H2H list component may be bound to headToHeadMatches (absent) instead of h2hSummary.matches (or vice versa). A simple property name mismatch can trigger the fallback even when the backend sent the data in the other field.

---

## Why one section populates while the other shows fallback

- The metrics are computed from the in-memory List<Match> h2h that MatchAnalysisService retrieves using broadened logic (IDs → cross‑league family → alias/name‑based sets). Therefore, Form vs H2H vs Blended shows populated H2H values.
- The dedicated H2H list relies on raw rows. If the backend does not set headToHeadMatches or H2HSummary.matches from the same h2h list, the frontend sees an empty array/undefined and triggers the fallback message.
- Alternatively, if the frontend expects headToHeadMatches while the backend only fills H2HSummary.matches (or vice versa), the UI for the list won’t find data even though the metrics are present.

In short: the same underlying H2H retrieval populates metrics, but the raw match list rows are not passed through (or not read correctly by the frontend), so the list still falls back.

---

## Data Flow Trace and divergence

1) Ingestion → Database
- Matches ingested into database tables (not changed here). Data is present as evidenced by metrics derived from actual matches.

2) Backend Services
- MatchAnalysisController → MatchAnalysisService.analyzeDeterministic(...)
- MatchAnalysisService attempts, in order:
  - Strict pair by IDs: matchRepository.findHeadToHead or findHeadToHeadAcrossLeagues
  - Fallback via team name/alias to sets: matchRepository.findHeadToHeadByTeamSets or ...AcrossLeagues
- When matches found, metrics are computed. Variable h2hUsed is set to the list used.

3) DTO mapping
- MatchAnalysisResponse has:
  - h2hSummary: lastN, ppgHome/ppgAway, bttsPct, over25Pct, and optional matches[] (H2HMatchItem)
  - headToHeadMatches: an alternative flat list with more detailed fields
- Divergence: There is no clear evidence that MatchAnalysisService always populates either h2hSummary.matches or headToHeadMatches from h2hUsed. If it populates neither, only metrics appear.

4) Frontend rendering
- The metrics table reads h2hSummary.* and shows values.
- The H2H list likely checks a property (e.g., response.headToHeadMatches?.length or response.h2hSummary?.matches?.length). If zero/undefined → show fallback.

Therefore, the divergence point is at DTO mapping. Metrics are derived, but raw rows are not consistently mapped to the response field(s) that the H2H list reads.

---

## Logic differences to double‑check

- ID vs name/alias resolution:
  - Metrics path: explicitly tries aliases and partial name contains across league families.
  - Raw list path (if distinct): may still require strict IDs or single-league scoping, returning empty.

- Field selection consistency:
  - Metrics path populates numeric fields only.
  - Raw list display depends on either h2hSummary.matches or headToHeadMatches. Confirm which property the frontend H2H list uses and ensure the backend populates that exact property.

- Fallback trigger condition:
  - UI likely does: if (!matches || matches.length === 0) → show fallback text. If it checks a different property than the one populated, it will fall back erroneously.

---

## Why the issue persists despite the “use names/aliases when IDs unavailable” fix

- The fix clearly applies to the retrieval used by metrics in MatchAnalysisService (see: alias-based ID set queries and cross-league family logic).
- However, the H2H raw list in the UI is probably driven by a different path that wasn’t updated:
  - Either a separate service/controller/endpoint dedicated to listing raw H2H matches still uses strict IDs or single-league scope; or
  - The same endpoint returns metrics only and never maps h2hUsed into headToHeadMatches/H2HSummary.matches; or
  - Frontend binds to a property the backend is not populating.

Thus, the fix improved metrics but did not affect the list-rendering path, leaving the fallback condition intact.

---

## Next steps (no coding yet)

Backend alignment actions:
1) Decide on a single canonical field for raw H2H rows in MatchAnalysisResponse and use it everywhere. Recommendation:
   - Use h2hSummary.matches: H2HSummary.matches (H2HMatchItem[]) – minimal, tailored for UI list.
   - Alternatively, if the UI prefers a richer table, settle on headToHeadMatches; but pick one and standardize.
2) In MatchAnalysisService, after computing metrics and having h2hUsed, map the same list into the chosen field:
   - For each match m in the first N used for metrics, create H2HMatchItem { date: ISO yyyy-MM-dd, home: m.homeTeam.name, away: m.awayTeam.name, score: “hg-ag” }.
   - Attach to response.h2hSummary.matches (or response.headToHeadMatches if that’s the standard).
3) Ensure cross-league/alias logic used for metrics also feeds the raw list. Avoid a second repository call for the list; reuse h2hUsed to guarantee parity.

Frontend verification actions:
4) Confirm which property the H2H list component uses. If it uses headToHeadMatches but backend fills h2hSummary.matches (or vice versa), update the binding to the canonical one.
5) Update fallback condition to check the agreed canonical property only.

Logging/DB checks:
6) Temporarily log the size of h2hUsed and the size of the DTO list populated:
   - [DEBUG] H2H matches found: {h2hUsed.size()}, window used: {window}
   - [DEBUG] H2H DTO rows populated: {matchesDto.size()}
7) If using a separate endpoint for raw list, add debug logging around its repository call to confirm parameters (leagueIds set, team ID sets resolved, etc.). Verify that it does not use stricter criteria than the metrics path.
8) DB inspect (read-only) a couple of recent Arsenal–Man City entries to confirm presence across multiple seasons/leagues if cross‑league families are expected.

Safeguards:
9) If the UI needs richer fields (competition, venue), add them to H2HMatchItem or consistently use headToHeadMatches across both metrics list and any detailed view.
10) Add a small unit test to assert that when h2hUsed is non-empty, the DTO list is also non-empty and contains N rows equal to min(DEFAULT_H2H_LIMIT, h2hUsed.size()).

---

## Concise root cause statement
The H2H metrics table and the H2H raw list are fed by different representations of the same data. The metrics path in MatchAnalysisService reliably finds H2H via IDs/aliases/cross‑league logic and computes percentages/PPG, but the raw match rows are not being populated into the DTO field that the UI’s H2H section reads (or the UI reads a different field). As a result, the list appears empty and triggers the fallback while the table shows valid H2H metrics.

---

## Recommendation
- Standardize on a single source of truth: populate and render the same h2hUsed list for both metrics and the UI list. Prefer h2hSummary.matches in the DTO for simplicity.
- Make the frontend’s H2H list consume that field exclusively and show fallback only if that specific list is empty.
- Add targeted logs to confirm non-zero matches are being mapped into the DTO and arriving at the client.

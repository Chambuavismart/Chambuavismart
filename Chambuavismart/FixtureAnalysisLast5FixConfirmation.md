# Fixture Analysis — Last 5 Fix Confirmation (Pre‑Implementation)

Author: Junie (autonomous programmer)
Date: 2025-09-16 11:10 (local)
Scope: Confirmation and alignment before coding. No code changes are included here.

---

## 1) Frontend Flow Confirmation

What we observe in code and logs, and what we need to confirm from the product side:

- Current capability
  - MatchService exposes both:
    - getH2HFormByIds(homeId, awayId, seasonId, limit)
    - getH2HFormByNamesWithAutoSeason(home, away, leagueId, seasonName?, limit)
  - This satisfies the ID-first plan outlined in prior reports.

- Observed mismatch in logs
  - Logs like: [H2H_MATCHES][REQ] home='null' away='null' homeId=780 awayId=651 suggest the component sometimes passes IDs but the name fields are null, hinting that a legacy name-based path may still be invoked under certain UI states (navigation/refresh, or when seasonId is not yet resolved).

- Confirmation requested
  1) Should PlayedMatches always use getH2HFormByIds after both teams’ IDs are resolved (and after seasonId is available)?
  2) If seasonId is not yet known, is it acceptable to block H2H form until /matches/season-id resolves, or do we still want a temporary name-based fetch (knowing it can misrepresent data in continental comps like UCL)?
  3) Are there known UX flows where resolved IDs are lost (route change, tabs switching, or race conditions with season context), forcing a fallback to the name-based path?

- Proposed stance (to validate)
  - Enforce ID-based calls once IDs are known. If seasonId is pending, briefly show a loading state until seasonId is resolved via /matches/season-id. Avoid name-based path for UCL/continental contexts because teams come from external domestic leagues.


## 2) Backend: SourceLeague and LazyInitializationException

- Current behavior
  - TeamController returns TeamDto with leagueId and leagueName using projection-based searches. In global fallback selection, Team entities may be used directly and their league association can be lazily loaded.
  - MatchController.getH2HForms supports an ID-first branch (preferred) and a name-based branch.
  - FormGuideService.computeForTeams executes per-team scoped form and global fallback (all competitions) if <3 matches.
  - buildTeamResponseById constructs matches[] from season and adds global matches if isFallback=true.

- Exception seen
  - LazyInitializationException: could not initialize proxy [Team#779] - no Session while accessing Team.league.name to compose sourceLeague during fallback logging/DTO creation outside an active session.

- Confirmation requested
  1) Where exactly is sourceLeague currently constructed? In TeamController (global fallback logs/DTO), FormGuideService, or MatchController when building notes? (From current code snapshot, FormGuideRowDTO has no sourceLeague field; sourceLeague may be created in controller logs or another DTO.)
  2) Is Team.league configured lazy (default)? If yes, confirm the specific method(s) that access Team.league.name without a transaction (e.g., logging in controllers or while assembling response notes).

- Proposed stance (to validate)
  - Provide sourceLeague via projection or explicit fetch within a @Transactional boundary, materializing a simple string field at selection time; never traverse lazy associations in controllers.


## 3) Data Preconditions for Global Fallback

- Intended data
  - Ath Bilbao (id=780) with domestic La Liga matches; Arsenal (id=651) with Premier League matches.
  - UCL context (leagueId=2) likely offers sparse or no seasonal matches for these teams in the selected season, making fallback to global necessary.

- Confirmation requested
  1) Do we have played matches in DB for teamId=780 and 651 from their domestic leagues (Footballworld or CSV ingestion)?
  2) Any known gaps in ingestion (date missing, future-dated rows, or status not PLAYED) that could cause matchRepository.findRecentPlayed… queries to return empty?

- Proposed stance (to validate)
  - If domestic matches are missing, Last 5 will remain empty even after logic fixes. We’ll need to ingest/repair data before verifying UI.


## 4) Validation of the Proposed Fix Plan

- Frontend
  - Enforce ID-based H2H form retrieval after ID + seasonId resolution.
  - Render fallback message when isFallback=true (and show sourceLeague when provided). Ensure empty matches[] shows a helpful message instead of a generic error state.

- Backend
  - getH2HForms: when homeId/awayId/seasonId present, always use computeForTeams (already implemented). If request arrives via names but IDs can be resolved server-side, immediately pivot to ID-based branch.
  - Eliminate LazyInitializationException by:
    - Using repository projections or fetch-joins to load leagueName at team resolution time; or
    - Encapsulating the team selection in a @Transactional service method that produces a DTO with leagueName; never access Team.league in controllers.
  - Extend response to include sourceLeague only when fallback=true, sourced from the safe DTO string (not from lazy entities).

- FormGuide details
  - computeForTeams: per-team fallback remains correct. Global fallback queries should be per requested team only (not league-wide). Ordering must be deterministic, exclude future dates, and avoid duplicates when merging season + global.

- Testing
  - Unit: TeamController resolution (league-scoped, global single, global multi-candidate), FormGuideService per-team fallback, MatchController ID-branch behavior.
  - Integration: /api/matches/h2h/form?homeId=780&awayId=651&seasonId=<uclSeasonId> returns two teams, matches populated via global fallback if needed, includes sourceLeague, and no LazyInitializationException.
  - Frontend: Ensure ID-based method is called; verify fallback note rendering.


## 5) Additional Constraints and Edge Cases

- Multiple candidates on global resolution: Controller returns 409 with candidates[]. Frontend should allow re-selection or pass teamId explicitly to by-name endpoint for disambiguation.
- No matches globally: The endpoint should still return a valid payload with fallback=true and an explanatory note; UI should display a clear “No recent matches globally” message.
- Season without played matches: Prefer autoSeason latest-with-played for leagueId when seasonName not found; otherwise show explicit messaging about fallback.
- Partial success: If one team resolves and the other doesn’t, return one side’s last5 instead of failing the whole response.


## 6) What We Will Implement After This Confirmation

- Frontend
  - Ensure PlayedMatches caches IDs and seasonId and unconditionally calls getH2HFormByIds for H2H forms when available.
  - Display fallback note including sourceLeague when present.

- Backend
  - Add/ensure a safe sourceLeague string is available without lazy loading (projection or fetch join), and include it in the H2H form response when fallback=true.
  - In the name-based branch, try to resolve names to IDs and redirect to the ID-based path (server-side safeguard).
  - Keep computeForTeams as the sole path for H2H per-team computation.

- Observability
  - Add structured logs at getH2HForms entry indicating which branch is used (ID vs Names), teamIds processed, and whether fallback was triggered per team.


## 7) Open Questions (Please confirm)

1) Frontend policy: Always use ID-based H2H forms once IDs and seasonId are known? Any exceptions we should preserve?
2) Exact location(s) where sourceLeague is read today, causing LazyInitializationException, so we can replace that access with a projection-based value.
3) Data check: Confirm presence of recent domestic matches for teamId=780 and 651; if missing, should we prioritize data ingestion before code changes?
4) Team entity mapping: Is Team.league lazy? If yes, provide the repository methods we should use/extend to fetch leagueName safely by teamId.


---

If the above confirmations align with expectations, I will proceed to implement the minimal backend and frontend changes described, followed by the tests outlined in the plan.
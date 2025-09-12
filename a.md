# Why the fix did not eradicate the H2H problem

This report explains why the recent fix did not fully resolve the "H2H window shows no matches in the Match Analysis tab" issue, despite backend logs reporting non‑zero H2H pairs found during analysis.

## Executive summary
- The analysis pipeline and the standalone H2H matches endpoint are not fully aligned. The analysis service can find H2H pairs via broader, league‑aware and alias‑aware queries, while the `/api/matches/h2h/matches` endpoint can still return zero for the same pair because it takes a different path or stricter filters in certain conditions.
- Season scoping remains inconsistent across client and server flows (e.g., UI selecting 2025/2026 while logs show seasonId=1), leading to queries that legitimately return 0 and then propagate to the UI.
- Data normalization and historical imports have matches with goals set but status not explicitly set to PLAYED. When strict status filters are used by one code path and not the other, results diverge.
- Client fallback relies on the endpoint that is returning 0, so the UI renders “No prior H2H” even when analysis already computed H2H metrics.
- Caching and test coverage masked the discrepancy: unit tests pass, but the specific cross‑path mismatch (analysis vs endpoint) and season variance weren’t asserted.

## Detailed causes

1) Endpoint/path divergence remains
- Analysis service (`MatchAnalysisService`) retrieves H2H using this order:
  - If seasonId provided: `findHeadToHeadBySeason(leagueId, seasonId, homeId, awayId)`.
  - Else and as fallback: within league family (across leagues with same name/country) or same league: `findHeadToHeadAcrossLeagues(...)` / `findHeadToHead(...)`.
  - Further fallback: alias/name‑based sets via `TeamRepository`/`TeamAliasRepository` and set‑based repository queries.
- The H2H matches endpoint (`/api/matches/h2h/matches`) previously attempted a simpler season‑scoped ID query and could return 0 even when analysis found matches via the wider league‑family or alias set logic. Although we added a league‑scoped season attempt, the endpoint still depends on inputs (seasonId, team IDs) that may not match the analysis inputs used (e.g., alias set expansions). Result: analysis logs show `pairsFound>0`, but endpoint returns `total=0`.

2) Season mismatch from UI to backend
- UI sometimes selects 2025/2026 (expected seasonId=51), while logs show requests executed with seasonId=1. If the endpoint receives seasonId=1 but the analysis computed cross‑season or league‑family results, you can get:
  - Analysis: non‑zero H2H summary (from cross‑season or league‑family fallback).
  - Endpoint: zero (strict ID+season path).
- Conversely, if season 2025/2026 isn’t fully populated for that pair, season‑scoped queries return empty.

3) Data shape differences (status vs goals)
- Imported rows may have non‑null `homeGoals`/`awayGoals` but `status != PLAYED` or null. Some repository methods were relaxed to accept either `status=PLAYED` OR both goals non‑null; others were still using `status=PLAYED` at the time of the failure.
- Any place still requiring `status=PLAYED` can filter out valid historical results, creating discrepancy between the two paths.

4) Frontend fallback logic depends on the problematic endpoint
- The page renders compact H2H from `analysis.h2hSummary.matches` OR detailed list from `analysis.headToHeadMatches`.
- If both are absent, it tries `GET /matches/h2h/matches?homeId&awayId&seasonId...` and renders the result. If that endpoint returns 0 (for reasons above), the UI shows the "No prior head‑to‑head" message, even though the analysis itself had H2H contribution.
- There’s no current UI path that takes the analysis’ internal list (when it exists but is empty due to trimming) and merges the broader, non‑season constrained list used to compute the metrics.

5) Caching and tests mask the issue
- Service tests assert that the analysis path computes H2H metrics correctly (and they pass), but do not assert that the standalone endpoint returns the same matches for the same inputs.
- Cache can serve a prior analysis response with H2H metrics while a subsequent direct endpoint call still yields empty results for the same selection, preserving the contradiction.

6) Environment and data readiness
- If season 2025/2026 (id=51) does not have the specific pair populated, or if league family resolution differs between environments, the analysis may fall back to cross‑season family data while the endpoint is still strictly season‑scoped, returning 0.

## Observable symptoms tying to the above
- Backend logs: `[ANALYZE][H2H] pairsFound=6 usingWindow=6` alongside `[H2H_MATCHES][RESP] total=0 returned=0` for the same pair/season shows the divergence.
- Frontend: shows blended metrics (which include H2H influence) but an empty H2H table and the message "No prior head‑to‑head matches...".

## Why the fix didn’t eradicate the problem
- We relaxed season‑scoped repository filters and added a league‑aware lookup in the endpoint, but the endpoint still does not fully mirror the analysis’ complete fallback tree (league family + alias set resolution + cross‑season fallbacks). Therefore, cases exist where analysis finds matches but the endpoint does not.
- Season selection in the UI can drive a strict season‑scoped endpoint request that legitimately returns 0 even when analysis leveraged cross‑season data.
- The frontend relies on the endpoint for rendering the detailed list when the compact list is empty; thus, the contradiction persists.

## Verification checklist (no code changes required to run)
- Confirm the exact inputs used by each path for a failing case:
  - POST /match-analysis/analyze with leagueId, seasonId, homeTeamName, awayTeamName
  - GET /matches/h2h/matches?homeId=&awayId=&seasonId=&limit=
- Compare repository queries triggered by logs:
  - Analysis: `findHeadToHeadBySeason` then `findHeadToHeadAcrossLeagues`/`findHeadToHead`, and possibly set‑based methods
  - Endpoint: `findHeadToHeadBySeason` else `findH2HByTeamIdsAndSeason` (IDs only)
- Run DB probe for the exact season and pair:
  - Are there rows for that pair in the selected season?
  - Do they have both goals non‑null or status=PLAYED?
- Toggle a clean run (clear caches or set refresh=true if available) and verify if the contradiction reproduces.

## Suggested next steps (for future fixes)
- Align endpoint logic with analysis logic fully, or make the analysis response authoritative by always shipping the flat H2H list it used (and have the UI render that first).
- Normalize season selection: ensure the UI seasonId matches the analysis and the H2H endpoint requests consistently.
- Solidify data: when importing, set status=PLAYED for rows with non‑null goals; or consistently use the relaxed predicate everywhere.
- Strengthen tests: add integration tests asserting parity between analysis H2H and endpoint H2H for the same inputs, including alias and league‑family scenarios.
- Improve UI messaging: when analysis shows non‑zero H2H metrics but the matches list endpoint is empty, show a hint that H2H was computed from cross‑season data and may not be available for the selected season only.

---
Report date: 2025‑09‑12 11:56 (local)
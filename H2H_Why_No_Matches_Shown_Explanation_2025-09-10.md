# Why the Head-to-Head (H2H) Results History Sometimes Shows “No matches found”

Date: 2025-09-10 13:02 (local)

This document explains why the Head-to-Head (H2H) Results History table could show “No matches found. Check if team names match stored records, or try swapping orientation.” even when users expect to see 4–5 matches. It also clarifies what changed in the latest patch and what remaining data/UI conditions can still lead to the message.

## Executive summary
- Root cause class (before the patch): the queries that powered H2H suggestions and the H2H matches list filtered strictly by `status = PLAYED`. Historic/archived records sometimes had non-null goals but `status` not set to `PLAYED`, so they were excluded.
- Fix applied (this session): We broadened the H2H-related repository queries to include matches that have non-null goals even if `status` isn’t set to `PLAYED`.
- Why you may still see “No matches found” in some cases:
  1) Orientation and exact-name expectations can still lead to zero results (e.g., “Chelsea” vs “Manchester City” vs aliases like “Chelsea FC”).
  2) Case-insensitive exact-name matching may not match upstream-normalized variants unless the fuzzy fallback is reached.
  3) The selected orientation (Home vs Away) is respected; if none were played in that direction, the table for that orientation will be empty even if the reverse exists.
  4) Data quality gaps (team naming inconsistencies across seasons/leagues) can bypass exact-name queries; fuzzy queries may still miss if names differ too much.

## What the code is doing now

### Frontend (Angular)
- Component: `frontend/src/app/pages/played-matches-summary.component.ts`
  - After you select an H2H pair, the component calls `GET /api/matches/h2h/matches?home=...&away=...` and renders the returned rows in the table, but only when the returned array has a positive length.
  - The message appears when the returned array is empty (or if the request fails), which is a simple UI guard: `*ngIf="h2hMatches?.length; else noH2HMatches"`.
  - Orientation is respected. If you picked A vs B, it will only list matches where A was home and B was away. The UI offers a quick way to swap orientation in the suggestions list.

- Service: `frontend/src/app/services/match.service.ts`
  - Exposes `suggestH2H(query)` and `getH2HMatches(home, away)` that call the backend endpoints.

### Backend (Spring Boot)
- Controller: `backend/src/main/java/com/chambua/vismart/controller/MatchController.java`
  - Endpoint `/api/matches/h2h/suggest`: returns unordered pair suggestions that have actually played.
  - Endpoint `/api/matches/h2h/matches`: fetches matches for the chosen orientation. It first tries exact name matching then falls back to fuzzy contains matching. It maps each `Match` into a simple DTO with year, date, homeTeam, awayTeam, and result.

- Repository: `backend/src/main/java/com/chambua/vismart/repository/MatchRepository.java`
  - The three key H2H queries were broadened in this session to include either `status = PLAYED` OR both goals non-null:
    - `findDistinctPlayedPairsByNameContains`
    - `findPlayedByExactNames`
    - `findPlayedByFuzzyNames`
  - This change ensures that archived matches with results recorded but status not set to `PLAYED` are now considered.

## Why “No matches found” can still appear

Even with the broadened queries, there are legitimate scenarios where the table will render the hint message:

1) Orientation-specific filtering
   - The `/h2h/matches` endpoint respects the requested orientation. If historical matches exist only in the opposite orientation (B home vs A away), the A vs B request can return zero rows. The UI suggests trying both orientations.

2) Exact vs fuzzy name matching rules
   - The controller calls exact name matching first (case-insensitive). If there are small differences (e.g., "Chelsea" vs "Chelsea FC" or diacritics), exact matching returns zero and only then the code tries a fuzzy contains search.
   - If the archived names differ significantly (e.g., localized names, abbreviations), the fuzzy contains condition may still not match, leaving no rows.

3) Data normalization across seasons/leagues
   - When importing historical archives, the same real-world team can be stored under slightly different names across seasons/leagues (e.g., “Man City”, “Manchester City”, “Manchester City FC”). Unless names converge enough for the fuzzy search, some valid fixtures won’t be captured by the chosen strings.

4) Legitimate absence of played matches under the chosen labels
   - H2H suggestions are derived from any pair seen in the dataset, but once a specific orientation and name pair is selected, that exact combination may lack recorded results (e.g., records exist under an alias, not the selected exact string). This can be confusing if the suggestion was seen, but the final orientation+name pair doesn’t return rows.

5) Import artifacts around null rounds/dates or partial records
   - The UI renders rows only when the backend returns them. If some legacy records are missing dates or were filtered out by sorting/ordering constraints, they may not appear. The current mapping defends against nulls, but extreme anomalies can still yield no usable rows.

## How to validate whether you’re hitting one of these cases
- Try the reversed orientation using the secondary clickable option in suggestions (both are presented per suggestion).
- Try searching and selecting using the most generic, commonly-used team names (drop suffixes like “FC”, “AC”, etc.) to increase the chance that fuzzy matching connects to the stored names.
- Use the Team Search (single team) to see the total counts and breakdowns for each team name you’re selecting, which can hint at the canonical form used most often in the data.
- If you can, query the backend endpoint directly in a browser or via curl/Postman to observe the raw JSON: `/api/matches/h2h/matches?home=TeamA&away=TeamB`.

## What changed in the latest patch
- We updated three repository queries to include matches that have non-null goals even when status is not set to `PLAYED`. This directly addresses the scenario where the UI previously showed “No matches found” despite obvious results in the database.
- No changes were needed in the Angular component logic that displays the table; it already renders as soon as it receives any rows.

## Remaining improvement opportunities (optional)
- Name normalization: maintain an alias map so that exact-name search can match known variants ("Man City" -> "Manchester City").
- Cross-league/alias-aware H2H: reuse the more robust alias logic that exists elsewhere (e.g., in analysis services) when forming H2H lists, not only suggestions.
- Diagnostics: temporarily log sizes of the exact and fuzzy results to spot where matches are lost in the pipeline.

## Bottom line
- The main historical blocker (strict `status = PLAYED`) has been addressed.
- If “No matches found” still appears, it’s most likely due to orientation selection or name variance that the exact and fuzzy checks can’t bridge for the chosen strings. Swapping orientation or trying a slightly different name form typically resolves it.

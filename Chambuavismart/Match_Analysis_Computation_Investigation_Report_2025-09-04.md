# ChambuaViSmart — Match Analysis Computation Investigation (2025-09-04)

This report documents, end-to-end, how matches are ordered and stored, how the Form Guide and League Table are built, and how the Match Analysis module generates its current predictions. It also highlights risks, gaps, and improvement opportunities.

Scope of analysis: backend Spring Boot code under `backend/src/main/java/com/chambua/vismart` (controllers, services, repositories, and models) and directly relevant DTOs. Frontend is not evaluated for business logic beyond API parameterization.


## 1) Match Ordering & Recency

Primary data model: `com.chambua.vismart.model.Match` mapped to `matches` table.

- Fields relevant to ordering and completion:
  - `match_date` (LocalDate, NOT NULL)
  - `round` (Integer, NOT NULL)
  - `home_goals`, `away_goals` (nullable; non-null indicates a completed match)
  - `league_id` (FK), `season_id` (FK, nullable in transitional phases), `home_team_id`, `away_team_id`
- Constraints/Indexes:
  - Unique constraint: `(league_id, round, home_team_id, away_team_id)` — `uk_match_league_round_home_away`
  - Indexes: `(league_id, round)` and `(match_date)`

How “recency” is determined in computations:
- Form Guide (`FormGuideService`):
  - Pulls completed matches (home_goals IS NOT NULL AND away_goals IS NOT NULL) scoped by `league_id` AND `season_id`.
  - Orders rows as: `ORDER BY team_id, match_date DESC, round DESC`.
  - After grouping by team, within-team list is sorted in memory by `date DESC` then `round DESC`.
  - Last N matches are taken from this sorted list, so “most recent” strictly means latest `match_date` and, for ties or missing rounds, higher `round`.
- League Table (`LeagueTableService`):
  - No explicit ordering by date is needed; it aggregates over all completed matches for a league (or league+season) irrespective of date ordering.

Safeguards for missing/misformatted/duplicated dates:
- Upload validation (`MatchDataValidationService`): marks a record with `Missing date` as ERROR, causing validation to fail.
- Date parsing (`MatchUploadService#parseDate`): supports `yyyy-MM-dd`, `d/M/yyyy`, `dd/MM/yyyy`. Unparsable dates throw `IllegalArgumentException("Invalid date: …")`.
- Duplicate protection: unique key `(league_id, round, home_team_id, away_team_id)` prevents duplicate fixtures per round; upsert logic also tries first to match by `(league, home, away, date)` then falls back to updating round-identified rows to avoid constraint violations.
- Chronological consistency: validator warns (not errors) if round dates are not non-decreasing (e.g., later round earlier than previous). This is a WARN, not a hard stop.

Edge cases:
- `match_date` is NOT NULL at the DB level in the entity, and service logic throws on invalid/blank date, so completed data should always have a valid date.
- Duplicate same-day fixtures: order fallback to `round DESC` helps disambiguate when dates are equal.


## 2) Form Guide Construction

API: `GET /api/form-guide/{leagueId}?seasonId=...&limit=...&scope=overall|home|away` → `FormGuideController` → `FormGuideService.compute(leagueId, seasonId, limit, scope)`.

Selection rules:
- `seasonId` is required by the controller; missing `seasonId` returns HTTP 400.
- `limit` parameter:
  - "all" → treated as `Integer.MAX_VALUE` (entire season per team in the given scope)
  - numeric → parsed int; invalid → defaults to 6
- `scope` parameter: overall (home+away), home-only, away-only.

Data pull and ordering (`FormGuideService`):
- Base queries (completed matches only):
  - Home rows: select `match_date, round, team_id, team_name, gf=home_goals, ga=away_goals` from matches where `league_id=?` and `season_id=?` and goals not null.
  - Away rows: same but swap gf/ga from away perspective.
- Scope behavior:
  - HOME: home-only rows, ordered by `team_id, date DESC, round DESC`.
  - AWAY: away-only rows, ordered by `team_id, date DESC, round DESC`.
  - OVERALL: `UNION ALL` home and away, then ordered by `team_id, date DESC, round DESC`.
- Group rows by team and sort within team by `date DESC, round DESC`. Take window = `min(limit, list.size())`.

Metrics:
- Window counts: W/D/L, GF, GA, PTS (3/1/0), sequence list `lastResults` with latest first.
- PPG: `pts / windowSize` rounded to 2 decimals.
- Percentages over window:
  - BTTS%: matches where `gf > 0 AND ga > 0`
  - Over 1.5%: `gf+ga >= 2`
  - Over 2.5%: `gf+ga >= 3`
  - Over 3.5%: `gf+ga >= 4`
- `totalMp`: total completed matches for the team in season and scope (length of list before truncation).
- `lastResults` length is capped to 10 when `limit=all` to keep UI manageable.

Validation:
- After computing all teams, there is a diagnostic check comparing sum of `totalMp` across teams with expected `completedMatches * factor` (factor 2 for OVERALL, 1 for HOME/AWAY). If deviation > max(2, 5%), WARN is logged.

Safeguards and ordering integrity:
- Strict chronological order: yes, by `match_date DESC` then `round DESC`. Requires valid non-null dates (enforced at ingress).
- If rounds are duplicated or out-of-sequence, date fields dominate ordering. Only if dates tie are rounds used to break ties.


## 3) League Table Calculations

API: `GET /api/league/{leagueId}/table` → `LeagueController` → `LeagueTableService.computeTable(leagueId)`.

Aggregation method:
- Canonical computation uses a UNION ALL over completed matches:
  - Home leg contributes 1 MP to home team with W/D/L/PTS values from `home_goals` vs `away_goals`.
  - Away leg contributes 1 MP to away team with reversed goals and points criteria.
- Group by team, SUM over MP, W, D, L, GF, GA, PTS; compute GD as `GF - GA`.
- Order teams by `PTS DESC, GD DESC, GF DESC, name ASC`.

Season handling:
- There are two methods:
  - `computeTable(Long leagueId)`: Filters by `league_id` only (legacy/back-compat). Note: This aggregates across all seasons currently stored for that league.
  - `computeTableBySeasonId(Long leagueId, Long seasonId)`: Filters strictly by `league_id AND season_id`.
- Controller currently calls the legacy `computeTable(leagueId)` endpoint.

Postponed/missing results:
- Matches with null goals are excluded from aggregation (treated as not played/not completed).
- There is no explicit status column; completeness is inferred solely from both goal fields being non-null.


## 4) Match Analysis Weights & Predictions

API: `POST /api/match-analysis/analyze` with body including `leagueId`, `homeTeamId/Name`, `awayTeamId/Name`, and `refresh` flag.

Current implementation: `MatchAnalysisService.analyzeDeterministic(...)` produces deterministic but mocked outputs using a seeded PRNG.

- Cache layer: `MatchAnalysisResultRepository` stores JSON responses per `(leagueId, homeTeamId, awayTeamId)` if available; reused when `refresh=false`.
- Seed: based on IDs when present, otherwise on normalized names, to keep deterministic outputs across calls.
- Output generation:
  - Win/Draw/Win probabilities: home and draw sampled in small ranges then away computed as `100 - (home + draw)`, with minimal clamps.
  - BTTS and Over 2.5 probabilities: random within 40–60% ranges.
  - xG values: random within plausible bounds (home ~1.2–2.0, away ~1.0–1.7).
  - Confidence: random 60–80.
  - Advice: heuristic strings based on the random BTTS/over25 values.

Critical note: The Match Analysis module does NOT currently use actual data such as recent form, goals for/against, home/away performance, or head-to-head. There are NO weights across real factors; no Bayesian update; no aggregation from league/form stats. It is intentionally a placeholder that returns deterministic pseudo-realistic numbers.

Home/away advantage usage: None beyond the superficial random ranges; no utilization of historical home/away splits.


## 5) Percentages & Probabilities

- Form Guide percentages (BTTS%, Over 1.5/2.5/3.5%) are direct ratios from counted events over the selected window size. They are per-team, per-scope, and strictly bounded [0–100] by construction.
- Match Analysis probabilities (win%, draw%, loss%, BTTS, Over 2.5) are pseudo-random values constrained to plausible ranges and normalized to 100% for W/D/W by construction (`away = 100 - home - draw`). There is no combination or normalization across multiple real factors (hence no risk of >100% because they are not additive from components).

Sources of these percentages:
- Form Guide: computed purely from uploaded match data (matches table) filtered by leagueId & seasonId.
- Match Analysis: NOT sourced from league-wide stats, head-to-head, or team form; purely seeded RNG.


## 6) Reliability & Data Dependencies

Dependencies on uploaded data:
- Form Guide and League Table rely exclusively on the uploaded match data in the `matches` table (plus team and league/season foreign keys).
- Upload pipeline (`MatchUploadService`) enforces date/round/team presence and parses dates; validation service provides structural checks and chronological WARNs.

External datasets: None used.

Fallback defaults with insufficient data:
- Form Guide: If a team has fewer than `limit` completed matches, the window uses however many are available (possibly zero), yielding zeros for PPG and percentages if `mpWindow=0`.
- League Table: Teams with no completed matches will not accrue MP/W/D/L/PTS.
- Match Analysis: Works regardless of data; uses seed from IDs/names to produce a deterministic mock result even with zero historical matches.


## 7) End-to-End Trace

1) Storage and ordering
- Matches are ingested from CSV or text, validated for presence of date/round and teams, date parsed into `match_date`. Duplicates prevented by unique key; upsert tries `(league, home, away, date)` then round-key fallback.
- Completed matches have both goals non-null. All calculations use only completed matches.

2) Form/league stats build
- Form Guide queries completed matches filtered by league+season and scope, orders by `team_id, date DESC, round DESC`, derives per-team windows (last N or all) to compute W/D/L, PTS, PPG, BTTS%, OverX.Y% and last results sequence.
- League Table aggregates completed matches (league-wide, or league+season in season-specific method) via UNION ALL and sums to produce MP, W, D, L, GF, GA, GD, PTS; sorts by points, GD, GF.

3) Analysis outputs
- Match Analysis generates deterministic pseudo-random probabilities and xG using a seed; it does not factor in computed form or league stats.


## 8) Weaknesses, Gaps, Risks

- Match Analysis is a placeholder: no real modeling, no factor weights, no utilization of form, home/away splits, head-to-head, or league strength. Users may be misled by confidence/advice language.
- League Table season scoping: Primary endpoint `/api/league/{leagueId}/table` uses `computeTable(leagueId)` which aggregates across all seasons for that league if multiple seasons coexist in the DB. There is a season-specific method, but not currently exposed on this endpoint. Risk: incorrect standings when multiple seasons present.
- Chronology warnings only: The ingestion validator warns if round-date order is inconsistent but still allows ingestion. This can produce valid but surprising within-season chronology—though Form Guide primarily uses `match_date DESC` so practical impact is limited.
- Duplicate detection is per (league, round, home, away). If the same fixture repeats in the same round due to data anomalies but on different dates (should not happen), the round-level unique key prevents duplicate insertion, but may overwrite date via upsert fallback logic.
- Missing season enforcement across API: Form Guide enforces seasonId; League Table main endpoint does not. Frontend usage should be audited to avoid combined-season tables.
- No postponed status: The system infers “played” solely from goals being non-null. Suspended/postponed/awarded statuses are not represented.


## 9) Recommendations (next steps)

- Replace Match Analysis with a data-driven model:
  - Factors: recent form PPG (last 5/10), home/away splits, head-to-head, league position/points, GF/GA rates, rest days, and ELO or SPI-like team strength.
  - Weighting: start with logistic regression or Bayesian updating; ensure W/D/W probabilities sum to 100% and calibrate via reliability curves.
  - BTTS/Over-Under: model via Poisson or bivariate Poisson calibrated on league and team rates; compute derived probabilities analytically.
- Expose season-scoped league table endpoint (e.g., `/api/league/{leagueId}/table?seasonId=...`) that calls `computeTableBySeasonId` and update frontend to require season.
- Strengthen chronological validation: optionally error-level when round-date regressions exceed a threshold; or auto-correct ordering based on date when computing rounds.
- Add explicit match status field (Scheduled/Played/Postponed/Abandoned/Awarded) to avoid relying on goal nullability.
- Expand date parsing formats only if needed; keep strictness to prevent silent mis-parsing.


## 10) File and Code References

- Form Guide:
  - Controller: `FormGuideController#getFormGuide`
  - Service: `FormGuideService#compute(leagueId, seasonId, limit, scope)`
  - DTO: `FormGuideRowDTO`
- League Table:
  - Controller: `LeagueController#getLeagueTable`
  - Service: `LeagueTableService#computeTable`, `#computeTableBySeasonId`
  - DTO: `LeagueTableEntryDTO`
- Match Analysis:
  - Controller: `MatchAnalysisController#analyze`
  - Service: `MatchAnalysisService#analyzeDeterministic`
  - Cache: `MatchAnalysisResultRepository`, entity `MatchAnalysisResult`
- Ingestion & Validation:
  - `MatchUploadService` (date parsing, upsert logic)
  - `MatchDataValidationService` (field checks, chronological warning)


Summary: Form Guide and League Table are fully data-driven and deterministic, with clear ordering and aggregation. Match Analysis is currently a deterministic placeholder unrelated to actual match data or form. Season handling is strict in Form Guide but lax in the primary League Table endpoint, which can aggregate across seasons if the DB holds multiple. Addressing these gaps will significantly improve analytical accuracy and user trust.

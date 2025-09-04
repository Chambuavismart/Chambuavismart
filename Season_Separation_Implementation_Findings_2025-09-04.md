# ChambuaViSmart — Season Separation: Findings, Decisions, and Execution Plan (2025-09-04)

Author: Junie (JetBrains Autonomous Programmer)
Date/Time: 2025-09-04 11:04 local

## 1) Executive Summary

The system historically conflated data across seasons, causing the UI to sometimes show combined stats instead of the selected season. During this session, we implemented strict season filtering in backend services (LeagueTableService, FormGuideService) and ensured the frontend (Form Guide and League Table pages) carries `seasonId` from the season dropdown to the API. Upload paths now resolve/create a Season entity and attach `season_id` when saving matches.

This document records key findings, the recommended approach to achieve clean season separation end-to-end (uploads → DB → API → UI), sanity rules at each layer, and a minimal-disruption execution plan.


## 2) Current Behavior and Observations

- Data model and DB
  - `seasons` table exists with FK from `matches.season_id` (nullable for rollout).
  - Migration `V5__add_seasons_and_match_fk.sql` created `seasons`, added `season_id` to `matches`, index on `season_id`.
  - `matches` still has a legacy season-agnostic uniqueness implied by code and unique constraint: `(league_id, round, home_team_id, away_team_id)`.

- Backend services and controllers
  - LeagueTableService:
    - New method `computeTableBySeasonId(leagueId, seasonId)` filters strictly by `season_id` when provided, and combines seasons when null.
    - Existing method overloads retained for backward compatibility.
  - FormGuideService:
    - `compute(leagueId, seasonId, limit, scope)` strictly filters by `season_id` if provided; combined view when `seasonId` is null.
    - Adds validation logging to detect suspicious totals.
  - Controllers accept `seasonId` as optional query param and route to the season-aware methods.
  - SeasonService provides a default Season auto-backfill if none exists for a league (from `League.season`).

- Upload path
  - `MatchUploadService.uploadCsv` and `uploadText` resolve/create `Season` for the given league + season string (or by explicit `seasonId`) and attach `season_id` to new/upserted matches.
  - Incremental updates also attach season when missing.

- Frontend
  - Form Guide and League Table pages now:
    - Load seasons for the chosen league via `/api/leagues/{leagueId}/seasons`.
    - Default the dropdown to the “current” season (by today within [startDate, endDate] or most recent available), and pass `seasonId` to backend endpoints.
    - Provide an explicit “Combined (All Seasons)” option as `null` seasonId.

- Tests
  - `SeasonFilterIntegrationTest` verifies:
    - Season-specific results differ appropriately and combined view aggregates across seasons for both League Table and Form Guide.


## 3) Key Problems Previously Causing Confusion

1. Season-agnostic queries aggregated matches across all seasons when no filter was applied.
2. Frontend frequently omitted season parameters, so users saw combined stats unintentionally.
3. Uploads historically lacked strict guarantees that `season_id` was set for every match.
4. DB constraints allowed `season_id` to be null and uniqueness rules did not include season, permitting cross-season duplication/overlap.


## 4) Recommended End-to-End Approach (Minimal Disruption)

- Principle: Season filtering should be explicit and consistent end-to-end. Wherever a user selects a season in the UI, that `seasonId` must be carried to the API and strictly enforced in queries.
- Combined view is still supported but must be an explicit user choice.

### 4.1 Database-level
- Keep `seasons` as the canonical season entity per league.
- Plan to make `matches.season_id` NOT NULL after safe backfill.
- Add composite FK to ensure league–season consistency: `(league_id, season_id) → seasons(league_id, id)`.
- Replace legacy uniqueness with a season-aware unique constraint. Prefer date-based identity:
  - Preferred unique: `(league_id, season_id, match_date, home_team_id, away_team_id)`.
  - Alternative unique (if round-based data sources are primary): `(league_id, season_id, round, home_team_id, away_team_id)`.
- Keep supporting indexes on `matches(season_id)`, `matches(league_id, round)`, and `matches(match_date)` as needed.

### 4.2 Backend services and API
- Always accept optional `seasonId` on stats endpoints and pass it through to services.
- Default behavior for missing `seasonId`:
  - Option A (recommended): Default to the league’s current season (contain today or most recent by startDate). Users can still choose Combined explicitly.
  - Option B: Retain combined default. (Not recommended due to usability confusion.)
- Service-layer contracts:
  - Strict season filtering when `seasonId` is present.
  - Combined only when explicit (or by default policy if you choose Option B).
- Add validation and diagnostics endpoints (already present for league table) to aid operations.

### 4.3 Uploads
- Require league name, country, and season string (or explicit `seasonId`).
- Resolve existing season by `(leagueId, seasonName)`; create if absent (minimal disruption).
- If a Season has defined `startDate`/`endDate`, apply policy:
  - Strict (recommended): Reject rows with `match_date` outside the window (or offer a dry-run mode that warns and skips).
  - Lenient mode is optional and off by default to prevent contamination.

### 4.4 Frontend
- Ensure the season dropdown is present and selected by default (current season preselected).
- Always include `seasonId` in API requests when a season is selected; pass `null` only for explicitly chosen Combined.
- Keep the chosen `seasonId` in the URL (route params or query string) so shareable links reproduce the exact view.
- On league change or season change, reload all relevant widgets.


## 5) Sanity Rules by Layer

- Upload
  - Must specify season or seasonId; resolve/create Season.
  - For seasons with dates, reject out-of-window matches (default) or warn-and-skip (dry-run).
  - Auto-create teams for historical uploads only if explicitly allowed; otherwise require existing teams.

- Database
  - No match without season_id after rollout (NOT NULL enforced).
  - Composite FK for league-season consistency.
  - Season-aware uniqueness to avoid cross-season duplication.

- Backend
  - Stats queries must filter by `seasonId` whenever provided.
  - Optionally default to current season when `seasonId` is missing.
  - Add logs to detect mismatches between expected totals and computed aggregates.

- Frontend
  - The season filter must always be carried to API requests.
  - “Combined” must be an explicit selection.


## 6) Minimal-Disruption Rollout Plan

Phase 0 — Audit (read-only)
- Report counts by league of matches with `season_id IS NULL`.
- Detect seasons with overlapping date windows per league.
- Detect matches whose dates fall outside any defined season window in their league.

Phase 1 — Backfill
- For each league:
  - If seasons have date windows: backfill `season_id` by matching `match_date` to the correct Season.
  - If no windows exist: map to the most appropriate Season by name (e.g., default or latest); flag ambiguous cases for manual review.
- Produce a delta report of rows updated.

Phase 2 — DB Migrations
- Add composite FK `(league_id, season_id) → seasons(league_id, id)`.
- Make `matches.season_id` NOT NULL.
- Replace legacy unique with season-aware unique (prefer `match_date` variant); migrate indices accordingly.

Phase 3 — Backend Enforcement
- Confirm controllers/services already propagate strict `seasonId` filtering (implemented during this session).
- Optionally add a SeasonScope resolver to infer default current season when missing (if Option A is chosen).
- Enforce upload validations per date windows.

Phase 4 — Frontend Alignment
- Ensure `seasonId` is in URLs for shareability and state persistence.
- Verify all widgets re-query on season change.

Phase 5 — Verification
- Run integration tests (already added for season filtering) and expand coverage for uploads and combined mode.
- Manual checks on representative leagues (with multiple seasons, with/without windows, historical data).
- Monitor logs for validation warnings.


## 7) API and Contract Clarifications

- League table: `GET /api/league/{leagueId}/table?seasonId={id}`
  - Filters strictly by season when `seasonId` is provided; combined otherwise.
- Form guide: `GET /api/form-guide/{leagueId}?limit={n|all}&scope={overall|home|away}&seasonId={id}`
  - Same policy as above.
- Seasons list: `GET /api/leagues/{leagueId}/seasons`
  - Returns seasons (currently ordered by startDate desc with auto-backfill if none exists).


## 8) Testing and Validation Strategy

- Integration tests (existing):
  - Verify per-season isolation and combined aggregation for League Table and Form Guide.
- Add tests for uploads:
  - Creating/locating Season by name or id.
  - Attaching `season_id` to upserted matches.
  - Strict rejection/skipping of out-of-window rows (when dates exist).
- Add tests for default-season behavior if Option A is adopted.
- Smoke tests from UI: ensure `seasonId` propagation and that Combined must be explicitly selected.


## 9) Risk and Mitigation

- Risk: Existing data with null `season_id` or overlapping seasons.
  - Mitigation: Phase 0 audit + Phase 1 backfill with reports and manual review for ambiguities.
- Risk: Unique constraint change may conflict with duplicates.
  - Mitigation: Detect duplicates pre-migration and deduplicate or merge using deterministic rules.
- Risk: Users accustomed to combined default.
  - Mitigation: Keep Combined as an explicit option; provide UI copy explaining the change.


## 10) Open Decisions (to be confirmed)

- Default behavior when `seasonId` is missing:
  - Option A (recommended): default to current season; combined only if explicitly chosen.
  - Option B: keep combined default. (Not recommended.)
- Upload strictness default:
  - Recommended: Strict reject out-of-window rows (with optional dry-run mode that warns and skips).
- Preferred unique key:
  - Recommended: (league_id, season_id, match_date, home_team_id, away_team_id).


## 11) Changes Already Implemented in This Session (Summary)

- Backend
  - LeagueTableService and FormGuideService: strict `seasonId` filtering when provided; combined when null.
  - Controllers accept `seasonId` and route to season-aware computations.
  - Season entities/services/migrations present to support season lists and auto-backfill on empty.
- Frontend
  - Form Guide and League Table components load seasons, default to current, and pass `seasonId` to API.
  - Explicit “Combined (All Seasons)” option available in both pages.
- Tests
  - SeasonFilterIntegrationTest validates season isolation vs combined aggregation for both services.


## 12) Next Steps

- Confirm open decisions (Section 10). If Option A is selected, add a small default-season resolver in controllers/services for missing `seasonId`.
- Execute Phases 0–2 (Audit, Backfill, DB migrations) in a controlled environment.
- Expand upload validations per season date windows.
- Add tests for upload behavior and default-season logic (if adopted).

---
This document captures the current state, the recommended approach, and a pragmatic rollout plan to fully isolate seasons while preserving a deliberate Combined mode. It aims for maximal clarity with minimal disruption to existing ingestion and stats logic.
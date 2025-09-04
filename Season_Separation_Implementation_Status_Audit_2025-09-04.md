# Season Separation Implementation Status Audit (2025-09-04)

This document audits the repository against the season-separation policy and rollout plan.

Scope: Backend (Spring Boot) and Frontend (Angular) included in this repository.

## Summary
- Core API filtering behavior: Implemented.
- Frontend dropdowns and explicit Combined: Implemented (with small gaps re: URL propagation).
- Upload strictness (season window enforcement, dry-run): Not yet implemented.
- Database integrity (NOT NULL, composite FKs, unique constraints): Not yet fully implemented via migrations.
- Diagnostics/logging: Partially implemented.

Overall: Major parts of season filtering are in place; data integrity and upload strictness require further work.

---

## Detailed Checklist

### Default Season Behavior
- When seasonId is missing, default to current season.
  - Implemented in controllers:
    - backend/src/main/java/com/chambua/vismart/controller/LeagueController.java
    - backend/src/main/java/com/chambua/vismart/controller/FormGuideController.java
  - Logic uses SeasonService.findCurrentSeason().
- Combined (All Seasons) must be an explicit choice in the UI.
  - Implemented:
    - Frontend services now pass `combined=true` when `seasonId === null`.
      - frontend/src/app/services/league.service.ts
    - UI dropdowns include "Combined (All Seasons)" option.
      - frontend/src/app/pages/league-table.component.ts
      - frontend/src/app/pages/form-guide.component.ts

Status: Implemented (✓)

### Upload Strictness
- Default = Strict. Reject rows whose match_date falls outside the season window.
- Provide dry-run mode that flags out-of-window rows with warnings/skips.
- No auto-routing to other seasons or auto-creation of seasons unless overridden.

Findings:
- MatchUploadService attaches Season to matches (by id or name) but does not validate match_date vs season start/end, nor provide a dry-run mode.
- Auto-creation of Season by name occurs if not found (could be acceptable only under explicit override, but currently unconditional when seasonId missing).

Status: Not implemented (✗)

### Database Integrity
- Make matches.season_id NOT NULL after backfill.
- Enforce composite FK (league_id, season_id) → seasons(league_id, id).
- Unique constraint: (league_id, season_id, match_date, home_team_id, away_team_id).
- Indexes: (season_id), (league_id, round), (match_date).

Findings:
- Entity model: Match.season is nullable (comment notes "nullable during rollout").
- Current unique constraint is on (league_id, round, home_team_id, away_team_id) at JPA level.
- Flyway disabled in dev; test/prod enable Flyway but no migrations present in repo to enforce above.

Status: Not implemented (✗)

### Backend Services & API
- All stats endpoints strictly filter by seasonId when present.
  - Implemented in LeagueTableService and FormGuideService SQL (season_id filter added when provided).
- Combined view only when explicitly requested.
  - Implemented in controllers via `combined=true` query parameter.
- Logging/diagnostics to detect mismatches.
  - Partially implemented: 
    - LeagueTableService logs debug counts; diagnostics DTO available.
    - FormGuideService adds a validation check comparing sum of MPs vs expected, with season-aware logging.
- Ensure uploads always attach season_id to matches.
  - Implemented in MatchUploadService (upsertMatch assigns season when available; incremental update attaches when provided).

Status: Implemented (core), Partial (diagnostics) (✓/△)

### Frontend (UI/UX)
- League pages must display a season dropdown, defaulting to current season.
  - Implemented in both League Table and Form Guide components; default to current if dates available.
- Pass seasonId on every request; Combined explicit.
  - Implemented via LeagueService (combined=true when seasonId null; seasonId when set).
- Keep seasonId in the URL (shareable links preserve state).
  - Not fully implemented: routing currently uses /league/:id without season query params; Form Guide also does not push seasonId to the URL.
- Ensure widgets reload on season change.
  - Implemented.

Status: Mostly implemented; URL propagation pending (△)

### Rollout Plan
- Phase 0: Audit null/invalid season IDs and overlapping windows.
  - Not in code; needs script/migration.
- Phase 1: Backfill existing matches by date windows.
  - Not implemented here.
- Phase 2: Apply DB migrations (FK + NOT NULL + unique).
  - Not present.
- Phase 3: Enforce backend validations.
  - Partially (season filtering); upload strictness missing.
- Phase 4: Align frontend season dropdown + propagation.
  - Dropdowns aligned; URL propagation missing.
- Phase 5: Verify with integration and manual tests.
  - Integration tests for filtering exist (SeasonFilterIntegrationTest). Build/test environment resolution may require network; tests not executed locally in this audit.

Status: Partial (△)

---

## Recommendations (Next Minimal Steps)
1. Frontend URL propagation (small, high-impact):
   - Update LeagueTableComponent and FormGuideComponent to reflect seasonId in URL query parameters and read it on init.
2. Upload strictness and dry-run mode:
   - Add parameters `strict=true` and `dryRun=true` to upload endpoints.
   - Validate each row: if match_date outside season window, add warning (dry-run) or error (strict mode) and skip persist.
3. Database migrations (requires Flyway):
   - Migration 1: backfill season_id where null (by joining date within seasons; otherwise log).
   - Migration 2: add season_id NOT NULL, add (league_id, season_id) composite FK; switch unique index to (league_id, season_id, match_date, home_team_id, away_team_id); add indexes on (season_id), (league_id, round), (match_date).
4. Diagnostics:
   - Enhance API to expose season coverage stats per league (e.g., /api/league/{id}/season-coverage) for admin checks.

With these steps, the season separation strategy will be fully compliant.

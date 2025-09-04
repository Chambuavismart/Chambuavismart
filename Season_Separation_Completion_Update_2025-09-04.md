# Season Separation Completion – Update (2025-09-04)

This update continues and completes the Season Separation Completion Plan.

## Backend Changes

1) Upload strictness + dry-run
- MatchUploadService:
  - Added strict date-window validation based on season start/end or inferred window from season name (YYYY/YYYY or YYYY).
  - Added dryRun flag to preview validation results without persisting.
  - Disabled season auto-create by default; allow via allowSeasonAutoCreate.
  - Applied to both CSV and Raw Text upload flows, including incremental vs full replace.
- MatchUploadController (legacy endpoints):
  - Exposed strict=true (default), dryRun=false, allowSeasonAutoCreate=false as request params (CSV) and JSON (Text).
- UnifiedUploadController:
  - Exposed strict, dryRun, and allowSeasonAutoCreate for multipart CSV endpoint and JSON endpoint.
  - Wired these flags through to MatchUploadService.

2) Database Integrity (Flyway migrations)
- Added minimal baseline schema and season-separation constraints as Flyway migrations:
  - V1__init.sql: Creates leagues, teams, seasons, matches with FKs and legacy unique constraint to match current JPA and tests.
  - V2__backfill_season_id.sql: Backfills matches.season_id using seasons date windows per league.
  - V3__enforce_season_id_not_null.sql: Enforces NOT NULL on matches.season_id.
  - V4__composite_fk_and_unique_changes.sql:
    - Adds composite FK (league_id, season_id) -> seasons(league_id, id).
    - Replaces unique key with (league_id, season_id, match_date, home_team_id, away_team_id).
    - Adds supporting indexes.

Notes:
- Test and prod profiles already have Flyway enabled. Dev remains schema-update by Hibernate. Migrations are designed to run cleanly on empty DBs (CI/tests) and to harden season separation in live DBs post-backfill.

3) Frontend Integration
- League Table page:
  - Preserves seasonId in URL query params; restores from URL on load for shareability and refresh correctness.
- Form Guide page:
  - Preserves leagueId and seasonId in URL query params; restores from URL on load.
  - All components refresh correctly on seasonId changes.

## Tests
- Integration test SeasonFilterIntegrationTest (already in repo) validates that both table and form guide strictly filter by seasonId when present and combine across seasons when not specified. The Flyway V1 baseline ensures schema is ready under test profile.

## Operational Guidance
- For existing databases with historical data, run Flyway migrations in this order (automatically handled by Flyway):
  1. V1 baseline (no-op if tables exist but may fail if definitions diverge significantly; if so, baseline-on-migrate is enabled and can be used appropriately).
  2. V2 backfill to attach season_id.
  3. Verify audit that season_id is fully populated.
  4. V3 to enforce NOT NULL on season_id.
  5. V4 to add composite FK and new uniqueness constraint.

- Upload endpoints:
  - strict=true (default): out-of-window matches are rejected.
  - dryRun=true: preview any issues without persisting.
  - allowSeasonAutoCreate=false by default; set to true to create season dynamically when missing.

## Summary
This update completes the planned items by: implementing strict upload validation and dry-run, adding necessary Flyway migrations to enforce season integrity, and updating frontend pages to persist season selection in the URL. Please review the Migration V3/V4 in staging with a backup to ensure data is fully backfilled before enforcing constraints in production.

# Season Separation Status Audit Report

Author: Junie (JetBrains Autonomous Programmer)
Date/Time: 2025-09-04 11:44 local
Scope: Backend (Spring Boot) + Frontend (Angular)

---

## 1) Upload Path Audit

Questions to answer:
- Are matches being saved with correct season_id for all upload methods (CSV, raw text, fixtures)?
- How many rows per league currently have season_id IS NULL?
- Which leagues/seasons have matches dated outside their season windows?

Findings:
- Match uploads (CSV and raw text)
  - Service: backend/src/main/java/com/chambua/vismart/service/MatchUploadService.java
  - Behavior:
    - Resolves Season via explicit seasonId when provided; otherwise via (leagueId, season name). If not found, auto-creates a Season row with that name (no dates).
    - Assigns season_id on insert/upsert through upsertMatch(); incremental updates also attach season if provided/found.
    - No validation of match_date vs Season.startDate/endDate yet.
  - Status: season_id attached (✓); date-window validation/dry-run not implemented (✗).

- Text uploads via Deprecated Unified Controller
  - Controller: backend/src/main/java/com/chambua/vismart/controller/UnifiedUploadTextController.java
  - Delegates to MatchUploadService with same season attachment behavior.

- Fixtures upload (separate store)
  - Service: backend/src/main/java/com/chambua/vismart/service/FixtureUploadService.java
  - Uses Fixture entity (no season_id field). Validates date is within provided season string window (e.g., 2024/2025 => Jul–Jun heuristic). Ensures a Season row exists for the league+season name.
  - Status: date-window validation present for fixtures, but fixtures are separate from matches and do not use season_id.

Metrics and SQL (to be run in DB):
- Count per-league rows with season_id IS NULL
  - See Section 4.3 (Audit SQL) for queries.
- Out-of-window matches (by Season.start_date/end_date):
  - See Section 4.3 for detection SQL. Note: Only works where seasons have start/end dates populated.

---

## 2) Database Integrity Audit

Questions to answer:
- Which DB constraints are currently applied (NOT NULL, FKs, unique keys)?
- Which ones are still pending implementation?
- Any duplicates or conflicts that would block the season-aware unique constraint?

Current model (from JPA entities):
- Match (backend/src/main/java/com/chambua/vismart/model/Match.java)
  - season_id: nullable, FK fk_match_season
  - Unique constraint (season-agnostic): (league_id, round, home_team_id, away_team_id)
  - Indexes: (league_id, round), (match_date)
- Season (backend/src/main/java/com/chambua/vismart/model/Season.java)
  - Columns: league_id (NOT NULL FK), name (NOT NULL), optional start_date/end_date
  - Index: (league_id)
- League (backend/src/main/java/com/chambua/vismart/model/League.java)
  - Unique constraint: (name, country, season) — legacy, serves as a league identity including its season string

Applied vs Pending:
- Applied
  - FK: matches.league_id → leagues.id (NOT NULL)
  - FK: matches.season_id → seasons.id (NULLABLE)
  - Unique: matches(league_id, round, home_team_id, away_team_id) [season-agnostic]
- Pending (recommended)
  - Make matches.season_id NOT NULL (post-backfill)
  - Composite FK: (league_id, season_id) → seasons(league_id, id) to ensure season belongs to the same league
  - Replace unique with season-aware uniqueness. Preferred: (league_id, season_id, match_date, home_team_id, away_team_id)
  - Indexes: (season_id), (league_id, round), (match_date) — already present except season_id index may depend on actual DDL; keep/confirm.

Duplicate/conflict risk before enforcing new unique key:
- Potential cross-season duplicates currently won’t violate the existing (round-based) unique constraint but will conflict once season is added to the uniqueness if the identity changes to date-based.
- Run detection SQL in Section 4.3 to list duplicates under the proposed unique key and deduplicate prior to migration.

---

## 3) Integration Test Failures

Observed during this session:
- Environment/build
  - Initial build succeeded.
  - Running tests encountered unresolved artifact dependencies (offline resolution issues) and would also require a running MySQL with Flyway migrations for the test profile.
- Test configuration
  - spring.profiles.active=test uses:
    - datasource: MySQL chambua_test
    - jpa.hibernate.ddl-auto=none
    - flyway.enabled=true (but no migration files in repo)
- Impact
  - Tests depending on schema creation/migrations will fail unless a MySQL test DB and Flyway migrations are available.

Suggested fixes:
- Provide minimal Flyway migrations to create required tables and constraints for the test profile.
- Or switch @DataJpaTest to use an embedded database for unit/integration tests, with testcontainers or H2 (align dialect carefully for native SQL).
- Add seed/migration for Seasons if tests rely on Season.start/end windows.

---

## 4) Execution Plan for Completion (Safe Rollout)

4.1 Phase 0 — Audit (Read-only)
- Run audit SQL to gather:
  - Per-league counts of matches with season_id IS NULL
  - Matches whose match_date does not fall into any known season window
  - Overlapping season windows within a league
- Output CSV/MD reports for sign-off.

4.2 Phase 1 — Backfill
- Backfill matches.season_id by joining on (league_id, match_date BETWEEN season.start_date AND season.end_date)
- Where seasons have no date windows, fallback mapping by Season.name (from League.season) or latest season; flag ambiguous rows for manual review.
- Re-run audit to confirm reductions in NULLs/out-of-window.

4.3 Phase 2 — DB Migrations (Flyway)
- V1__backfill_season_id.sql
  - Perform the UPDATE join by date range where seasons have windows.
  - Optionally insert temporary mapping table for manual fixes where needed.
- V2__enforce_constraints.sql
  - Add composite FK (league_id, season_id) → seasons(league_id, id)
  - Make matches.season_id NOT NULL
  - Replace unique:
    - Drop uk_match_league_round_home_away
    - Create unique on (league_id, season_id, match_date, home_team_id, away_team_id)
  - Ensure supporting indexes: (season_id), (league_id, round), (match_date)

4.4 Phase 3 — Upload Strictness and Dry-run
- Add strict and dryRun flags (default strict=true, dryRun=false) to MatchUpload endpoints.
- If Season.startDate/endDate present, then:
  - strict=true: reject and skip persistence for out-of-window rows (collect errors)
  - dryRun=true: do not persist; produce warnings for out-of-window rows and show would-be changes
- Prevent unconditional auto-creation of Season unless explicitly allowed via a flag (e.g., allowSeasonAutoCreate=false by default in strict mode).

4.5 Phase 4 — Frontend
- Confirm “Combined (All Seasons)” is an explicit option (already present).
- Keep selected seasonId in the URL query string for shareable links.
- Ensure all widgets re-query when seasonId changes (already done).

4.6 Phase 5 — Tests and Validation
- Provide Flyway migrations for test profile; run @DataJpaTest successfully against embedded DB or testcontainers.
- Add tests for uploads:
  - season_id attachment
  - strict out-of-window rejection and dry-run warnings
- Re-run audits as part of CI (read-only queries) to detect regressions.

---

## 5) Quick Reference: SQL for Audit and Backfill

5.1 Distribution by season
```sql
SELECT league_id, season_id, COUNT(*) AS matches
FROM matches
GROUP BY league_id, season_id
ORDER BY league_id, season_id;
```

5.2 Null season_id count per league
```sql
SELECT league_id, COUNT(*) AS null_season_matches
FROM matches
WHERE season_id IS NULL
GROUP BY league_id
ORDER BY null_season_matches DESC;
```

5.3 Date/season range validation (inside-season but wrong/missing season_id)
```sql
SELECT m.id, m.league_id, m.match_date, m.season_id AS current_season_id, s.id AS expected_season_id
FROM matches m
JOIN seasons s ON s.league_id = m.league_id
WHERE m.match_date BETWEEN s.start_date AND s.end_date
  AND (m.season_id IS NULL OR m.season_id <> s.id)
ORDER BY m.league_id, m.match_date DESC;
```

5.4 Out-of-range rows (do not match any known season window)
```sql
SELECT m.*
FROM matches m
LEFT JOIN seasons s
  ON s.league_id = m.league_id
 AND m.match_date BETWEEN s.start_date AND s.end_date
WHERE s.id IS NULL;
```

5.5 Backfill season_id by date range (apply)
```sql
UPDATE matches m
JOIN seasons s ON s.league_id = m.league_id
SET m.season_id = s.id
WHERE m.match_date BETWEEN s.start_date AND s.end_date
  AND (m.season_id IS NULL OR m.season_id <> s.id);
```

5.6 Duplicate detection for proposed unique key (date-based)
```sql
SELECT league_id, season_id, match_date, home_team_id, away_team_id, COUNT(*) AS cnt
FROM matches
GROUP BY league_id, season_id, match_date, home_team_id, away_team_id
HAVING cnt > 1
ORDER BY cnt DESC, match_date DESC
LIMIT 200;
```

---

## 6) Current Status Summary
- Core API filtering behavior (strict when seasonId provided; combined otherwise): Implemented in services (✓)
- Frontend explicit Combined option and seasonId propagation: Implemented; URL propagation partially pending (△)
- Upload strictness (season window enforcement; dry-run): Not implemented (✗)
- DB integrity via migrations (NOT NULL season_id; composite FK; season-aware unique): Not implemented (✗)
- Diagnostics: Partially implemented (△)

---

## 7) Actionable Next Steps (Minimal Set)
1) Add strict + dryRun parameters to uploads; implement date-window checks in MatchUploadService.
2) Prepare Flyway migrations for backfill and constraint enforcement; run in test/prod with change-management.
3) Add URL query param propagation for seasonId in frontend routes.
4) Stabilize tests: embedded DB or testcontainers + minimal migration set.

This .md document is self-contained and reflects the latest repository state as of 2025-09-04 11:44 local.
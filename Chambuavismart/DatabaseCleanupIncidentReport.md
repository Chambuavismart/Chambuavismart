# Database Cleanup Incident Report – Matches Table Emptied After Normalization Task

Date: 2025-09-15
Owner: Backend (Java/Spring Boot) – ChambuaViSmart
Scope: One-time data normalization and admin diagnostics related to Matches table


## Executive Summary
- Symptom: The `matches` table is now empty (0 rows), while other tables (seasons, teams, leagues) may still be intact.
- Trigger context: A recently added “one-time normalization” intended to set `status = 'PLAYED'` for rows that already have goals and a past date. This could be executed either at startup (guarded by a flag) or via an admin endpoint.
- Instruction: Do not run any new code or DB migrations now. Provide a diagnosis and safe recovery plan.

Conclusion up front: The normalization method we shipped is an UPDATE-only query and should not delete rows by itself. The most likely causes are (a) a schema reset (e.g., `ddl-auto=create`/`create-drop` on a non-test environment), (b) an accidental delete/truncate/cascade from another operation (e.g., deleting leagues/seasons), or (c) a divergent/hotfixed version of the normalization that used a DELETE/cleanup step. Recovery should prefer backup restore; failing that, use the existing ingestion pipeline to re-import from raw sources. Prevent recurrence by adding dry-run/guardrails, feature flags off by default, and tests.


## 1) Analysis of the Migration/Normalization Task

Relevant implemented components:
- DataNormalizationService.normalizePastScoredMatches(today) → calls `MatchRepository.normalizeScoredPastMatches(today)` and logs the updated count; optional `@PostConstruct` runner controlled by `app.normalizeOnStartup` (default false).
- AdminDiagnosticsController exposes `/api/admin/normalize` (POST) and `/api/admin/anomalies` (GET) for manual operations and checks.
- MatchRepository.normalizeScoredPastMatches JPQL:
  ```sql
  update Match m
  set m.status = PLAYED
  where m.status <> PLAYED
    and m.homeGoals is not null
    and m.awayGoals is not null
    and m.date <= :today
  ```
  This is an UPDATE statement, not a DELETE.

Potential failure points and how they could lead to a “0 rows in matches” outcome:

1. Overly broad write operations (elsewhere) or missing WHERE clauses
   - While the shipped normalization query is safe (UPDATE only), a variant or hotfix could have been deployed with an incorrect statement, e.g.:
     - `delete from Match m where m.status <> PLAYED and m.date <= :today` (attempt to remove anomalies) → wipes many rows.
     - Native SQL `DELETE FROM matches WHERE ...` with a mis-specified predicate (e.g., null handling differences), or even a mistaken `TRUNCATE matches` for a “re-ingest” scenario.
   - Another vector: maintenance scripts or import “cleanup” steps calling repository `.deleteAll()` or `.deleteByLeague(league)` before re-populating, but the re-population failing.

2. Cascade deletes or unintended side effects
   - Entities: Match has mandatory foreign keys to League, Season, Team. If Season or League rows were deleted (e.g., to fix duplicate seasons), DB-level ON DELETE CASCADE (if present in the actual database schema even if not declared in JPA) could cascade-delete matches.
   - JPA cascades are not configured for delete in the entity model, but database-level constraints may still cascade.
   - The repository does expose `long deleteByLeague(League league)`. If a maintenance action deleted leagues (or seasons) “to rebuild”, all their matches would be removed.

3. Error handling gaps (transactions, rollback, logging)
   - Normalization is annotated with `@Transactional` at the repository update method, and the service logs the count returned. If an exception occurred after a destructive operation (elsewhere) without proper transaction demarcation/rollback, partial destructive actions could be committed.
   - If logs aren’t persisted or observed, operators might not notice a very large “affected rows” change or a DELETE being triggered.

4. How an UPDATE could escalate to a DELETE state
   - UPDATE by itself will not delete rows. But if the environment was started with `spring.jpa.hibernate.ddl-auto=create` or `create-drop` (as used in tests), the schema could be recreated empty on startup, making it appear as though a normalization “deleted everything”.
   - Our test profiles explicitly use `create-drop` in unit/integration tests to reset schema. If such a property leaked into a non-test profile (e.g., through environment variables or shared application properties), the production DB could have been wiped on app restart.
   - A concurrent “rebuild” procedure might have run: delete to clean, followed by ingestion, but the ingestion failed or targeted the wrong database.

5. Divergent code / manual SQL
   - If a quick manual SQL “cleanup” was run to remove anomalies (e.g., future-dated PLAYED rows) and the predicate was wrong (or a cartesian join in a DELETE with JOIN), all rows might be deleted.


## 2) Current DB State Diagnosis (Non-destructive checks)
Run these queries/inspections manually on the affected database instance. Do not change data.

1) Confirm table contents and isolation
- Count rows in key tables:
  - `SELECT COUNT(*) AS matches_count FROM matches;`
  - `SELECT COUNT(*) AS seasons_count FROM seasons;`
  - `SELECT COUNT(*) AS teams_count FROM teams;`
  - `SELECT COUNT(*) AS leagues_count FROM leagues;`
- If `matches_count = 0` but others are not, it suggests targeted deletion or schema mismatch only for matches.

2) Verify if this is an in-memory DB reset vs. persistent store
- Check JDBC URL: is it H2 in-memory (e.g., `jdbc:h2:mem:testdb`) or file-based (e.g., `jdbc:h2:file:...`)?
- Inspect for multiple schemas/databases (dev, test, prod). Ensure you are querying the expected database.
- Check application properties/env vars actually used at runtime: `spring.jpa.hibernate.ddl-auto`, `spring.datasource.url`, `spring.profiles.active`.

3) Inspect DDL and constraints for cascades
- Export DDL or use INFORMATION_SCHEMA to check FK constraints on `matches`:
  - Is there `ON DELETE CASCADE` from seasons/leagues/teams? If yes, were any parent rows deleted recently?
- Check audit logs or application logs for delete operations on seasons/leagues/teams.

4) Look for recent destructive operations
- Review application logs around the incident window for:
  - Calls to `/api/admin/normalize` and returned `updated` count.
  - Any custom admin endpoints or scripts calling `deleteAll`, `deleteByLeague`, or batch deletes.
  - Startup logs mentioning `Hibernate: drop table ...` or `create table ...` (indicates schema reset).

5) Check for failed ingestion after a cleanup step
- If a cleanup preceded an ingestion, verify whether ingestion logs show errors (e.g., parsing failures, constraint errors). A failed ingestion after a delete would leave the table empty.


## 3) Recovery Recommendations (Do Not Execute Yet)
Prioritize data integrity and auditability. Choose the first feasible path.

A) Restore from Backups (Preferred)
- If periodic DB backups exist:
  1. Create a fresh clone/restore to a staging database first.
  2. Validate counts and last updated timestamps in staging.
  3. If correct, schedule a maintenance window to restore production.
  4. Communicate downtime and validate post-restore with targeted smoke checks (e.g., total matches, a few specific fixtures).

B) Restore from H2 or DB Files
- If using H2 in file mode: locate the database files (e.g., `.mv.db`/`.h2.db`) on the server. Recover from a previous snapshot or file-system backup.
- If running Docker: check volume snapshots or previous container volumes.

C) Re-ingest Using Existing Pipeline
- If no backups are viable, repopulate using the ingestion paths:
  - Use the CSV/fixture upload endpoints (FixtureUploadService / UI upload) to import known datasets (e.g., `E0.csv`, league archives present in repo).
  - For multi-league data, iterate league by league, season by season, verifying counts per step.
  - Before starting, disable any normalization-on-startup flags and any destructive cleanup toggles.

D) Safeguard While Recovering
- Set `app.normalizeOnStartup=false` (the default) to prevent any automatic writes on startup.
- Ensure `spring.jpa.hibernate.ddl-auto` is NOT `create`/`create-drop` in the runtime profile. Use `none` or `validate`.
- Temporarily remove admin privileges for endpoints that can delete data (if any) until recovery is complete.


## 4) Safe Re-implementation of Normalization (When Ready)
To avoid a repeat and to regain confidence, refactor the normalization workflows as follows:

1) Add a Dry-Run Mode
- Extend the admin normalize endpoint to accept `dryRun=true` (default). In dry-run, compute and return the row count that WOULD be updated and a sample of IDs, but do not execute the update.
- Require `dryRun=false` explicitly to perform the update, and log a confirmation line with the expected count and parameters.

2) Strong Transactional and Logging Guarantees
- Annotate the service layer with `@Transactional` for the full method and perform the validation query (COUNT) before the UPDATE.
- Log:
  - Query parameters (e.g., `today`),
  - Pre-count (rows matching predicate),
  - Rows actually updated,
  - A bounded sample of affected IDs (e.g., first 50).

3) Guardrails and Feature Flags
- Keep `app.normalizeOnStartup=false` by default in all non-test profiles.
- Require an explicit header or environment gate (e.g., `X-Admin-Confirm: normalize-2025-09-15`) for write operations.
- Enforce an upper safety threshold: if the operation would affect more than, say, 30% of all matches, abort and require an override flag.

4) Limit and Iterate
- Optional: implement a batched update with `LIMIT` (native SQL) or by IDs in chunks of 100–1000 rows; verify between batches.

5) Tests and Observability
- Unit/Integration tests that:
  - Verify no future-dated rows are updated.
  - Verify only rows with both goals and past/today date are updated.
  - Assert that dry-run returns the correct count and sample.
- Add metrics (e.g., via Micrometer) for `normalization.rows_affected`, `anomalies.count_future_dated_played`, etc.


## 5) Prevention and Process Improvements
- Never use `ddl-auto=create` or `create-drop` outside of a dedicated test profile. Add a startup assertion that fails the app if such a value is found in `prod`/`staging` profiles.
- Introduce a pre-flight “DB safety checklist” for any job that mutates large datasets:
  - Requires: backup verified, dry-run executed, approval recorded.
  - Requires: explicit environment flag and rate-limiting.
- Maintain an audit trail:
  - Admin operations should write an entry to an `admin_audit` table (who, when, action, count, parameters).
  - Consider soft-deletes or archiving matches prior to hard deletes.
- Add a read-only health check to the admin UI that shows:
  - Total matches,
  - Matches updated in the last 24h,
  - Anomalies counts (the existing `/api/admin/anomalies` is a good basis).
- For any future “cleanup” that involves DELETE:
  - Require a two-step process: (1) mark candidates into a staging table and review, (2) explicit approve to delete those IDs only.


## 6) What To Check Next (Actionable, No-Write)
1. Confirm via SQL that `matches` is indeed 0 and others are intact.
2. Inspect runtime properties: confirm `ddl-auto` value, datasource URL, and active profile at the time of incident.
3. Review logs for:
   - `create table/drop table` at startup.
   - Calls to `/api/admin/normalize` and their returned counts.
   - Any admin endpoints or scripts that may have performed deletes.
4. Check whether any seasons/leagues were deleted (which could cascade to matches if DB constraints allow it).
5. Decide recovery path (backup vs. re-ingestion) and schedule a controlled restore.


## Appendix: Why the Shipped Normalization Should Not Delete Rows
- The repository method is an UPDATE with selective WHERE filters and no DELETE statements.
- The service method only calls that update and logs the affected count; it does not chain any cleanup.
- Tests included in the codebase validate that only past/today with scores are updated, and future-dated scored matches remain untouched.

Given the above, the most plausible root causes for a completely empty matches table are external to the specific normalization UPDATE: schema reset (`ddl-auto`), manual/automated DELETE/TRUNCATE, or cascading deletes from parent table operations. Strengthening environment safety and admin workflows will minimize recurrence.

# Fixtures Upload & Display — Investigation Report (2025-09-03)

Author: Junie (Autonomous Programmer)

This report investigates why uploading parsed fixtures shows success in the UI but no fixtures appear in MySQL Workbench and why the UI no longer displays upcoming fixtures. It covers backend, frontend, database schema/data flow, and provides a way forward. No code changes are required at this time; the current evidence points to an environment/schema mismatch rather than a logic bug. Minimal diagnostics and operational steps are recommended first.

---

## 🔍 Backend Analysis

### 1) Do fixture upload endpoints write to the database?
- Endpoints (FixtureController):
  - POST `/api/fixtures/upload` (JSON body: FixturesUploadRequest)
  - POST `/api/fixtures/upload-text` (JSON body: FixturesUploadRequest)
  - POST `/api/fixtures/upload-csv` (multipart CSV)
- Service: FixtureUploadService (annotated with `@Transactional`) persists parsed fixtures via FixtureRepository.
- Upsert logic:
  - Entity: `Fixture` with unique constraint `(league_id, home_team, away_team, date_time)` and table `fixtures`.
  - If a row exists with the same key, it updates scores/status/round; otherwise inserts a new row.
  - `fullReplace` optionally deletes all existing fixtures for the league first using `deleteByLeague_Id`.
- Status handling: When scores are missing (typical for future fixtures), `status` is set to `UPCOMING` by the service.
- Return values: Upload endpoints return an UploadResultDTO with `success=true` only after save/saveAll completes inside the transaction.

Conclusion: The upload endpoints do write to the `fixtures` table when returning success. The control flow doesn’t indicate silent skipping on success.

### 2) Could inserts be landing in the wrong table (matches) or failing silently?
- All fixture upload paths (text/CSV) are implemented in `FixtureUploadService` and use `FixtureRepository` targeting the `fixtures` table. There is no redirection to `matches` for these endpoints.
- Validation failures cause `success=false` and return error details; the service does not continue to the save phase on failure.
- Transactions: Declared with `@Transactional`. No custom rollbacks present.

Conclusion: Writing to `matches` instead of `fixtures` is not occurring in the fixtures endpoints. Silent failure is unlikely given `success=true` is set only after persistence.

### 3) Validation or transaction issues blocking inserts without surfacing errors?
- Validation in FixtureUploadService allows missing goals (a prior change ensured fixture-mode is permissive). It still checks:
  - Season consistency (fixture dates inferred against the provided season)
  - Duplicate entries within the batch
  - Non-fatal round/date consistency warnings
- On any error discovered, the service returns `success=false` with messages. Only when `errors` is empty will it save and return success.

Conclusion: There is no path where the service reports success and then rolls back without an error. If `success=true`, inserts/updates should be committed.

---

## 🎨 Frontend Analysis

### 1) Does the UI still hit the correct endpoint for fixtures upload?
- In `match-upload.component.ts`, the fixtures tab now sends a POST to `/api/fixtures/upload-text` with `{ leagueId, season, fullReplace, rawText }` — this is the correct endpoint for fixtures.

### 2) Is the fixtures page querying the right data source?
- `fixtures.service.ts` calls the `/api/fixtures` backend:
  - `GET /api/fixtures/leagues` — builds league list using counts of `UPCOMING` fixtures.
  - `GET /api/fixtures/{leagueId}?upcomingOnly=` — fetches league fixtures.
  - `GET /api/fixtures/by-date` and `/available-dates` — fetch fixtures/dates directly from the `fixtures` table.

Conclusion: The UI correctly targets the fixtures endpoints and expects data from the fixtures table, not the matches table.

### 3) Bindings and seasons
- Fixtures associate directly to `League` (which includes a `season` string). The fixtures upload uses the selected league’s season and leagueId.
- The newer Season entity used for match uploads does not interfere with fixture persistence.

---

## 🗂️ Database Schema & Data Flow

### 1) Confirm table and schema usage
- Entity: `Fixture` maps to table `fixtures` with indexes on `(league_id, date_time)` and `status`.
- application.yml (profiles):
  - Default (no profile): schema `chambua` (ddl-auto=update)
  - dev profile: schema `chambua_dev` (ddl-auto=update)
  - test profile: schema `chambua_test` (ddl-auto=none, Flyway enabled)
  - prod profile: schema `chambua_prod` (ddl-auto=none, Flyway enabled)

Likely Root Cause: The backend may be connected to `chambua_dev` (or another profiled schema), while MySQL Workbench is inspecting `chambua`. This perfectly explains “upload success but no new rows visible,” and “no upcoming fixtures” if the UI and DB client look at different schemas.

### 2) Are rows upserted with status=scheduled/UPCOMING?
- Yes. When scores are null or dashes, `FixtureUploadService` sets `status=UPCOMING`.
- `/api/fixtures/leagues` counts only `UPCOMING`; if uploads included scores, they would be `FINISHED` and not contribute to the upcoming count — but they still appear in the fixtures list when `upcomingOnly=false`.

### 3) Did season support or migrations cause fixture insertions to be skipped?
- No fixtures-specific migration or season gating exists that would skip inserts. Season is used to infer year from the date lines and for optional filtering/join queries. There is no logic that suppresses inserts based on past seasons.

---

## ✅ Verification Checklist (to confirm environment/profile alignment)
1. Identify the active Spring profile at runtime (e.g., check startup logs or env var SPRING_PROFILES_ACTIVE). Common values: none, `dev`, `test`, `prod`.
2. Open the exact schema in MySQL Workbench matching the active profile:
   - none → `chambua`
   - dev → `chambua_dev`
   - test → `chambua_test`
   - prod → `chambua_prod`
3. Run:
   - `SELECT COUNT(*) FROM fixtures;`
   - `SELECT COUNT(*) FROM fixtures WHERE status = 'UPCOMING';`
4. Ensure the `leagues` table has the league you uploaded fixtures for in the same schema and note its `id`. Optionally verify:
   - `SELECT * FROM fixtures WHERE league_id = <that_id> ORDER BY date_time DESC LIMIT 20;`
5. In the UI, select that league and call:
   - `GET /api/fixtures/{leagueId}?upcomingOnly=false` to confirm rows list.

If counts/rows appear in the profile-matched schema, the discrepancy is confirmed as an environment/schema mismatch.

---

## 🚦 Way Forward Recommendations

### A) Fix data persistence visibility (most likely)
- Align the schema you inspect in Workbench with the backend’s active profile. If you intend to use `chambua` for local dev, either:
  - Run without a Spring profile (default) or set `SPRING_PROFILES_ACTIVE=`; or
  - Switch Workbench to `chambua_dev` if you run with `dev` profile.
- After re-aligning, re-upload fixtures and re-check counts.

### B) Ensure fixtures appear again in the UI
- With alignment, `GET /api/fixtures/leagues` should show positive `upcomingCount` for leagues with UPCOMING fixtures.
- If `upcomingCount` is zero but fixtures exist with scores, uncheck “Upcoming only” in the UI (or call `GET /api/fixtures/{leagueId}?upcomingOnly=false`).
- Optionally invoke `GET /api/fixtures/{leagueId}?refresh=true` to sync any completed results from the matches table into fixtures (FixtureRefreshService).

### C) Low-risk diagnostics (optional improvements; not mandatory)
- Temporarily enable SQL logging or add info logs in `FixtureUploadService` summarizing `insertedOrUpdated` and `deleted` counts per upload.
- Validate the upload payload matches an existing league: ensure `leagueId` sent by the UI resolves in the same schema.
- Add an environment banner (e.g., active profile) in your UI admin page to avoid confusion.

### D) Risks and compatibility
- Changing schema/profile alignment is operational and does not alter application logic.
- No risk to match upload logic; fixtures and matches pipelines are separate.
- Avoid running the backend against one schema while inspecting another — this will recur otherwise.

---

## 📌 Summary
- Code-level review indicates the fixtures upload flow is intact and writes to the `fixtures` table.
- The symptoms are best explained by the backend using a different MySQL schema (per Spring profile) than the one viewed in Workbench.
- Recommended: Confirm active Spring profile, open the corresponding schema, and re-test. The fixtures page should populate as soon as the environment alignment is corrected.

If, after environment/schema alignment, you still observe success without rows, please share:
- Active profile and JDBC URL from logs.
- The exact request body used for `/api/fixtures/upload-text` or a sample CSV header/body for `/upload-csv`.
- Results of the SQL checks above in the profile-matched schema.

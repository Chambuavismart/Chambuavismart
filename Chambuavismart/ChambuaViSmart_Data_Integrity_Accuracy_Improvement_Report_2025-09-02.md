# ChambuaViSmart – Data Integrity, Accuracy, and Improvement Report

Date: 2025-09-02 13:23 (local)
Prepared by: Junie (JetBrains Autonomous Programmer)
Scope: Non‑coding report focused on analysis and evaluation

---

## 1) Project Status Overview

Current state of the project
- Backend: Spring Boot app runs with a Health endpoint. MySQL configured; Flyway enabled. Only one Flyway script present (V4) adding a unique index to `matches` on `(league_id, round, home_team_id, away_team_id)`. No baseline schema migration (V1) in the classpath for core tables.
- Frontend: Angular app scaffolded with routing and placeholder components for key pages (Fixtures, Teams, Matchup, League, xG, Advice, History, Admin). UI shell (navbar/footer) present; feature pages not wired to backend services yet.
- Docker/DevOps: docker‑compose provides only MySQL. Backend/frontend service images are not configured at compose level (Dockerfile.backend and Dockerfile.frontend exist at root but are not yet referenced in compose). No full E2E run via compose.
- Data: No visible baseline schema or seed data in the repository. A sample CSV (E0.csv) is present, suggesting intended ingestion workflows, but no backend ingestion endpoints are currently implemented.

Key milestones achieved so far
- Health API operational (GET /api/health returns status=UP).
- Routing and page scaffolds in the frontend are in place.
- Environment configuration (application.yml) set up for dev/test/prod profiles with MySQL.
- Unique constraint for `matches` targeting duplicates across league+round+teams exists (via Flyway V4).

What is working well
- Clear technical blueprint (ChambuaViSmart_Rebuild_Blueprint_NoML.md) defines the target contracts, data flows, and UI/UX—good north star.
- Spring Boot + MySQL config is ready, with Flyway wired in all profiles.
- Frontend route map aligns closely with blueprint; navigation is established.

Areas needing improvement
- Missing baseline database schema migrations (V1__init.sql) and seed data block all data‑backed features.
- No implemented domain controllers/services for fixtures, teams, league tables, xG, predictions, history, or uploads.
- No ingestion endpoints (CSV/Raw Text); thus, no validated path from user data to persisted entities.
- Dockerization incomplete for app services; lacking backend/frontend services in compose.
- Data semantics (chronology, result vs. fixture, normalization) not yet enforced in code, increasing risk of inconsistent datasets when implemented.

---

## 2) Data Integrity & Accuracy Assessment

Reliability and consistency of match, fixture, and league data being uploaded/managed
- Current repository lacks the baseline schema and ingestion pipeline; thus actual runtime integrity cannot be verified end‑to‑end. However, the environment and blueprint indicate intended entities: leagues, teams, fixtures, matches, predictions, xg_analyses, etc.
- Existing Flyway V4 adds a unique index on the `matches` table to prevent duplicates per league+round+home+away—good practice—but without V1..V3, creation order and presence of dependent tables/columns are unknown. There is a risk of Flyway starting at V4 with an empty DB, leading to errors or a partially migrated schema.

Risks of duplicates, missing values, or outdated data
- Duplicates:
  - Matches: partially mitigated by V4 unique constraint, assuming the table and columns exist and semantics match.
  - Fixtures: no visible unique constraint yet. Without a uniqueness strategy (e.g., league_id + home_team_id + away_team_id + kickoff_utc), duplicate fixtures are likely after repeated uploads or differing time formats.
- Missing values:
  - Without strict validation/parsing, CSV ingestion can produce nulls for kickoff times, league IDs, or team references. Absent NOT NULL constraints and FK checks, referential integrity could degrade.
- Outdated data:
  - No background refresh/sync established. If fixtures change (postponed, status updates), stale records may persist. Frontend placeholders mean data may not visually refresh even if backend updates later.

Chronological order handling, results vs. upcoming fixtures, and data validation
- Chronology:
  - No implemented sorting/indexes verified for `(league_id, kickoff_utc)`; blueprint recommends indexes but until migrations exist, queries may be slow or unordered.
- Results vs. upcoming fixtures:
  - Lack of status field enforcement (e.g., SCHEDULED, LIVE, FT) can blur the distinction between fixtures and completed matches. The schema should require a `status` enum and either split `fixtures` vs. `matches` or derive `matches` from completed fixtures.
- Validation:
  - No code for parsers/validators yet. Risk area: date parsing (UTC vs. local), league/team normalization, deduping, and silent failure on malformed rows.

Specific pain points observed or anticipated
- Fixture refresh issues: With no background job or status sync strategy, fixtures can become stale or remain in SCHEDULED after kick‑off.
- Static data displays: Frontend components are placeholders; even when backend is implemented, stale UI data could result if not using async streams with explicit refresh triggers.
- Silent skipping of matches: CSV upload flows often skip rows with parsing failures without robust error reporting; blueprint calls for strict parsing and error payloads, but implementation does not exist yet.
- Migration gap: Flyway configured but baseline scripts absent. Starting at V4 can cause runtime exceptions or, worse, partially applied constraints without tables.

---

## 3) Data Quality Risks & Challenges

Risks related to user uploads (CSV/Raw Text/Fixture Uploads)
- Format variability: Different CSV headers, date formats, or league names cause parsing ambiguity without a canonical schema and strict header validation.
- Team/League normalization: Aliases and typos (e.g., “Man Utd” vs “Manchester United”) can lead to duplicate team rows or mis‑joins. A normalized alias map is required.
- Time zones: Kickoff times in local vs. UTC create duplicate/overlap risks and incorrect “Today’s Fixtures”.
- Idempotency: Re‑uploading the same file without checksum/deduping can create duplicates if unique constraints are not enforced.
- Partial uploads: Network interruption or mid‑file parse error can lead to partially ingested datasets without transactional boundaries.

Risks around interpretation (win/draw/loss recognition, averages, percentages, derived stats like xG and BTTS)
- Outcome classification: If result inference relies on string scores without strict pattern checks, W/D/L may be misclassified.
- Averages/percentages: Window selection (last 5 vs last 10) and inclusion of cup matches can distort league‑only stats unless filters are explicit.
- xG inputs: If xG values are missing for some matches, derived stats like xG diff averages or BTTS rates may be biased; need null‑safe aggregations and confidence tiers.
- Rounding and thresholds: Inconsistent rounding (two decimals vs integer %) and thresholds can produce mismatched UI vs backend numbers.

UI/UX risks affecting perception of accuracy
- Stale fixtures not refreshing: Without auto‑refresh or a clear “last updated” indicator, users may assume data is wrong.
- Inconsistent league/team naming in UI: If normalization is backend‑only and the UI shows raw names from uploads, users will see duplicates as separate teams/leagues.
- Pagination/sorting drift: If client‑side sorting differs from server ordering, rows can appear “out of order,” undermining trust.

---

## 4) Recommendations for Improvement

Strategies to improve data reliability and validation
1) Establish a complete baseline schema (Flyway V1__init.sql) with:
   - Core tables: leagues, teams, fixtures, matches, fixture_predictions, xg_analyses, team_stats, recommendations, contextual_factors.
   - Constraints:
     - NOT NULL on essential columns.
     - Foreign keys: fixtures→leagues/teams, matches→fixtures.
     - Uniqueness:
       - fixtures unique (league_id, home_team_id, away_team_id, kickoff_utc)
       - matches unique (league_id, round, home_team_id, away_team_id) [already planned by V4; align names]
   - Indexes for frequent queries: fixtures (league_id, kickoff_utc), matches (date_utc), fixture_predictions (fixture_id, created_at desc), xg_analyses (league, analyzed_at desc).
2) Strict parsing & normalization for uploads:
   - Enforce CSV headers and schema; reject files that don’t match, with clear 400 responses.
   - Normalize league and team names via a canonical dictionary and alias table (teams.alias_json).
   - Parse dates to UTC explicitly; require timezone or assume a configured default and log conversions.
   - Row‑level validation with structured error reporting per line; never silently skip.
3) Idempotent ingestion:
   - Use transactional batch inserts; on conflict, upsert based on unique keys.
   - Maintain ingestion manifests (file checksum, row counts, success/failure tallies) for audit trails.
4) Automated integrity checks:
   - Pre‑ingestion: dry‑run validate endpoint returning detected issues (duplicates, unknown teams/leagues).
   - Post‑ingestion: scheduled job compares fixture counts vs. upstream source; flag anomalies.
   - Periodic constraints validation (e.g., orphan fixtures, duplicated teams by name variants).
5) Error feedback and observability:
   - Standardize error payloads: { error: string, details?: any, line?: number }.
   - Add structured logs with correlation IDs for uploads; expose ingestion summaries to Admin UI.

Enhancing consistency across modules (Form Tables, Predictions, Analysis)
- Shared normalization utilities: One service for team/league normalization used by Fixtures, Team Profiles, Matchup, and League Table.
- Shared date/time utilities: Centralized UTC handling and “local time for display” formatting to ensure consistent ordering and “Today” calculations.
- Deterministic windows: Define constants for rolling windows (e.g., last 5 league matches) and ensure every module uses the same filters.
- DTO parity and formatting rules: Align percentage and decimal rounding rules across services and UI pipes.

Optimizations for background refreshing and backend/frontend sync
- Backend:
  - Scheduler to update fixture statuses around match times; configurable cron (e.g., every 5 minutes) and manual “refresh league” endpoint.
  - Cache for expensive league tables with explicit evict endpoint (already in blueprint).
- Frontend:
  - Use reactive streams with manual refresh buttons and an auto‑refresh interval on data‑dense screens.
  - Display “Last updated” timestamps and skeleton loaders to set expectations.
  - Avoid static snapshots by re‑querying after actions (upload, analyze, mark‑analysed).

Steps to ensure future‑proofing of data handling
- Versioned migrations: Keep Flyway versioning sequential and avoid gaps (do not ship V4 without V1–V3 baseline in a new environment).
- Schema evolution policy: Add new columns as nullable with backfills; then tighten constraints.
- Data dictionary: Maintain canonical league/team names and alias rules; store in DB and expose via an Admin UI.
- Test strategy: Include property‑based tests for parsers; repository tests covering unique constraints and date range queries.
- Backups and recovery: In prod, enable MySQL backups; test recovery paths and migration re‑runs.

---

## 5) Conclusion

Strengths (current)
- Solid blueprint and clear endpoint contracts; routing scaffolded on the frontend; backend environment configured with Flyway enabled.
- Existence of a uniqueness constraint for matches shows awareness of duplicate risks.

Prioritized improvements to ensure high data integrity and accuracy
1) Create and apply Flyway V1__init.sql baseline with full core schema, constraints, and essential indexes; add minimal seed data for fixtures. 
2) Implement ingestion endpoints with strict parsing, normalization, and transactional idempotency; provide per‑row error reporting to prevent silent skips.
3) Add fixture uniqueness constraint and UTC handling utilities; ensure Today’s Fixtures respects timezone consistently.
4) Implement P1 endpoints (Fixtures Today, Team Profiles, Matchup Analyzer, League Table, Advice) with shared normalization/date utilities and deterministic rules; wire frontend services and UI refresh patterns.
5) Introduce background refresh for fixture statuses and caching with explicit evict endpoints; expose “Last updated” in UI.
6) Establish automated integrity checks and admin dashboards for ingestion manifests, duplicates, and orphan records.
7) Complete Dockerization for backend/frontend and extend docker‑compose for E2E spins; add CI checks for migrations and tests.

If these steps are executed, ChambuaViSmart will have a robust foundation for reliable, consistent, and accurate data handling across fixtures, matches, leagues, and analytics.

---

Appendix: Evidence & References
- Backend entrypoint: backend/src/main/java/com/chambua/vismart/ChambuaViSmartApplication.java
- Health Controller: backend/src/main/java/com/chambua/vismart/controller/HealthController.java (GET /api/health)
- Config: backend/src/main/resources/application.yml (Flyway enabled; profiles dev/test/prod)
- Migrations present: backend/src/main/resources/db/migration/match_upload/V4__add_unique_index_matches_round_home_away.sql (unique index on matches)
- Blueprint: ChambuaViSmart_Rebuild_Blueprint_NoML.md (contracts, schema guidance)
- Status docs: “ChambuaViSmart Rebuild – Current Status & Next Steps.md” and “ChambuaViSmart Rebuild – Current Status & Next Steps (Generated 2025-09-01).md”

# ChambuaViSmart Comprehensive Implementation Plan (No ML)

Last updated: 2025-09-01 07:45 (local)

This plan details the end-to-end roadmap to rebuild the ChambuaViSmart app from scratch, excluding any Machine Learning (ML) features. It operationalizes the specifications in “ChambuaViSmart_Rebuild_Blueprint_NoML.md” and provides a sequence, dependencies, and verification steps. No code is included—this is a plan-only document.

Source blueprint reference: ChambuaViSmart_Rebuild_Blueprint_NoML.md (Sections 1–16; authoritative for specs and contracts)

---

## 1. Backend Structure

Authoritative refs: Blueprint Sections 3, 4, 5, 6, 8, 9, 10, 11, 12, 13

### 1.1 Architecture and Modules
- Tech: Java 17+, Spring Boot (Web, Data JPA, Validation), Maven, Lombok, Flyway/Liquibase.
- Base URL: `/api`. Content-Type: `application/json;charset=UTF-8`.
- CORS (dev): allow http://localhost:4200 (optionally 4300, 51048).
- Packages:
  - `config`, `controller`, `service`, `repository`, `dto`, `model` (entities), `util`, `exception`.
- Profiles: `dev`, `prod` via `application.yml`.

### 1.2 Entities (DB Tables)
- League (leagues): id, name, country, level.
- Team (teams): id, league_id FK→leagues.id, name, alias_json.
- Fixture (fixtures): id, league_id FK, home_team_id FK→teams.id, away_team_id FK→teams.id, kickoff_utc TIMESTAMP, status ENUM, round, created_at, updated_at.
  - Unique: (league_id, home_team_id, away_team_id, kickoff_utc)
- Match (matches): id, fixture_id FK→fixtures.id, date_utc, home_goals, away_goals, result ENUM, odds_ref, created_at.
- FixturePrediction (fixture_predictions): id, fixture_id FK, method, home_score NUMERIC(4,2), away_score NUMERIC(4,2), market, confidence INT, created_at.
- XgAnalysis (xg_analyses): id, league, home_team, away_team, home_xg NUMERIC(5,2), away_xg NUMERIC(5,2), xg_diff NUMERIC(5,2), expected_goals NUMERIC(5,2), actual_home_goals INT NULL, actual_away_goals INT NULL, analyzed_at TIMESTAMP.
- XgStats (xg_stats; optional cache): summary_json JSON, computed_at TIMESTAMP.
- TeamStats (team_stats): id, team_id FK, season, form_json JSON, momentum_json JSON, gf, ga, ppg NUMERIC(4,2), updated_at.
- H2HStats (h2h_stats): id, team_a_id FK, team_b_id FK, stats_json JSON, updated_at.
- Recommendation (recommendations): id, fixture_id FK, market, suggestion, confidence INT, rationale TEXT, created_at.
- ContextualFactor (contextual_factors): id, fixture_id FK, team_id FK NULL, factor_type, impact NUMERIC(4,2), created_at.

### 1.3 Repositories (JPA)
- FixtureRepository: CRUD; `findDistinctLeagues()`, `findByLeague(...)`, `findByDate(...)`, today queries.
- TeamRepository, LeagueRepository: lookups by normalized names and league.
- MatchRepository: by fixture, by date range, rolling windows.
- FixturePredictionRepository: by fixture ordered by `created_at` desc; delete by fixture; latest.
- XgAnalysisRepository: filters by league/team/date; latest; stats aggregations.
- TeamStatsRepository, H2HStatsRepository, RecommendationRepository, ContextualFactorRepository.

### 1.4 DTOs (Contracts)
- TeamProfileDTO: teamName, leagueName, recentMatches: MatchScoreDTO[], overall/home/away: TeamSummaryDTO, ppgTrend: PpgTrendPointDTO[], strengths, weaknesses, streaks, narrative.
- TeamProfileWithFixtureDTO:
  - profile: TeamProfileDTO
  - nextFixture: { opponent, isHome, fixtureDate ISO string, daysUntilFixture, fixtureId?, league? } | null
  - rates: { homeBTTS?, awayBTTS?, homeOver25?, awayOver25?, combinedBTTS?, combinedOver25? } | null
  - correctScoreOutlook: { likelyScores: string[], rationale: string, confidence: "low"|"medium"|"high" } | null
- LeagueTableDTO: league, season?, teamStats: TeamStandingDTO[] with rank, team, played, won, draw, lost, gf, ga, gd, pts, ppg, form[], momentumFlags[], errorMessage?.
- MatchHistoryDTO: id, analysisDate, teamAName, teamBName, predictionResult, confidenceLevel, rationale fields.
- FixturePredictionDTO, XgPrediction, XgPredictionBlend, XgStatsSummary per endpoints.
- Error payload shape everywhere: `{ error: string }` (with appropriate status codes).

### 1.5 Services (Deterministic, Non‑ML)
- FixtureAnalysisService: normalize leagues/teams; analyze using last N matches, home/away splits, standings, momentum; stable ordering; no randomness.
- FixturePredictionService: rule‑based + xG blending via XgFixtureIntegrationService and XgOnlyPredictionService; produce DTOs and explanations.
- TeamProfileService: compile recentMatches, PPG trends, strengths/weaknesses, streaks, narrative.
- TeamProfileAggregationService: builds TeamProfileWithFixtureDTO; compute BTTS/Over2.5 rates deterministically; nextFixture may be null.
- MatchupAnalyzerService: compute per‑team strength indices [0..1], classification (Superior/Lean/Even), confidence %, narrative, and feature contributions.
- LeagueTableService: compute standings and advanced metrics; cache per league; expose momentum and under‑pressure calculations.
- ExpectedGoalsAnalysisService + XgStatsEngineService: compute xG history; updates of actual goals; summaries/insights; recommendations based on xG only.
- MatchHistoryService + MatchCategoryService: paginated history; categorized tiers; search/filter/sort.
- Parsing services: CSV/raw text/H2H parsing with validation; normalize leagues/team names.
- Caching: in‑memory caches for heavy computations; eviction endpoints provided.

### 1.6 Controllers (Endpoints)
- FixtureController (`/api/fixtures`): upload CSV/text; list; today; by league; by id; delete; analyze; contextual factors; mark analyzed/modified; analyzed list; prediction linkage.
- FixturePredictionController (`/api/fixture-predictions`): predict one/all; list; latest; by league; delete; contextual factors; xg/xg-only/blend/explanation endpoints.
- ExpectedGoalsAnalysisController (`/api/xg-history`): list with filters & optional backfill; update actual goals (PUT/PATCH); stats summary; insights; recommendation.
- TeamsController + TeamProfileAggregationController (`/api/teams`): list/search; profile; profile-with-fixture.
- LeagueTableController (`/api/league-table`): table, clear-cache, leagues, teams-under-pressure, momentum-teams.
- MatchHistoryController (`/api/match-history`): paginated list; get by id; delete; categorized; leagues.
- BettingRecommendationController (`/api/betting-recommendations`): recommendation by xG.
- Admin/Export/Migration Controllers as per ingestion/export/migration needs in blueprint.
- Explicitly omit all ML controllers (MLPredictionController, XgMlTrainingController).

### 1.7 Database Schema & Migrations
- Use Flyway with `db/migration` scripts.
- V1__init.sql creates all tables, foreign keys, and indices:
  - Key indices: fixtures (league_id, kickoff_utc), unique composite on (league_id, home_team_id, away_team_id, kickoff_utc); matches (fixture_id, date_utc); fixture_predictions (fixture_id, created_at desc); xg_analyses (league, analyzed_at desc); team_stats (team_id, season).
- Seeds: follow‑up migration for demo data (leagues, teams, sample fixtures/matches/xg).
- Timestamps in UTC; sensible NOT NULL defaults; referential integrity enforced.

### 1.8 Determinism & Error Handling
- Determinism: no randomness; explicit ordering; fixed tie‑breakers; UTC storage and parsing.
- Errors: map validation to 400 `{ error }`; not found 404; server 500 `{ error }`. Log details server‑side.

---

## 2. Frontend Structure

Authoritative refs: Blueprint Sections 2, 7, 12

### 2.1 Tech Stack and Base Setup
- Angular (same major as current app), Node 16+ LTS.
- Proxy: `/api` → `http://localhost:8082` in dev.
- Structure:
  - `src/app/components/*` (fixtures, team-profile, advice-display, league-table, matchup-analyzer, xg-history, match-history, admin-upload)
  - `src/app/services/*` (HTTP clients)
  - `src/app/models/*` (DTO parity)
  - `src/app/routes/app-routing.module.ts` (route map)
  - `src/styles/*` + SharedModule (common styles, pipes, directives)

### 2.2 Routing Map
- `/` Dashboard/Home
- `/fixtures/today`
- `/teams/profile?league={LEAGUE}&team={TEAM}`
- `/teams/{league}/{team}/profile-with-fixture`
- `/matchup/analyzer`
- `/league/table/{league}`
- `/xg/history`
- `/advice`
- `/history`
- `/admin/upload`
- Behavior: deep linking via query params; initialize state from URL; update URL on change; breadcrumbs.

### 2.3 Core Components and Pages
- AppShell: navbar, footer, router-outlet, breadcrumbs, 404 page.
- FixturesTodayComponent: table [Date/Time, League, Home, Away, Status]; sortable; row click navigates to profile or analyzer.
- TeamProfileComponent: reads `/api/teams/profile` and renders profile; link to aggregated profile.
- AggregatedProfileComponent: `/api/teams/{league}/{team}/profile-with-fixture` (profile + nextFixture + rates + correct-score outlook).
- MatchupAnalyzerComponent: form; POST analyze; charts (strength bars, confidence meter, trend line, home/away splits); narrative.
- LeagueTableComponent: GET league table; tabs for momentum and under-pressure lists; cache clear button.
- XgHistoryComponent: xG history list/visualizations; PUT/PATCH updates; insights panel.
- AdviceDisplayComponent: GET recommendation by xG inputs.
- MatchHistoryComponent: paginated, filterable/sortable history table.
- AdminUploadComponent: CSV and raw text upload with validation feedback.

### 2.4 Services (HTTP Clients)
- fixtures.service.ts; fixture-predictions.service.ts; team-profile.service.ts; matchup-analyzer.service.ts; league-table.service.ts; xg.service.ts; advice.service.ts; history.service.ts; upload.service.ts.
- Methods map 1:1 to backend endpoints (see Section 3 of blueprint).

### 2.5 Styles, Colors, Typography, Accessibility
- Colors (authoritative): Primary #1A3C34, Accent #1E90FF, Accent Red #FF4500, Success #32CD32, Chelsea Blue #034694, Gold #FDB913, Light Gray #F5F5F5, White #FFFFFF, Black #000000.
- Gradients: Primary Button, Accent Button, Card Background, Borders (see blueprint 2.3).
- Typography: Poppins (headings 600+), Inter/Roboto (body). Scale: H1 28–32, H2 24, H3 20, Body 14–16 px.
- Spacing: base 8px; cards 24/16; section gaps 24–32 desktop.
- Responsiveness: breakpoints 0–599, 600–959, 960–1279, 1280+.
- Accessibility: focus outlines; ARIA on charts/buttons; contrast > 4.5:1; keyboard accessible controls.

---

## 3. Integration Plan

Authoritative refs: Blueprint Sections 3, 4, 7, 13, 14

### 3.1 Data Contracts (DTO Parity)
- TypeScript interfaces mirror backend DTOs exactly (fields, casing, nullability):
  - TeamProfileDTO, TeamProfileWithFixtureDTO, LeagueTableDTO, MatchHistoryDTO, FixturePredictionDTO, XgPrediction, XgPredictionBlend, XgStatsSummary.
- Optional/nullable fields as specified (e.g., `nextFixture` may be `null`).

### 3.2 API Calling Conventions
- All under `/api`; use HttpClient with typed responses.
- Filtering via query parameters; PUT/PATCH for xG actual goals; POST for analyses and cache evictions.

### 3.3 Error Handling & UX
- Map HTTP errors to consistent UI:
  - 400: display validation details; highlight fields.
  - 404: not-found state; offer navigation back.
  - 500: toast with retry; log details (not shown to user).
- Standard error payload: `{ error: string }`.

### 3.4 Caching & Determinism
- Frontend: route-scoped caching via RxJS `shareReplay`; invalidate on param changes; persist key filters in localStorage.
- Backend: in-memory caches for league tables and heavy summaries; explicit eviction endpoint.
- Deterministic sorting on lists and charts; stable scales/legends.

---

## 4. Development Workflow

Authoritative refs: Blueprint Sections 11, 12

### 4.1 Project Initialization (P0)
- Backend: Spring Boot skeleton (Web, JPA, Validation, Lombok, Flyway); `application.yml`; health endpoint; CORS.
- Frontend: Angular workspace; routing; `proxy.conf.json`; app shell + navbar.
- Verification: Health 200 OK; Angular dev server loads with shell.

### 4.2 Database & Migrations (P0)
- Implement V1__init.sql per schema; add seed data migration.
- Verification: Flyway runs; CRUD via repositories; sample data visible at `/api/fixtures`.

### 4.3 Fixtures (P1)
- Implement FixtureController, parsing services; FixturesTodayComponent.
- Verification: Upload CSV; GET `/api/fixtures/today` renders sorted table.

### 4.4 Teams & Profiles (P1)
- TeamProfileService/TeamsController; TeamProfileComponent; AggregatedProfileComponent.
- Verification: `/api/teams/profile` returns DTO; UI shows narrative and trends.

### 4.5 Matchup Analyzer (P1)
- MatchupAnalyzerService; POST analysis endpoint(s); component with charts.
- Verification: Deterministic classification/confidence; charts render.

### 4.6 League Table & Momentum (P1)
- LeagueTableService/Controller with caching; component tabs for momentum/pressure.
- Verification: standings + lists render; cache eviction works.

### 4.7 Advice Display (P1)
- BettingRecommendationController; AdviceDisplayComponent.
- Verification: recommendation displays given xG.

### 4.8 xG History & Insights (P2)
- ExpectedGoalsAnalysisController; XgHistoryComponent; PUT/PATCH updates.
- Verification: history loads; insights visible; updates persist.

### 4.9 Match History (P2)
- MatchHistoryService/Controller; MatchHistoryComponent.
- Verification: pagination/filter/sort; leagues endpoint used for filter.

### 4.10 Admin Upload (P2)
- CSV and raw text upload endpoints; AdminUploadComponent.
- Verification: uploads parse; fixtures stored; clear errors.

### 4.11 Exports & Reporting (P3)
- ExportController; CSV/JSON downloads.
- Verification: correct files and data integrity.

### 4.12 Polish: Accessibility, Performance, Caching
- Focus rings; ARIA; contrast; lazy loading; code-splitting as needed.
- Verification: Lighthouse/axe checks; cache behavior documented.

### 4.13 Docker & Environments
- Dockerfiles (backend, frontend); docker-compose with MySQL; env vars.
- Verification: stack runs locally; smoke checklist passes.

### 4.14 Final Acceptance
- Cross-check against blueprint Section 16; remove ML artifacts; finalize README.

---

## 5. Testing Strategy

Authoritative refs: Blueprint Sections 9, 13

### 5.1 Backend
- Unit: services (FixtureAnalysisService, FixturePredictionService, LeagueTableService momentum, ExpectedGoalsAnalysisService).
- Repository: queries via Testcontainers/MySQL or H2 (compatibility mode).
- Web layer: `@WebMvcTest` for critical controllers (fixtures today, team profile, league table, xg, recommendations).
- Integration: seed data → flow tests (upload → analyze → predict → history).
- Determinism tests: identical inputs yield identical outputs; date/time normalization.

### 5.2 Frontend
- Unit: components with mock services; services with HttpTestingController verifying routes/params.
- Integration: components + services; routing and query param sync.
- E2E: Cypress for core flows (Deployment Verification checklist).
- Accessibility: axe-core on key pages.

### 5.3 CI Pipeline
- Lint, formatting; backend tests (`mvn test`); frontend tests (`ng test`); Cypress E2E in dev compose.
- Artifacts: backend JAR, frontend `dist`; Docker images; push for staging.

---

## 6. Endpoint ↔ Frontend Mapping (Quick Reference)
- FixturesTodayComponent: GET `/api/fixtures/today` → Fixture[]; row click → TeamProfile or prefilled MatchupAnalyzer.
- TeamProfileComponent: GET `/api/teams/profile?league=&team=` → TeamProfileDTO.
- AggregatedProfileComponent: GET `/api/teams/{league}/{team}/profile-with-fixture` → TeamProfileWithFixtureDTO.
- MatchupAnalyzerComponent: POST `/api/fixtures/{fixtureId}/analyze` or `/api/matchup/analyze` → MatchAnalysis.
- LeagueTableComponent: GET `/api/league-table/{league}`; tabs GET `momentum-teams`, `teams-under-pressure`; POST `clear-cache`.
- XgHistoryComponent: GET `/api/xg-history` (filters); PUT/PATCH `/api/xg-history/{id}/actual-goals`; GET `stats-summary`, `stats-insights`.
- AdviceDisplayComponent: GET `/api/betting-recommendations/recommendation?homeXg=&awayXg=` → MatchBettingRecommendation.
- MatchHistoryComponent: GET `/api/match-history` (pagination/filters); GET `/api/match-history/leagues`.
- AdminUploadComponent: POST `/api/fixtures/upload` (multipart) and POST `/api/fixtures/upload-text`.

---

## 7. Determinism, Validation, and Error Mapping Rules
- Normalize league/team names on ingestion; strict CSV validation (line numbers in messages).
- Sorting conventions: upcoming fixtures by date asc; predictions/analyses by `created_at` desc.
- Time handling: store in UTC; parse/format explicitly; frontend displays local where appropriate.
- Error payloads: `{ error: string }` across services; avoid leaking stack traces.
- Remove and omit any ML code, classes, endpoints (scan for “MLPrediction”, “XgMlTraining”).

---

## 8. Sequencing and Dependencies Overview
- Dependencies: DB schema → repositories → services → controllers → frontend services → components → E2E flows.
- Roadmap mirrors Blueprint Section 11: P0 Foundations → P1 Core Flows → P2 Analytics/History/Admin → P3 Enhancements.
- Verification gates at each milestone (unit/integration/E2E + Deployment Verification checklist) before proceeding.

---

## Appendix: Deployment Verification Checklist (from Blueprint)
- Health check returns OK.
- /fixtures/today renders a list.
- Team profile loads from /teams/profile and aggregated profile works.
- Matchup analyzer returns classification and confidence.
- League table shows standings, momentum, and under-pressure views.
- xG history loads and insights display; actual goals updates persist.
- Advice display returns recommendation based on xG only.



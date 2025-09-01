# ChambuaViSmart Rebuild Blueprint (No ML)

This document is a complete, stand‑alone technical blueprint for rebuilding the ChambuaViSmart app (frontend + backend) from scratch, excluding any Machine Learning (ML) features. By following only this document, an engineering team can implement the application to match the current app’s look, feel, UI/UX, behavior, and backend functionality—minus ML endpoints, predictors, and ML simulations.

Last updated: 2025-09-01

--------------------------------------------------------------------------------

## 1. Scope and Explicit Exclusions

- Goal: Rebuild the application to match the current app’s UX and functionality exactly for the following feature areas: fixtures, team profiles, matchup analysis, league statistics, recommendations/advice display, expected goals (xG) calculations, what‑if simulations, parsing, and data ingestion.
- Do NOT include: any ML features. Specifically exclude:
  - MLPredictionController and any ML endpoints
  - Predictive models, probability simulators based on ML, model training or calibration
  - XgMlTrainingController and any ML training flows
- Keep everything else intact, including xG analytics (non‑ML), deterministic rules/logic, and all data ingestion and parsing flows.

--------------------------------------------------------------------------------

## 2. UI/UX Specification

The UI must replicate the existing application precisely: layout, colors, typography, spacing, routing, deep links, and interactions. This section details all screens, navigation, and visual specifications necessary to do so without consulting the original app.

2.1 Global Navigation and Routing Map
- Primary routes and screens:
  - “/” → Home/Dashboard
  - “/fixtures/today” → Today’s Fixtures list
  - “/teams/profile?league={LEAGUE}&team={TEAM}” → Team Profile (query params)
  - “/teams/{league}/{team}/profile-with-fixture” → Deep link consumer for aggregated profile (service URL; UI route displays same content)
  - “/matchup/analyzer” → Matchup Analyzer (form + results)
  - “/league/table/{league}” → League Table (with momentum/pressure views)
  - “/xg/history” → xG History & Insights
  - “/advice” → Recommendations/Advice Display
  - “/history” → Match Analysis History
  - “/admin/upload” → Data Upload/Parsing (CSV/raw text)
- Navigation elements:
  - Top navbar with sections: Home, Fixtures, League, Teams, Matchup, xG, Advice, History, Admin
  - Sub‑navigation/tabs on some pages (e.g., League: Table | Momentum Teams | Teams Under Pressure)
  - Breadcrumbs: show Home > Section > Subsection for deep pages (e.g., Home > League > Table)
  - Preserve browser back/forward behavior; keep query params in URL for stateful filters
- Deep linking: URL query parameters control filters (league, date windows, team names). Pages initialize their state from the URL and update query params on changes.

2.2 Components and Views
- Cards and Sections: Use elevated cards with consistent paddings (24px desktop, 16px mobile). Maintain gradients and shadows (see colors below).
- Tables:
  - Today’s Fixtures: columns [Date/Time, League, Home, Away, Status]; sortable by Date/Time; row click navigates to Team Profile or Matchup Analyzer prefilled.
  - League Table: columns [Pos, Team, MP, W, D, L, GF, GA, GD, Pts, PPG, Form]; sticky header; highlight momentum indicators; filters for form window.
  - Match History: paginated; columns [Date, League, Team A, Team B, Result/Outcome, Confidence, Category]; filterable by date/league/search, sortable by date/confidence.
- Charts:
  - Matchup Analyzer: strength indices (0‑1 scale) bar chart; confidence meter (0–100%); form trend line for last 5 matches; home/away splits.
  - League Table: mini‑sparklines for recent form; momentum badge chips.
  - xG History: scatter/line charts for xG diff and realized goals; insights panel.
- Filters & Interactions:
  - Consistent controls (selects, date pickers, search boxes). Keyboard accessible; Enter triggers search; Esc clears.
  - Persist important filters in query params and localStorage (e.g., last selected league).
- Accessibility:
  - All interactive elements have focus outlines; ARIA labels on charts and buttons; color contrast > 4.5:1 for text.
- Responsiveness:
  - Breakpoints: 0–599, 600–959, 960–1279, 1280+ px. Cards stack on small screens; tables become scrollable with sticky headers.

2.3 Color, Typography, Spacing (Authoritative)
- Color palette (from UI_COLORS.md):
  - Primary: #1A3C34 (Dark green)
  - Accent: #1E90FF (Dodger Blue)
  - Accent Red: #FF4500 (errors)
  - Success: #32CD32
  - Chelsea Blue: #034694
  - Gold/Yellow: #FDB913 (confidence/draw indicators)
  - Light Gray: #F5F5F5, White: #FFFFFF, Black: #000000
- Gradients:
  - Primary Button: linear-gradient(135deg, #1A3C34 0%, #0F2A24 100%)
  - Accent Button: linear-gradient(135deg, #1E90FF 0%, #1565C0 100%)
  - Card Background: linear-gradient(145deg, #f5f5f5 0%, #ffffff 100%)
  - Borders: linear-gradient(90deg, #1A3C34 0%, #1E90FF 100%)
- Typography:
  - Headings: Poppins (600+); Body: Inter/Roboto; enable font smoothing
  - Scales: H1 28–32, H2 24, H3 20, Body 14–16 px; line-height 1.4–1.6
- Spacing:
  - Base unit 8px; cards use 24/16 px padding; section gaps 24–32 px on desktop

--------------------------------------------------------------------------------

## 3. Backend Specification (All Services/APIs, No ML)

General:
- Base URL: /api
- Content type: application/json;charset=UTF-8 unless uploading files
- CORS: allow http://localhost:4200 (add 4300 and 51048 in dev if needed)
- Error payloads: { "error": string, ... } with appropriate HTTP status

3.1 Fixtures
Controller: FixtureController @RequestMapping("/api/fixtures")
- POST /api/fixtures/upload (multipart/form-data)
  - Params: file (CSV), leagueName (string)
  - Response: { success: boolean, message: string, fixtureCount?: number }
  - Behavior: validates CSV; normalizes league; parses and stores fixtures.
- GET /api/fixtures
  - Response: Fixture[]
- GET /api/fixtures/today
  - Response: Fixture[] (empty array when none)
- GET /api/fixtures/league/{league}
  - Response: Fixture[] (league normalized)
- GET /api/fixtures/{id}
  - Response: Fixture | 404
- DELETE /api/fixtures/{id}
  - Response: { message } | appropriate status
- GET /api/fixtures/{fixtureId}/analyses
  - Response: MatchAnalysis[]
- GET /api/fixtures/{fixtureId}/analyses/latest
  - Response: MatchAnalysis | 404 if none
- GET /api/fixtures/{fixtureId}/predictions
  - Response: FixturePrediction[] (non‑ML rules/xG powered)
- POST /api/fixtures/{fixtureId}/analyze
  - Response: MatchAnalysis (deterministic analysis)
- POST /api/fixtures/{fixtureId}/contextual-factor
  - Body: { teamName: string, factorType: string, impact: number }
  - Response: FixtureContextualFactorRecord | error
- POST /api/fixtures/{id}/mark-analysed
  - Body: MatchAnalysisResponseDTO
  - Response: { message: string, updated: boolean }
- GET /api/fixtures/analyzed
  - Response: AnalyzedFixtureDTO[]
- POST /api/fixtures/{id}/mark-modified
  - Response: { message: string }
- POST /api/fixtures/upload-text (application/x-www-form-urlencoded or json)
  - Params/Body: fixtureText, leagueName
  - Response: { success, message, fixtureCount? }

3.2 Fixture Predictions & xG (non‑ML)
Controller: FixturePredictionController @RequestMapping("/api/fixture-predictions")
- POST /api/fixture-predictions/predict-all → FixturePredictionDTO[]
- POST /api/fixture-predictions/{fixtureId}/predict → FixturePredictionDTO
- GET /api/fixture-predictions/{fixtureId} → FixturePredictionDTO[]
- GET /api/fixture-predictions/{fixtureId}/latest → FixturePredictionDTO
- GET /api/fixture-predictions/by-league → { [league: string]: FixturePredictionDTO[] }
- DELETE /api/fixture-predictions/{predictionId}
- DELETE /api/fixture-predictions/fixture/{fixtureId}
- POST /api/fixture-predictions/{fixtureId}/contextual-factor → record context factor
- GET /api/fixture-predictions/{fixtureId}/xg → XgPrediction
- GET /api/fixture-predictions/{fixtureId}/blend → XgPredictionBlend
- GET /api/fixture-predictions/{fixtureId}/xg-explanation → { explanation: string }
- GET /api/fixture-predictions/{fixtureId}/xg-only → XgPrediction

3.3 xG History and Insights
Controller: ExpectedGoalsAnalysisController @RequestMapping("/api/xg-history")
- GET /api/xg-history
  - Query: league?, from?, to?, team?, confidenceTier?, sortByXgDiffDesc?, triggerBackfill?(default true)
  - Response: ExpectedGoalsAnalysis[]
  - Behavior: optional backfill run for league; returns filtered history.
- PUT /api/xg-history/{id}/actual-goals?homeGoals=&awayGoals=
  - Response: ExpectedGoalsAnalysis | 404
- PATCH /api/xg-history/{id}/actual-goals
  - Body: { actualHomeGoals: number, actualAwayGoals: number }
  - Response: 200 OK
- GET /api/xg-history/stats-summary → XgStatsSummary
- GET /api/xg-history/stats-insights → string[]
- GET /api/xg-history/{id}/recommendation → MatchBettingRecommendation | 404
- GET /api/xg-history/recommendation?homeXg=&awayXg= → MatchBettingRecommendation

3.4 Team Search and Profiles
Controllers: TeamsController @RequestMapping("/api/teams"), TeamProfileAggregationController @RequestMapping("/api/teams")
- GET /api/teams → string[] (all teams or by league=)
- GET /api/teams/search?query= → TeamSearchResultDTO[]
- GET /api/teams/profile?league=&team= → TeamProfileDTO
- GET /api/teams/{league}/{team}/profile-with-fixture → TeamProfileWithFixtureDTO
  - Behavior: profile + optional upcoming fixture + optional rates + optional correct‑score outlook; nextFixture may be null.

3.5 League Tables and Momentum
Controller: LeagueTableController @RequestMapping("/api/league-table")
- GET /api/league-table/{leagueName} → LeagueTableDTO
- POST /api/league-table/{leagueName}/clear-cache → { message }
- GET /api/league-table/leagues → string[] (if present in LeagueController; see also MatchHistoryController /leagues)
- GET /api/league-table/{leagueName}/teams-under-pressure → TeamUnderPressureDTO[]
- GET /api/league-table/{leagueName}/momentum-teams → UnderdogMomentumTeamDTO[]

3.6 Match History
Controller: MatchHistoryController @RequestMapping("/api/match-history")
- GET /api/match-history
  - Query: page=0, size=10, filter?, search?, sortBy=\"date|confidence|league\", league?, dateFrom?, dateTo?
  - Response: { matches: MatchHistoryDTO[], total: number }
- GET /api/match-history/{id} → MatchHistoryDTO | 404
- DELETE /api/match-history/{id} → 204
- GET /api/match-history/categorized → { [tier: string]: MatchAnalysisDTO[] }
- GET /api/match-history/leagues → string[]

3.7 Recommendations (Advice)
Controller: BettingRecommendationController @RequestMapping("/api/betting-recommendations")
- GET /api/betting-recommendations/recommendation?homeXg=&awayXg= → MatchBettingRecommendation

3.8 Data Ingestion and Admin (non‑exhaustive but sufficient)
- Raw Text and H2H Parsing: endpoints exist under RawTextMatchUploadController, RawMatchDataController, EnhancedLeagueMatchUploadController, LeagueUploadController.
- Export utilities: ExportController for dumping data/reports.
- Migration support: MigrationController for controlled migrations.

Notes:
- Remove/omit any ML endpoints listed in MLPredictionController and XgMlTrainingController from this rebuild.

--------------------------------------------------------------------------------

## 4. DTOs and Repositories (Key Contracts)

DTOs (Java Lombok builders; replicate fields exactly):
- TeamProfileDTO
  - teamName: string
  - leagueName: string
  - recentMatches: MatchScoreDTO[]
  - overall: TeamSummaryDTO; home: TeamSummaryDTO; away: TeamSummaryDTO
  - ppgTrend: PpgTrendPointDTO[]
  - strengths: string[]; weaknesses: string[]; streaks: string[]
  - narrative: string
- TeamProfileWithFixtureDTO
  - profile: TeamProfileDTO
  - nextFixture: { opponent: string, isHome: boolean, fixtureDate: ISO string, daysUntilFixture: int, fixtureId?: long, league?: string } | null
  - rates: { homeBTTS?: number, awayBTTS?: number, homeOver25?: number, awayOver25?: number, combinedBTTS?: number, combinedOver25?: number } | null
  - correctScoreOutlook: { likelyScores: string[], rationale: string, confidence: \"low|medium|high\" } | null
- LeagueTableDTO (typical fields)
  - league: string; season?: string
  - teamStats: TeamStandingDTO[] with rank, team, played, won, draw, lost, gf, ga, gd, pts, ppg, form[], momentumFlags[]
  - errorMessage?: string
- MatchHistoryDTO
  - id: long; analysisDate: Instant; teamAName, teamBName: string; predictionResult: string; confidenceLevel: int; additional fields for rationale
- Fixture, FixturePrediction, MatchAnalysis and related DTOs as per controllers above

Repositories (JPA):
- FixtureRepository: CRUD for Fixture, plus findDistinctLeagues(), findByLeague, findByDate
- Team repositories to support profiles and summaries
- History/Analysis repositories for xG, predictions, and match history
- Ensure proper indexes exist on (league, date), fixture foreign keys, and lookup columns

--------------------------------------------------------------------------------

## 5. Business Logic and Data Processing

Implement services to mirror current behavior in a deterministic, non‑ML way:
- FixtureAnalysisService: standardize fixture normalization; compute deterministic analysis results (e.g., using last N matches, home/away splits, league position, momentum).
- FixturePredictionService: non‑ML rules blended with xG projections (XgFixtureIntegrationService, XgOnlyPredictionService) to produce FixturePredictionDTO.
- TeamProfileService: compile TeamProfileDTO with recent matches, PPG trends, strengths/weaknesses, streaks; produce narrative.
- TeamProfileAggregationService: constructs TeamProfileWithFixtureDTO; nextFixture may be null; may compute lightweight BTTS/Over2.5 rates deterministically.
- MatchupAnalyzerService: compute two‑team strength indices (0–1), classification (Superior/Lean/Even), confidence % (0–100), narrative, and feature contributions; input MatchupAnalyzeRequest.
- LeagueTableService: compute standings and advanced metrics; cache per league; expose momentum and pressure calculations.
- XgStatsEngineService and ExpectedGoalsAnalysisService: compute xG history and insights from past matches; allow updating actual goals; generate recommendations based on xG only (no ML).
- MatchHistoryService & MatchCategoryService: retrieve paginated history and produce categorized views by tiers.
- Parsing Services: CSV/Raw text/H2H parsing with validation; normalize leagues and team names.
- Caching: In‑memory caches for heavy computations (e.g., league tables) with explicit evict endpoints.

Determinism:
- Fix any randomness (use constant seeds) and always order inputs; time‑zone consistency (use UTC for storage, local for display when needed).

Error handling:
- Map validation to 400, not found to 404, server failures to 500; return { error } payloads where specified.

--------------------------------------------------------------------------------

## 6. Database Schema (Relational)

Core tables (suggested schema; adjust naming to match your conventions):
- leagues(id PK, name, country, level)
- teams(id PK, league_id FK→leagues.id, name, alias_json)
- fixtures(id PK, league_id FK, home_team_id FK→teams.id, away_team_id FK→teams.id, kickoff_utc TIMESTAMP, status ENUM, round, created_at, updated_at)
- matches(id PK, fixture_id FK→fixtures.id, date_utc TIMESTAMP, home_goals INT, away_goals INT, result ENUM, odds_ref VARCHAR, created_at)
- fixture_predictions(id PK, fixture_id FK, method VARCHAR, home_score NUMERIC(4,2), away_score NUMERIC(4,2), market VARCHAR, confidence INT, created_at)
- xg_analyses(id PK, league VARCHAR, home_team VARCHAR, away_team VARCHAR, home_xg NUMERIC(5,2), away_xg NUMERIC(5,2), xg_diff NUMERIC(5,2), expected_goals NUMERIC(5,2), actual_home_goals INT NULL, actual_away_goals INT NULL, analyzed_at TIMESTAMP)
- xg_stats(summary_json JSON, computed_at TIMESTAMP) — optional cache table
- team_stats(id PK, team_id FK, season VARCHAR, form_json JSON, momentum_json JSON, gf INT, ga INT, ppg NUMERIC(4,2), updated_at)
- h2h_stats(id PK, team_a_id FK, team_b_id FK, stats_json JSON, updated_at)
- recommendations(id PK, fixture_id FK, market VARCHAR, suggestion VARCHAR, confidence INT, rationale TEXT, created_at)
- contextual_factors(id PK, fixture_id FK, team_id FK NULL, factor_type VARCHAR, impact NUMERIC(4,2), created_at)

Indexes and constraints:
- fixtures: IDX(league_id, kickoff_utc), unique on (league_id, home_team_id, away_team_id, kickoff_utc)
- matches: IDX(fixture_id), IDX(date_utc)
- fixture_predictions: IDX(fixture_id, created_at desc)
- xg_analyses: IDX(league, analyzed_at desc)
- team_stats: IDX(team_id, season)

Migrations:
- Use Flyway (db/migration) or Liquibase; provide baseline V1__init.sql and follow semantic versioning
- Provide seed scripts for demo data (use sample_fixture_data.csv and sample_match_data.csv patterns)

--------------------------------------------------------------------------------

## 7. Frontend Stack and Project Structure

- Framework: Angular (same major version as current repo)
- Node: 16+ LTS (ensure compatibility with Angular CLI in use)
- Structure:
  - src/app/components/* → feature components (fixtures, team-profile, advice-display, league-table, matchup-analyzer, xg-history, match-history, admin-upload)
  - src/app/services/* → HTTP clients (fixtures.service.ts, team-profile.service.ts, matchup-analyzer.service.ts, league-table.service.ts, xg.service.ts, advice.service.ts, history.service.ts, upload.service.ts)
  - src/app/models/* → TypeScript interfaces for DTO parity with backend
  - src/app/routes/app-routing.module.ts → route map per section above
  - src/styles/* and shared module for common styles/pipes (odds/percent formatters)
- Theming: implement colors and gradients from Section 2.3. Keep CSS specificity controlled; use SCSS variables for palette.
- Accessibility: maintain focus rings, aria-labels for charts and actionable items, semantic headings.

--------------------------------------------------------------------------------

## 8. Backend Stack and Project Structure

- Java 17+, Spring Boot (Web, Data JPA, Validation), Maven 3.6+
- Modules (packages): controller, service, repository, dto, model, util, config
- Configuration:
  - application.yml with dev and prod profiles
  - CORS for http://localhost:4200 (optionally 4300, 51048) in dev
  - DB config (MySQL for prod; H2 or Testcontainers for tests)
- Logging: use SLF4J; log key operations and errors; redact sensitive data
- Determinism: avoid non‑deterministic ordering; fix seeds if randomness needed (prefer none)

--------------------------------------------------------------------------------

## 9. Dependencies and Tooling

Backend:
- spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation
- mysql-connector-j (prod), h2 (tests)
- lombok
- flyway-core or liquibase-core

Frontend:
- @angular/* (CLI, router, forms, common)
- rxjs
- charting lib used by current app (e.g., Chart.js or ngx-charts). Keep scales and legends identical.

Tooling:
- Node 16+ LTS, npm 8+
- Maven 3.6+
- Docker, docker-compose
- Scripts: start-dev.bat, prod-build.bat, dev-mode.bat, prod-mode.bat

--------------------------------------------------------------------------------

## 10. Deployment, Environments, and Docker

Environments:
- Dev: local run or docker-compose
- Staging: docker-compose on VM
- Prod: docker-compose or Kubernetes; externalize DB; enable backups

Docker images:
- Backend: build from Dockerfile.backend (expose 8082)
- Frontend: build static assets via ng build and serve with Nginx (expose 80)

docker-compose.yml (conceptual wiring):
- services:
  - db (mysql:8) with volume, env MYSQL_DATABASE=chambua, MYSQL_USER, MYSQL_PASSWORD
  - backend (app-backend) depends_on db; env DB_URL; maps 8082:8082
  - frontend (app-frontend) depends_on backend; maps 4200 or 80; in dev, use Angular dev server with proxy
- networks: default

Environment variables:
- DB_URL, DB_USER, DB_PASSWORD
- APP_ENV (dev|prod)
- CORS_ALLOWED_ORIGINS

Seeding and migrations:
- Flyway runs on backend start; seed minimal data.

Verification checklist:
- Health check returns OK
- Load /fixtures/today → list renders
- Navigate to team profile → data appears
- Run matchup analyzer → classification + confidence shown
- Open league table → standings and momentum visible
- xG history → list and insights visible
- Advice display → recommendation based on xG visible

--------------------------------------------------------------------------------

## 11. Prioritized Feature Roadmap (No ML)

P0 (Foundations)
- Project scaffolding, routing, theming, DB connectivity, migrations

P1 (Core Flows)
- Today’s Fixtures (UI + /api/fixtures/today)
- Team Profiles (UI + /api/teams/profile and /api/teams/{league}/{team}/profile-with-fixture)
- Matchup Analyzer (UI + /api/matchup/analyze)
- League Table & Momentum (UI + /api/league-table/*)
- Advice Display (UI + /api/betting-recommendations/recommendation)

P2 (Analytics and History)
- xG History & Insights (UI + /api/xg-history/*, non‑ML)
- Match History (UI + /api/match-history/*)
- Data Ingestion/Admin (upload CSV, raw text/H2H parsing)

P3 (Enhancements)
- Exports & reporting
- Caching and performance improvements
- Accessibility audits and fine‑tuning

Note: ML Predictor and ML Simulations are intentionally omitted.

--------------------------------------------------------------------------------

## 12. Step‑by‑Step Build Instructions (From Scratch)

Backend
1) Initialize Spring Boot project with Web, JPA, Validation, Lombok, Flyway. Configure MySQL (prod) and H2 (test). Set server.port=8082.
2) Create entities and repositories for leagues, teams, fixtures, matches, predictions, xg_analyses, contextual_factors, recommendations.
3) Implement services from Section 5 (deterministic logic). Add caching for league table.
4) Implement controllers and endpoints exactly as defined in Section 3 (omit ML controllers entirely).
5) Add Flyway migrations (V1__init.sql) and initial seeds. Verify CRUD paths and analysis flows.
6) Add unit tests for services (e.g., MatchHistoryServiceTest, LeagueTableServiceMomentumTeamsTest) and controller tests for critical paths.
7) Build: mvn clean package. Run: mvn spring-boot:run.

Frontend
1) Create Angular workspace and app. Add routing. Configure proxy.conf.json to forward /api to http://localhost:8082.
2) Implement global shell: navbar, footer, route outlets, 404 page.
3) Implement pages/components:
   - FixturesTodayComponent: calls GET /api/fixtures/today; table view; click navigates to profiles/matchup.
   - TeamProfileComponent: consumes /api/teams/profile and renders profile; link to aggregated profile (with next fixture).
   - TeamExplorer/AggregatedProfileComponent: consumes /api/teams/{league}/{team}/profile-with-fixture.
   - MatchupAnalyzerComponent: form for league/season/team ids; POST /api/matchup/analyze; show charts and narrative.
   - LeagueTableComponent: GET /api/league-table/{league}; tabs to momentum and under‑pressure lists.
   - XgHistoryComponent: GET /api/xg-history and insights; update actual goals via PUT/PATCH.
   - AdviceDisplayComponent: GET /api/betting-recommendations/recommendation.
   - MatchHistoryComponent: GET /api/match-history; filters; pagination.
   - AdminUploadComponent: POST /api/fixtures/upload and upload‑text.
4) Apply styles: implement colors/gradients and typography per Section 2.3; ensure responsive layouts.
5) Testing: unit tests for services and components; add Cypress E2E for core flows.
6) Build: ng build --configuration production.

Docker/Deploy
1) Build backend image from Dockerfile.backend; build frontend image serving dist/ via Nginx.
2) Compose with MySQL; set env vars; run migrations on backend start.
3) Smoke test the checklist from Deployment Verification.

--------------------------------------------------------------------------------

## 13. Testing, Error Handling, Caching, Determinism

Testing
- Backend unit tests for services and repository queries; controller tests for endpoints
- Frontend unit tests for components and services; E2E (Cypress) for main flows

Error Handling
- Input validation: return 400 with { error }
- Not found: 404
- Server errors: 500 with { error, message } (message for logs; generic for clients as needed)

Caching
- League table cached per league; evict via POST /api/league-table/{league}/clear-cache
- Consider cache for expensive xG summaries

Determinism
- No ML randomness; sort inputs and fix any sampling order
- Use UTC storage; explicit date parsing; avoid local‑time ambiguities

--------------------------------------------------------------------------------

## 14. Reference Contracts (TypeScript Models)

Replicate these TS interfaces to match backend contracts:
- TeamProfileWithFixtureDTO (from frontend/services/team-profile.service.ts):
  interface TeamProfileWithFixtureDTO {
    profile: TeamProfileDTO;
    nextFixture?: { opponent: string; isHome: boolean; fixtureDate: string; daysUntilFixture: number; fixtureId?: number; league?: string } | null;
    rates?: { homeBTTS?: number; awayBTTS?: number; homeOver25?: number; awayOver25?: number; combinedBTTS?: number; combinedOver25?: number } | null;
    correctScoreOutlook?: { likelyScores: string[]; rationale: string; confidence: 'low'|'medium'|'high' } | null;
  }
- TeamProfileDTO: mirror Java fields listed in Section 4
- LeagueTableDTO, MatchHistoryDTO, FixturePredictionDTO, XgPrediction, XgPredictionBlend, XgStatsSummary: as per Section 3 endpoints

--------------------------------------------------------------------------------

## 15. Out‑of‑Scope (Explicit)

- All ML endpoints and features are excluded from this rebuild:
  - com.tchatchohub.smartmatch.controller.MLPredictionController
  - com.tchatchohub.smartmatch.controller.XgMlTrainingController
  - Any ML model training, calibration, or ML‑based simulations

--------------------------------------------------------------------------------

## 16. Final Acceptance Criteria

- The rebuilt app reproduces the UI/UX, routing, styling, and interactions specified in Sections 2 and 7.
- All listed endpoints in Section 3 are implemented, function as described, and return correct DTOs.
- Database schema implemented with migrations; seed data loads; indexes present.
- No ML endpoints or features exist in the codebase.
- Deterministic behavior: same inputs yield same outputs; date/time and ordering consistent.
- Dockerized deployment works locally; smoke tests pass.

--------------------------------------------------------------------------------

Appendix: Quick Start (Dev)
- Backend: mvn spring-boot:run (port 8082)
- Frontend: npx ng serve --port 4200 --proxy-config proxy.conf.json
- Visit http://localhost:4200 → Fixtures Today → Team Profile → Matchup Analyzer → League Table → xG → Advice → History

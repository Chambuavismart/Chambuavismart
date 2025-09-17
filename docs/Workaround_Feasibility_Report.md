# Workaround Feasibility Report: Persist Individual Fixture Analyses and Repurpose “Chambua‑Leo”

Date: 2025-09-17 (Africa/Nairobi)

---

1. Overview
- Proposed twist: When a user opens a fixture (homepage → Fixtures Analysis tab) and clicks “Analyse this fixture”, we persist the full MatchAnalysisResponse (W/D/L probabilities, BTTS, Over X.5, xG, correct scores, explainability fields) to the database alongside metadata: leagueId, fixture context (home/away teams), analysisDate (LocalDate in Africa/Nairobi), seasonId, userId (if available), and timestamps.
- Then the “Chambua‑Leo” one‑click button/tab fetches and displays all persisted analyses for today (LocalDate.now(ZoneId.of("Africa/Nairobi"))) in a consolidated view (list + downloadable consolidated PDF), reusing the LaTeX/PDF logic.

Current paths observed in code:
- Individual analysis endpoint (deterministic, per‑fixture/per‑match):
  - Backend: POST /api/match-analysis/analyze via MatchAnalysisController
    ```java
    @RestController
    @RequestMapping("/api/match-analysis")
    public class MatchAnalysisController {
        @PostMapping("/analyze")
        public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req) {
            return matchAnalysisService.analyzeDeterministic(
                league.getId(), homeId, awayId, req.getSeasonId(),
                league.getName(), homeName.trim(), awayName.trim(), req.isRefresh()
            );
        }
    }
    ```
  - Service: MatchAnalysisService.analyzeDeterministic(...) generates MatchAnalysisResponse (high‑fidelity, consistent with the UI’s “Analyse this fixture”).

- Batch/Consolidation endpoints (current “Chambua‑Leo” flow):
  - Backend: /api/fixture-analysis/batch/... from the Angular service, handled by FixtureBatchAnalysisController/BatchAnalysisController.
  - PDF consolidation: LaTeXService.buildConsolidatedDailyReportFromResults(...) and buildConsolidatedDailyReport(...), which can consume either a list of FixtureAnalysisResult or a list of MatchAnalysisResponse items.
  - Angular one‑click trigger: HomeComponent → BatchAnalysisService.start(...) → navigates to /batch/:jobId → BatchResultsComponent lists results and exposes consolidatedPdf/zip links.

Conclusion: The individual analyze path already produces the desired MatchAnalysisResponse; PDF consolidation can already handle MatchAnalysisResponse lists. The missing piece is persistence + a “get today’s persisted analyses” endpoint and a minor frontend rewire.

2. Feasibility Assessment (Backend - Spring Boot)
2.1 Data Model
- Existing cache entity: MatchAnalysisResult (match_analysis_results)
  ```java
  @Entity
  @Table(name = "match_analysis_results", uniqueConstraints = @UniqueConstraint(name = "uk_match_analysis_fixture", columnNames = {"league_id","home_team_id","away_team_id"}))
  public class MatchAnalysisResult {
      @Id @GeneratedValue private Long id;
      @Column(name = "league_id", nullable=false) private Long leagueId;
      @Column(name = "home_team_id", nullable=false) private Long homeTeamId;
      @Column(name = "away_team_id", nullable=false) private Long awayTeamId;
      @Lob @Column(name="result_json", nullable=false, columnDefinition="LONGTEXT") private String resultJson;
      @Column(name="last_updated", nullable=false) private Instant lastUpdated;
  }
  ```
- Repository: MatchAnalysisResultRepository supports lookup by leagueId + homeTeamId + awayTeamId.
- Observations:
  - This functions as a cache, not date‑scoped; it stores only the latest compute result, not “today’s” set nor user attribution, and it does not key by fixtureId or date.

Options for persistence of “today’s” individual analyses:
- Option A: New entity PersistedFixtureAnalysis with explicit analysisDate and optional fixtureId/userId; store MatchAnalysisResponse JSON as a blob.
  - Fields (suggested): id (PK), leagueId, seasonId, homeTeamId, awayTeamId, homeTeamName, awayTeamName, fixtureId (nullable), analysisDate (LocalDate), userId (nullable), resultJson (LONGTEXT), createdAt (Instant), updatedAt (Instant), source (enum: INDIVIDUAL/BATCH).
  - Indexes: (analysisDate), (analysisDate, leagueId), and unique constraint on (analysisDate, leagueId, homeTeamId, awayTeamId) to prevent duplicates per day per fixture context.
  - Pros: Clean separation from cache; easy querying by date; can store multiple day-by-day snapshots; enables attribution and auditing.
  - Cons: Adds a new table and migration; requires a simple repository and controller.

- Option B: Extend MatchAnalysisResult to add analysisDate and possibly composite keys for date scoping.
  - Pros: Fewer tables.
  - Cons: Breaks cache semantics; complicates unique constraints; may affect existing caching lookups and overwrite logic intended for “latest only”. Not recommended.

Recommendation: Option A (new PersistedFixtureAnalysis) is cleaner and safer.

2.2 Persistence Logic
- Where to persist: After MatchAnalysisService.analyzeDeterministic(...) returns a MatchAnalysisResponse in MatchAnalysisController (or directly inside analyzeDeterministic if we want service‑level auto‑persist), create and save PersistedFixtureAnalysis.
- Duplicate handling: Upsert by (analysisDate, leagueId, homeTeamId, awayTeamId). If an entry exists for today, update its JSON and timestamps.
- JSON storage: Serialize MatchAnalysisResponse with Jackson and store as LONGTEXT (consistent with MatchAnalysisResult). This is already used for cache JSON, so no new library needs are required.
- Timezone & date: Use LocalDate.now(ZoneId.of("Africa/Nairobi")).
- User attribution: Accept optional X-User-Id header from the request (like BatchAnalysisController does) and store when present.
- Feasibility: High. Matches existing patterns (JPA + Jackson + repositories). Minimal service/controller additions.

2.3 Query/Compile Endpoint
- Add a new controller providing:
  - GET /api/persisted-analyses/today → returns List<MatchAnalysisResponse> (or a wrapper with metadata) by querying repo.findByAnalysisDate(todayEAT).
  - GET /api/persisted-analyses/today/pdf → returns consolidated PDF using LaTeXService.buildConsolidatedDailyReport(list, todayEAT).
  - Optional: GET /api/persisted-analyses/date/{yyyy-MM-dd} for backfills or testing.
- PDF generation reuse:
  - LaTeXService already supports buildConsolidatedDailyReport(List<MatchAnalysisResponse> items, LocalDate date), used elsewhere. We can reuse it directly.
- Edge handling:
  - If no items: return 200 with empty list, and for PDF return a minimal PDF with a friendly “No persisted analyses for today” message (LaTeXService.minimalPdf or a new small helper). Current LaTeXService has fallbacks and helpers to accomplish this trivially.
- Feasibility: High.

2.4 Edge Cases & Considerations
- Champions League vs domestic context: Persisted results reflect the individual analyzer’s deterministic logic and should remove batch discrepancies. If team resolution fails on individual path, nothing is persisted, and the daily list naturally omits these.
- Volume: If 50–200 analyses are persisted in a day, JSON blobs are fine. Add (analysisDate) index for fast daily retrieval.
- Staleness: If a user re‑analyzes a fixture, we update today’s record (idempotent per day/fixture context). Optionally record createdAt/updatedAt for audit.
- Security: Endpoint can be open (read‑only) like existing batch endpoints or require auth; current controllers are @CrossOrigin("*") without auth by default.

3. Feasibility Assessment (Frontend - Angular)
3.1 Individual Analysis Persistence
- Current service (MatchAnalysisService) calls backend:
  ```ts
  @Injectable({ providedIn: 'root' })
  export class MatchAnalysisService {
    private baseUrl = `${getApiBase()}/match-analysis`;
    analyze(req: MatchAnalysisRequest): Observable<MatchAnalysisResponse> {
      return this.http.post<MatchAnalysisResponse>(`${this.baseUrl}/analyze`, req);
    }
  }
  ```
- Two approaches:
  - Backend-only persistence: Preferred. The backend persists automatically when analyze() succeeds — no frontend changes required for persistence.
  - Frontend also POSTs to a new /api/persist-analysis endpoint after success ("fire‑and‑forget"). Feasible but redundant if backend handles it already.
- Feasibility: High. Zero or very small UI change.

3.2 “Chambua‑Leo” Tab/Button Repurpose
- Current one‑click flow calls batch:
  - Angular service: BatchAnalysisService → baseUrl = /api/fixture-analysis/batch; start(date?, seasonId?, refresh?, analysisMode?) → navigates to /batch/:jobId.
  - HomeComponent triggers start() and navigates to /batch/:jobId; BatchResultsComponent polls status and displays items and PDF links.
- Proposed repurpose (minimal change):
  - Add a new service (or extend the existing BatchAnalysisService) with methods:
    - getPersistedToday(): Observable<MatchAnalysisResponse[]> → calls GET /api/persisted-analyses/today
    - getPersistedTodayPdfUrl(): string → returns /api/persisted-analyses/today/pdf for direct download
  - UI: On Chambua‑Leo click, route to a new component or reuse BatchResultsComponent with a mode flag that renders a simple table from MatchAnalysisResponse[] (no job polling). Provide consolidated PDF download button that links to getPersistedTodayPdfUrl().
  - Optional: Keep the old batch path behind an “Advanced/Experimental” toggle.
- Feasibility: High. Changes are localized and mirror existing patterns.

3.3 UX Implications
- Pro: Leverages the proven individual analysis path; results match what the user saw and trusted.
- Con: Requires user to have run analyses individually for fixtures they care about; the “one‑click” becomes a “collect my already‑analyzed fixtures” view for the day.
- Mitigation: Add a prompt/tooltip indicating that the daily page consolidates your analyzed fixtures for the day. Optionally highlight zero‑state messaging with a shortcut to today’s fixtures list.

4. Overall Feasibility
- Rating: High.
- Pros:
  - Bypasses persistent batch‑flow edge cases (team resolution across competitions, H2H filtering discrepancies).
  - Reuses existing deterministic analysis logic and PDF generator.
  - Simple DB persistence (JSON blob), minimal schema impact, straightforward endpoints.
- Cons:
  - Relies on user‑initiated individual analyses; may not be fully automated.
  - Potential data growth over time (manageable with pruning/TTL or by keeping only N days).
- Dependencies:
  - New JPA entity + repository + controller; Jackson already present.
  - Database migration: add persisted_fixture_analyses (Flyway/Liquibase if used; otherwise existing schema management approach).

5. High‑Level Implementation Steps (If Feasible)
- Step 1 (Backend): Create PersistedFixtureAnalysis entity/repository
  - Fields: id, leagueId, seasonId, homeTeamId, awayTeamId, homeTeamName, awayTeamName, fixtureId (nullable), analysisDate (LocalDate), userId (nullable), resultJson (LONGTEXT), createdAt, updatedAt, source (INDIVIDUAL).
  - Constraints: unique (analysisDate, leagueId, homeTeamId, awayTeamId). Index analysisDate.
  - Migration: add table and indexes.
- Step 2 (Backend): Auto‑persist on individual analyze
  - In MatchAnalysisController (or MatchAnalysisService), after compute success, serialize MatchAnalysisResponse and save via PersistedFixtureAnalysisRepository, using LocalDate.now(EAT).
  - Capture optional X-User-Id header.
- Step 3 (Backend): Retrieval and PDF endpoints
  - GET /api/persisted-analyses/today → returns List<MatchAnalysisResponse> with lightweight metadata (league/team names already embedded in response).
  - GET /api/persisted-analyses/today/pdf → uses LaTeXService.buildConsolidatedDailyReport(list, todayEAT).
- Step 4 (Frontend): Repurpose Chambua‑Leo
  - Add PersistedAnalysesService with getPersistedToday() and pdf URL helper.
  - Update HomeComponent Chambua‑Leo button to navigate to a new “persisted‑today” route or a mode within BatchResultsComponent.
  - Render a simple list; include download PDF button. Optionally a refresh button (re‑fetch list).
- Step 5 (Tests)
  - Backend unit tests: repository save/query; controller serialization/deserialization; date scoping (Africa/Nairobi).
  - Integration: Analyze → persisted → GET today returns item → PDF endpoint returns bytes.
  - Frontend: service methods; component renders list; PDF link points to correct URL.
- Estimated Effort: Low (≈1–2 days including migration and UI adjustments).

6. Recommendations
- Primary: Implement Option A (new PersistedFixtureAnalysis) and backend‑side auto‑persist on successful individual analyses. Provide today list + consolidated PDF endpoints.
- Alternative/Hybrid: Keep existing batch path for leagues/fixtures known to be robust (e.g., 3. Liga), and show merged results: persisted individual results override batch entries for the same fixture; others fall back to batch. This is more complex and can be a phase‑2 enhancement.
- Operational: Add pruning (e.g., keep last 60–90 days) or archive if storage growth becomes significant.
- Next Step: Proceed to implementation behind a small feature flag (e.g., chambua.persistedDaily.enabled=true) to allow quick rollback if needed.

Appendix: Supporting Code References
- Individual endpoint: backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java → POST /api/match-analysis/analyze
- Deterministic analyzer: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java → analyzeDeterministic(...)
- Cache entity (existing): backend/src/main/java/com/chambua/vismart/model/MatchAnalysisResult.java
- Batch controller/PDF: backend/src/main/java/com/chambua/vismart/controller/BatchAnalysisController.java and FixtureBatchAnalysisController.java; PDF methods in backend/src/main/java/com/chambua/vismart/service/LaTeXService.java (buildConsolidatedDailyReport, buildConsolidatedDailyReportFromResults)
- Angular services/components: frontend/src/app/services/match-analysis.service.ts, frontend/src/app/services/batch-analysis.service.ts, frontend/src/app/home/home.component.ts, frontend/src/app/pages/batch-results.component.ts

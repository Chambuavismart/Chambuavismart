# One‑Click “Today’s Fixtures” Analysis — End‑to‑End Investigation Report

Date: 2025‑09‑17 (EAT)

Author: Junie (JetBrains Autonomous Programmer)

---

## 1) Overview

This report investigates the one‑click feature that analyzes all of today’s fixtures and produces a consolidated PDF. The goal is to verify whether it uses the preferred, advanced “Fixtures Analysis” method or inadvertently falls back to the simpler “Match Analysis” path.

What the one‑click feature does at a high level:
- Frontend: User clicks a button (Angular). The app calls a batch endpoint to start a background job for today’s fixtures.
- Backend: Spring Boot creates a batch job, fetches all fixtures scheduled for the chosen date (default: today EAT), analyzes each fixture, stores per‑fixture results in memory, and exposes status/result retrieval endpoints.
- PDF: A consolidated PDF (and optional ZIP of per‑fixture single‑page PDFs) is generated from the analysis results.

Two analysis methods in the app:
- “Match Analysis” (simpler): Historically uses raw averages and Poisson as a fallback. Core implementation is in MatchAnalysisService.analyzeDeterministic. Inputs are team IDs/names and league/season context; outputs include W/D/L, BTTS, Over X.5, expected goals, correct scores, etc.
- “Fixtures Analysis” (advanced): Public entry point FixtureAnalysisService.analyzeFixture resolves team IDs and season, delegates to the deterministic engine, and guarantees presence of probabilities and correct scores (computing with Poisson when needed). It is the preferred path for fixture‑centric flows because it normalizes inputs and provides safer defaults and enrichments.

Initial hypothesis:
- The one‑click feature is wired to use FixtureAnalysisService.analyzeFixture (the advanced, fixture‑centric path). Code inspection confirms this: the batch coordinator invokes fixtureAnalysisService.analyzeFixture for every fixture discovered on the selected date.

---

## 2) Code Flow Investigation

### 2.1 Frontend (Angular)

Service: src/app/services/batch-analysis.service.ts
```ts
@Injectable({ providedIn: 'root' })
export class BatchAnalysisService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/fixture-analysis/batch`;

  start(date?: string, seasonId?: number, refresh: boolean = false): Observable<BatchStartResponse> {
    const params: any = {};
    if (date) params.date = date;
    if (seasonId != null) params.seasonId = seasonId;
    if (refresh) params.refresh = true;
    return this.http.post<BatchStartResponse>(`${this.baseUrl}`, null, { params });
  }

  status(jobId: string): Observable<BatchStatus> { return this.http.get<BatchStatus>(`${this.baseUrl}/${jobId}`); }
  results(jobId: string, page = 0, size = 50) { return this.http.get<any>(`${this.baseUrl}/${jobId}/results`, { params: { page, size } }); }
  consolidatedPdf(jobId: string) { return `${this.baseUrl}/${jobId}/pdf`; }
  zip(jobId: string) { return `${this.baseUrl}/${jobId}/zip`; }
}
```
Observations:
- The one‑click path explicitly targets /api/fixture-analysis/batch, not /api/match-analysis/batch.
- This indicates intent to go through the fixture‑centric path on the backend.

UI components:
- Batch results page: src/app/pages/batch-results.component.ts (wired to poll status/results and offer download links). The exact button wiring is not shown here, but the service endpoints above are the ones used by the one‑click UI.

### 2.2 Backend (Spring Boot) — Controllers

Controller 1 (Fixture‑centric batch): backend/src/main/java/com/chambua/vismart/controller/FixtureBatchAnalysisController.java
```text
@RestController
@RequestMapping("/api/fixture-analysis/batch")
@CrossOrigin(origins = "*")
public class FixtureBatchAnalysisController {
  private final BatchAnalysisCoordinator coordinator;
  private final LaTeXService laTeXService;

  @PostMapping
  public Map<String, String> start(@RequestParam(value = "date", required = false) String dateStr,
                                   @RequestParam(value = "seasonId", required = false) Long seasonId,
                                   @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh,
                                   @RequestHeader(value = "X-User-Id", required = false) String userId,
                                   @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
    // ... construct date (default: today EAT), light rate-limit, then:
    String jobId = coordinator.start(date, seasonId, refresh);
    return Map.of("jobId", jobId);
  }

  @GetMapping("/{jobId}") public Map<String, Object> status(@PathVariable String jobId) { /* ... */ }
  @GetMapping("/{jobId}/results") public Page&lt;JobModels.FixtureAnalysisResult&gt; results(...) { /* ... */ }
  @GetMapping("/{jobId}/pdf") public ResponseEntity<ByteArrayResource> consolidatedPdf(@PathVariable String jobId) { /* ... */ }
  @GetMapping("/{jobId}/zip") public ResponseEntity<byte[]> zip(@PathVariable String jobId) { /* ... */ }
}
```

Controller 2 (Historical/parallel path): backend/src/main/java/com/chambua/vismart/controller/BatchAnalysisController.java
```java
@RestController
@RequestMapping("/api/match-analysis/batch")
public class BatchAnalysisController {
  // Same orchestration endpoints as the fixture one, also backed by BatchAnalysisCoordinator
}
```

Both controllers delegate to the same coordinator. The Angular one‑click feature calls the first controller under /api/fixture-analysis/batch.

### 2.3 Backend — Batch Orchestration

Service: backend/src/main/java/com/chambua/vismart/service/BatchAnalysisCoordinator.java
```java
@Service
public class BatchAnalysisCoordinator {
  private final FixtureService fixtureService;
  private final FixtureAnalysisService fixtureAnalysisService;
  private final SeasonResolutionService seasonResolutionService;
  private final InMemoryJobStore jobStore;
  private final Executor executor;
  private final SeasonRepository seasonRepository;

  public String start(LocalDate date, Long seasonId, boolean refresh) {
    JobModels.JobMetadata job = new JobModels.JobMetadata(date);
    job.status = JobModels.JobStatus.RUNNING;
    job.startedAt = Instant.now();
    jobStore.put(job);
    processAsync(job, seasonId, refresh);
    return job.jobId;
  }

  @Async("batchAnalysisExecutor")
  protected void processAsync(JobModels.JobMetadata job, Long seasonId, boolean refresh) {
    String seasonName = (seasonId != null) ? seasonRepository.findById(seasonId).map(s -> s.getName()).orElse(null) : null;
    List<Fixture> fixtures = fixtureService.getFixturesByDate(job.date, seasonName);
    // sort, chunk into waves, and for each fixture run analyzeOne(...)
  }

  private void analyzeOne(JobModels.JobMetadata job, Fixture f, Long seasonId, boolean refresh) {
    // ... resolve seasonId when null
    MatchAnalysisResponse resp = fixtureAnalysisService.analyzeFixture(
        league.getId(), sid,
        null, f.getHomeTeam(),
        null, f.getAwayTeam(),
        refresh
    );
    r.payload = resp; r.success = true; /* ... */
  }
}
```
Key point:
- The coordinator calls fixtureAnalysisService.analyzeFixture(...) for each fixture (by names if team IDs are not present), which is the advanced, fixture‑centric entry point.

### 2.4 Backend — Analysis Invocation

Advanced fixture‑centric service: backend/src/main/java/com/chambua/vismart/service/FixtureAnalysisService.java
```java
@Service
public class FixtureAnalysisService {
  private final MatchAnalysisService matchAnalysisService; // deterministic engine
  // + FormGuideService, TeamRepository, TeamAliasRepository, SeasonResolutionService, cache repo

  @Transactional(readOnly = true)
  public MatchAnalysisResponse analyzeFixture(Long leagueId, Long seasonId,
                                              Long homeTeamId, String homeTeamName,
                                              Long awayTeamId, String awayTeamName,
                                              boolean refresh) {
    Long resolvedHomeId = (homeTeamId != null) ? homeTeamId : resolveTeamId(homeTeamName, leagueId);
    Long resolvedAwayId = (awayTeamId != null) ? awayTeamId : resolveTeamId(awayTeamName, leagueId);
    if (resolvedHomeId == null || resolvedAwayId == null) throw new IllegalArgumentException("Team not found: ...");
    Long sid = (seasonId != null) ? seasonId : seasonService.resolveSeasonId(leagueId, null).orElse(null);

    // optional cache read (skipped if refresh or season present)

    // Delegate to deterministic engine (advanced model path)
    MatchAnalysisResponse matchResp = matchAnalysisService.analyzeDeterministic(
        leagueId, resolvedHomeId, resolvedAwayId, sid, null, homeTeamName, awayTeamName, refresh);

    // Ensure completeness (compute Over1.5/3.5 + correct scores via Poisson if missing)
    // ... then return matchResp
  }
}
```
Observations:
- Despite delegating to MatchAnalysisService, this is the advanced path: it resolves team IDs robustly (including aliases), resolves season context, and post‑fills probabilities/correct scores using Poisson when needed.
- Any consumer calling analyzeFixture benefits from this enriched processing.

Deterministic engine (used by both flows): backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java (selected excerpts)
```java
public MatchAnalysisResponse analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId,
                                                  Long seasonId,
                                                  String leagueName, String homeTeamName, String awayTeamName,
                                                  boolean refresh) {
  // optional caching on (leagueId, homeId, awayId) when seasonId is null and refresh=false
  // Compute Win/Draw/Loss probabilities using weighted PPG splits (home vs away)
  List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, sid, DEFAULT_FORM_LIMIT, Scope.OVERALL);
  // derive PPGs for home/away using weighted home/away splits when enough matches, else fallback to overall PPG
  // combine into W/D/L with 75% scaling and draw residual; default safeguards when rows missing

  // Compute BTTS, Over 2.5 using weighted split percents (fallback to overall when insufficient split matches)

  // Integrate H2H using recency weighting across last N matches (multi‑league family aware)

  // Expected goals (xG) and correct scores either computed or passed through; probabilities normalized

  // Returns MatchAnalysisResponse with: league, home/away names, winProbabilities, BTTS/OverX.5, expectedGoals, advice, correctScores, notes, etc.
}
```

How analysis type is determined:
- The batch flow hard‑codes a call to FixtureAnalysisService.analyzeFixture (preferred). No request parameter switches this choice in the batch path.

### 2.5 PDF Generation

Service: backend/src/main/java/com/chambua/vismart/service/LaTeXService.java
- buildConsolidatedDailyReport(List<MatchAnalysisResponse> items, LocalDate date): Builds an A4 iText PDF that groups items by league and lists per‑fixture home/away, W/D/L probabilities, and advice.
```text
public byte[] buildConsolidatedDailyReport(List<MatchAnalysisResponse> items, LocalDate date) {
  doc.add(new Paragraph("Chambua-Leo — Fixtures for " + date + " EAT"));
  // group by league; per row shows Home, Away, Home%, Draw%, Away%, Advice
}
```
- buildZipOfPerFixturePdfs(List<FixtureAnalysisResult>): Builds a ZIP where each entry is a single‑page PDF generated by buildSingleSummaryPdf(MatchAnalysisResponse).

---

## 3) Key Classes and Methods Inventory

Frontend
- Path: frontend/src/app/services/batch-analysis.service.ts
  - start(date?: string, seasonId?: number, refresh = false) → Observable<{jobId:string}>. Calls POST /api/fixture-analysis/batch.
  - status(jobId: string) → Observes GET /{jobId}.
  - results(jobId: string, page=0, size=50) → Observes GET /{jobId}/results.
  - consolidatedPdf(jobId: string) → URL for GET /{jobId}/pdf.
  - zip(jobId: string) → URL for GET /{jobId}/zip.

Backend Controllers
- com.chambua.vismart.controller.FixtureBatchAnalysisController (backend/.../controller/FixtureBatchAnalysisController.java)
  - POST /api/fixture-analysis/batch → String jobId (starts job)
  - GET /{jobId}, /{jobId}/results, /{jobId}/pdf, /{jobId}/zip
- com.chambua.vismart.controller.BatchAnalysisController (backend/.../controller/BatchAnalysisController.java)
  - POST /api/match-analysis/batch → parallel endpoints (not used by Angular one‑click)

Backend Services
- com.chambua.vismart.service.BatchAnalysisCoordinator
  - start(LocalDate, Long seasonId, boolean refresh) → jobId
  - processAsync(Job, seasonId, refresh) [@Async] → fetch fixtures and wave‑process via analyzeOne
  - analyzeOne(Job, Fixture, seasonId, refresh)
    - Resolves seasonId if null
    - Calls FixtureAnalysisService.analyzeFixture(leagueId, sid, null, homeName, null, awayName, refresh)
- com.chambua.vismart.service.FixtureService
  - getFixturesByDate(LocalDate date, String seasonName)
    - Fetches fixtures in [date, date+1) with JOIN FETCH league; can restrict to season name
- com.chambua.vismart.service.FixtureAnalysisService
  - analyzeFixture(leagueId, seasonId, homeTeamId, homeTeamName, awayTeamId, awayTeamName, refresh) → MatchAnalysisResponse
    - Resolves team IDs via TeamRepository + TeamAliasRepository
    - Resolves season via SeasonResolutionService
    - Delegates to MatchAnalysisService.analyzeDeterministic and ensures Over1.5/Over3.5/correct scores are present (Poisson fallback)
- com.chambua.vismart.service.MatchAnalysisService
  - analyzeDeterministic(...) → MatchAnalysisResponse (advanced deterministic engine)
    - Uses FormGuideService splits, H2H recency weighting, league‑family matching, and normalization
- com.chambua.vismart.service.LaTeXService
  - buildConsolidatedDailyReport(List<MatchAnalysisResponse>, LocalDate)
  - buildZipOfPerFixturePdfs(List<JobModels.FixtureAnalysisResult>)

Entities/DTOs/Utils
- com.chambua.vismart.batch.JobModels
  - JobMetadata: holds jobId, date, status, counters, timings, and results list
  - FixtureAnalysisResult: per‑fixture record with league/team names, flags, payload=MatchAnalysisResponse, durationMs, cacheHit
- com.chambua.vismart.dto.MatchAnalysisResponse
  - Holds league/home/away names, win probabilities, BTTS/OverX.5, expectedGoals, advice, notes, correctScores, etc.
- Repositories injected across services (e.g., FixtureRepository, SeasonRepository, TeamRepository, TeamAliasRepository, MatchAnalysisResultRepository)

Conditional logic selecting between methods
- BatchAnalysisCoordinator always calls FixtureAnalysisService.analyzeFixture (advanced flow). There is no if/else path that calls the simpler legacy “match analysis” directly in the batch flow.

---

## 4) Inputs/Outputs and Data Flow

Inputs for one‑click:
- Date: Optional request param. Defaults to LocalDate.now(ZoneId.of("Africa/Nairobi")).
- Season: Optional seasonId; resolved to a season name to constrain fixture fetching; if missing, per‑fixture seasonId is resolved by SeasonResolutionService.
- Fixtures: FixtureService.getFixturesByDate(date, seasonName) returns Fixture entities (with eager League join) containing league, kickoff, home/away team names.

Processing:
- Fixtures are sorted by league then kickoff.
- Processed in “waves” of 20 fixtures using a ThreadPoolTaskExecutor to balance concurrency and DB load.
- For each fixture: FixtureAnalysisService.analyzeFixture resolves IDs/season and invokes the deterministic engine; results are stored in JobMetadata.results.

Outputs:
- Per‑fixture MatchAnalysisResponse JSON payloads (accessible via GET /results) including:
  - winProbabilities: { homeWin, draw, awayWin } as percentages
  - bttsProbability, over15/25/35Probability
  - expectedGoals: { home, away }
  - correctScores: [{ score: "2-1", probability: 14.2 }, ...]
  - advice, notes, metadata fields (league, homeTeam, awayTeam, etc.)
- Consolidated PDF: One section per league with a table listing each fixture and W/D/L percentages and advice.
- ZIP: Per‑fixture one‑page summary PDFs.

Comparison: inputs/outputs between methods
- Match Analysis (simpler historical concept): When called directly with IDs (and no seasonId), it may use cache and computes probabilities using form guide splits; Poisson is used for correct score derivations where needed; does not perform the team alias resolution or post‑fill guarantees by itself.
- Fixtures Analysis (advanced fixture‑centric): Adds:
  - Robust team ID resolution from names/aliases within league
  - Season resolution if not supplied
  - Ensures Over1.5/Over3.5 and correct scores are present (Poisson fallback)
  - Returns the same MatchAnalysisResponse schema for compatibility

Dummy examples
- Match Analysis input: (leagueId=39, homeTeamId=50, awayTeamId=51, seasonId=null)
  - Output: { winProbabilities:{homeWin:48,draw:26,awayWin:26}, over25:58, btts:55, xg:{1.35,1.12}, ... }
- Fixtures Analysis input: (leagueId=39, seasonId=2024, homeTeamName="Arsenal", awayTeamName="Chelsea")
  - Output: Same structure but with team IDs resolved; ensures correctScores present; includes notes “Based on form, H2H, and league adjustments”.

Data transformations
- No distinct DTO between FixtureAnalysisService and LaTeX: job stores MatchAnalysisResponse; LaTeXService consumes List<MatchAnalysisResponse>.

---

## 5) Potential Issues and Bugs

Discrepancies looked for (and findings):
- Is the analysis type param missing? Not relevant — the batch flow has a single hard‑coded call to FixtureAnalysisService.analyzeFixture.
- Default fallback to “Match Analysis”? Not in batch coordinator. The deterministic engine (MatchAnalysisService) is used under the hood by design, but the entry point is FixtureAnalysisService, which is the advanced path.
- Exceptions/logs indicating bypassing? Coordinator logs per‑fixture as “[MatchAnalysis][PerFixture] ...” which can be confusing by name, but the call stack clearly goes through fixtureAnalysisService.analyzeFixture.

Edge cases and operational notes:
- No fixtures for today: coordinator will set total=0 and quickly complete; PDF will still be generated with zero items.
- Team ID resolution failures: analyzeFixture throws IllegalArgumentException("Team not found..."). The coordinator catches and records an error message per fixture without aborting the entire job.
- Timezone: “today” is computed as LocalDate.now(ZoneId.of("Africa/Nairobi")) in both controllers, matching the product decision for EAT.
- Performance: Waves of 20 fixtures processed concurrently. This balances throughput and DB load. H2H queries can be heavier; code includes league‑family lookups with fallbacks.
- Caching: Optional cache read in both MatchAnalysisService and FixtureAnalysisService (when seasonId is null and refresh=false). Cache hits are not currently surfaced back to the job result cacheHit flag (left false by TODO comment).
- PDF: LaTeX template compilation is attempted first; if latexmk is unavailable or fails, an iText fallback is used. Consolidated PDF contains W/D/L and advice only (no BTTS/Over lines in consolidated view), which is acceptable for a high‑level rollup page.

Potential sources of confusion:
- Two controllers exist: /api/fixture-analysis/batch and /api/match-analysis/batch. The Angular one‑click uses the former. However, logs inside BatchAnalysisCoordinator use a tag “[MatchAnalysis][PerFixture] ...”. This log tag does not change the actual method invoked but might mislead a quick log reader.

Known bugs from code inspection:
- cacheHit is hardcoded to false in coordinator results (line ~137). If caching matters to UX, this could be improved to reflect real cache usage.

---

## 6) Recommendations Teaser (not implementing yet)

- Make the intent explicit in logs and naming:
  - Update log tags in BatchAnalysisCoordinator from “[MatchAnalysis][PerFixture]” to “[FixtureBatch][PerFixture]” and add a line logging the exact service method invoked for clarity.
- Add a guardrail flag:
  - In BatchAnalysisCoordinator, add an optional request param analysisMode=FIXTURE|MATCH (default FIXTURE) and fail fast if MATCH is requested on this endpoint. This prevents regressions.
- Strengthen PDF content:
  - In the consolidated report, consider adding BTTS and Over 2.5 columns if space allows, leveraging existing fields on MatchAnalysisResponse.

---

## Conclusion

The one‑click feature (Angular → /api/fixture-analysis/batch) uses BatchAnalysisCoordinator, which in turn calls FixtureAnalysisService.analyzeFixture for each fixture. This confirms it follows the advanced, preferred “Fixtures Analysis” path. The underlying deterministic engine (MatchAnalysisService) is shared, but entry via FixtureAnalysisService ensures robust team/season resolution and complete outputs. No code change is required to switch analysis methods — the current wiring already targets the advanced path.

Appendix: Key references
- frontend/src/app/services/batch-analysis.service.ts
- backend/src/main/java/com/chambua/vismart/controller/FixtureBatchAnalysisController.java
- backend/src/main/java/com/chambua/vismart/service/BatchAnalysisCoordinator.java
- backend/src/main/java/com/chambua/vismart/service/FixtureAnalysisService.java
- backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java
- backend/src/main/java/com/chambua/vismart/service/LaTeXService.java

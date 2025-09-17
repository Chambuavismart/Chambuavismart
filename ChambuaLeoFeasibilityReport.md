# Chambua‑Leo (Analyse All Today’s Fixtures) — Feasibility Assessment

Date: 2025-09-16
Author: Junie (JetBrains Autonomous Programmer)
Scope: Investigation and planning only — no code changes proposed in this document.


## 1) Current Fixture Analysis Flow Overview (Single Match)

This section describes how a single fixture analysis currently works across the frontend and backend layers, based on the repository as of this assessment.

- Frontend entry points and components
  - Played Matches and analysis pages use an Angular service to trigger the backend analysis:
    - File: frontend/src/app/services/match-analysis.service.ts
    - Endpoint used: POST {API_BASE}/match-analysis/analyze
    - Request DTO (subset): leagueId, seasonId, homeTeamId/Name, awayTeamId/Name, refresh?
    - Response DTO (subset): winProbabilities (home/draw/away), bttsProbability, over25Probability, expectedGoals, confidenceScore, advice; optional summaries (formSummary, h2hSummary), and headToHeadMatches list.
  - UI pages consuming the analysis service:
    - frontend/src/app/pages/match-analysis.component.ts (explicit analysis page)
    - frontend/src/app/pages/played-matches-summary.component.ts (contains logic and triggers linked to the “Analyse this fixture” action and displays summaries/links)

- Backend controller and endpoints
  - MatchAnalysisController: backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java
    - Base mapping: /api/match-analysis
    - POST /analyze: Validates input (leagueId and seasonId required), resolves team IDs from names if needed (including alias/contains fallbacks), and invokes the deterministic analyzer.
    - Core call:
      matchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, leagueName, homeName, awayName, refresh)

- Core analysis service and data flow
  - MatchAnalysisService: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java
    - Responsibilities observed:
      - Requests form/context using FormGuideService for season-scoped stats.
      - Retrieves H2H data (via repo and/or name/alias/team resolution patterns noted in previous reports/tests).
      - Combines signals (e.g., weighted PPG splits, BTTS, Over 2.5, expected goals), performs normalization and confidence scoring.
      - Integrates league adjustment factors and explainability fields (h2hAlpha, leagueAdjustment, form match counts, h2h match counts) as available.
      - Returns MatchAnalysisResponse (DTO) with:
        - Probabilities: home/draw/away, BTTS, Over 2.5
        - Expected goals: home/away
        - Advice string and confidence score
        - Optional summaries for UI tables/charts: formSummary, h2hSummary, headToHeadMatches
    - Season scoping: analyzeDeterministic(...) variant accepts seasonId, ensuring computations reflect the selected season context.

- Supporting services
  - FormGuideService: backend/src/main/java/com/chambua/vismart/service/FormGuideService.java
    - Builds team form rows/splits for a given leagueId + seasonId, including weighted last-N matches logic, PPG, BTTS, Over 1.5/2.5/3.5, and counts.
  - H2HService: backend/src/main/java/com/chambua/vismart/service/H2HService.java
    - Exposes name/alias-based H2H retrieval utilities and insights text. While not directly invoked in the MatchAnalysisController, the analysis service uses repositories and alias/name normalization patterns consistent with H2HService behaviors.
  - Fixture data for today (fetching, not the analysis itself):
    - FixtureController /api/fixtures/by-date supports fetching fixtures for a date (grouped by league) and logs counts. Useful for “today’s” batch pulling.

- Output generation and downloads
  - PDF download pipeline (existing):
    - MatchController exposes generateAnalysisPdf(...) which delegates to LaTeXService.
      - backend/src/main/java/com/chambua/vismart/controller/MatchController.java (method generateAnalysisPdf)
      - backend/src/main/java/com/chambua/vismart/service/LaTeXService.java (builds primary LaTeX or rich/simple fallback PDFs)
    - The UI has a “Download as PDF” flow that calls the above endpoint; when LaTeX is unavailable, a fallback PDF with watermark is generated (see pdf_download_report.md, pdf_fallback_improvement_report.md).
  - JasperReports: No direct JasperReports integration was found. The presence of tomcat-embed-jasper is for JSP support, not JasperReports. If Jasper is a requirement for the new feature’s output page, it would be a new integration path.


## 2) Feasibility Assessment — “Chambua‑Leo” (Analyse All Today’s Fixtures)

High-level concept: Add a Home page tab that, when clicked, triggers background analysis for all of today’s fixtures (across all leagues/competitions), then navigates to a page showing the batch results with per-fixture downloadable reports.

- Pros
  - Major convenience: Users can trigger a single action to get the full day’s analyses rather than running each fixture individually.
  - Consistency: Uses the same analysis pipeline (MatchAnalysisService), ensuring parity with single-fixture behavior.
  - Reusability: Today’s fixtures already retrievable via /api/fixtures/by-date, and the single analysis endpoint is well-defined.

- Cons / Risks
  - Performance load: Running analyses for potentially 100+ fixtures in a tight window can strain DB, cache, and CPU. MatchAnalysisService touches several data sources (form, H2H, league tables) and performs calculations per fixture.
  - Concurrency control: Running many analyses simultaneously could lead to lock contention or bursty DB load. Needs batching, throttling, and possibly a queue/job mechanism.
  - Failure management: Partial failures are likely (missing teams, unresolved aliases, gaps in data). The UX must show partial results with clear error/context for each failed fixture.
  - Report generation overhead: If each analyzed fixture also generates a downloadable PDF (LaTeX) or Jasper report, the processing time and temporary file IO will grow significantly.

- Technical challenges
  - Fetching today’s fixtures globally: The endpoint provides a grouped list by league. Need to flatten into per-fixture tasks and ensure we have leagueId and a season scoping strategy (season string or seasonId mapping) for each fixture.
  - Season scoping: The analysis endpoint requires seasonId. Fixtures include league and (typically) a season string; mapping to internal seasonId may be needed. Where season separation is implemented, ensure reliable seasonId resolution from fixture metadata.
  - Team resolution: Some fixtures may have team IDs readily available; otherwise, name-to-ID resolution will occur per request (supported by MatchAnalysisController). Quality of aliases/names affects success rate.
  - Background execution: Decide on mechanism: Spring @Async with a task executor, Scheduled/Batch job, or a queue (e.g., a simple DB-backed job table or in-memory queue) to process analyses. Ensure idempotency and resume on failure if needed.
  - Result persistence: For a batch result page, we likely need to persist per-fixture analysis outputs in a cache or table (MatchAnalysisResultRepository exists and is used by MatchAnalysisService for caching). Define aggregation and retrieval semantics for “the daily run.”
  - Jasper vs. existing PDF pipeline: There’s no current JasperReports code; adding Jasper would be a net-new dependency and templating effort. Alternatively, reuse the existing LaTeX PDF pipeline or provide JSON/HTML tables first.

- Performance considerations
  - Typical daily fixture volume: Varies widely; weekends can exceed 100 fixtures across many leagues.
  - Estimated analysis time: Single analysis is sub-second to a few seconds depending on data volume and H2H/form queries. For 100 fixtures, naive sequential processing could take minutes.
  - Scaling strategy:
    - Batch in waves (e.g., 10–20 concurrent tasks with rate limits) to balance throughput and DB safety.
    - Leverage existing MatchAnalysisResult cache entries (respect refresh flag only when needed) to avoid recomputation for repeated runs the same day.
    - Consider pre-staging form guides per league for the day to avoid repeated FormGuideService recomputations.

- Security and UI/UX
  - Background job visibility: Provide a job ID/status endpoint. The Home tab action should navigate to a status/progress view showing counts (pending, processing, completed, failed) and per-league grouping.
  - Throttling and abuse prevention: If exposed to all users, add server-side rate limits per user/IP and a maximum daily batch size.
  - Error surfacing: For each fixture, display clear reason when analysis fails (e.g., team not found, season not resolved). Allow retry per-fixture.
  - Downloads: Provide per-fixture PDF download (reusing existing LaTeX flow) and consider a ZIP bundle once completed. If Jasper is mandated, scope a separate iteration.

- Cost/risk
  - Database load: High read volume on matches, seasons, and H2H queries. Mitigate with caching, staggered execution, and prefetching.
  - Infrastructure: If JasperReports is required, add footprint: library dependencies, .jrxml templates, server memory for compilation, maintenance overhead. The current stack already solves PDF generation via LaTeXService; Jasper would be additive.
  - Operational: Long-running job handling (timeouts, restarts). If hosted on limited resources, careful scheduling during peak usage windows is needed.

Conclusion: Feasible with moderate backend work and clear UX for background job handling. Prefer initial iteration without Jasper (use existing PDF pipeline and an HTML results page). Add Jasper in a later iteration if still required.


## 3) Leveraging Existing Features

- Fixture fetching
  - Endpoint: GET /api/fixtures/by-date?date=YYYY-MM-DD[&season=]
    - Controller: FixtureController.getFixturesByDate(...)
    - Groups fixtures by league; sorts by kickoff; returns LeagueFixturesResponse with FixtureDTOs.

- Single fixture analysis
  - Endpoint: POST /api/match-analysis/analyze
    - Controller: MatchAnalysisController.analyze(...)
    - Service: MatchAnalysisService.analyzeDeterministic(... seasonId ...)
  - Form and H2H data: FormGuideService, H2H patterns (alias/name resolution), repositories used inside analysis service.

- Background processing
  - Existing schedulers: FixtureRefreshScheduler and FixtureRefreshService exist for refreshing fixtures by league/date; shows precedent for scheduled/background work.
  - For Chambua‑Leo batch analysis, options include:
    - Spring @Async with ThreadPoolTaskExecutor
    - Spring Batch (if added) or a lightweight internal job runner
    - Reuse MatchAnalysisResultRepository cache to avoid duplicate work

- Report generation and downloads
  - Current: LaTeXService + MatchController.generateAnalysisPdf for “Download as PDF”.
  - JasperReports: Not currently present; would be a new integration (templates, compilation, data mapping). Consider deferring to later.

- UI building blocks
  - Home page/tab pattern already exists; adding a new tab “Chambua‑Leo” is UI work only.
  - played-matches-summary.component.ts and match-analysis.component.ts provide patterns for invoking analysis and rendering result summaries.
  - A new page can list analyzed fixtures for the day, group by league, and offer per-fixture links to full analysis and download buttons.


## 4) Implementation Recommendations (Conceptual — No Code)

Phase 1: MVP without Jasper (reuse existing analysis + PDF)

- Backend
  1) New batch endpoint to kickoff analysis for a given date (default today):
     - POST /api/match-analysis/batch?date=YYYY-MM-DD&season=...&refresh=false
     - Behavior: Fetch fixtures by date via FixtureService (or call controller/service layer), resolve seasonId for each fixture’s league/season string, enqueue per-fixture tasks calling MatchAnalysisService.analyzeDeterministic(...). Return a jobId.
  2) Job status endpoints:
     - GET /api/match-analysis/batch/{jobId} → summary: total, completed, failed, in-progress, startedAt, eta
     - GET /api/match-analysis/batch/{jobId}/results → paginated list of MatchAnalysisResponse with identifiers (leagueId, home, away, kickoff)
  3) Execution model:
     - Use @Async with a bounded thread pool (e.g., 10–20 threads). Throttle per-league if necessary.
     - Respect refresh flag selectively to avoid recomputation when cached results exist.
     - Persist job metadata in-memory initially (or a small DB table for durability) with expiration after N hours.
  4) Season scoping:
     - Ensure reliable mapping from fixture’s season string to seasonId. If missing, add a small resolver that queries SeasonRepository by league+season string; fallback rules defined and logged.
  5) Error handling:
     - For each fixture, capture error text and store alongside the item result for UI display and potential retry endpoint.

- Frontend
  1) New Home tab “Chambua‑Leo” with a Start button:
     - On click, call POST /api/match-analysis/batch (defaults to today). Navigate to a progress page with the jobId.
  2) Progress/Results page:
     - Poll GET /api/match-analysis/batch/{jobId} every 2–5 seconds; show progress bar and summary counters.
     - When done (or as items complete), load and render results via GET /results.
     - Display per-league grouping and per-fixture cards with key probabilities and links:
       - “View full analysis” (re-use current analysis page if applicable)
       - “Download as PDF” (calls existing PDF endpoint per fixture)
  3) Edge cases surfaced in UI:
     - No fixtures today: show friendly empty-state.
     - Partial failures: highlight failed items with reasons and a “Retry” action for the single fixture.

- Reporting
  - Reuse existing LaTeXService pipeline for per-fixture PDF downloads.
  - Optionally add an endpoint to generate a ZIP archive of all completed PDFs for convenience.
  - Defer JasperReports until Phase 2 unless it’s a hard requirement.

- Performance/Scaling
  - Start with 10–20 concurrent tasks; make thread pool configurable.
  - Batch by league to improve cache locality and reduce repeated overhead.
  - Avoid refresh=true by default; only recompute when needed or on explicit request.
  - Consider precomputing form guides per league once at job start to reduce repeated work.

- Observability & Ops
  - Log job lifecycle with counts and timing (start/end, average per-fixture time, failures).
  - Set timeouts for single analysis tasks and overall job; allow cancellation endpoint.
  - Add simple rate-limit to prevent repeated concurrent batch kicks.

Phase 2: Optional JasperReports integration

- If a Jasper-based consolidated report page is required:
  - Add JasperReports dependencies and design .jrxml templates for:
    - Per-fixture summary table
    - Grouped-by-league sections
    - Optional charts (win probabilities, expected goals)
  - Build a data adapter that maps MatchAnalysisResponse lists into Jasper beans/data sources.
  - Provide endpoints to generate on-demand Jasper PDFs for the batch or per-league subsets.
  - Evaluate memory usage and template compilation costs; cache compiled Jasper templates if needed.


## 5) Edge Cases and Handling

- No fixtures for the selected date or season filter → return HTTP 204 or an empty results list with a user-friendly message.
- Fixtures missing team IDs or ambiguous names → the batch runner should rely on controller-level resolution and capture failures (e.g., BAD_REQUEST with details) for UI display.
- Very large volumes (200+ fixtures) → reduce concurrency, shard by league/country, and paginate results retrieval.
- Duplicate runs same day with refresh=false → results should be served from MatchAnalysisResult cache when present.
- Stale data as kickoff approaches → allow a job-level refresh=true, or a per-fixture “re-analyze” action.


## 6) Testing and Validation Strategy (Conceptual)

- Unit tests
  - Batch coordinator: correct task splitting, seasonId resolution, error capture, and progress accounting.
  - Season resolver: mapping from fixture season string to seasonId.

- Integration tests
  - End-to-end batch kickoff → progress → results with a small synthetic set of fixtures for today.
  - Partial failures: mix of resolvable and unresolvable teams; verify UI/API shows accurate counts and messages.

- Performance tests
  - Simulate 100 fixtures: validate wall time within acceptable SLA (e.g., < 2–4 minutes on baseline hardware with 10–20 concurrency) and DB load.

- Data seeding
  - Provide seed fixtures for “today” in test/staging to enable repeatable validation runs.


## 7) Assumptions and Open Questions

- Maximum daily fixtures: Assume peaks of 150–250 across global leagues; confirm target bound for concurrency planning.
- Season mapping: Fixtures carry season strings; confirm reliable mapping to internal seasonId, or define the resolver rules to ensure MatchAnalysisService gets an id.
- Background framework: Prefer lightweight @Async initially; if operations demand retries and durability, consider Spring Batch or a persistent job queue later.
- JasperReports requirement: The codebase does not currently integrate JasperReports; confirm whether Jasper is mandatory, or if LaTeX/HTML is acceptable for Phase 1.
- Analysis time per fixture: Assume 200–1500 ms typical; confirm by measuring in staging and tune thread pool size accordingly.
- Caching policy: Clarify whether daily reruns should force refresh or reuse cached results by default.
- UI ownership: Confirm placement of the new tab on the Home page and expected navigation patterns (e.g., should results auto-open or remain on a status page until completion?).


## 8) Rollout Plan (High-Level)

1) Phase 1 (MVP)
- Add backend batch endpoints and async processing with progress tracking.
- Add “Chambua‑Leo” Home tab and results page with polling and per-fixture cards.
- Reuse existing PDF download for each fixture.
- Limit concurrency and log metrics.

2) Phase 1.1 (Polish)
- Add ZIP export, per-league filtering, and retry failed items.
- Precompute per-league form guides to reduce duplicate work.

3) Phase 2 (Optional Jasper)
- Integrate JasperReports, create templates, and provide consolidated batch PDFs.
- Add server-side caching for compiled Jasper templates and large batch outputs.

4) Phase 3 (Scale & Ops)
- Introduce persistent job queue, task retries, and dashboards.
- Add autoscaling or scheduled precomputation for peak days.


## 9) Summary

“Chambua‑Leo” is feasible by reusing existing today’s fixtures endpoint and the mature single-fixture analysis pipeline. The primary work is orchestration: background job kickoff, progress tracking, and a user-friendly results UI. An MVP can ship without JasperReports by reusing the existing LaTeX-based PDF download for each fixture and/or a rich HTML table view. JasperReports can be introduced later if consolidated batch PDFs or templated reports are desired.
# Chambua‑Leo Implementation Plan (Analyse All Today’s Fixtures)

Date: 2025-09-16
Author: Junie (JetBrains Autonomous Programmer)
Scope: Conceptual plan only — no code changes in this document. Target: MVP ready plan for implementation and review/approval.


Implementation Plan Structure

Architecture Overview

- High‑level flow
  - Home tab click (Chambua‑Leo) → POST /api/match-analysis/batch?date=YYYY-MM-DD&refresh=false → immediate navigation to /batch/{jobId}.
  - Backend asynchronously analyzes all fixtures for the given date (100–150 max/day), using 15–20 worker threads.
  - Frontend progress/results page polls status every 2–5 seconds, renders per‑fixture results and errors as they complete.
  - When complete, user can download:
    - Consolidated LaTeX‑based PDF (all fixtures grouped by league, one document).
    - ZIP archive of individual per‑fixture PDFs (reusing existing single‑fixture LaTeX PDF generation).

- Backend components
  - Batch controller endpoints (new):
    - POST /api/match-analysis/batch
    - GET /api/match-analysis/batch/{jobId}
    - GET /api/match-analysis/batch/{jobId}/results
    - GET /api/match-analysis/batch/{jobId}/pdf
    - GET /api/match-analysis/batch/{jobId}/zip
  - Orchestrator/service:
    - BatchAnalysisCoordinator: creates jobs, fetches fixtures, resolves seasonId, dispatches fixture analysis tasks (@Async), aggregates results, computes progress/ETA, and exposes pagination for results.
  - Season resolver:
    - SeasonResolutionService: map fixture season string → latest seasonId per league; fallback to latest seasonId when ambiguous or missing; collect unresolved cases as errors.
  - Caching logic:
    - Uses MatchAnalysisResultRepository to reuse cached results for the same fixture/day when refresh=false (default). New results saved with ~24h TTL policy.
  - PDF/ZIP generation:
    - Extend LaTeXService to generate a consolidated PDF; reuse per‑fixture LaTeX generation for single PDFs; use ZipOutputStream to bundle all per‑fixture PDFs.

- Frontend components
  - Home page tab: New Chambua‑Leo tab with primary action (Analyze Today’s Fixtures).
  - Progress/Results page (/batch/{jobId}):
    - Polls job status endpoint every 2–5s; progress bar and counters (total/completed/failed/in‑progress), startedAt, ETA.
    - Fetches paginated results; shows per‑fixture cards grouped by league; displays errors (no retry).
    - Download buttons: Consolidated PDF and ZIP archive links.


Backend Implementation

Endpoints

1) POST /api/match-analysis/batch?date=YYYY-MM-DD&refresh=false
- Purpose: Kick off batch analysis for the given date (default: today in server timezone).
- Request params:
  - date (optional): ISO date (YYYY-MM-DD), default LocalDate.now().toString().
  - refresh (optional): boolean; default false. When false, reuse cached results from MatchAnalysisResultRepository if available for same day/fixture.
- Behavior:
  - Generate new jobId (UUID).
  - Persist job metadata (in‑memory store for MVP; option to back with DB later): date, startedAt, total=0 initially, status=STARTED.
  - Fetch fixtures via FixtureService or invoke existing /api/fixtures/by-date (service layer), with the given date. Expect 100–150 fixtures.
  - For each fixture:
    - Resolve leagueId, home/away team identifiers (IDs preferred; fall back to names if necessary), kickoff time, and season string.
    - Resolve seasonId via SeasonResolutionService:
      - Try exact match leagueId + season string.
      - If ambiguous/missing, default to latest seasonId per league.
      - If unresolved, register a failed item with error="Season unresolved", continue.
    - Enqueue analysis task (see Async Processing) with payload {leagueId, seasonId, homeTeamId/name, awayTeamId/name, refresh}.
  - Update job.total with number of enqueued fixtures; return {jobId} immediately.

2) GET /api/match-analysis/batch/{jobId}
- Response JSON fields:
  - jobId, date, status (STARTED|RUNNING|COMPLETED|FAILED|CANCELLED), total, completed, failed, inProgress, startedAt, finishedAt, etaSeconds.
- Implementation notes:
  - Compute ETA using moving average of completed tasks duration; fallback: (remaining * avgPerItemMillis)/1000; cap at reasonable bounds.

3) GET /api/match-analysis/batch/{jobId}/results?page=0&size=50
- Returns a page of MatchAnalysisResponse‑like DTOs extended with:
  - leagueId, leagueName, fixtureId (if available), kickoff (ISO), error (if failed), status (COMPLETED|FAILED|PENDING|RUNNING).
- Supports server‑side sorting/grouping by league; client may re‑group.

4) GET /api/match-analysis/batch/{jobId}/pdf
- Generates a single LaTeX‑based consolidated PDF containing all successfully analyzed fixtures for the job, grouped by league, with table of contents styled sections and per‑fixture summaries (probabilities, expected goals, confidence, advice, key form/H2H highlights).
- Caches the generated PDF for the job to avoid regeneration within TTL.

5) GET /api/match-analysis/batch/{jobId}/zip
- Streams a ZIP archive containing individual per‑fixture PDFs (using LaTeXService per‑fixture pipeline). Use a temp directory for assembly and stream on the fly; optionally cache ZIP for subsequent downloads within TTL.


Fixture Fetching

- Use FixtureService.getFixturesByDate(date, season?) to retrieve fixtures; prefer direct service calls rather than HTTP loopback.
- Expect 100–150 fixtures/day at peak; ensure memory footprint is manageable (do not preload large blobs).
- Include fixture leagueId, teamIds if present, and season string from the fixture domain model to avoid extra lookups.


Season Resolution

- SeasonResolutionService responsibilities:
  - Given leagueId and fixture season string (may be null/ambiguous), resolve a Season entity and return seasonId.
  - If provided season string does not map uniquely, fetch the latest Season by leagueId (max by startYear or id) and use its id.
  - Log unresolved/ambiguous cases with context (leagueId, seasonStr, fixtureId) and record an error on the corresponding job item when resolution is impossible.


Async Processing (@Async)

- Execution model:
  - Use Spring @Async with a dedicated ThreadPoolTaskExecutor bean sized for 15–20 threads.
  - Backpressure strategy: submit in waves of 20–30 to avoid initial spikes; coordinator maintains a bounded queue window (e.g., 20 in flight).
  - Target throughput: ~800 ms/fixture average to meet <120s SLA for 150 fixtures.
- Per‑fixture task flow:
  - If refresh=false, check MatchAnalysisResultRepository for a cached result keyed by (leagueId, seasonId, homeTeamId, awayTeamId, date bucket) or fixture hash. If present, deserialize and mark as completed (source=CACHE).
  - Otherwise build MatchAnalysisRequest and call MatchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, leagueName, homeName, awayName, refreshFlag).
  - Persist result to MatchAnalysisResultRepository with 24h expiry window (policy) for reuse within the same day.
  - On exceptions (e.g., team not found, season unresolved), capture error text into job item and mark as failed. No retry in MVP.
- Progress accounting:
  - Maintain atomic counters for completed/failed/inProgress. Store per‑item status for UI parity.


Caching

- Reuse MatchAnalysisResultRepository for same‑day runs when refresh=false (default):
  - Define cache key conventions aligned with existing storage: leagueId, seasonId, homeTeamId, awayTeamId, analysis date.
  - Ensure consistent team ID ordering (home, away) to avoid collisions.
  - Store serialized MatchAnalysisResponse (existing JSON via ObjectMapper) plus metadata (timestamp, source=COMPUTED|CACHE).
- Eviction/TTL: 24 hours suggested for MVP; configurable property later.


Error Handling

- Failure scenarios captured per fixture:
  - Team ID not found / name unmatched.
  - Season unresolved for league.
  - Data gaps (no last N matches, empty H2H). Still produce partial results when possible; otherwise mark as failed with message.
- API behavior:
  - Status endpoint includes failed count and does not auto‑retry.
  - Results endpoint returns items with error text; frontend renders prominently as “Failed: <reason>”.


PDF Generation (LaTeX)

- Consolidated PDF (new):
  - Extend LaTeXService: new method buildConsolidatedDailyReport(jobResultsGroupedByLeague).
  - Structure: Title page (Chambua‑Leo Daily Report – {date}); Summary by league (counts, average probabilities); Sections per league with a table/list of fixtures containing: teams, kickoff time, W/D/L probabilities, BTTS, Over 2.5, expected goals, confidence, and advice.
  - Performance: Generate once after job completion; cache the produced PDF (e.g., temp file path keyed by jobId) for subsequent downloads.
- Per‑fixture PDFs:
  - Reuse existing endpoint/service that generates LaTeX PDF for a single fixture analysis result.
  - For ZIP creation, prefer producing PDFs on demand (if not cached) and streaming directly into ZipOutputStream.


ZIP Archive

- Implementation: Create a ZipOutputStream response; for each successful item, add an entry using a safe filename convention, e.g., "{leagueName}_{home}_vs_{away}_{date}.pdf" (normalized).
- Source of PDFs: generate per‑fixture via LaTeXService; optionally cache in a temp folder for reuse; delete temp files after TTL or on job eviction.


Frontend Implementation

Home Tab (Chambua‑Leo)

- UI placement: Home page tab bar; add a new tab labeled “Chambua‑Leo”.
- Action: Button “Analyze All Today’s Fixtures”.
- Click handler:
  - POST /api/match-analysis/batch (no body; query params date=today, refresh=false).
  - On success, obtain jobId and navigate immediately to /batch/{jobId}.

Progress/Results Page (/batch/{jobId})

- Data polling:
  - Poll GET /api/match-analysis/batch/{jobId} every 2–5 seconds (adaptive interval acceptable).
  - Once status indicates completion, optionally stop polling; keep a manual refresh button if desired.
- UI components:
  - Header with date and jobId, counts (total/completed/failed/in‑progress), animated progress bar, ETA.
  - Results list/grid:
    - Fetch via GET /api/match-analysis/batch/{jobId}/results (paginated).
    - Group by league (accordion/sections), show per‑fixture cards resembling played‑matches‑summary patterns:
      - Teams and kickoff time.
      - Key stats: W/D/L probabilities, BTTS, Over 2.5, Expected Goals, Confidence.
      - Advice string.
      - Error messages for failed items: “Failed: <reason>”. No retry button in MVP.
  - Downloads:
    - Button: “Consolidated PDF” → /api/match-analysis/batch/{jobId}/pdf.
    - Button: “ZIP Archive” → /api/match-analysis/batch/{jobId}/zip.
- Navigation behavior:
  - Immediate redirect to /batch/{jobId} after starting the batch.
  - Auto‑refresh results as items complete; show final state when done.


Performance Optimizations

- Concurrency:
  - Thread pool size: 15–20 threads; configurable via application properties.
  - Submit in waves/batches of ~20 to avoid DB spikes; coordinator ensures at most N in‑flight tasks.
- Precomputation:
  - At job start, precompute or warm FormGuideService caches per league/season to reduce repeated queries; optionally store intermediate summaries per league.
- Caching:
  - Leverage MatchAnalysisResultRepository to skip recomputation for same‑day runs when refresh=false.
- Batching strategy:
  - Group fixtures by league; process groups sequentially with intra‑group concurrency to improve locality and reduce lock contention.
- PDF efficiency:
  - Consolidated PDF only once after completion.
  - Per‑fixture PDFs generated on demand and optionally cached during ZIP creation.


Testing Plan

Unit Tests

- Batch coordinator:
  - Splits fixtures correctly, honors concurrency bounds, and tracks progress/ETA.
  - Correctly records successes/failures and exposes accurate status metrics.
- Season resolver:
  - Maps various season strings to latest seasonId per league; falls back correctly; logs unresolved.
- Caching behavior:
  - Skips analysis when cache hit and refresh=false; persists new results with timestamp/TTL.
- PDF builder:
  - Consolidated LaTeX output contains required per‑fixture fields and grouping.

Integration Tests

- End‑to‑end happy path:
  - POST /batch → poll status → fetch paginated results → download consolidated PDF and ZIP for a dataset of ~50 synthetic fixtures.
- Mixed failures:
  - Include fixtures with missing team IDs/aliases and ambiguous seasons; confirm error messages surface in results.
- Caching reuse:
  - Run same job twice with refresh=false; confirm high cache hit rate and reduced total time.

Performance Tests

- Load test 150 fixtures with 15–20 threads; validate total runtime < 120s on staging hardware.
- Measure DB query counts for FormGuideService and H2H lookups; verify improvements from precomputation and caching.

Seed Data

- Provide 100–150 representative fixtures for “today” in staging; cover multiple leagues and typical data issues (aliases, missing IDs, etc.).


Edge Cases

- No Fixtures:
  - If fixtures list is empty, mark job as COMPLETED immediately with total=0. Frontend shows “No fixtures today.”
- Failures:
  - Record and display clear error messages; do not retry in MVP.
- Large Volumes (upper bound 150):
  - Use batching/waves and tune thread pool for SLA; ensure memory footprint stays within limits.
- Duplicate Runs:
  - With refresh=false, return cached results for same‑day runs; expose a subtle “Refresh all (re‑analyze)” option in the UI later (post‑MVP).
- Ambiguous Seasons:
  - Default to latest seasonId per league and log warnings.


Assumptions

- Max 150 fixtures/day (peak) for MVP sizing.
- LaTeX‑based PDF generation is available and preferred; no JasperReports in MVP.
- MatchAnalysisResultRepository is suitable for storing/retrieving cached results with 24h TTL.
- Default to latest seasonId per league when fixture season strings are ambiguous/missing.
- SLA: complete analysis within 120 seconds for 100–150 fixtures using 15–20 threads.
- No retry for failed analyses in MVP; errors are reported only.
- ZIP archive bundles all per‑fixture PDFs for download.


Open Questions

- Temp storage location for generated PDFs/ZIPs: local filesystem vs. external storage (e.g., S3). Proposed MVP: server filesystem under a temp dir with periodic cleanup.
- Exact UI styling for results page: league grouping layout, card contents, sorting (e.g., by kickoff or confidence score).
- Should we expose a manual “Refresh” button on results page to allow explicit re‑analysis (refresh=true)? If yes, post‑MVP.
- Timezone handling for “today”: confirm server vs. user timezone; proposal: server default with clear display of date/timezone.
- Access control/rate limiting: who can trigger the batch and how often?


Rollout Plan

- Phase 1 (MVP)
  - Implement new endpoints, async processing, caching integration, and consolidated PDF/ZIP downloads.
  - Frontend: Add tab, invoke batch POST, progress/results page with polling, results grouping, and download buttons.
  - Logging/metrics: record durations, counts, cache hit rate.

- Phase 1.1 (Polish & Optimization)
  - Add league filtering/sorting, better ETA estimation, and DB query optimizations (precomputation/warming).
  - Improve temp file lifecycle management and introduce configurable TTLs.

- Phase 2 (Optional)
  - Explore JasperReports for enhanced templated reporting and alternative output formats.
  - Consider persistent job queues, retries with backoff, and multi‑node coordination if scaling out.


Notes on Reuse of Existing Components

- MatchAnalysisService: primary computation engine; keep interface stable; use analyzeDeterministic with seasonId and refresh flag.
- FormGuideService: precompute/warm per league to reduce repeated hits; ensure season‑scoped queries.
- MatchAnalysisResultRepository: reuse for caching to avoid recomputation within the same day.
- LaTeXService: extend to support consolidated PDF; reuse single‑fixture generation as is for per‑fixture PDFs.
- FixtureController/Service: rely on getFixturesByDate for daily batch sources; group by league where beneficial.


Acceptance Criteria (for approval before coding)

- The plan adheres to clarified requirements:
  - Handles 100–150 fixtures/day; targets <120s with concurrency and caching.
  - Uses LaTeX‑based PDF pipeline for consolidated and per‑fixture outputs; provides a ZIP archive.
  - Respects caching via MatchAnalysisResultRepository unless refresh=true.
  - Defaults to latest seasonId per league for ambiguous seasons.
  - Immediate navigation to progress/results page; error messages shown without retry.
- Endpoints, async model, and UI behavior are fully specified and reusable components identified.
- Testing, edge cases, assumptions, and rollout steps are clearly articulated.

# ChambuaViSmart — Investor & Stakeholder Pitch

Date: 2025-09-19 07:09 (Local)

Executive Summary
- ChambuaViSmart is an end-to-end football (soccer) analytics and insights platform that ingests historical match data and generates actionable, explainable pre-match predictions and reports for bettors, tipsters, fantasy players, and media outlets.
- The system blends team form, head-to-head (H2H) recency-weighted signals, league context, and a transparent Poisson-based model for goal expectations. Outputs include: win/draw probabilities, BTTS and Over 2.5 likelihoods, expected goals (xG proxies), confidence scores, advice strings, and exportable PDF reports.
- Built with a modern, scalable stack: Spring Boot backend (Java), Angular frontend, scheduled jobs, and a resilient repository layer. The app is production-minded with feature flags, timezone control, and LaTeX-powered report generation.
- Today, the product consistently produces accurate, explainable insights at scale with unit/integration coverage for key components. There is a clear path to higher accuracy via model calibration, richer features, and near-real-time data feeds.

Problem & Opportunity
- Problem: Bettors and sports content creators are overwhelmed by noisy stats and opaque models. They need reliable, interpretable predictions that combine recent form, matchup dynamics, and league context.
- Opportunity: A trusted, explainable predictions engine with professional reporting can monetize via subscriptions, B2B API licensing, white-label widgets, and enterprise data services.

High-Level Architecture

    +------------------------+            +--------------------------+
    |        Angular         |  HTTPS     |        Spring Boot       |
    |   Frontend (SPA)       +----------->+  REST Controllers        |
    | - Components/Routes    |            |  (MatchController, etc.) |
    | - Services (match)     |            +------------+-------------+
    +-----------+------------+                         |
                ^                                      |
                |                                      v
                |                           +-------------------------+
                |                           |     Service Layer       |
                |                           |  - MatchAnalysisService |
                |                           |  - H2HService           |
                |                           |  - FormGuideService     |
                |                           |  - LeagueTableService   |
                |                           |  - QuickInsightsService |
                |                           |  - LaTeXService (PDF)   |
                |                           +------------+------------+
                |                                        |
                |                                        v
                |                           +-------------------------+
                |                           |   Repository Layer      |
                |                           |  - JPA Repositories     |
                |                           |    (Match, Team, Season)|
                |                           +------------+------------+
                |                                        |
                |                                        v
                |                           +-------------------------+
                |                           |   Relational Database   |
                |                           +-------------------------+
                |
                |  Static & PDF exports
                +<-----------------------------------------------+

Backend Entry Point and Ops
- com.chambua.vismart.ChambuaViSmartApplication: Spring Boot entrypoint.
  - @EnableScheduling: enables periodic jobs, e.g., fixtures refreshes and data hygiene.
  - Timezone: default set to Africa/Nairobi to standardize date/time computations and logs. On startup it prints the configured server time for auditability.
- FeatureFlags: gate experimental features without redeploys.
- Configuration via application.yml/properties and application-test.yml for test isolation.

Primary User Flows
1) Browse fixture insights
   - Frontend fetches deterministic analysis via MatchController/MatchAnalysisController.
   - Analysis includes probabilities (home/draw/away), BTTS, Over 2.5, expected goals, and narrative insights text.
2) Generate analysis PDF
   - User submits an AnalysisRequest. LaTeXService renders a polished PDF from templates and archives it via PdfArchiveService.
3) H2H deep-dive & form guide
   - Users search for teams, inspect last-N head-to-head matches, goal differentials, and form sequencing (WWWDL), with derived streak insights.

Key Controllers
- MatchController
  - Public APIs for: total matches, team-specific statistics, H2H suggestions, H2H matches, form by team, streak insights, correct score verification (Poisson), PDF generation and retrieval.
  - Notable math utilities inside:
    - poisson(lambda, k): classic Poisson PMF for expected goals → correct score grids.
    - factorial(n): helper for Poisson PMF.
  - Generates and serves PDFs via LaTeXService and PdfArchiveService.
- MatchAnalysisController
  - Thin REST layer delegating to MatchAnalysisService for deterministic match analysis responses.

Core Services & Computation Logic
1) MatchAnalysisService (heart of the model)
   - Purpose: Produce a MatchAnalysisResponse with explainable and bounded probabilities.
   - Inputs: league/team identifiers or names, optional season context, refresh toggle.
   - Process (deterministic pipeline):
     a. Collect form rows (FormGuideService) for both teams in a relevant season/context.
     b. Identify each team’s row (findTeamRow) and compute base form-derived probabilities.
     c. Integrate H2H recency-weighted signals over last N matches (H2HSummary), including:
        - Points-per-game (PPG) for home and away.
        - BTTS and Over 2.5 percentages.
        - Goal differential summaries, last-5 forms in H2H context.
     d. League context normalization (determineDomesticLeagueId) to align cross-competition matches.
     e. Blend and normalize probabilities using clampPercent and normalizeTriplet, ensuring:
        - Sum(homeWin, draw, awayWin) = 100
        - Percent fields bounded to [0, 100]
     f. Expected goals proxy via round2 on derived team attack/defense rates.
     g. Confidence score: summarizes signal agreement, data freshness, and sample sizes.
     h. Advice text: succinct actionable recommendation (e.g., Home DNB, Over 2.0 lines), based on thresholds.
   - Caching: MatchAnalysisResultRepository stores serialized responses keyed by inputs and seed.
   - Seed computation (computeSeed) ensures stable outputs for identical inputs unless refresh is forced.
2) H2HService
   - Resolves team identities safely (TeamNameNormalizer + Repository lookups) from natural language.
   - getH2HByNames: returns historical matches between two sides in any orientation.
   - computeGoalDifferentialByNames: aggregates H2H goal stats and returns GoalDifferentialSummary.
   - generateInsightsText: creates human-friendly narratives from H2H outcomes and trends.
3) FormGuideService
   - Builds recent form windows for teams, emitting compact symbols (W/D/L), recent streaks, and performance splits (home/away, league context).
   - Outputs are consumed by MatchAnalysisService and controllers for UI.
4) LaTeXService (PDF generation)
   - Templates: resources/templates/analysis.tex
   - Pipeline:
     - Build a safe, escaped LaTeX model from AnalysisRequest (teams, H2H rows, correct scores, etc.).
     - Render through a minimal engine or pre-bundled template.
     - Return raw PDF bytes to controller; PdfArchiveService persists metadata and blob for downloads.
5) QuickInsightsService
   - Convenience service compiling high-signal text insights from H2H + form + last-5 patterns.

Data Model & Repositories
- JPA entities include Match, Team, Season, MatchAnalysisResult, PdfArchive (and more), with repositories:
  - MatchRepository: core historical data access, H2H queries, last-N windows.
  - TeamRepository and TeamAliasRepository: robust team identity handling.
  - SeasonRepository: binds fixtures to league-season context.
  - MatchAnalysisResultRepository: analysis cache for fast repeat access.

MatchAnalysisResponse DTO (API Contract)
- Fields surfaced to the frontend and PDFs:
  - homeTeam, awayTeam, league
  - winProbabilities { homeWin, draw, awayWin }
  - bttsProbability, over25Probability
  - expectedGoals { home, away }
  - confidenceScore, advice
  - formSummary (base unblended stats) and h2hSummary (recency-weighted)
  - headToHeadMatches: flat list for UI timelines
  - homeStreakInsight, awayStreakInsight: structured streak narratives

Explainability & Math Notes
- Probability normalization
  - normalizeTriplet ensures Home+Draw+Away = 100 while preserving ratios.
  - clampPercent bounds all % outputs to [0, 100].
- Poisson model (correct scores, expected goals)
  - poisson(lambda, k) = e^{-lambda} * lambda^k / k!
  - lambdas estimated from team attack/defense factors and recent scoring rates → produce a grid of scoreline probabilities.
- H2H recency weighting
  - Last N (configurable) matches between sides, favoring more recent outcomes for PPG, BTTS, Over 2.5, and goal differential.
- League-context alignment
  - determineDomesticLeagueId reconciles cross-competition data to a domestic baseline for fair comparison.

What’s Working Well Today
- Deterministic, explainable outputs: every number has a provenance (form, H2H, league alignment).
- Robust identity resolution for teams, reducing noisy duplicates via TeamAliasRepository and normalizer.
- Fast repeat analyses via persistent cache (MatchAnalysisResultRepository) and stable seeding.
- Professional-grade reporting (LaTeX PDFs) suitable for distribution, social sharing, and B2B deliverables.
- API ergonomics: Angular frontend integrates cleanly; endpoints expose both aggregated summaries and raw lists for custom UI.

Accuracy & Reliability
- Current accuracy is supported by:
  - Balanced signal blend: form + H2H + league context reduces variance from any single source.
  - Recency emphasis: more weight on recent matches captures short-term form swings.
  - Poisson model: industry-standard baseline for low-scoring sports; calibrated lambdas improve scoreline distribution realism.
  - Normalization and bounds prevent pathological outputs (e.g., 101% totals).
- Internal tests and backfills (see backend tests and analysis reports) show competitive hit rates on:
  - 1X2 tendencies (directional accuracy rather than exact score)
  - BTTS and Over 2.5 thresholds for mainstream leagues
- Reliability features:
  - Timezone pinning (Africa/Nairobi) eliminates parsing drift across environments.
  - @EnableScheduling for automatic refresh jobs.
  - Feature flags to ship safely and A/B model variants.

Why We Win (Differentiators)
- Explainability by design: both numbers and narratives are first-class API outputs.
- PDF automation: turnkey reports publishers can monetize immediately.
- Identity robustness: fewer data errors around team naming and league season mapping.
- Developer velocity: modular services, DTO contracts, and a clear testing surface.

Target Users & Use Cases
- Bettors and tipsters: daily match edges and packaged PDFs.
- Fantasy and prediction game players: form & H2H synthesis.
- Sports media/editors: syndicated previews and sponsor-ready graphics.
- B2B platforms: API access and white-label embeddable widgets.

Monetization & Go-To-Market
- B2C: tiered subscriptions (Basic → Pro) with limits on daily analyses and PDF downloads.
- B2B: API licensing, volume-based pricing, SLA-backed data feeds.
- White-label: custom-branded PDF templates and embeddable components.
- Enterprise: data exports and on-prem compute with support contracts.

Roadmap (Improvements That Increase Accuracy & Value)
1) Model Calibration & Features
   - Calibrate Poisson lambdas per league, include home-field advantage priors.
   - Incorporate ELO/Glicko-like ratings and rest/travel adjustments.
   - Add injury/news ingestion and lineup probabilities as covariates.
2) Better Sampling & Windows
   - Adaptive window sizing (recent volatility vs. long-run strength).
   - Cross-validation loops for threshold tuning (BTTS/Over lines).
3) Data Quality & Coverage
   - Expand league coverage with automated ingestion and QA checks.
   - Enrich metadata (xG from third parties where licensed) to refine expected goals.
4) UX & Distribution
   - Interactive charts, scoreline heatmaps, and scenario toggles on frontend.
   - Scheduled email reports and dashboarding for subscribers.
5) Ops & Scale
   - Horizontal scaling for API and batch jobs; caching layer for hot endpoints.
   - Observability (traces/metrics) for model drift and data latency.

Risks & Mitigations
- Data quality drift: mitigate with validation dashboards and redundancy in data sources.
- Model overfitting: use out-of-sample validation and holdout weekends per league.
- Regulatory/compliance: ensure regional compliance for betting-related content and clear disclaimers.
- Third-party dependencies (LaTeX, data feeds): containerize and health-check; fallbacks to minimal templates.

Simple Data Flow Diagram (ASCII)

    [DB] --(JPA queries)--> [Services]
      |                         |
      |                    [MatchAnalysisService]
      |                         |
      v                         v
    [H2HService]           [FormGuideService]
      |                         |
      +------> blend/normalize <-+
                     |
                     v
             [MatchAnalysisResponse]
                     |
                     +--> REST JSON to Angular UI
                     +--> LaTeXService -> PDF bytes -> Archive -> Download

Class & Responsibility Map (Selected)
- MatchController: REST orchestration for stats, H2H, form, PDF.
- MatchAnalysisController: Analysis endpoint wrapper.
- MatchAnalysisService: Deterministic analysis core; blending/normalization; confidence/advice.
- H2HService: H2H retrieval, goal differential, narrative insights.
- FormGuideService: Recent form, splits, streaks.
- LaTeXService: PDF build from structured request, template handling, safe escaping.
- PdfArchiveService: Persist & list generated PDFs.
- Repositories: Match, Team, Season, TeamAlias, MatchAnalysisResult.

Operational Excellence
- Config management (application.yml/properties) supports env-specific overrides.
- Test resources (application-test.yml) facilitate isolated runs.
- Startup log prints server TZ and current time for forensic traceability.
- Schedulers ready for fixture refresh and cache warming.

Why Purchase/Invest
- Immediate utility: working, explainable engine with exportable reports.
- Clear monetization: subscriptions, API, white-label, enterprise.
- Technical moat: identity resolution, explainability, PDF automation, and modular architecture.
- Scalable foundation: Spring Boot + Angular with test coverage and CI/CD-ready config.

KPIs to Track Post-Investment
- Prediction hit rates by market (1X2, BTTS, Over/Under) per league and time window.
- Subscriber conversion and retention; PDF downloads per user.
- API latency and success rates; cache hit ratios.
- Model drift indicators and data freshness SLAs.

Appendix — Selected Endpoints & DTOs
- GET /api/matches/h2h?homeName=...&awayName=... → H2HMatchDto list
- GET /api/matches/form?name=... → FormSummary
- POST /api/matches/analysis/pdf → binary PDF (via AnalysisRequest)
- GET /api/matches/analysis/pdfs?page=&size= → paginated archive list
- GET /api/matches/analysis/pdfs/{id}/download → PDF download
- GET /api/matches/verify-correct-scores?homeTeamId=&awayTeamId=&leagueId= → Poisson grid for sanity checks
- Deterministic analysis response (MatchAnalysisResponse) includes:
  - winProbabilities {homeWin, draw, awayWin}
  - bttsProbability, over25Probability
  - expectedGoals {home, away}
  - confidenceScore, advice
  - h2hSummary (ppgHome, ppgAway, bttsPct, over25Pct, goalDifferential, lastN)
  - headToHeadMatches (date, home, away, score)
  - home/away streak insights

Legal & Ethical Note
- The platform provides informational predictions and should be accompanied by regional disclaimers. Users retain responsibility for betting decisions. We advocate responsible gaming.

Contact & Next Steps
- Live demo: run backend and frontend modules, generate a sample PDF, and walk through an EPL fixture analysis.
- Technical deep-dive: review service code paths (MatchAnalysisService, H2HService) and calibration roadmap.
- Business: discuss pricing, SLAs, and integration packages with potential partners.

# Batch Viability Assessment: Full Implementation of All Poisson Enhancements in ChambuaViSmart

Date: 2025-09-19
Author: Junie (JetBrains autonomous programmer)

---

## Executive Summary

This report evaluates the feasibility of shipping all three proposed enhancements to the Poisson-based prediction engine as a single, always-on batch update across all environments (dev, test, prod), without phased rollout or OFF-by-default flags:

- Dixon–Coles (DC) Adjustment: Low effort, strong fit; adjusts joint PMF for low-score correlation.
- Lambda Calibration via Optimization: Low–medium effort; calibrates team lambdas using recent windows with MLE.
- ML Wrapper Layer (Random Forest blend): Medium effort; blends Poisson baseline with ML probabilities for final output.

Overall feasibility for batch coding now: Yes, technically possible as one cohesive update. Estimated combined uplift: 8–20% across 1X2 directional accuracy and BTTS/Over hits, based on literature and applied practice when each piece is implemented correctly and evaluated on sufficient data.

Disruption level for all-at-once: Medium–High. The primary risks stem from increased surface area, dependency additions (ML), training determinism, and cache/versioning changes.

Prototype timeline (single sprint): 7–12 days for a functional prototype with backtests and tests, assuming one focused implementation cycle. A production-hardened deployment may require 2–3 additional days for CI/CD and monitoring.

---

## Viability of Batch Implementation

- Possible as one cohesive update? Yes.
  - Why it fits: The existing pipeline in MatchAnalysisService.analyzeDeterministic(...) already sequences inputs → lambda derivation → Poisson grid → market probabilities. We can place:
    1) Lambda Calibration before grid computation,
    2) Dixon–Coles adjustment when building the score grid (affecting 0–0, 1–0, 0–1, 1–1 cells with tau), then renormalize,
    3) ML Wrapper at the final blending stage using Poisson outputs and contextual features.
  - Shared integration points simplify orchestration. Determinism can be preserved via pinned model artifacts, stable windows, and seeded training.

- Pros of all-at-once
  - Faster time-to-value with immediate visibility of combined uplift across APIs, PDFs, and UI.
  - Single refactor pass through core pipeline, caching, and DTO explainability fields.
  - Unified testing and documentation update cycle; fewer multi-phase coordination costs.

- Cons of all-at-once
  - Higher test and validation burden; harder to attribute uplift/degradation to individual components.
  - Larger blast radius: dependency, configuration, and cache/version changes land together.
  - Operational risk if ML model quality or training cadence is not stable on day one.

- Dependencies/Setup
  - ML library (Weka or Smile) added to backend build. Weka is common and mature for RF; Smile is another option.
  - New JPA entity/repository: LeagueAdjustment (stores per-league DC tau and metadata).
  - Training job or offline script: assemble historical dataset via MatchRepository + services, train RF, serialize model into resources (e.g., classpath models/rf-1x2.model) with versioning.

---

## Integration & Disruption Analysis

- Code Changes (estimated scope)
  - Total: ~8–12 files, 500–900 LoC.
  - Key touchpoints:
    - MatchAnalysisService: integrate pipeline order → Calibrate lambdas → Build Poisson grid → Apply DC tau on low-score cells → Renormalize → Compute probabilities → ML blend → normalizeTriplet.
    - FormGuideService: new calibration methods (MLE over recent windows, guardrails, caching).
    - New DC fitting service + LeagueAdjustmentRepository + entity.
    - ModelLoader/MLService: load serialized RF model once; compute features; blend with Poisson.
    - Configuration: application.yml with always-on toggles and deterministic settings (seed, model path, blend weights).
    - Optional: Controller query param (?useMl=true) if we want runtime opt-ins; for truly “always-on,” default to enabled in service.

- Runtime/Build Impact
  - Build: Adds ML dependency; CI must cache/download these artifacts. New tests added.
  - Runtime overhead per request (post-warm):
    - DC: <1 ms for grid adjustment and renormalization.
    - Calibration: negligible if cached per fixture/week; 2–10 ms if optimized on-demand.
    - ML inference (RF): ~1–3 ms per request given modest feature vector; model load is one-time at startup or first use.
  - Combined expected latency impact: +5–15 ms per match analysis in typical scenarios.

- Risks to Core
  - Overfitting/Instability: Combining calibration, DC, and ML without staged validation can overstate improvements or cause regressions in specific leagues/periods.
  - Determinism: Must pin model version, RandomForest seed (training time), and windows. Serving should be deterministic.
  - Cache Invalidation: Caching keys must include a version tag to avoid mixing old/new results.
  - DTO Compatibility: MatchAnalysisResponse should remain stable; only add optional fields (e.g., modelVersion, topFeatureImportances, dcTau, calibrationStatus) that clients can ignore.
  - Dependency/Build Risk: New ML dependency can lengthen builds and require memory tuning in CI.

---

## Safeguards for Immediate Enablement

- Rollout (as a single commit/PR)
  1) Implement all three enhancements behind configuration that is ON by default in application.yml across all profiles (no disabling via profile overrides).
  2) Include a versioned cache key suffix (e.g., modelVariant=v3) in MatchAnalysisResult and related cache logic to segregate from pre-batch results.
  3) Ship model artifact (rf-1x2.model) with a fixed checksum and document seed, training timestamp, and data cut; keep a previous model on hand for quick reversion if needed.
  4) Provide a clear, env-agnostic configuration: deterministic seeds, calibration windows, DC tau sourcing (repository default or fallback constant), blend weight (e.g., 0.7/0.3) in yml.
  5) Ensure startup validation: on application start, verify model presence, ability to load, and that LeagueAdjustment records exist or sensible defaults apply.

- Testing
  - Unit tests
    - DC: verify score grid corrections only affect target cells and renormalize to 1; low-score probability movements are directionally correct.
    - Calibration: synthetic dataset with known lambdas to verify optimizer recovery; enforce min/max guardrails; fallback path when insufficient data.
    - ML Blend: deterministic outputs with a known serialized model; verify blend math and normalization; test missing-model fallback (Poisson-only) still deterministic.
  - Integration tests
    - analyzeDeterministic end-to-end with all features ON; verify triplet normalization, DTO stability, and populated optional fields; assert cache keys include variant.
    - API tests ensuring no endpoint or DTO contract break; PDFs still render.
  - Backtests (pre-merge gate)
    - Historical leagues sampling; compute 1X2/BTTS/Over accuracy deltas, calibration curves (Brier score/NLL); set acceptance thresholds and publish summary.

- Monitoring and Rollback
  - Monitoring: log modelVersion, dcTau per league, calibration window/coverage, latency metrics per analysis. Capture counters for requests using ML and calibration.
  - Rollback: if batch must be reverted, use a git revert PR for the single commit; keep previous model and pre-batch config ready. Because features are always-on, feature-flag rollback is not the plan—version-controlled revert is.

---

## Prompt Readiness

A single, well-structured prompt can guide full coding for this batch update. Ideal prompt structure for an autonomous assistant:

- Context
  - Summarize current architecture (Spring Boot services, repositories, DTOs; Poisson baseline in MatchAnalysisService).
  - State requirements: implement DC, Calibration, and ML Wrapper as always-on across all profiles; preserve determinism, explainability, DTO stability, and caching with versioned keys.

- Deliverables
  - Code changes with file paths and contents for:
    - DC integration (service changes, entity/repository, tau fitting utility, application.yml entries).
    - Lambda calibration (FormGuideService methods, caching, guardrails).
    - ML wrapper (training script/job stub, ModelLoader, service blend, config, resources model file placeholder).
    - Updated tests (unit + integration) and backtest harness skeleton.
  - Documentation updates: README section for model versioning, config, and rollback.

- Constraints and Quality Gates
  - Deterministic outputs; normalized probabilities;
  - Config defaults ON; no profile-specific OFF overrides;
  - Caching version suffix; DTO compatibility (optional fields only);
  - CI builds with ML dependency; model artifact present;
  - Performance target: +≤20 ms per analysis at p95.

- Acceptance Criteria
  - All tests pass; startup validation succeeds; backtest script produces metrics and stores summary logs.

With this structure, an AI (or human) can implement the end-to-end batch with minimal ambiguity.

---

## Appendix

- Assumptions
  - Historical data via MatchRepository is sufficient (≥ 2–3 seasons per league) for RF training; a smaller first pass is acceptable if features are regularized and class-balanced.
  - We can add Weka (or Smile) as a dependency without organizational restrictions; memory/CPU budgets are adequate in CI and prod.
  - Determinism is defined as: fixed model file + fixed feature engineering + fixed seeds/windows; no randomness at serving time.
  - Application.yml is global (no profile overrides to disable features); any environment-specific differences will not turn features OFF.

- References to Prior Context
  - Previous assessment outlined phased enablement; this batch plan differs by enabling all features immediately and relying on repo-level rollback rather than runtime flags.
  - Core services: MatchAnalysisService (analyzeDeterministic, normalizeTriplet, computeSeed), FormGuideService, H2HService, LaTeXService; repositories including MatchRepository and new LeagueAdjustmentRepository.

- Key External References
  - Dixon, M. J., & Coles, S. G. (1997): Modeling association football scores; low-score correlation correction.
  - Weka: RF classifier docs and model serialization.
  - Apache Commons Math: optimization (Nelder–Mead/BOBYQA) and statistical routines.
  - Spring Boot best practices for deterministic ML: pinned artifacts, startup validation, and versioned caching.

# Viability Assessment: Enhancing ChambuaViSmart Prediction Accuracy with Poisson Layers

Date: 2025-09-19
Author: Junie (JetBrains autonomous programmer)

---

## Executive Summary

This assessment evaluates three proposed improvements layered on top of the existing deterministic Poisson framework in the ChambuaViSmart Java/Spring Boot application:

- Dixon–Coles (DC) Adjustment: Yes — low effort, strong fit for current architecture. Estimated uplift: 3–7% in 1X2 calibration and low-score CS distribution; 2–5% for BTTS/Over in some leagues. Disruption: Low. Timeline: 2–4 days to prototype + 2 days for validation.
- ML Wrapper Layer (Random Forest blend): Yes — medium effort and compatible with existing Spring stack, using Weka or Smile. Estimated uplift: 5–15% on 1X2 directional accuracy; 5–10% on BTTS/Over. Disruption: Medium. Timeline: 6–10 days (prototype + offline training pipeline + feature flags), plus 1–2 days to harden scheduling/ops.
- Lambda Calibration with Optimization (per team/season MLE): Yes — low-to-medium effort. Estimated uplift: 4–10% especially on totals/BTTS consistency and CS grid realism. Disruption: Low-to-Medium. Timeline: 4–6 days including backtests.

Overall viability: All three are feasible within the current codebase while preserving determinism and explainability if introduced behind feature flags and with careful normalization. Recommend phased rollout: start with Dixon–Coles, then Lambda Calibration, and finally the ML Wrapper.

---

## Viability Per Improvement

### 1) Dixon–Coles Adjustment (Low-effort enhancement to Poisson)

- Possible? Yes.
  - Why: Current service computes Poisson score grids inside `backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java` (method `analyzeDeterministic(...)`, see file structure). After lambdas are derived from form/H2H, a DC tau correction can be applied to the joint PMF for low scores (0–0, 1–0, 0–1, 1–1), then the grid can be renormalized. Uses only Apache Commons Math (already present) for MLE fitting per league.
  - No external deps beyond Commons Math; we can store league-level `tau` in a new JPA entity/repository.

- Pros
  - Better fit to empirical low-score correlations; improves 1X2 calibration (draw and narrow wins) without large complexity.
  - Fully deterministic; explainable tweak layered on top of existing lambdas.
  - Small surface area; minimal runtime cost.

- Cons
  - Gains tend to be modest and league-specific; overfitting risk if tau is fit on small samples.
  - Requires periodic re-fitting; need persistence for league-level tau.

- Integration Points
  - `MatchAnalysisService.analyzeDeterministic(...)`: After computing `lambdaHome`, `lambdaAway` and before computing 1X2/BTTS/Over probabilities, apply DC adjustment to the score grid for (0–0, 1–0, 0–1, 1–1), then renormalize.
  - `normalizeTriplet(...)` and clamping remain unchanged; final triplet normalization still applied.
  - Add `LeagueAdjustment` JPA entity + `LeagueAdjustmentRepository` (fields: `leagueId`, `tau`, `fittedAt`); fit tau via a small service using Commons Math MLE on last ~50 league matches. Cache in-memory with a TTL.
  - Feature flag: `@Value("${features.dixonColes:false}")` consumed in `MatchAnalysisService` to toggle.

- Estimated Accuracy Uplift
  - Literature and betting model practice: 3–7% improvement in 1X2 calibration where low-score dependence matters; 2–5% for BTTS/Over edges in tighter leagues.


### 2) ML Wrapper Layer (Medium-effort, RF blend)

- Possible? Yes.
  - Why: The pipeline exposes deterministic features and outputs suitable as inputs to a classifier: lambdas, expected goals, Poisson 1X2 baseline, H2H and form features (`FormGuideService`, `H2HService`). Weka/Smile can be used off-line for training. Model can be serialized and loaded at runtime; blending remains deterministic once the model file is fixed.
  - Dependencies: Introduce Weka (or Smile) as an optional dependency. No change to API contracts required.

- Pros
  - Captures non-linear interactions and interactions between Poisson-derived signals and contextual features; empirically boosts hit rates.
  - Maintain explainability via: (a) report Poisson component separately; (b) log/return feature importances; (c) keep blend weighting transparent (e.g., 70% Poisson + 30% ML).
  - Deterministic responses given a pinned model file and seeded feature transforms.

- Cons
  - Additional ops: training job, model versioning, and drift monitoring.
  - Added dependency and CI complexity; potential latency when loading the model (once) and slight runtime overhead for inference.

- Integration Points
  - Data: Query `MatchRepository` to assemble training data (historical matches with goals, form/H2H aggregates). Use services (`FormGuideService`, `H2HService`) to compute features as needed.
  - Training: New offline component or `@Scheduled` job in backend to retrain weekly; serialize to `/resources/models/rf-1x2.model`.
  - Serving: In `MatchAnalysisService.analyzeDeterministic(...)`, after producing Poisson baseline probabilities and features, if `features.useMl=true`, load model (once, cached), compute RF probabilities, and blend: `p_final = 0.7 * p_poisson + 0.3 * p_rf` (weights configurable).
  - Explainability: Add optional fields in `MatchAnalysisResponse` to include top-K feature importances and model version string, but keep default DTO unchanged by gating behind flag or supplemental field.
  - Feature flag and query flag: Spring `@Value` for service-level toggle, plus query parameter `?useMl=true` in the controller path that forwards to service.

- Estimated Accuracy Uplift
  - Based on comparable sports modeling practices: 5–15% directional accuracy uplift on 1X2; 5–10% on BTTS/Over, assuming reasonable historical coverage and weekly retraining.


### 3) Lambda Calibration with Optimization (Low–medium effort)

- Possible? Yes.
  - Why: You already compute proxy lambdas from form/H2H. Replacing these with MLE-calibrated lambdas via Commons Math (e.g., Nelder–Mead/Simplex) per team/season on recent windows (last ~20 matches) is aligned with the codebase. Calculations can occur in `FormGuideService` (or a small calibrator helper) before Poisson grid construction.

- Pros
  - Improves consistency of expected goals, CS grid, and totals with recent performance, reducing bias from simplistic attack/defense rates.
  - Limited scope change; explainable (same Poisson semantics) and deterministic when seeds/windows are fixed.

- Cons
  - Risk of overfitting on small windows; need smoothing/priors and guardrails on lambda ranges.
  - Slight runtime cost if computed on-demand (can be cached per fixture/week).

- Integration Points
  - `FormGuideService`: Add method to calibrate `lambdaHome`, `lambdaAway` via MLE on a recent H2H/context window; fall back to proxy lambdas if insufficient data.
  - `MatchAnalysisService.analyzeDeterministic(...)`: If flag `features.calibrateLambdas=true`, call calibration before score grid generation; otherwise keep current proxies.
  - Use Commons Math optimization (Nelder–Mead or BOBYQA) to minimize negative log-likelihood across observed goals.

- Estimated Accuracy Uplift
  - 4–10% for totals/BTTS; can also stabilize 1X2 edges when teams shift strength mid-season.

---

## Disruption Analysis

- Extent of Code Changes
  - Dixon–Coles: 2–3 files; ~120–200 LoC. New repository/entity for `LeagueAdjustment` (tau), minor changes in `MatchAnalysisService` (apply tau and renormalize), small fitting utility/service.
  - ML Wrapper: 6–10 files; ~300–600 LoC. Adds a training job/service, model loader, configuration, and a blending branch in `MatchAnalysisService`. Optional dependency (Weka/Smile) and resources path for serialized model.
  - Lambda Calibration: 2–4 files; ~150–250 LoC. Methods in `FormGuideService` and a calibrator helper; call site guarded by a flag.

- Build/Test Impact
  - Dixon–Coles: Minimal; unit tests for grid adjustment and probability sums. No API changes.
  - ML Wrapper: Moderate; add dependency and tests for model loading/blending; CI needs caching for model artifact.
  - Lambda Calibration: Minimal–moderate; tests for optimizer convergence, guardrails, and fallback.

- Runtime Performance
  - Dixon–Coles: Negligible overhead (<1 ms per match).
  - ML Wrapper: Slight overhead (inference per request: ~1–3 ms with RF and small feature vector; model loads once). Retraining job runs off-path.
  - Lambda Calibration: If cached per fixture/week, negligible. On-demand optimization might add 2–10 ms depending on iterations and window size.

- Risk to Core Functionality
  - Determinism: All features behind flags; DC and Calibration are deterministic. ML becomes deterministic once the model file is pinned and feature extraction seeded. Use `computeSeed(...)` as part of any randomized steps if needed (e.g., RF seed during training, not serving).
  - API Contracts and DTOs: Keep `MatchAnalysisResponse` stable; add optional fields only under flags. Ensure `normalizeTriplet(...)` and clamping unchanged.
  - Caching: `MatchAnalysisResultRepository` keys may need to incorporate relevant flags (`useMl`, `dc`, `calibrate`) or a version suffix to avoid cache pollution across variants.

---

## Safeguards to Minimize Disruption

- Rollout Plan
  1) Introduce feature flags via `@Value` in services: `features.dixonColes`, `features.calibrateLambdas`, `features.useMl`, `features.mlBlendWeight`.
  2) Implement Dixon–Coles first with per-league tau storage. Ship OFF by default; internal QA via backtests.
  3) Add Lambda Calibration next; OFF by default. Compare calibrated vs. proxy lambdas on historical sets.
  4) Integrate ML Wrapper last; OFF by default. Run shadow mode (compute ML but don’t affect outputs) and log KPIs.
  5) Once validated, progressively enable per-league via configuration.

- Testing Strategy
  - Unit Tests
    - DC: Verify adjusted cells (0–0, 1–0, 0–1, 1–1) change as expected, and the grid renormalizes to 1. Validate triplet sums (100 after normalization/clamp).
    - Calibration: Synthetic data where true lambdas are known; optimizer recovers within tolerance; guardrails on min/max lambdas.
    - ML Blend: Given a fixed serialized model with known outputs, verify deterministic probabilities and blend math.
  - Integration Tests
    - End-to-end `analyzeDeterministic(...)` with flags ON/OFF; ensure DTO fields remain present and compatible; confirm caches segregate by variant.
  - Backtests
    - Historical 1X2/BTTS/Over hit rates per league; log uplift deltas and calibration curves (Brier/NLL).

- Rollback and Monitoring
  - Immediate rollback via flags in `application.yml` (and environment overrides). Keep OFF-by-default posture.
  - Logging: Include model version, feature-flag states, tau values, and calibration status in debug logs.
  - Metrics: Add counters for accuracy KPIs (offline) and latency (online). For training, monitor data freshness and sample sizes.

---

## Recommendations

1) Prioritize Dixon–Coles first: it’s low-risk, fast, and directly improves low-score calibration without compromising determinism.
2) Add Lambda Calibration second: improves lambda realism and downstream market props (BTTS/Over/CS) with modest effort.
3) Pilot ML Wrapper last: use a conservative blend weight (e.g., 20–30%) and run shadow evaluations before impacting outputs. Maintain explainability by reporting top feature importances and Poisson vs. ML contributions.
4) Ensure cache-versioning: include a variant tag in `MatchAnalysisResult` keys when flags are ON.
5) Establish weekly retraining cadence with change control and simple model registry (file versioning in resources or object storage).

---

## Appendix

- Assumptions
  - Access to Apache Commons Math (present) and permission to add Weka/Smile as dependencies if ML path is pursued.
  - Adequate historical data accessible via `MatchRepository` (played matches with goals) and services to compute form/H2H features.
  - We can create a simple `LeagueAdjustment` entity/repository for tau and reference leagues by `leagueId`.
  - Current `MatchAnalysisResponse` supports adding optional fields without breaking clients (or we gate new fields by flags only).

- Relevant Code Areas
  - `backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java` — `analyzeDeterministic(...)`, `normalizeTriplet(...)`, `computeSeed(...)`.
  - `backend/src/main/java/com/chambua/vismart/service/FormGuideService.java` — candidate host for lambda calibration.
  - `backend/src/main/java/com/chambua/vismart/service/H2HService.java` — H2H-derived features and insights used as inputs and narratives.
  - `backend/src/main/java/com/chambua/vismart/repository/*` — `MatchRepository`, `TeamRepository`, and new `LeagueAdjustmentRepository` for DC tau storage.
  - PDF generation: `LaTeXService` consumes existing DTOs; keep DTO shape stable.

- References
  - Dixon, M. J., & Coles, S. G. (1997). Modelling association football scores and inefficiencies in the football betting market.
  - Weka: Machine Learning Group at the University of Waikato. Documentation for RandomForest and serialization.
  - Apache Commons Math: Optimization and statistical distributions.
  - Best practices for deterministic ML in Spring Boot: model versioning, feature flags, and seeded transforms.

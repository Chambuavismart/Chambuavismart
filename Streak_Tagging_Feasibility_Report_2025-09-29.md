# Feasibility & Planning Report — Historical Streak Pair Tagging (same_streak vs different_streak)

Date: 2025-09-29
Author: Junie (Autonomous Programmer)

## Objective
Add a feature that:
- Tags each analyzed match-up as either same_streak (both teams’ current longest streak types are the same letter W/D/L) or different_streak (they differ).
- Computes historical outcome summaries conditioned on those pair tags across past matches, to display on the frontend (e.g., draw %, Over/Under 2.5, win/loss splits).

This report assesses feasibility in the current ChambuaViSmart codebase and outlines a safe, incremental implementation plan.

---

## 1) Is this implementable now?
Short answer: Yes, incrementally and safely.

Why this is feasible:
- The backend already computes per-team current streaks as part of a domestic-context last-5 form step inside `MatchAnalysisService` (see `computeFormLastFive`), storing them in `MatchAnalysisResponse.H2HSummary.homeForm/awayForm` via a `currentStreak` string like `3W`, `2D`, `4L`.
- There is an existing per-team historical insight function `computeStreakInsight(teamId, teamName, pattern)` that scans the team’s past matches (across seasons/leagues) to compute outcome distributions for the next match after a given streak pattern. This demonstrates we can efficiently scan chronological match histories and compute conditional outcome stats.
- The frontend already renders new streak insights (`homeStreakInsight`, `awayStreakInsight`) in `match-analysis.component.ts`. Extending the payload with one pair-level tag plus a small aggregated summary object is straightforward.
- Data needed for pair tagging is already present at analysis time: we have the home and away current streak patterns (e.g., `3W` vs `2W`) and their types (letters). We only need a simple comparison to assign `same_streak` or `different_streak`.

Conclusion: The codebase provides the necessary building blocks. We can add a pair-level tagging and compute conditional historical summaries in a controlled way with feature-flag guarding.

---

## 2) What needs to change (modules/dependencies)?

Backend (Java / Spring Boot):
- DTOs:
  - Extend `MatchAnalysisResponse` with a minimal, non-breaking, optional field set:
    - `pairStreakTag`: enum-like string `"same_streak" | "different_streak" | "unknown"`.
    - `pairStreakSummary`: object with metrics computed historically under that tag. Suggested minimal fields:
      - `sampleSize` (int)
      - `homeWinPct`, `drawPct`, `awayWinPct` (ints)
      - `over25Pct`, `over15Pct`, `over35Pct`, `bttsPct` (ints)
      - Optional: `notes` (string)
  - Alternatively create a small DTO `PairStreakSummary` referenced by `MatchAnalysisResponse`.
- Service:
  - `MatchAnalysisService`:
    - Derive current streak type for each team from `H2HSummary.homeForm/awayForm.currentStreak` (the last-5 domestic-context already computed) by extracting the trailing letter (W/D/L). If either missing, mark as `unknown`.
    - Assign `pairStreakTag` accordingly.
    - Compute `pairStreakSummary` by scanning historical matches and filtering pre-match contexts where both teams had current streaks with matching letter (for `same_streak`) or different letters (for `different_streak`). Two pragmatic computation options:
      1) On-the-fly scan at request time (bounded window to protect performance) using existing repositories.
      2) Precompute/caching layer keyed by team IDs + tag type (recommended if usage is frequent).
- Repository:
  - We can reuse existing repository methods to retrieve recent played matches for each team. For pair-conditioned history, we do not need head-to-head; we need each team’s pre-match streak state before a match, then classify that match’s pair tag. We’ll likely compute from each team’s own sequence and then join contexts around the same match date only when they faced some opponent; given complexity, we’ll scope to per-team distributions conditioned on its streak and the opponent’s streak type when available. See Risks/Approach.
- Feature flag:
  - Add to `FeatureFlags` class (already injected in `MatchAnalysisService` as `featureFlags`): `isPairStreakTaggingEnabled()` defaulting to true/false as you prefer for rollout.

Frontend (Angular):
- Types:
  - Extend `MatchAnalysisResponse` interface in `match-analysis.service.ts` with optional fields `pairStreakTag?: 'same_streak' | 'different_streak' | 'unknown'` and `pairStreakSummary?: { ... }`.
- UI:
  - In `match-analysis.component.ts`, add a small card below existing “Streak Insight Summary” panel to display:
    - Tag badge (Same Streak vs Different Streak)
    - Sample size and outcome/goal metrics.
  - Make this section fully optional (hidden if backend doesn’t provide it).

Docs:
- Add this feasibility report (done).

No external dependencies beyond current stack are needed. All required data is available via existing entities and repositories.

---

## 3) Potential risks and mitigations

1) Performance with large history (150k+ matches):
   - Risk: On-demand pair-conditioned scans could be heavy if implemented as cross-join of two teams’ entire histories.
   - Mitigation:
     - Limit historical depth (e.g., last N seasons or last 500 played matches for each team).
     - Compute per-team pre-match streak states once per scan pass (single forward pass as already done in `computeStreakInsight`).
     - For pair-level summary, use a simplified approximation: classify by comparing the current team’s streak type with the opponent’s pre-match streak type at the time of their meeting (only when they actually played each other), i.e., restrict to head-to-head contexts first. This yields a small but directly relevant dataset and is fast (H2H count is small).
     - If broader sample size is required (beyond H2H), compute aggregate baselines separately for “opponent on W/D/L” buckets using cached tables or precomputation triggered nightly.
     - Add a feature flag and an upper bound on computations.

2) Data consistency across leagues/seasons:
   - Risk: Cross-competition comparisons may mix contexts (domestic vs international), influencing streak behavior.
   - Mitigation:
     - Reuse the domestic-context rule already established for last-5: when teams come from different leagues, compute their current streak from their domestic league; for history, annotate the context used; optionally expose a label “domestic streak context.”
     - Offer a simple toggle in code to restrict summaries to domestic matches only or allow all.

3) UI stability:
   - Risk: New fields could be undefined on older cached payloads or during rollout.
   - Mitigation:
     - Mark new fields optional; null-check in templates.
     - Maintain backward compatibility in DTOs.

4) Statistical validity / small samples:
   - Risk: `same_streak` or `different_streak` buckets may be small or skewed for certain teams or leagues.
   - Mitigation:
     - Show sample size and subtle “low sample” hint under thresholds (e.g., < 20).
     - Fade the visual emphasis or omit aggressive recommendations when sample is low.

5) Complexity creep:
   - Risk: Full pair-conditioned history (not just H2H) could be complex to compute on the fly.
   - Mitigation:
     - Phase the implementation:
       - Phase A: H2H-only conditioned summaries (very fast; small sample but directly relevant).
       - Phase B: Broader per-team baseline vs opponent-streak-type using cached precomputation.

---

## 4) Proposed safe, step-by-step approach

Phase A — Minimal viable, H2H-conditioned tagging and summary (1–2 days)
1. Backend DTO additions (optional properties):
   - Add `pairStreakTag` and `pairStreakSummary` to `MatchAnalysisResponse`.
2. Tag computation:
   - Extract `homeForm.currentStreak` and `awayForm.currentStreak` from the already computed forms.
   - Derive types (W/D/L). If both present and equal → `same_streak`; if both present and differ → `different_streak`; else `unknown`.
3. H2H-conditioned summary:
   - Retrieve H2H last-N matches (already available as `h2hSummary.matches`). For each H2H match in chronological order, reconstruct pre-match streak types for both sides using a lightweight pass similar to `computeFormLastFive` but limited to the match’s immediate pre-context (we already compute PPG series and can extend to track streak types). Classify each H2H match into `same` or `different` bucket based on those pre-match types, then aggregate next-outcome stats for that match (result, BTTS, O/U thresholds).
   - This yields pair-conditioned stats strictly for these two teams historically, which is fast and avoids cross joins.
4. Feature flag + limits:
   - Guard with `featureFlags.isPairStreakTaggingEnabled()`.
   - Cap to last N H2H matches (e.g., 20) to ensure speed.
5. Frontend:
   - Add optional card, render tag and summary with null checks.
6. QA:
   - Verify on a few known pairs across leagues; test both same and different cases; confirm fallbacks when unknown.

Phase B — Broader baseline (optional; scheduled batch or cached on-demand) (2–4 days)
1. Compute per-team distributions vs opponent’s streak type buckets (opponent on W, D, or L pre-match) across recent seasons, independent of the specific opponent.
2. When H2H sample is small, blend H2H-conditioned summary with this broader baseline using weights by sample size.
3. Cache results by teamId and refresh periodically (e.g., nightly job via existing scheduling).

---

## 5) Blockers / Gaps
- None critical for Phase A. All building blocks (repositories, in-memory passes, DTOs, feature flags, frontend rendering) exist.
- For Phase B, we would benefit from a small cache table or an in-memory Caffeine/Guava cache. Currently not mandatory.

---

## 6) Implementation details to watch
- Streak parsing: `currentStreak` strings are like `3W`. Extract last char for type; handle `"0"` or null as unknown.
- Context consistency: The same domestic-context rule that feeds `homeForm`/`awayForm` should be used when forming the current tag so the tag accurately reflects what the user sees elsewhere in the UI.
- Timezone: Already set to Africa/Nairobi at startup; no changes needed.
- Backward compatibility: Keep all new fields optional in JSON to avoid breaking old cached payloads or other consumers.

---

## 7) Estimated effort
- Phase A (pair tag + H2H-conditioned summary + UI card + feature flag): ~1–2 dev days.
- Phase B (baseline vs opponent streak-type buckets + caching/blending): ~2–4 dev days.

---

## 8) Acceptance criteria (Phase A)
- When analyzing a match in the Match Analysis page, the payload includes `pairStreakTag` with values `same_streak`, `different_streak`, or `unknown`.
- If historical H2H data exists, `pairStreakSummary.sampleSize > 0` and shows realistic percentages; otherwise, it is omitted or shows `sampleSize = 0` with graceful UI fallback.
- Frontend renders a small, optional card showing the tag and percentages without breaking existing content.
- Feature can be toggled via feature flag.

---

## 9) Rollout plan
- Enable in staging with logs enabled for performance timing (per-request compute time for pair summary).
- Monitor typical latency increment. If >50–100 ms per request in average cases, reduce H2H window further or add caching.
- Enable in production behind the feature flag; progressively roll out by league if needed.

---

## 10) Conclusion
This feature is implementable now with minimal, safe changes. Start with Phase A (H2H-conditioned approach) to deliver immediate value and low risk, then optionally iterate to Phase B to improve coverage with blended baselines.

# Played Matches – Phase 1 Predictive-Support Verification (2025-09-10)

This note summarizes verification for the Phase 1 predictive-support features surfaced in the Played Matches tab.

Feature flag used to gate all new UI/logic: `predictive.h2h.phase1.enabled` (default true in application.yml).

## 1) Goal Differential Summary (GD)
- Backend: H2HService computes `GoalDifferentialSummary` (aggregate, average, perMatchGD, insufficient flag) respecting feature flag.
- Frontend (Played Matches tab):
  - KPI card shows Aggregate GD (signed) and Average GD.
  - Warning shown when fewer than 3 valid matches.
  - GD is computed client-side from the fetched H2H matches to avoid introducing new API dependencies on this tab.
  - Visibility gated by feature flag via ConfigService.
- Result: Displays correctly; warning appears when <3 valid H2H matches. Hidden when flag is OFF.

## 2) Form & Streaks (Last 5)
- Backend: New DTO `FormSummary` { recentResults, currentStreak, winRate, pointsEarned, ppgSeries } and GET `/api/matches/form/by-name` (flag-aware).
- Frontend: Two cards (Home/Away) render badges (W=green, D=gray, L=red), streak text (e.g., "3W in a row"), win rate, and points. Shows “Insufficient data.” when <5.
- Result: Colors and values render as expected; fetch only occurs if flag ON. Hidden when flag is OFF.

## 3) Momentum / PPG Trend
- Backend: `ppgSeries[]` included in `FormSummary` (cumulative PPG, most recent first).
- Frontend: CSS sparkline (tiny bars; scale 0–3 PPG) and text fallback "PPG Trend: S → E" displayed when series exists. Gated by flag.
- Result: Renders correctly; fallback text present. Hidden when flag is OFF.

## 4) Combined Insights Panel
- Backend: `insightsText` added to `MatchAnalysisResponse`; H2HService provides `generateInsightsText()` (flag-aware) combining GD, streaks, PPG trends for general usage.
- Frontend (Played Matches tab): Builds insights text client-side from the same ingredients already loaded on this tab (GD client computation + `/form/by-name`). Shows “Limited match history available.” if no parts. Gated by flag.
- Result: Renders correctly and updates with H2H selection. Hidden when flag is OFF.

## 5) Feature Flag Integration
- Backend: `FeatureFlags` bean reads `predictive.h2h.phase1.enabled`. Services and controllers short-circuit when OFF.
- Frontend: `ConfigService` fetches `/api/config/flags` and gates predictive UI + requests.
- Manual toggle verification: Setting the property to `false` hides GD card, Form/Streaks, PPG, and Insights panel; `/form/by-name` returns empty summary.

## Fixes/Adjustments Made During Verification
- Added feature-flag checks in backend: H2HService (GD, insights), MatchController (/form/by-name), and MatchAnalysisService (attach GD/Form/insights only when ON).
- Added ConfigController and ConfigService to expose and consume flags.
- Gated Played Matches tab predictive UI and avoided predictive calls when flag OFF.
- Added small UI helpers and styles (badges, sparkline).
- Added backend unit tests:
  - H2HServiceFeatureFlagTest: flag OFF/ON behaviors, GD edge cases.
  - MatchControllerFormFlagTest: flag OFF returns empty; ON computes form + PPG series.

## Final Status
All Phase 1 predictive-support features (GD, Form, PPG Trend, Insights) are visible and functional in the Played Matches tab when the feature flag is ON, and correctly hidden/disabled when OFF. Values and colors render correctly and align with backend data/logic.

## How to Toggle
- Edit backend/src/main/resources/application.yml:

```
predictive:
  h2h:
    phase1:
      enabled: true|false
```

- Restart backend. Frontend fetches flags at runtime on page load.

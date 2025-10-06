# ChambuaViSmart Automated Predictions Workflow Guide

Date: 2025-10-01

---

## 1) Automation Objective

- Goal: Convert any fixture click (upcoming, ongoing, or finished) into an automated, end-to-end prediction analysis that fuses the Fixture Analysis and Streak Insight tabs and outputs a single system-generated recommendation.
- Trigger: User clicks a fixture from any of the following entry points:
  - Fixtures Tab
  - Home → Today’s Fixtures Tab
  - Past Days Tab
- Sequencing & Integration:
  1) Launch Fixture Analysis pipeline (H2H, recent form, league context, scoring trends, correct score ladder where applicable).
  2) Automatically fetch Streak Insight for both teams (current streak pattern and historical “what happens next” distribution).
  3) Fuse results: overlay/merge pattern-conditioned percentages with fixture-context signals.
  4) Deliver a Final Recommendation: Outcome lean (1X2), Correct Score shortlist, BTTS, Over/Under, and a confidence score based on alignment between tabs and sample sizes.

---

## 2) Workflow Design

### Navigation Flow (Step-by-step)
- Step 0: User clicks fixture F from any entry point.
- Step 1: Frontend fires a single “fixtureSelected(FixtureId, HomeId, AwayId, LeagueId, SeasonId, KickoffUtc)” event.
- Step 2: Router navigates to a dedicated Analysis Route, e.g., `/analysis?fixtureId=...&leagueId=...&seasonId=...`.
- Step 3: Analysis Route orchestrates two parallel/linked tasks:
  - Task A: Fetch Fixture Analysis data.
  - Task B: Fetch Streak Insight for Home and Away.
- Step 4: As soon as both are ready (or time out gracefully), compose the Final Recommendation and display the Recommendation Card at the top of the Analysis view.
- Step 5: The Analysis view exposes two tabs below the Recommendation Card: “Fixture Analysis” and “Streak Insight” for detailed exploration.
- Step 6: Back/Forward works across the entry→analysis→details journey by preserving query params and cached payloads.

### Visual Flow (ASCII)

- User Clicks Fixture
  -> Route: /analysis?fixtureId=...&leagueId=...&seasonId=...
    -> Load Shell (Recommendation Card placeholder + Tabs)
      -> Parallel Loads:
         - Fixture Analysis API
         - Streak Insight API (Home, Away)
      -> Merge & Score
      -> Render Recommendation Card (1X2, CS shortlist, BTTS, O/U, Confidence)
      -> Tabs: [Fixture Analysis] [Streak Insight]

### Back/Forward Navigation
- Browser back from Analysis returns to the originating list (Fixtures/Home Today/Past Days) with scroll and filters preserved.
- Within Analysis, switching tabs does not alter the URL path; only the hash or `tab=` query parameter changes.
- Deep links: `/analysis?fixtureId=...&tab=streaks` opens with Streak Insight initially selected while still showing the Recommendation Card.

---

## 3) Automation Mechanics

### Events/Triggers (on Fixture Click)
- `onFixtureClick(fixture)` dispatches an Analytics/UX event and navigates to Analysis.
- The Analysis component’s `ngOnInit`/`useEffect` parses params and fires a single Orchestration action `ANALYZE_FIXTURE`.

### Backend Orchestration (APIs and Order)
- API Calls (can be parallelized):
  1) Fixture Analysis API: `/api/analysis/fixture?fixtureId=...&leagueId=...&seasonId=...`
     - Returns: H2H, recent form, market-aligned rates (BTTS/O-U), optional Poisson correct-score ladder and 1X2 distribution, data quality flags.
  2) Streak Insight API (Home): `/api/streaks/insight?teamId=...`
  3) Streak Insight API (Away): `/api/streaks/insight?teamId=...`
- Optional batching: `/api/streaks/insights?teamIds=homeId,awayId` to reduce latency.
- Caching:
  - Short-term (e.g., 5–15 minutes) in-memory/Redis cache keyed by fixtureId and teamId.
  - Pre-warm cache for fixtures visible on the list page (prefetch on scroll/viewport).
- Timeouts & Fallbacks:
  - If one source is slow, show partial card with a loading chip for the missing section.
  - If Streak instances are below threshold, apply caution flags and lower weight in fusion.

### UI Integration
- Automatic Redirects: Entry points navigate to the unified Analysis view.
- Tabs Switching: Controlled by `tab` param or UI state; keeps the Recommendation Card docked at the top.
- Overlay Chips: Streak percentages (with sample sizes) appear as chips over the relevant market panels (1X2, BTTS, O/U) within the Fixture Analysis tab when toggled on.
- Loading States:
  - Skeletons for Recommendation Card and each tab.
  - Inline status badges (e.g., “Fetching streaks…”, “Using cached form data”).

---

## 4) Final Recommendation Output

Each Recommendation Card contains:
- Match Outcome Lean (1X2): Home/Draw/Away with percentage or odds-style display and a textual rationale snippet.
- Correct Score Shortlist: 2–4 likely scores with relative ranking.
- BTTS Recommendation: Yes/No/Lean with rationale chips.
- Over/Under Recommendation: Primary line (usually 2.5) plus secondary lines if strong signals exist.
- Confidence Score (0–100): Computed from alignment between Fixture Analysis and Streak Insight, streak instances sample size, and data quality flags.
- Explainability:
  - Top 2–3 factors highlighted (e.g., “Home 3W pattern historically yields 58% next W; Fixture Analysis shows Home xGA suppressed; H2H at venue: Home unbeaten in 5”).

Suggested Layout:
- Card Header: Teams, kickoff, venue, league.
- Left Column: 1X2 lean + confidence meter.
- Right Column: BTTS and Over/Under chips.
- Bottom Row: Correct Score shortlist with small ladder indicators.
- Actions: “Export Report (.md/.pdf)”, “Copy rationale”, “Open tabs”.

---

## 5) Optimization Considerations

- Sparse Data Handling:
  - If streak `instances < 30`, label as Low Sample (red); `30–99` Medium (amber); `>= 100` High (green).
  - Downweight low-sample streaks in confidence computation.
- Conflicting Signals:
  - If Streak and Fixture Analysis disagree strongly, include a divergence warning and lower confidence.
  - Provide an info tooltip showing side-by-side metrics.
- Transparency (“Why”):
  - Show a compact rationale stack with the exact metrics used and their weights.
  - Expose a link: “View details in Fixture Analysis”/“View details in Streak Insight”.
- Performance:
  - Prefetch data for fixtures rendered on list screens.
  - Cache merged recommendations for each fixture; invalidate on lineup news tick or time-based expiry.
  - Use HTTP conditional requests (ETag/If-None-Match) where possible.

---

## 6) Example User Journey

- User clicks Fixture A on Today’s Fixtures.
- The Analysis view opens immediately with a skeleton Recommendation Card.
- In the background:
  - Fixture Analysis loads H2H, recent form, and Poisson ladder.
  - Streak Insight fetches Home (current 3W pattern) and Away (current 2L pattern).
- Fusion:
  - Home 3W → next W 58%, O2.5 44%, BTTS 48% (instances=112, green).
  - Away 2L → next L 52%, BTTS 43% (instances=87, amber).
  - Fixture Analysis: Home xGA low, H2H venue advantage, recent total goals ~2.2.
- Recommendation Card populates:
  - 1X2 Lean: Home Win (Confidence 72).
  - Correct Scores: 1-0, 2-0, 2-1.
  - BTTS: No (lean).
  - Over/Under: Under 2.5 (lean), note that secondary O1.5 still ~70%.
- User taps the Streak Insight tab to read narrative; then navigates back to the Recommendation Card which remains at the top.
- Presses Export → saves a single-page PDF including both tabs’ summaries and the card.

---

## 7) Summary Recommendations

- Build an end-to-end automated flow that routes all fixture clicks to a unified Analysis view which orchestrates both Fixture Analysis and Streak Insight in parallel.
- Keep UX one-click: clicking a fixture auto-runs analysis; Recommendation Card appears without extra user inputs.
- Maintain transparency: show sample sizes, divergence flags, and key factors driving the call.
- Optimize performance with prefetching and caching; batch streak lookups for the Home and Away teams.
- Treat the Recommendation Card as the “single source of truth” summary; allow deep dives via tabs without losing context.

---

## Appendix A: Confidence Scoring (Illustrative)

- Base confidence from Fixture Analysis quality (0–60):
  - Data completeness, recency, opponent quality normalization, model availability.
- Streak Alignment Bonus (0–25):
  - Add points for agreement proportional to streak instances bucket (Low 0–3, Medium 4–10, High 11–25).
- Consistency & Risk Adjustments (−15 to +15):
  - Penalize strong divergences; penalize very low samples; small bonus for high H2H freshness.

---

## Appendix B: API Sketches (Non-binding)

- GET `/api/analysis/fixture`
  - Query: fixtureId, leagueId, seasonId
  - Returns: { h2h, recentForm, marketRates: {btts, o15, o25, o35}, poisson: {enabled, ladder, oneXtwo}, flags }

- GET `/api/streaks/insights`
  - Query: teamIds
  - Returns: [{ teamId, teamName, pattern, instances, nextWinPct, nextDrawPct, nextLossPct, over15Pct, over25Pct, over35Pct, bttsPct, summaryText }]

---

## Appendix C: UI State Diagram (Text)

States: Idle → Loading → PartiallyReady → Ready → Error
- Idle: Waiting for params.
- Loading: Awaiting both APIs.
- PartiallyReady: One API complete; card shows partial data and a loading chip for the missing part.
- Ready: Both complete; card + tabs active.
- Error: Show retry and the most recent cached result if available.

# Home Page Fixtures – Team Names Visibility Investigation Report

Date: 2025-09-23 06:17 (EAT)
Author: Junie (JetBrains Autonomous Programmer)
Scope: ChambuVS Frontend (Angular) + Fixtures API Integration

## Executive Summary
- Symptom: On the Home page, today’s fixtures render many cards where home/away team names are blank, leaving only “vs” and a working “Details” link. The counter (e.g., 38) can still show a large number of fixtures.
- Observed Behavior: Clicking “Details” navigates correctly to the Fixture Analysis (Played Matches Summary) page—so routing works, but the visible team name labels are empty.
- Prior Errors: Repeated Angular error `NG02100` (seen in console stacks) occurred earlier, typically arising when a pipe (e.g., DatePipe) receives invalid inputs or during an interrupted change detection cycle. We added a defensive `toSafeDate` for the Home page date rendering. However, the visibility issue persists.
- Likely Root Causes:
  1) Data normalization and rendering divergence: filtering/norming happens in a getter (`todayFlat`) using `getTeamName`, while the template renders using a separate resolver `displayTeamName`. If the two paths diverge, the template can print empty labels even when the getter filtered differently.
  2) Getter-based recomputation: `todayFlat` recomputes a new array each change detection cycle, which can interact poorly with transient errors (earlier `NG02100`) and lead to inconsistent UI states.
  3) Payload variability: Backend fixtures may use different shapes/keys for team names (camelCase, snake_case, nested). Although we added robust resolvers, discrepancies between the normalization path and template path still exist.
  4) Date/time parsing interruptions: If any DatePipe or date parsing rethrows during CD, it can bail out rendering mid-pass, leaving partially populated DOM where only static pieces (like the literal ‘vs’ text) remain visible.
- Why “Details” works: The click is bound to `goToAnalysis(...)` which executes even when the visible labels are empty. It uses `displayTeamName`/fallbacks to build query params, and the router navigates. Thus navigation is not coupled to the problem of visible labels.

## What Works
- Navigation: Sidebar interactions and the “Details” link correctly route to the Fixture Analysis page.
- The page theme and layout: Cards, colors, and responsiveness remain intact.
- Data retrieval: Today’s fixtures are fetched via `FixturesService.getFixturesByDate`, and counts/badges update.

## What Appears Broken or Fragile
- Team name labels on many fixture cards render as empty strings.
- The list may include placeholder-like entries that show only “vs”, indicating that either:
  - entries with empty names passed the filter, or
  - the template’s display resolver returns empty while the filter’s resolver returned non-empty during a previous pass.

## Evidence and Console Clues
- Console logs (prior):
  - `[LeagueContext] Initialized with saved leagueId: <id>`
  - Multiple `ERROR ze: NG02100` stack traces pointing at `DatePipe` usage (`f.transform`) and change detection internals. These usually indicate a pipe receiving invalid arguments or a thrown error during CD.
- User observation: Even after backend date serialization fix, team names are still not visible.

## Frontend Code Path Analysis (Home)

Files of interest:
- `frontend/src/app/home/home.component.ts`
- `frontend/src/app/home/home.component.html`
- `frontend/src/app/home/home.component.css`

Key parts:
1) Data source
   - `loadToday()` calls `fixturesApi.getFixturesByDate(this.todayIso, this.season)` and stores in `todayFixtures`.

2) Getter-based flattening and filtering
   - `get todayFlat()` iterates `todayFixtures` and:
     - Derives home/away names via `getTeamName(f, 'home'|'away')`.
     - Skips entries with empty names (`if (!home || !away) continue`).
     - Filters to only fixtures that occur “today” in Nairobi (`isSameDayInTz`).
     - Normalizes fixture object: `{ ...f, homeTeam: home, awayTeam: away }`.
     - Sorts by kickoff.
   - This returns a fresh array on every change detection pass.

3) Template rendering
   - Renders `*ngFor="let item of todayFlat"` (re-evaluates getter every CD).
   - Displays team names using `displayTeamName(item.fixture, 'home')` and `'away'`.
   - Displays times via `toSafeDate(item.fixture.dateTime) | date:...`.

4) Name resolution duplication
   - There are two resolvers: `getTeamName` (used in filter path) and `displayTeamName` (used in render path). They are similar, but not guaranteed identical and may diverge on edge cases/nested shapes.

5) Navigation
   - “Details” click uses `goToAnalysis({ leagueId, leagueName }, item.fixture)`.
   - It composes query params using `displayTeamName(...) || f?.homeTeam || ''`. So navigation works even if visible labels are empty—router does not depend on the visible text nodes.

6) Styling
   - CSS explicitly sets `.fixture-teams .team { color:#e0e0e0; font-weight:700; font-size:16px; }`, so pure CSS invisibility is unlikely now.

## Hypotheses Explaining Blank Names
1) Divergent resolution paths
   - The array filter/normalization (`getTeamName`) differs from what the template prints (`displayTeamName`). If `displayTeamName` fails a case that `getTeamName` handled, the template will show empty text while the filter allowed the item.

2) Getter recomputation + CD timing
   - `todayFlat` recomputes arrays each CD cycle. If any expression in the template (e.g., DatePipe or function calls) throws or returns transiently invalid values, Angular may partially render or skip updates, leaving “vs” visible (it’s a static span), while the name text nodes remain empty.

3) Payload heterogeneity
   - Mixed backend sources or ingest paths might produce fixtures where team fields appear as nested objects or under alternate keys. The two resolvers handle slightly different sets of keys—gaps can lead to mismatches.

4) Hidden whitespace / normalizing conditions
   - If names contain only whitespace or unusual unicode characters, `.trim()` may produce empty strings. The filter may pass non-empty raw values, but after trimming or different path, render returns empty.

## Why "Details" Still Navigates
- The click binding exists and is independent of the visible labels. The handler composes query params from `displayTeamName(...)` or `f.homeTeam`, but even if those are empty strings, Angular router still navigates to `/played-matches-summary` with empty h2h parameters or the page’s own fallbacks. Hence, the navigation reliably works despite empty UI labels.

## Recommended Approach (Minimal, Low-Risk, Frontend-First)

1) Unify team name normalization (single source of truth)
   - Normalize team names once upon data load (in `loadToday()` after fetching), produce a stabilized array (e.g., `todayFlatData: FlatItem[]`).
   - Replace the getter `todayFlat` with this stabilized computed property so change detection does not re-create arrays each tick.
   - Store normalized fields directly on the fixture objects used by the template (e.g., `fixture.homeTeam`, `fixture.awayTeam`).
   - Update the template to bind directly to `item.fixture.homeTeam` / `awayTeam` (no render-time resolver).

2) Add `trackBy` for *ngFor
   - Implement `trackByFixtureId(index, item)` returning `item.fixture.id` (or a composite key). This avoids unnecessary DOM diffs and stabilizes rendering.

3) Harden date handling
   - Keep `toSafeDate(...)` guarding DatePipe.
   - Consider preformatting date labels in the computed array to remove all pipes from the hot path.

4) Instrumentation for verification
   - Temporarily log how many items enter the pipeline, how many are filtered out for empty names, and sample items with their resolved `homeTeam/awayTeam`.
   - In dev builds, assert that display fields are non-empty before rendering.

5) Validate backend payloads
   - Inspect a sample from `/api/fixtures/by-date?date=YYYY-MM-DD` in the browser Network tab, checking the exact shape of team fields for problematic entries. Ensure they’re mapped consistently to strings.

6) Only if needed: backend normalization
   - If payloads contain nested team structures, consider normalizing in `FixtureDTO.from(...)` (or in the service layer) to always emit string `homeTeam`/`awayTeam`.

## Acceptance Criteria
- Home page “Today’s Fixtures” shows correct, non-empty team names for all items that have valid team data in the API response.
- “vs” placeholder appears only when a fixture truly lacks names in the payload (and such entries should be excluded from the list or show a clear placeholder state with league/round).
- No `NG02100` errors in the console while viewing the Home page.
- “Details” still navigates correctly with `h2hHome` and `h2hAway` present in URL query parameters for valid entries.
- Fixture counts (badge) match the number of rendered cards after filtering invalid entries.

## Step-by-Step Diagnostic Checklist (Before Code Changes)
1) Open DevTools → Network → Inspect `/api/fixtures/by-date?...` response. Confirm for blank cards whether `homeTeam`/`awayTeam` are present as strings, alternate keys, or nested objects.
2) Add temporary console logs in `loadToday()` to print a sample of normalized items and the number filtered for empty names.
3) Verify `toSafeDate(...)` returns non-null for a random sample of items and that DatePipe shows correctly.
4) Temporarily bind template to `item.fixture.homeTeam`/`awayTeam` to see if normalized names appear, bypassing `displayTeamName`.
5) Add `trackBy` to the `*ngFor` and confirm that repeated render cycles do not re-create DOM and that names stay visible.

## Implementation Plan (Phased)
- Phase 1 (No backend changes):
  - Compute `todayFlatData` once in `loadToday()` after response arrives: map, filter, and normalize team names and date labels.
  - Replace template `*ngFor="let item of todayFlat; trackBy: trackByFixtureId"` and bind directly to normalized `item.fixture.homeTeam` / `awayTeam`.
  - Remove `displayTeamName` calls from the hot path; keep a single normalization function reused during computation.
  - Keep `toSafeDate` or preformat date strings.

- Phase 2 (Stabilization):
  - Add console diagnostics (guarded by environment) to compare backend payload vs. computed names for a few items.
  - Adjust normalization to cover any remaining edge cases discovered.

- Phase 3 (Optional backend polish):
  - If payloads are inconsistent, harmonize them in the backend DTO mapping so frontend receives consistent strings.

## Risks and Mitigations
- Risk: Changing iteration source could affect ordering or counts.
  - Mitigation: Preserve sort logic and existing filters; add unit tests/dev assertions.
- Risk: Navigation params might rely on old resolvers.
  - Mitigation: Use normalized `homeTeam`/`awayTeam` for building query params too.
- Risk: Hidden regressions on Fixtures page.
  - Mitigation: Changes confined to Home component; Fixtures tab already renders strings from API.

## Rollback Strategy
- Since changes are localized to the Home component presentation pipeline, rollback is as simple as reverting the Home component TS/HTML changes to the previous commit.

## Appendix – Relevant Code References (Summarized)
- Rendering:
  - Template binds: `*ngFor="let item of todayFlat"` and `{{ displayTeamName(item.fixture,'home') }}`.
- Filtering/Normalization:
  - Getter `todayFlat`: filters by non-empty `getTeamName` and same-day check; normalizes fields on spread copy.
- Date Safety:
  - `toSafeDate(input)` to prevent `NG02100` in DatePipe.
- Navigation:
  - `goToAnalysis(...)` uses `displayTeamName`/fallbacks to build router query params; independent of visible labels.

---

This report documents the current investigation, likely root causes, and a conservative remediation plan centered on unifying normalization and stabilizing change detection. Once approved, we can implement Phase 1 with minimal code changes in the Home component and validate via the acceptance criteria above.
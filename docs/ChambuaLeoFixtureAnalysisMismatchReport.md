# Chambua-Leo Fixture Analysis Mismatch Report

Commit scope: docs/chambua-leo-mismatch-investigation
Date: 2025-09-16
Author: Junie (JetBrains Autonomous Programmer)

Summary
- Problem: The new Chambua-Leo batch feature (/api/fixture-analysis/batch) now outputs the correct fields for all fixtures, but the probabilities and xG differ from the Fixture Analysis tab’s single-fixture outputs (Match Analysis).
- Scope: Diagnostic only. No code changes. This report documents expected vs. actual values, traces the batch vs. single paths in code, highlights data flow differences (IDs, season, caching), analyzes calculation differences (Poisson/xG, W/D/L, markets), and identifies the root cause. Recommendations and SQL checks are provided.

---

1) Expected vs. Actual Output

Example: Verl vs Alemannia Aachen (2025-09-16)
- Chambua-Leo (batch) output (observed):
  - W/D/L: 27/20/53%
  - BTTS: 73%
  - Over 1.5 / 2.5 / 3.5: 91% / 76% / 57%
  - xG: 1.67 / 2.33
  - Correct scores: [included]
  - Notes: “Based on recency-weighted form (home/away splits) and Poisson model. Window=5; … fallback comps …”
- Fixture Analysis tab (single) expected output (observed):
  - W/D/L: 12/25/63%
  - BTTS: 59%
  - Over 1.5 / 2.5 / 3.5: 79% / 56% / 34%
  - xG: 1.61 / 1.33
  - Correct scores: [n/a in tab]

Observed differences
- W/D/L: Batch is less draw-heavy and more balanced towards away than the tab. Draw 20% vs 25% (tab). Away 53% vs 63% (tab). Home 27% vs 12% (tab).
- Over-goals and BTTS: Batch substantially higher (91/76/57 and BTTS 73) vs tab (79/56/34; BTTS 59).
- xG: Batch 1.67/2.33 vs tab 1.61/1.33 — away λ especially higher in batch; tab’s away λ is much lower.
- Notes: Batch notes solely cite recency-weighted form + Poisson; tab uses additional constraints and blending (H2H, league adjustments, draw bias).

Conclusion from outputs: The two paths are applying different modeling assumptions and inputs, not merely formatting.

---

2) Implementation Review

2.1 FixtureAnalysisService.analyzeFixture (Batch path)
File: backend/src/main/java/com/chambua/vismart/service/FixtureAnalysisService.java
Key logic
```
// form rows (N=5, overall scope)
List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, seasonId, 5, FormGuideService.Scope.OVERALL);
// resolve by team names (IDs are optional/null)
FormGuideRowDTO home = byName.get(hn.toLowerCase());
FormGuideRowDTO away = byName.get(an.toLowerCase());

// λ derivation (home splits blended with opponent defense; away splits blended with opponent defense)
double homeGFh = nz(home.getWeightedHomeGoalsFor(), nz(home.getAvgGfWeighted(), 1.3));
double homeGAh = nz(home.getWeightedHomeGoalsAgainst(), nz(home.getAvgGaWeighted(), 1.1));
double awayGFw = nz(away.getWeightedAwayGoalsFor(), nz(away.getAvgGfWeighted(), 1.2));
double awayGAw = nz(away.getWeightedAwayGoalsAgainst(), nz(away.getAvgGaWeighted(), 1.2));

double lambdaHome = clamp(mean(homeGFh, awayGAw), 0.2, 3.5);
double lambdaAway = clamp(mean(awayGFw, homeGAh), 0.2, 3.5);

// Poisson grid → W/D/L, BTTS, Over 1.5/2.5/3.5, correct scores
gp = deriveAll(lambdaHome, lambdaAway);

// Notes: "Based on recency-weighted form (home/away splits) and Poisson model. Window=5; ..."
```
Characteristics
- Deterministic Poisson model solely from form-derived λ with simple shrinkage defaults (1.3, 1.1, 1.2).
- No H2H blending, no explicit league-level adjustment, no draw bias from PPG, no caching usage.
- Team resolution by name within form rows; throws if not found.
- Logs example:
```
[FixtureAnalysis][Response] home={} away={} durMs={} wdl={}/{}/{} btts={} over15={} over25={} over35={} xg={}/{}
```

2.2 BatchAnalysisCoordinator (Batch orchestration)
File: backend/src/main/java/com/chambua/vismart/service/BatchAnalysisCoordinator.java
Key logic
```
List<Fixture> fixtures = fixtureService.getFixturesByDate(date, null);
Long seasonId = seasonResolutionService.resolveSeasonId(league.getId(), league.getSeason()).orElse(null);
MatchAnalysisResponse resp = fixtureAnalysisService.analyzeFixture(
  league.getId(), seasonId, null, f.getHomeTeam(), null, f.getAwayTeam(), refresh);
```
Characteristics
- Batch explicitly resolves seasonId via SeasonResolutionService (fallbacks to latest season for the league when league.season is unresolved).
- Passes names not IDs to FixtureAnalysisService.
- refresh flag is forwarded but unused by FixtureAnalysisService.

2.3 Fixture Analysis tab (Single) path
Controller: backend/src/main/java/com/chambua/vismart/controller/MatchAnalysisController.java
- Endpoint: POST /api/match-analysis/analyze
- Validates leagueId/seasonId; resolves team IDs by names and aliases if needed (TeamRepository, TeamAliasRepository), then calls:
```
matchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, leagueName, homeName, awayName, refresh)
```

Service: backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java
Highlights (selected excerpts)
- Caching path:
```
if (!refresh && seasonId == null && leagueId != null && homeTeamId != null && awayTeamId != null) {
  cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(...)
}
```
- W/D/L from PPG with draw bias and cap:
```
// compute split PPG (weighted-home for home; weighted-away for away)
if (!haveHome || !haveAway) { home=40; draw=20; away=40; } else {
  double total = homePpg + awayPpg;
  if (total <= 0) { home=33; draw=34; away=33; }
  else {
    home = round(homePpg/total * 100 * 0.75);
    away = round(awayPpg/total * 100 * 0.75);
    draw = 100 - (home + away);
  }
}
```
- BTTS/Over2.5 from form split percentages with fallbacks:
```
btts = avg(hBttsEff, aBttsEff)
over25 = avg(hOv25Eff, aOv25Eff)
```
- Head-to-Head blending with adaptive alpha and league-family lookup; also computes H2H BTTS/Over 2.5 context and PPG:
```
// retrieves h2h via IDs or name/alias sets; may scope by seasonId or across league family
// computes h2h-derived W/D/L; blends into final with alpha depending on window/quality
```
- League adjustment and explainability metadata (h2hAlpha, leagueAdjustment, samples); logs:
```
[MatchAnalysis][Response] ... winProbs=... confidence=... h2hAlpha=...
[MatchAnalysis][Metadata] formHomeMatches=... formAwayMatches=... h2hMatches=... h2hAlpha=... leagueAdjustment=...
```
- xG derivation (λ) near lines 420–452: also blends home/away split goals for/against, then clamps to [0.3,3.0] and rounds to 2dp:
```
if (haveHomeX) xgHome = (homeGF + awayGA)/2.0;
if (haveAwayX) xgAway = (awayGF + homeGA)/2.0;
// clamp [0.3,3.0], round to 2dp
```
- Important: Single path integrates H2H and league adjustments into W/D/L, and applies draw-biasing via PPG scaling (0.75). It also can pull cached results when seasonId is null.

Conclusion: Batch uses FixtureAnalysisService (form-only Poisson), while Single uses MatchAnalysisService (form PPG → W/D/L with draw-bias, H2H blending, league adjustment, caching) and a slightly tighter clamp on xG (max 3.0 vs 3.5 in batch).

---

3) Data Flow Mismatch Checks

3.1 Team ID resolution
- Batch:
  - FixtureAnalysisService resolves teams solely by matching provided names against FormGuideService rows (toLowerCase string key). If names differ from form guide’s normalized team names, it throws. No alias handling.
- Single:
  - MatchAnalysisController resolves IDs using: exact normalized name within league, alias repository fallback, and “contains” fallback; then it can also use names. H2H path further explores league families by name/alias sets.
- Risk: If batch name lookup maps to a different/ambiguous team than the single path’s ID resolution (e.g., multiple aliases or changed naming), the form rows used to derive λ can differ, shifting xG and derived probabilities.

3.2 Season scoping
- Batch:
  - Explicitly resolves seasonId via SeasonResolutionService.resolveSeasonId(leagueId, league.season); if the string is missing or not exact, it falls back to latest season for the league.
- Single:
  - Requires seasonId in request and passes it. If absent, MatchAnalysisService finds current season via SeasonService.findCurrentSeason(leagueId) for some paths.
- Risk: If batch resolved a different season (latest) than the tab’s selected one, form rows and thus λ will differ.

3.3 Caching
- Batch:
  - Does not consult MatchAnalysisResultRepository cache and passes refresh through to FixtureAnalysisService, which ignores it.
- Single:
  - May hit cache when seasonId is null; otherwise recomputes. Cached results reflect MatchAnalysisService outputs (with H2H/league adjustments).
- Risk: Batch always computes fresh Poisson-form outputs; single may serve cached adjusted outputs, increasing divergence.

3.4 Input availability (form/H2H)
- Batch logs we can rely on:
  - [FixtureAnalysis][Response] wdl=... xg=...
- Single logs expose input availability explicitly:
  - [ANALYZE][FORM] rows=...
  - [MatchAnalysis][Metadata] formHomeMatches=... formAwayMatches=... h2hMatches=... h2hAlpha=...
- Hypothesis aligned with Verl vs Aachen:
  - Batch likely used awayGAw and homeGAh higher values, yielding λAway≈2.33 (much higher than tab’s 1.33). Single likely had insufficient away attack × home defense combination or a league adjustment pulling away xG down.

---

4) Calculation Differences

4.1 W/D/L
- Batch (FixtureAnalysisService):
  - W/D/L derived from Poisson distribution on λHome/λAway only.
  - No explicit draw bias or league adjustment.
- Single (MatchAnalysisService):
  - W/D/L from PPG-based weights with scale 0.75, creating an implicit draw-increase (remaining mass goes to draw). Then H2H-blended with adaptive alpha; may include league adjustments.
- Impact: For Verl vs Aachen, single path’s away heavy (63%) and draw=25% vs batch’s draw=20% indicates the PPG draw bias and H2H/league context pull probabilities differently than a pure Poisson grid.

4.2 Over goals & BTTS
- Batch: Derived purely from Poisson grid using λ sum; higher λ → higher Over and BTTS.
- Single: BTTS and Over2.5 are taken as averages of split percentages from FormGuide rows, not from Poisson; often lower and more conservative due to direct empirical rates and caps.
- Impact: Explains 91/76/57 vs 79/56/34 and 73 vs 59.

4.3 xG and Correct Scores
- Batch: λ clamps to [0.2, 3.5]; away λ can be higher than single due to clamp allowing up to 3.5 and using default shrinkages 1.3/1.1/1.2 when split stats missing. Correct scores then come from Poisson grid.
- Single: λ clamps to [0.3, 3.0], slightly stricter upper bound and different fallbacks. No correct scores are returned by the single tab service.

---

5) Root Cause

Primary root cause: The batch feature uses FixtureAnalysisService (a streamlined Poisson-based engine using recency-weighted form only), whereas the Fixture Analysis tab uses MatchAnalysisService (PPG-based W/D/L with draw-bias, H2H blending, league adjustments, and different xG clamping), and may additionally serve cached computations. Inputs also differ: batch resolves season via SeasonResolutionService (often latest) and resolves teams by name only within form rows; single resolves team IDs using richer logic and strictly uses the requested seasonId.

Therefore, the two paths are not equivalent in: model, inputs, fallback logic, and clamping—producing the observed mismatches.

Corroborating code references
- FixtureAnalysisService.analyzeFixture: lines 37–93 show name-based form lookup; λ derivation; Poisson; no H2H, no caching.
- BatchAnalysisCoordinator.analyzeOne: lines 119–131 show season resolution and call into FixtureAnalysisService with names.
- MatchAnalysisController.analyze: routes /api/match-analysis/analyze to MatchAnalysisService with leagueId+seasonId and ID resolution.
- MatchAnalysisService.analyzeDeterministic: integrates PPG → W/D/L (0.75 scaling), H2H blending, league family lookups, xG clamp to 3.0, and logs metadata.

---

6) Logs to Review (for Verl vs Alemannia Aachen)

Batch (FixtureAnalysisService)
- Expect:
```
[FixtureAnalysis][Response] home=Verl away=Alemannia Aachen durMs=... wdl=27/20/53 btts=73 over15=91 over25=76 over35=57 xg=1.67/2.33
```
- If home/away rows had fallbacks, Notes will include source leagues: “includes fallback comps: H:... A:...”.

Single (MatchAnalysisService)
- Key lines to seek in logs around the same timeframe:
```
[ANALYZE][REQ] leagueId=... seasonId=... homeId=... awayId=... home='Verl' away='Alemannia Aachen' refresh=false
[ANALYZE][FORM] leagueId=... seasonId=... rows=...
[ANALYZE][FORM] homeRowFound=true awayRowFound=true homeId=... awayId=...
[MatchAnalysis][Metadata] formHomeMatches=... formAwayMatches=... h2hMatches=... h2hAlpha=... leagueAdjustment=...
[MatchAnalysis][Response] ... winProbs=0.12,0.25,0.63 ...
```

If h2hMatches > 0, H2H blending impacted the result; if 0, the difference is likely due to PPG draw bias, market derivation (empirical vs Poisson), and λ clamp/fallback.

---

7) Recommendations (Conceptual; no code changes performed)

Model alignment
- Unify the backend paths by ensuring Chambua-Leo batch uses the same computation engine as the Fixture Analysis tab:
  - Option A: Make batch call MatchAnalysisService.analyzeDeterministic (season-aware), not FixtureAnalysisService. Pros: exact parity for W/D/L, BTTS, Over2.5, xG, and logs. Cons: performance; H2H lookups are heavier.
  - Option B: Refactor FixtureAnalysisService to internally call a shared computation core extracted from MatchAnalysisService (or vice versa), controlling feature flags to include/exclude H2H or caching as desired.

Input consistency
- Team resolution: In batch, resolve team IDs via TeamRepository/TeamAliasRepository (same as controller) before computing. Avoid name-only matching against form rows.
- Season scoping: Pass the exact seasonId used by the single tab into batch (e.g., per league/day selection), or guarantee SeasonResolutionService resolves to the same season as the tab’s UI selection.
- Caching: Decide on parity. If single can return cached results, either disable cache for single (when used by tab) or allow batch to optionally consult cache for the same key space.

λ and market methodology
- Clamp parity: Align λ clamp ranges (batch uses [0.2,3.5], single uses [0.3,3.0]).
- Markets parity: If you want Over/BTTS parity, drive them from the same mechanism in both paths (either both Poisson or both empirical split averages). Currently they differ (Poisson vs empirical averages).

Observability
- Add batch-side input logs mirroring single:
  - “[FixtureAnalysis][Inputs] formHomeMatches=..., formAwayMatches=...; sourceLeagueH=..., sourceLeagueA=...; seasonId=...”
  - Optionally compute/report a pseudo h2hAlpha=0.0 to clarify no H2H used.

---

8) Data Checks (SQL snippets)

Verify team IDs and names (league/season scope)
```
-- Replace :leagueId, :seasonId, :homeLike, :awayLike
SELECT t.id, t.name, t.normalized_name, l.name AS league, s.name AS season
FROM team t
JOIN league l ON l.id = t.league_id
LEFT JOIN season s ON s.league_id = l.id AND s.id = :seasonId
WHERE t.league_id = :leagueId
  AND (LOWER(t.name) LIKE LOWER(:homeLike) OR LOWER(t.name) LIKE LOWER(:awayLike))
ORDER BY t.name;
```

Check aliases used by single path resolution
```
SELECT a.alias, t.id, t.name, t.league_id
FROM team_alias a
LEFT JOIN team t ON t.id = a.team_id
WHERE LOWER(a.alias) IN (LOWER(:homeName), LOWER(:awayName));
```

Confirm form rows used by batch (FormGuideService)
```
-- What rows would compute() return for leagueId+seasonId?
-- Inspect matches backing the weighted split metrics for both teams
SELECT m.id, m.date, ht.name AS home, at.name AS away, m.home_goals, m.away_goals
FROM match m
JOIN team ht ON ht.id = m.home_team_id
JOIN team at ON at.id = m.away_team_id
WHERE m.league_id = :leagueId
  AND m.season_id = :seasonId
  AND (ht.name ILIKE :homeLike OR at.name ILIKE :homeLike OR ht.name ILIKE :awayLike OR at.name ILIKE :awayLike)
ORDER BY m.date DESC
LIMIT 20;
```

Check H2H availability for single path
```
-- Season-scoped strict H2H
SELECT m.date, ht.name as home, at.name as away, m.home_goals, m.away_goals
FROM match m
WHERE m.league_id = :leagueId AND m.season_id = :seasonId
  AND ((m.home_team_id = :homeId AND m.away_team_id = :awayId)
    OR (m.home_team_id = :awayId AND m.away_team_id = :homeId))
ORDER BY m.date DESC;

-- Across league family (if used)
SELECT m.date, ht.name as home, at.name as away, m.home_goals, m.away_goals
FROM match m
WHERE m.league_id IN (:leagueIds)
  AND ((m.home_team_id IN (:homeIdSet) AND m.away_team_id IN (:awayIdSet))
    OR (m.home_team_id IN (:awayIdSet) AND m.away_team_id IN (:homeIdSet)))
ORDER BY m.date DESC;
```

Season resolution parity
```
-- Verify latest season for league (batch fallback)
SELECT id, name, start_date
FROM season
WHERE league_id = :leagueId
ORDER BY start_date DESC
LIMIT 1;
```

---

9) Final Determination
- Batch and single paths intentionally diverge today:
  - Batch: FixtureAnalysisService → Poisson-only, form-derived λ, broader λ clamp, Poisson-derived markets, no H2H, no caching, name-only team mapping against form rows, season resolved by SeasonResolutionService.
  - Single: MatchAnalysisService → PPG-derived W/D/L with draw bias, H2H blending and league family support, league adjustment, λ clamp tighter, empirical BTTS/Over2.5, potential caching, robust ID/alias resolution.
- This explains Verl vs Aachen and Blackpool vs Barrow mismatches comprehensively.

Next Steps (if/when implementing fixes)
- Choose a single canonical engine and expose it to both paths, with flags for batch runtime limits.
- Ensure identical inputs: same seasonId, same team IDs resolution, and same clamping/market methods.
- Decide on cache parity policy.

Appendix: Code References
- FixtureAnalysisService.java lines 38–70, 88–94 (Poisson/logs).
- BatchAnalysisCoordinator.java lines 119–131 (season resolution + call).
- MatchAnalysisController.java entire file (ID resolution + endpoint).
- MatchAnalysisService.java around 120–186 (W/D/L, BTTS/Over2.5 form logic), 197–284+ (H2H retrieval/blending), 420–452 (xG clamp), 486–506 (response logs/meta).

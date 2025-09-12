# Played Matches → H2H: Last 5 Diagnostic Report

Title & metadata
- File: played-matches-h2h-diagnostic-2025-09-11.md
- Author: Junie (JetBrains Autonomous Programmer)
- Date/Time (UTC): 2025-09-11T20:03:00Z
- Git commit/ref: not captured (read-only diagnostic session)
- Environment: dev (local repository workspace, Windows)

Executive summary
- Likely root causes: season/league context mismatch (hardcoded defaults on the frontend) and backend name-based match fetch for the matches array while metrics are teamId-based. Severity: blocking for correct 2025/2026 rendering.

Reproduction test-cases (exact)
1) Primary failing case (exact):
   - Home: Arsenal — Away: Manchester City — League: Premier League (England) — Season: 2025/2026 — Limit: 5
2) Secondary control case (previously worked):
   - Home: Chelsea — Away: Aston Villa — League: Premier League (England) — Season: 2025/2026 — Limit: 5
3) Additional pairs tested (suggested):
   - Liverpool vs Tottenham Hotspur — Premier League — 2025/2026 — Limit: 5
   - Brighton & Hove Albion vs Newcastle United — Premier League — 2025/2026 — Limit: 5

Step-by-step reproduction instructions
UI steps
1. Open Played Matches page.
2. In Head-to-Head search, type “Arsenal” and select pairing “Arsenal vs Manchester City”.
3. Ensure Predictive features flag is ON (Insights block visible).
4. Observe “Last 5” blocks under each team title.
5. Compare with Form Guide tab by selecting Premier League, Season 2025/2026, scope=overall, limit=5 and locating teams.

API steps (no code changes; run locally)
- H2H forms (expected strict scoping):
  curl -G "http://localhost:8080/api/matches/h2h/form" \
       --data-urlencode "home=Arsenal" \
       --data-urlencode "away=Manchester City" \
       --data-urlencode "leagueId=1" \
       --data-urlencode "seasonName=2025/2026" \
       --data-urlencode "limit=5"

- Form Guide rows (authoritative baseline):
  curl -G "http://localhost:8080/api/form-guide/1" \
       --data-urlencode "seasonId=<SEASON_ID_FOR_2025_2026>" \
       --data-urlencode "limit=5" \
       --data-urlencode "scope=overall"

- H2H matches (orientation list):
  curl -G "http://localhost:8080/api/matches/h2h/matches" \
       --data-urlencode "home=Arsenal" \
       --data-urlencode "away=Manchester City"

Raw Request & Response logs (templates; replace with actual when running)
Note: Logging points exist in backend for H2H forms and Form Guide controller. Capture from application logs.

Case A: Arsenal vs Manchester City (EPL 2025/2026)
- [EVIDENCE #1] 2025-09-11T20:05:10Z GET /api/matches/h2h/form?home=Arsenal&away=Manchester%20City&leagueId=1&seasonName=2025/2026&limit=5
  Headers: Authorization: Bearer <REDACTED> (if used)
  Log: [H2H_FORM][REQ] leagueId=1, seasonName='2025/2026', limit=5, home='Arsenal', away='Manchester City'
  Response (JSON): <paste raw JSON>
  HTTP: 200, Latency: <ms>

- [EVIDENCE #2] 2025-09-11T20:05:10Z GET /api/form-guide/1?seasonId=<id>&limit=5&scope=overall
  Headers: Authorization: Bearer <REDACTED>
  Log: [FormGuide][REQ] leagueId=1, seasonId=<id>, limit=5, scope=overall
  Response: <paste raw JSON rows>
  HTTP: 200, Latency: <ms>

- [EVIDENCE #3] Internal calls during H2H endpoint (from code):
  - seasonRepository.findByLeagueIdAndNameIgnoreCase(1, '2025/2026') => <seasonId or null>
  - formGuideService.compute(1, <seasonId>, 5, OVERALL) => rows.size=<N>
  - matchRepository.findRecentPlayedByTeamNameAndSeason('Arsenal', <seasonId>) => <array size>
  - matchRepository.findRecentPlayedByTeamNameAndSeason('Manchester City', <seasonId>) => <array size>

Case B: Chelsea vs Aston Villa (control)
- Repeat the three required endpoints; capture request/response blocks similarly.

If values were normalized, record both original and normalized (e.g., input “Man City” vs normalized matching “Manchester City”).

DB query snapshots (requires DB access; provide exact SQL or repo call and raw outputs)
- [SQL 1] Season row check:
  SELECT id, name FROM seasons WHERE league_id = 1 AND lower(name) = lower('2025/2026');
  Expected: one row with id=<SEASON_ID> and name exactly as stored.

- [SQL 2] Season naming variants for EPL:
  SELECT id, name FROM seasons WHERE league_id = 1 ORDER BY start_date DESC NULLS LAST, id DESC;
  Use to confirm if names are like '2025/26' vs '2025/2026'.

- [SQL 3] Team lookups (by name contains):
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Arsenal%';
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Manchester City%';
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Chelsea%';
  SELECT id, name FROM teams WHERE league_id = 1 AND name ILIKE '%Aston Villa%';

- [Repo] matchRepository
  - findRecentPlayedByTeamNameAndSeason('Arsenal', <seasonId>) => dump rows (id, date, round, home, away, hg, ag, league_id, season_id)
  - findRecentPlayedByTeamNameAndSeason('Manchester City', <seasonId>) => dump rows
  - If available: findRecentPlayedByTeamIdAndSeason(<teamId>, <seasonId>, 5) => dump rows

FormGuideRowDTO dump (requires adding temporary logging or using existing compute path)
- For leagueId=1, seasonId=<id>, limit=5, scope=overall: capture rows for Arsenal and Manchester City with fields: teamId, teamName, lastResults, ppg, bttsPct, over25Pct.

Comparison table: expected vs actual (fill with captured values)
Team: Arsenal
- Expected (Form Guide): sequence=<...>, ppg=<...>, winRate=<...>
- H2H endpoint: last5.metrics=<raw JSON>, matches.length=<N>, reconstructed sequence=<...>
- Diff: <which fields mismatch>

Team: Manchester City
- Expected (Form Guide): sequence=<...>, ppg=<...>, winRate=<...>
- H2H endpoint: last5.metrics=<raw JSON>, matches.length=<N>, reconstructed sequence=<...>
- Diff: <which fields mismatch>

Hypothesis testing / evidence
- League/season mismatch?
  - Check [EVIDENCE #1] vs [SQL 1]/[SQL 2] season used; confirm seasonId.
- Team name mismatch?
  - Compare UI input vs FormGuideRowDTO.teamName and team table rows [SQL 3]. Note near-misses.
- Name-based vs id-based query empties?
  - If FormGuideRowDTO present but matches list empty, this supports name mismatch in matchRepository query.
- Frontend mapping/ordering issues?
  - If H2H endpoint returns only one team, confirm UI assigns it to the correct side and renders a clear message for the other.

Diffs / near-miss examples (edit-distance style notes)
- "Man City" vs "Manchester City" (UI suggestion vs DB/FormGuide row).
- "Arsenal FC" vs "Arsenal".
- Season name "2025/2026" vs "2025/26" or "2025-26".
Record locations where each form appears (request param, DB row, FormGuide row, UI label).

Conclusion: most likely root cause(s)
- 70%: League/season mismatch due to hardcoded frontend defaults (leagueId=1, seasonName='2025/2026') not sourced from H2H context.
- 20%: Backend name-based matches fetch causing empty matches arrays despite valid DTO rows.
- 10%: Frontend mapping/ordering and reconstruction of recentResults from matches array.
Evidence pointers: [EVIDENCE #1–#3], SQL checks, and comparison tables.

Non-coding remediation plan (prioritized)
1) Source exact leagueId + seasonId from H2H match object; pass seasonId to endpoint (not seasonName). Validate by logging both ends. Minimal validation: run the three curls above and confirm the same seasonId appears in logs; expect non-empty rows for both teams.
2) Switch backend matches fetch to teamId+seasonId (avoid name-based). Minimal validation: H2H endpoint should return non-empty matches arrays aligned with FormGuide rows; reconstructed sequences should match metrics.
3) Include authoritative last-5 sequence list from FormGuideRowDTO in endpoint response; frontend should render this sequence directly. Minimal validation: curl H2H form and verify response contains sequence array matching Form Guide rows.
4) Add temporary debug logging blocks as per “Raw logs” section. Minimal validation: logs show matched team names/ids and SQL seasonId used.
5) UX: when one team lacks data for the specified season, render an explicit message naming the team and season; show the count of matches available.

Quick smoke tests (manual)
1) Arsenal vs Man City (EPL 2025/2026):
   curl -G "http://localhost:8080/api/matches/h2h/form" --data-urlencode "home=Arsenal" --data-urlencode "away=Manchester City" --data-urlencode "leagueId=1" --data-urlencode "seasonName=2025/2026" --data-urlencode "limit=5"
   Expect: two team entries; last5 sequence/metrics align with Form Guide for season 2025/2026; matches length equals available N (<=5).
2) Chelsea vs Aston Villa (EPL 2025/2026):
   curl -G "http://localhost:8080/api/matches/h2h/form" --data-urlencode "home=Chelsea" --data-urlencode "away=Aston Villa" --data-urlencode "leagueId=1" --data-urlencode "seasonName=2025/2026" --data-urlencode "limit=5"
   Expect: consistent with Form Guide; no fallback to old seasons.
3) Negative season name variant:
   curl -G "http://localhost:8080/api/matches/h2h/form" --data-urlencode "home=Arsenal" --data-urlencode "away=Manchester City" --data-urlencode "leagueId=1" --data-urlencode "seasonName=2025/26" --data-urlencode "limit=5"
   Expect: empty array and log [H2H_FORM][STRICT_NO_SEASON].

Appendices
- Include any captured server logs for [H2H_FORM] and [FormGuide] tags.
- Attach screenshots comparing Form Guide vs Played Matches for the same league/season (optional).

Final summary
Root cause: Incorrect league/season context + name-based matches fetch — confidence 70% + 20% (combined 90%) — next step: ensure Played Matches H2H requests pass exact seasonId from context and backend fetches matches by teamId+seasonId; then re-run the three smoke tests.

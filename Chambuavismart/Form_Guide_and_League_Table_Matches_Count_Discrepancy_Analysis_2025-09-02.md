Title: Why Form Guide and League Table Show Fewer Matches Per Team Than Expected
Date: 2025-09-02 21:20
Owner: Data/Backend Analysis Note (no code changes)

Summary
- Observation: The Form Guide and the League Table pages are showing fewer matches played (MP) per team than expected for the league so far.
- Scope: This note explains, based on the current codebase, why MP can appear lower than the true number of fixtures played, lists concrete mechanisms in the backend and frontend that lead to under-counting, and provides verification steps and remediation recommendations.

Primary Reasons (Code-Backed)
1) Only completed matches up to today are counted
   Where:
   - Backend Form Guide: FormGuideService.compute()
     SQL filters (both home and away legs):
     - m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
     - m.match_date <= CURRENT_DATE
   - Backend League Table: LeagueTableService.computeTable()
     SQL filters (both legs):
     - m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
     - m.match_date <= CURRENT_DATE
   Effect:
   - Any fixture without both scores set is excluded.
   - Any match dated in the future (even if the result was uploaded) is excluded.
   Note: This is by design for “played so far”, but it can undercount when dates or scores are incomplete (see points 2, 3, 5).

2) Date misalignment can exclude legitimately played matches
   Where: The above date filter relies on m.match_date <= CURRENT_DATE. The Match entity stores a LocalDate column named match_date.
   Causes of misalignment:
   - Year inference from season during ingestion (MatchUploadService.parseDayMonthToDate and inferYearFromSeason) and in FixtureUploadService.resolveYearFromSeason can produce an off-by-one year near season boundaries (July/June logic).
   - If a CSV provides a date in a format not covered by parseDate, ingestion fails or the row is skipped.
   - If an actual completed match is recorded with a future calendar date (e.g., UTC/local confusion when preparing the CSV, or a simple data entry error), the table logic will exclude it until that date is reached.
   Visible symptom: Today’s finished matches might not appear if they were stored with a date of “tomorrow,” lowering MP.

3) Missing or non-numeric scores exclude matches
   Where: Both FormGuideService and LeagueTableService require non-null home_goals and away_goals.
   - If results weren’t uploaded yet (or saved as null), those rows are excluded from both views.
   - In contrast, some ingestion paths convert dashes to zeros (see point 5), which can artificially include upcoming fixtures as 0-0. That can cause other issues but generally would not reduce MP.

4) Team-join dependency: missing team records can drop matches
   Where:
   - Form Guide queries join teams t ON t.id = m.home_team_id / m.away_team_id to get names.
   - League Table aggregates then JOIN teams t ON t.id = s.team_id after UNION ALL.
   If a team record is missing (or was created under a different league/season combination), union rows for that team will be dropped by the JOIN, and that team’s MP can appear reduced or the team can be missing in the output. This most typically manifests as the team not appearing at all; however, mis-normalized names can split stats across multiple team rows during ingestion.

5) Ingestion normalization and placeholder goals
   Where: MatchUploadService
   - parseNullableInt treats '-', '–', '—' as zero, converting fixtures without a result into 0-0. Validation currently requires non-null goals, but since '-' becomes 0, these rows pass validation and persist as played 0-0.
   Mixed outcome:
   - If uploads intended to represent “upcoming fixtures” used '-', these are stored as 0-0 on the match date. If such a row uses a future date, it is excluded (due to date filter), reducing apparent MP until the date passes.
   - If the date is in the past but the fixture was not played, it will incorrectly increase MP as a 0-0. This is data integrity rather than undercounting, but it explains discrepancies between expected vs shown MP.

6) Round/date identity and batch de-duplication can merge or skip matches
   Where: MatchUploadService.upsertMatch and upload loops
   - Canonical identity is (league, date, home, away). If date is absent, fallback identity is (league, round, home, away).
   - The uploader also uses a per-batch seenKeys keyed by (leagueId:homeId:awayId:round). If a league has two fixtures between the same teams in the same “round” label (e.g., split stages, cup replays mislabeled), one will be skipped in that batch.
   Effects:
   - If different matches share the same round identifier and teams, an additional legitimate match can be silently skipped, reducing MP.
   - If date parsing fails and round-based fallback is used, two different fixtures can be merged into one row.

7) Frontend Form Guide “Limit” window vs “Total MP” display
   Where: frontend src/app/pages/form-guide.component.ts
   - matchesPlayed(r): by default shows totalMp if provided, otherwise W+D+L. When a user explicitly chooses a numeric limit (e.g., 6), the MP column switches to show the limited window r.mp, not totalMp.
   - When “Entire League” is selected, the last results badges are still capped to 10 for readability (display-only), which can make it look like only 10 matches were considered, even though totals use all.
   Possible user perception issues:
   - If a user previously set a numeric limit, MP shows the smaller window, not the total matches played. This can be misread as the true MP being lower.
   - The cap of last 10 badges with “+” indicator might be overlooked, leading to an assumption that only 10 matches exist.

8) Scope and filters (Form Guide)
   - FormGuideService supports scopes OVERALL, HOME, AWAY. If the UI is in HOME or AWAY scope, totalMp reflects only home or only away matches played, which is half of overall. If this scope selection is unnoticed, MP will appear “too low.”

Secondary/Contextual Contributors
- Distinct teams per league/season: Teams are unique per league row. If matches were uploaded under a slightly different league name/country/season (normalization differences), they will not be counted for the target leagueId. The UI shows the selected leagueId only.
- Round nullability and unique constraint: Although entity columns are non-nullable, incorrect or duplicated rounds in CSV may result in updates that overwrite previous entries (same round/home/away identity), reducing counted matches.
- Data repair schedulers update fixtures (not matches) based on existing matches; they don’t fix match dates/scores if those are wrong.

How to Verify (Checklist)
1) Backend diagnostic queries (examples)
   - Total completed matches counted by the table filters:
     SELECT COUNT(*) FROM matches m WHERE m.league_id = :leagueId AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL AND m.match_date <= CURRENT_DATE;
   - Per-team match counts (ground truth for OVERALL):
     SELECT t.id, t.name, COUNT(*) AS mp FROM (
       SELECT home_team_id AS team_id FROM matches WHERE league_id = :leagueId AND home_goals IS NOT NULL AND away_goals IS NOT NULL AND match_date <= CURRENT_DATE
       UNION ALL
       SELECT away_team_id AS team_id FROM matches WHERE league_id = :leagueId AND home_goals IS NOT NULL AND away_goals IS NOT NULL AND match_date <= CURRENT_DATE
     ) s JOIN teams t ON t.id = s.team_id GROUP BY t.id, t.name ORDER BY mp DESC, t.name ASC;
   - Compare the sum of per-team totalMp in FormGuideService logs (a WARN is emitted if it deviates beyond ±5% of expected).

2) Data integrity: sample 10 matches that are expected but missing
   - Look up their rows in matches with WHERE league_id = :leagueId AND (home_team_id=:tid OR away_team_id=:tid) and check match_date and goals.
   - Confirm if match_date is indeed <= today and both goals are set.

3) Team normalization
   - Verify that both home and away team entries exist in teams for the target leagueId and names match the ingested values. Watch for name variants (accents, spacing, case) across uploads.

4) Duplicate/merge check
   - Spot-check if two different fixtures (different dates) might share the same round for the same pairing and were merged/overwritten.
   - If you see fewer MPs for a team that had two matches vs the same opponent in close succession, inspect rounds and dates in the upload history.

Recommendations
A) Short-term (non-breaking)
- Data audit: Run the verification checklist for 2–3 affected leagues to identify whether the shortfall is due to date misalignment, missing scores, team joins, or batch de-duplication.
- UI clarification:
  - In the Form Guide, add a small tooltip next to the MP header clarifying whether MP shows the last N or total so far.
  - Consider showing both “Window MP” and “Total MP” columns when a numeric limit is in effect.
  - Show the selected scope (Overall/Home/Away) prominently.

B) Backend robustness (low risk)
- Replace CURRENT_DATE with a parameter (e.g., todayDate) provided by the server clock in the application timezone, to avoid database timezone ambiguity. Alternatively, use LocalDate.now() and pass as a parameter.
- Tighten ingestion: do not interpret '-' as 0 when the source indicates a fixture; either reject rows with placeholders or store nulls and rely on Fixture tables for upcoming matches. This avoids wrong inclusions and clarifies counts.
- Improve identity: include match_date in seenKeys where available to prevent valid duplicates in the same round from being dropped during a batch.
- Optional: add a diagnostics endpoint already present for the table (LeagueTableService.getDiagnostics) and mirror it for form guide to help compare ground truth counts vs API output.

C) Data hygiene
- Re-upload or correct rows with future-dated completed matches.
- Ensure all completed matches have numeric scores present.
- Normalize team names consistently across uploads (one canonical form per team per league/season).

Why this matches what you see
- If your league has played, say, 4 rounds, but the UI shows MP=3 for some teams, common causes are: one round stored with a future date; one fixture missing a score; or the UI currently in HOME/AWAY scope; or a prior numeric limit causing the MP column to show the window size.
- You may also notice today’s results missing until the database calendar date matches today (if they were recorded for the next day), which is consistent with the m.match_date <= CURRENT_DATE logic.

Appendix: Code References
- Form Guide logic: backend/src/main/java/com/chambua/vismart/service/FormGuideService.java (filters, grouping, mp vs totalMp, 10-cap on last results when limit='all')
- Form Guide API: backend/src/main/java/com/chambua/vismart/controller/FormGuideController.java (limit='all' supported, scopes)
- League Table logic: backend/src/main/java/com/chambua/vismart/service/LeagueTableService.java (UNION ALL, filters, join to teams)
- League Table API: backend/src/main/java/com/chambua/vismart/controller/LeagueController.java
- Match ingestion: backend/src/main/java/com/chambua/vismart/service/MatchUploadService.java (date parsing, placeholder goals, upsert identity, batch seenKeys)
- Fixture ingestion: backend/src/main/java/com/chambua/vismart/service/FixtureUploadService.java (season-based year inference)
- Frontend Form Guide: frontend/src/app/pages/form-guide.component.ts (matchesPlayed(), limit behavior, last results display cap)
- Frontend League Table: frontend/src/app/pages/league-table.component.ts

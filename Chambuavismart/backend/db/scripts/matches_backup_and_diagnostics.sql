-- Safety first — BACKUP (run once)
CREATE TABLE matches_backup_before_fix AS SELECT * FROM matches;

-- Snapshot counts to confirm snapshot
SELECT COUNT(*) AS backup_rows FROM matches_backup_before_fix;

-- STEP A — Run diagnostics (replace <leagueId> and '2024/2025')

-- A1 — distinct fixtures vs raw rows
SELECT 
  (SELECT COUNT(*) FROM (SELECT DISTINCT league_id, home_team_id, away_team_id, match_date FROM matches WHERE league_id = <leagueId>) t) AS distinct_fixtures,
  (SELECT COUNT(*) FROM matches WHERE league_id = <leagueId>) AS total_rows;

-- A2 — list duplicates (if any)
SELECT league_id, home_team_id, away_team_id, match_date, COUNT(*) AS cnt
FROM matches
WHERE league_id = <leagueId>
GROUP BY league_id, home_team_id, away_team_id, match_date
HAVING cnt > 1;

-- A3 — per-team ground-truth MP using UNION ALL (completed matches only)
SELECT team_id, COUNT(*) AS mp
FROM (
  SELECT home_team_id AS team_id FROM matches
    WHERE league_id = <leagueId>
      AND home_goals IS NOT NULL AND away_goals IS NOT NULL
      AND match_date <= CURRENT_DATE
  UNION ALL
  SELECT away_team_id AS team_id FROM matches
    WHERE league_id = <leagueId>
      AND home_goals IS NOT NULL AND away_goals IS NOT NULL
      AND match_date <= CURRENT_DATE
) s
GROUP BY team_id
ORDER BY mp DESC;

-- STEP B — If duplicates exist: safe dedupe + prevention

-- B1 — Backup subset (per league)
CREATE TABLE matches_backup_<leagueId> AS
SELECT * FROM matches WHERE league_id = <leagueId>;

-- B2 — Remove duplicates keeping smallest id
DELETE m1
FROM matches m1
JOIN matches m2
  ON m1.league_id = m2.league_id
  AND m1.home_team_id = m2.home_team_id
  AND m1.away_team_id = m2.away_team_id
  AND m1.match_date = m2.match_date
  AND m1.id > m2.id
WHERE m1.league_id = <leagueId>;

-- B3 — Verify dedupe
-- Re-run A2 query; it should return zero rows.

-- B4 — Add unique constraint (see Flyway migration V2__matches_unique_constraint.sql)
-- ALTER TABLE matches
-- ADD UNIQUE INDEX uq_matches_unique (league_id, home_team_id, away_team_id, match_date);

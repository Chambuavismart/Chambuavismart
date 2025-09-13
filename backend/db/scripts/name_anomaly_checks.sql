-- Safety check: detect trailing/leading spaces in teams.name
SELECT id, name, LENGTH(name) AS name_len
FROM teams
WHERE LENGTH(name) <> LENGTH(TRIM(name));

-- Detect duplicates after trimming and case-insensitive collation (MySQL)
-- Replace (...) with a candidate set if you want to scope; otherwise use a self-join/group
SELECT LOWER(TRIM(name)) AS norm_name, league_id, COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id) AS ids
FROM teams
GROUP BY league_id, LOWER(TRIM(name))
HAVING cnt > 1;

-- Optional: Using explicit collation to mimic case-insensitive comparison
-- Adjust collation per your DB character set as needed
SELECT DISTINCT t1.name
FROM teams t1
JOIN teams t2
  ON t1.league_id = t2.league_id
 AND t1.id <> t2.id
 AND t1.name COLLATE utf8mb4_general_ci = t2.name COLLATE utf8mb4_general_ci;

-- Quick diagnostics for H2H name mismatches between teams and matches via join on ids only
-- (Matches reference team IDs, so mismatches usually come from bad team names, not matches)
SELECT m.id AS match_id, m.home_team_id, th.name AS home_name, m.away_team_id, ta.name AS away_name
FROM matches m
JOIN teams th ON th.id = m.home_team_id
JOIN teams ta ON ta.id = m.away_team_id
WHERE (LENGTH(th.name) <> LENGTH(TRIM(th.name)))
   OR (LENGTH(ta.name) <> LENGTH(TRIM(ta.name)))
LIMIT 100;
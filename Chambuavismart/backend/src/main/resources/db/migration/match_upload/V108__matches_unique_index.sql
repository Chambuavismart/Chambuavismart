-- Enforce uniqueness of fixtures per league
-- Combination uses (league_id, home_team_id, away_team_id, match_date, round)
-- Before adjusting the unique index, remove any duplicate rows, keeping the lowest id per combination
DELETE m1 FROM matches m1
JOIN matches m2
  ON m1.league_id = m2.league_id
 AND m1.home_team_id = m2.home_team_id
 AND m1.away_team_id = m2.away_team_id
 AND m1.match_date = m2.match_date
 AND m1.round = m2.round
 AND m1.id > m2.id;

-- Idempotently ensure the unique index 'uq_matches_unique' matches the new 5-column definition
-- 1) Detect whether an index with name 'uq_matches_unique' exists with exactly the desired columns
SET @has_new_def := (
  SELECT CASE WHEN COUNT(*) = 5 AND SUM(
      CASE
        WHEN (column_name = 'league_id'   AND seq_in_index = 1) OR
             (column_name = 'home_team_id' AND seq_in_index = 2) OR
             (column_name = 'away_team_id' AND seq_in_index = 3) OR
             (column_name = 'match_date'   AND seq_in_index = 4) OR
             (column_name = 'round'        AND seq_in_index = 5)
        THEN 1 ELSE 0 END
    ) = 5 THEN 1 ELSE 0 END
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'matches'
    AND index_name = 'uq_matches_unique'
);

-- 2) If index exists but with an old/other definition, drop it
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'matches'
    AND index_name = 'uq_matches_unique'
);

SET @drop_sql := IF(@has_new_def = 1 OR @idx_exists = 0,
  'SELECT 1',
  'ALTER TABLE matches DROP INDEX uq_matches_unique'
);
PREPARE stmt FROM @drop_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) If the desired 5-column index is not present, create it
SET @create_sql := IF(@has_new_def = 1,
  'SELECT 1',
  'ALTER TABLE matches ADD UNIQUE INDEX uq_matches_unique (league_id, home_team_id, away_team_id, match_date, round)'
);
PREPARE stmt FROM @create_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

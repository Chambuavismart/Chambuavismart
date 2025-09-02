-- Ensure unique match per league+season (via league_id), round, home, away
-- Idempotent creation for MySQL: add the constraint only if it does not exist

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'matches'
    AND index_name = 'uk_match_league_round_home_away'
);

SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE matches ADD CONSTRAINT uk_match_league_round_home_away UNIQUE (league_id, round, home_team_id, away_team_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

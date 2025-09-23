-- Add unique constraint to prevent duplicates within a season for the same teams and date
-- Make idempotent for MySQL 8.0 by guarding with INFORMATION_SCHEMA checks

-- Add unique constraint if missing
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND CONSTRAINT_TYPE = 'UNIQUE'
        AND CONSTRAINT_NAME = 'uk_match_season_home_away_date'
    ),
    'SELECT 1',
    'ALTER TABLE matches ADD CONSTRAINT uk_match_season_home_away_date UNIQUE (season_id, home_team_id, away_team_id, match_date)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Supporting index (optional in some MySQL versions, but helpful for query planner), create if missing
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND INDEX_NAME = 'idx_matches_season_teams_date'
    ),
    'SELECT 1',
    'CREATE INDEX idx_matches_season_teams_date ON matches (season_id, home_team_id, away_team_id, match_date)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

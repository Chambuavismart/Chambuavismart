-- Add composite foreign key (league_id, season_id) -> seasons(league_id, id)
-- Replace unique constraint with (league_id, season_id, match_date, home_team_id, away_team_id)

-- Ensure supporting index on seasons(league_id, id) - guard for MySQL 8.0 (no IF NOT EXISTS)
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'seasons'
        AND INDEX_NAME = 'idx_seasons_league_id_pk'
    ),
    'SELECT 1',
    'CREATE INDEX idx_seasons_league_id_pk ON seasons(league_id, id)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop existing simple FK fk_match_season if present
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        AND CONSTRAINT_NAME = 'fk_match_season'
    ),
    'ALTER TABLE matches DROP FOREIGN KEY fk_match_season',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add composite FK fk_match_league_season if missing
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        AND CONSTRAINT_NAME = 'fk_match_league_season'
    ),
    'SELECT 1',
    'ALTER TABLE matches ADD CONSTRAINT fk_match_league_season FOREIGN KEY (league_id, season_id) REFERENCES seasons (league_id, id)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop old unique constraint uk_match_league_round_home_away if present
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND CONSTRAINT_TYPE = 'UNIQUE'
        AND CONSTRAINT_NAME = 'uk_match_league_round_home_away'
    ),
    'ALTER TABLE matches DROP INDEX uk_match_league_round_home_away',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add new unique constraint across identity including season and date if missing
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND CONSTRAINT_TYPE = 'UNIQUE'
        AND CONSTRAINT_NAME = 'uk_match_league_season_date_home_away'
    ),
    'SELECT 1',
    'ALTER TABLE matches ADD CONSTRAINT uk_match_league_season_date_home_away UNIQUE (league_id, season_id, match_date, home_team_id, away_team_id)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Supporting index on matches(league_id, season_id) if missing
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND INDEX_NAME = 'idx_matches_league_season'
    ),
    'SELECT 1',
    'CREATE INDEX idx_matches_league_season ON matches(league_id, season_id)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

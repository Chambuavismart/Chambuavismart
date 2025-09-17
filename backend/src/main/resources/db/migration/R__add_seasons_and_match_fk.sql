-- V5: Add seasons table and season_id foreign key on matches (nullable for backfill)

CREATE TABLE IF NOT EXISTS seasons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    metadata JSON NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_season_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
);

-- index for seasons by league
SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seasons' AND INDEX_NAME = 'idx_seasons_league_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_seasons_league_id ON seasons(league_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- add season_id to matches (nullable initially, MySQL-safe idempotent)
SET @col_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'season_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE matches ADD COLUMN season_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- add FK if not exists (MySQL lacks IF NOT EXISTS for FK; check in INFORMATION_SCHEMA)
SET @fk_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_match_season'
);
SET @sql := IF(@fk_exists = 0, 'ALTER TABLE matches ADD CONSTRAINT fk_match_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- index on matches.season_id
SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND INDEX_NAME = 'idx_matches_season_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_matches_season_id ON matches(season_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

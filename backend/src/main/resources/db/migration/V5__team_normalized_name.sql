-- Add normalized_name column to teams, backfill, and enforce unique constraint per league (MySQL-safe idempotent)
-- Conditionally add the column without using unsupported IF NOT EXISTS in ALTER TABLE
SET @col_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teams' AND COLUMN_NAME = 'normalized_name'
);
SET @sql_add_col := IF(@col_exists = 0,
  'ALTER TABLE teams ADD COLUMN normalized_name VARCHAR(255) NOT NULL DEFAULT '''' AFTER name',
  'SELECT 1');
PREPARE stmt_add_col FROM @sql_add_col; EXECUTE stmt_add_col; DEALLOCATE PREPARE stmt_add_col;

-- Backfill normalized_name using trim + collapse spaces + lower-case
UPDATE teams
SET normalized_name = LOWER(TRIM(REGEXP_REPLACE(name, '\\s+', ' ')));

-- Drop old unique constraint on (name, league_id) if exists and add new one on (normalized_name, league_id)
-- MySQL names UNIQUE constraints as indexes; handle conditionally
SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teams' AND INDEX_NAME = 'uk_team_name_league'
);
SET @sql := IF(@idx_exists = 1, 'ALTER TABLE teams DROP INDEX uk_team_name_league', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx2_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teams' AND INDEX_NAME = 'uk_team_normalized_league'
);
SET @sql2 := IF(@idx2_exists = 0, 'ALTER TABLE teams ADD CONSTRAINT uk_team_normalized_league UNIQUE (normalized_name, league_id)', 'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- Remove default now that data is populated (safe even if column pre-existed)
ALTER TABLE teams ALTER COLUMN normalized_name DROP DEFAULT;

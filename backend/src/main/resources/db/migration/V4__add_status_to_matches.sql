-- Add explicit status column to matches with default SCHEDULED and backfill played rows (idempotent for MySQL)
-- MySQL versions prior to 8.0.29 do not support "ADD COLUMN IF NOT EXISTS".
-- Use INFORMATION_SCHEMA + dynamic SQL to add the column only if missing.
SET @col_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'status'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE matches ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''SCHEDULED''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill: any row with both goals present is considered PLAYED
UPDATE matches SET status = 'PLAYED' WHERE home_goals IS NOT NULL AND away_goals IS NOT NULL;

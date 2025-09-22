-- Add explicit status column to matches with default SCHEDULED and backfill played rows
-- Make idempotent for MySQL versions that do NOT support "ADD COLUMN IF NOT EXISTS"
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matches'
        AND COLUMN_NAME = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE matches ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''SCHEDULED'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill: any row with both goals present is considered PLAYED
UPDATE matches SET status = 'PLAYED' WHERE home_goals IS NOT NULL AND away_goals IS NOT NULL;

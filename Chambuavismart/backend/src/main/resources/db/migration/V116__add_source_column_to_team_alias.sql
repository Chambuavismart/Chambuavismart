-- Ensure team_alias has `source` column to match JPA entity
-- MySQL compatibility: Use dynamic SQL with information_schema check instead of IF NOT EXISTS
-- This script is idempotent and safe on reruns.

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'team_alias'
    AND COLUMN_NAME = 'source'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE team_alias ADD COLUMN source VARCHAR(64) NULL',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
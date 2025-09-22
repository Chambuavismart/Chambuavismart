-- Ensure team_alias has `valid_from` and `valid_to` columns to match JPA entity
-- MySQL-compatible, idempotent migration using information_schema checks and dynamic SQL

-- Add valid_from if missing
SET @vf_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'team_alias'
    AND COLUMN_NAME = 'valid_from'
);

SET @ddl_vf := IF(@vf_exists = 0,
  'ALTER TABLE team_alias ADD COLUMN valid_from DATE NULL',
  'SELECT 1');
PREPARE stmt_vf FROM @ddl_vf;
EXECUTE stmt_vf;
DEALLOCATE PREPARE stmt_vf;

-- Add valid_to if missing
SET @vt_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'team_alias'
    AND COLUMN_NAME = 'valid_to'
);

SET @ddl_vt := IF(@vt_exists = 0,
  'ALTER TABLE team_alias ADD COLUMN valid_to DATE NULL',
  'SELECT 1');
PREPARE stmt_vt FROM @ddl_vt;
EXECUTE stmt_vt;
DEALLOCATE PREPARE stmt_vt;
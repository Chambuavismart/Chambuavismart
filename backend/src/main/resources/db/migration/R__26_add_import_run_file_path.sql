-- Add file_path column to import_run to store uploaded CSV location
SET @col_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_run' AND COLUMN_NAME = 'file_path'
);
SET @sql := IF(@col_exists = 0, 'ALTER TABLE import_run ADD COLUMN file_path VARCHAR(1000) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

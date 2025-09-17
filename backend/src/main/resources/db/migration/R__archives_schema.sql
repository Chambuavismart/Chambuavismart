-- Archives schema migration (MySQL compatible, idempotent)
-- Tables: import_run, import_error, team_alias, match_stats
-- Alter: matches add source_type, import_run_id, checksum; indexes; FK to import_run

-- 1) import_run
CREATE TABLE IF NOT EXISTS `import_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `file_hash` VARCHAR(128) NOT NULL,
  `provider` VARCHAR(64),
  `source_type` VARCHAR(32),
  `filename` VARCHAR(255),
  `params` JSON,
  `rows_total` INT DEFAULT 0,
  `rows_success` INT DEFAULT 0,
  `rows_failed` INT DEFAULT 0,
  `started_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT 'IN_PROGRESS'
) ENGINE=InnoDB;

-- 2) import_error
CREATE TABLE IF NOT EXISTS `import_error` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `import_run_id` BIGINT NOT NULL,
  `row_number` INT,
  `error_message` TEXT,
  `error_type` VARCHAR(64),
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_import_error_run` FOREIGN KEY (`import_run_id`) REFERENCES `import_run`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3) team_alias
CREATE TABLE IF NOT EXISTS `team_alias` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `team_id` BIGINT NOT NULL,
  `alias` VARCHAR(255) NOT NULL,
  `league_id` BIGINT,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_team_alias_team` FOREIGN KEY (`team_id`) REFERENCES `teams`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4) match_stats
CREATE TABLE IF NOT EXISTS `match_stats` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `match_id` BIGINT NOT NULL,
  `stat_type` VARCHAR(64),
  `value` DECIMAL(5,2),
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_match_stats_match` FOREIGN KEY (`match_id`) REFERENCES `matches`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5) Alter matches table (columns + FK) without procedures (Flyway-safe)
SET @db := DATABASE();

-- Add column `source_type` if missing
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'source_type');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `matches` ADD COLUMN `source_type` VARCHAR(32)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add column `import_run_id` if missing
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'import_run_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `matches` ADD COLUMN `import_run_id` BIGINT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add FK if missing
SET @fk_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS 
  WHERE CONVERT(CONSTRAINT_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(CONSTRAINT_NAME USING utf8mb4) = CONVERT('fk_matches_import_run' USING utf8mb4)
    AND CONVERT(UNIQUE_CONSTRAINT_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
);
SET @sql := IF(
  @fk_exists = 0,
  'ALTER TABLE `matches` ADD CONSTRAINT `fk_matches_import_run` FOREIGN KEY (`import_run_id`) REFERENCES `import_run`(`id`) ON DELETE SET NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add column `checksum` if missing
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'checksum');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `matches` ADD COLUMN `checksum` VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) Indexes (idempotent via INFORMATION_SCHEMA + dynamic SQL)
-- These indexes support querying runs and filtering matches by import metadata.
SET @db := DATABASE();

-- import_run(status)
SET @index_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
                      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'import_run' AND INDEX_NAME = 'idx_import_run_status');
SET @sql := IF(@index_exists = 0, 'CREATE INDEX `idx_import_run_status` ON `import_run`(`status`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- import_run(started_at)
SET @index_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
                      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'import_run' AND INDEX_NAME = 'idx_import_run_started');
SET @sql := IF(@index_exists = 0, 'CREATE INDEX `idx_import_run_started` ON `import_run`(`started_at`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- matches(source_type)
SET @index_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
                      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'matches' AND INDEX_NAME = 'idx_matches_source_type');
SET @sql := IF(@index_exists = 0, 'CREATE INDEX `idx_matches_source_type` ON `matches`(`source_type`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- matches(import_run_id)
SET @index_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
                      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'matches' AND INDEX_NAME = 'idx_matches_import_run_id');
SET @sql := IF(@index_exists = 0, 'CREATE INDEX `idx_matches_import_run_id` ON `matches`(`import_run_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

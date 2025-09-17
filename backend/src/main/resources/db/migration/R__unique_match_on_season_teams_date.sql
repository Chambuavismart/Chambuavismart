-- Add unique constraint to prevent duplicates within a season for the same teams and date (idempotent)
-- MySQL 8.0 compatible; avoids collation issues via CONVERT(... USING utf8mb4)

SET @db := DATABASE();

-- 1) Add UNIQUE constraint if missing
SET @uk_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
    AND CONVERT(CONSTRAINT_NAME USING utf8mb4) = CONVERT('uk_match_season_home_away_date' USING utf8mb4)
    AND CONVERT(CONSTRAINT_TYPE USING utf8mb4) = CONVERT('UNIQUE' USING utf8mb4)
);
SET @sql := IF(
  @uk_exists = 0,
  'ALTER TABLE `matches` ADD CONSTRAINT `uk_match_season_home_away_date` UNIQUE (`season_id`, `home_team_id`, `away_team_id`, `match_date`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Supporting index (optional in some MySQL versions, but helpful for query planner)
SET @idx_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
    AND CONVERT(INDEX_NAME USING utf8mb4) = CONVERT('idx_matches_season_teams_date' USING utf8mb4)
);
SET @sql := IF(
  @idx_exists = 0,
  'CREATE INDEX `idx_matches_season_teams_date` ON `matches` (`season_id`, `home_team_id`, `away_team_id`, `match_date`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

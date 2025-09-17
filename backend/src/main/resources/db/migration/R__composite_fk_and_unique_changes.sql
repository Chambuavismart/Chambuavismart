-- Add composite foreign key (league_id, season_id) -> seasons(league_id, id)
-- Replace unique constraint with (league_id, season_id, match_date, home_team_id, away_team_id)
-- Idempotent and MySQL 8.0 compatible (no IF NOT EXISTS for indexes/constraints)

SET @db := DATABASE();

-- 1) Ensure supporting index on seasons(league_id, id)
SET @idx_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('seasons' USING utf8mb4)
    AND CONVERT(INDEX_NAME USING utf8mb4) = CONVERT('idx_seasons_league_id_pk' USING utf8mb4)
);
SET @sql := IF(@idx_exists = 0,
               'CREATE INDEX `idx_seasons_league_id_pk` ON `seasons`(`league_id`, `id`)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Drop existing simple FK if present (fk_match_season)
SET @fk_old_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
  WHERE CONVERT(CONSTRAINT_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(CONSTRAINT_NAME USING utf8mb4) = CONVERT('fk_match_season' USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
);
SET @sql := IF(@fk_old_exists > 0,
               'ALTER TABLE `matches` DROP FOREIGN KEY `fk_match_season`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) Add composite FK if missing
SET @fk_new_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
  WHERE CONVERT(CONSTRAINT_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(CONSTRAINT_NAME USING utf8mb4) = CONVERT('fk_match_league_season' USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
);
SET @sql := IF(@fk_new_exists = 0,
               'ALTER TABLE `matches` ADD CONSTRAINT `fk_match_league_season` FOREIGN KEY (`league_id`, `season_id`) REFERENCES `seasons`(`league_id`, `id`) ON DELETE CASCADE',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) Drop old unique index if present
SET @uk_old_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
    AND CONVERT(INDEX_NAME USING utf8mb4) = CONVERT('uk_match_league_round_home_away' USING utf8mb4)
);
SET @sql := IF(@uk_old_exists > 0,
               'ALTER TABLE `matches` DROP INDEX `uk_match_league_round_home_away`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) Add new unique constraint across identity including season and date if missing
SET @uk_new_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
    AND CONVERT(CONSTRAINT_NAME USING utf8mb4) = CONVERT('uk_match_league_season_date_home_away' USING utf8mb4)
);
SET @sql := IF(@uk_new_exists = 0,
               'ALTER TABLE `matches` ADD CONSTRAINT `uk_match_league_season_date_home_away` UNIQUE (`league_id`, `season_id`, `match_date`, `home_team_id`, `away_team_id`)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) Supporting composite index on matches(league_id, season_id) if missing
SET @idx2_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE CONVERT(TABLE_SCHEMA USING utf8mb4) = CONVERT(@db USING utf8mb4)
    AND CONVERT(TABLE_NAME USING utf8mb4) = CONVERT('matches' USING utf8mb4)
    AND CONVERT(INDEX_NAME USING utf8mb4) = CONVERT('idx_matches_league_season' USING utf8mb4)
);
SET @sql := IF(@idx2_exists = 0,
               'CREATE INDEX `idx_matches_league_season` ON `matches`(`league_id`, `season_id`)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

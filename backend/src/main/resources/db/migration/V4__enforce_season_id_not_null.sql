-- Enforce NOT NULL on matches.season_id after backfill
-- MySQL error 1830 occurs if a FK uses ON DELETE/UPDATE SET NULL while column is NOT NULL.
-- Adjust FK fk_match_season to be compatible, then enforce NOT NULL.

-- 1) Drop FK fk_match_season if it exists
SET @fk_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_match_season'
);
SET @sql := IF(@fk_exists = 1, 'ALTER TABLE matches DROP FOREIGN KEY fk_match_season', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Ensure there are no NULLs remaining (V3 backfill should have populated these)
-- If any NULLs exist, this ALTER will fail; you can manually fix data and rerun.
ALTER TABLE matches MODIFY season_id BIGINT NOT NULL;

-- 3) Recreate FK without SET NULL semantics (use RESTRICT to prevent orphaning)
ALTER TABLE matches
  ADD CONSTRAINT fk_match_season
  FOREIGN KEY (season_id) REFERENCES seasons(id)
  ON DELETE RESTRICT
  ON UPDATE RESTRICT;
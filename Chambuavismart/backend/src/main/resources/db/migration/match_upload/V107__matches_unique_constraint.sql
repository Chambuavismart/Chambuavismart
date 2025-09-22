-- Add unique index on matches to prevent duplicate fixtures per league
-- Uniqueness defined by (league_id, home_team_id, away_team_id, match_date)
-- This matches the canonical fixture identity for a given day; round is not considered part of identity.
-- Idempotent: only create the index if it does not already exist.

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'matches'
    AND index_name = 'uq_matches_unique'
);

SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE matches ADD UNIQUE INDEX uq_matches_unique (league_id, home_team_id, away_team_id, match_date)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

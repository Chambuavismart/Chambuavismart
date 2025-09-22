-- Ensure match_analysis_results table exists with proper column types
CREATE TABLE IF NOT EXISTS match_analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    result_json LONGTEXT NOT NULL,
    last_updated TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_match_analysis_fixture UNIQUE (league_id, home_team_id, away_team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- If the table already existed with a smaller column, widen it
ALTER TABLE match_analysis_results 
    MODIFY COLUMN result_json LONGTEXT NOT NULL;

-- Ensure the unique constraint or index exists (create index if the constraint is missing)
-- MySQL will error if duplicate key name exists; guarded by checking information_schema
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'match_analysis_results'
      AND index_name = 'uk_match_analysis_fixture'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE match_analysis_results ADD UNIQUE INDEX uk_match_analysis_fixture (league_id, home_team_id, away_team_id);',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Archives schema migration (MySQL compatible, idempotent)
-- Tables: import_run, import_error, team_alias, match_stats
-- Alter: matches add source_type, import_run_id, checksum; indexes

-- 1) import_run
CREATE TABLE IF NOT EXISTS import_run (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  file_hash VARCHAR(128) NOT NULL,
  provider VARCHAR(64),
  source_type VARCHAR(32),
  filename VARCHAR(255),
  params JSON,
  rows_total INT DEFAULT 0,
  rows_success INT DEFAULT 0,
  rows_failed INT DEFAULT 0,
  started_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMP NULL DEFAULT NULL,
  status VARCHAR(32) DEFAULT 'IN_PROGRESS'
) ENGINE=InnoDB;

-- Index: import_run(file_hash)
DELIMITER //
DROP PROCEDURE IF EXISTS CreateIndexIfNotExists;
CREATE PROCEDURE CreateIndexIfNotExists(IN v_table VARCHAR(64), IN v_index VARCHAR(128), IN v_stmt TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = v_table AND index_name = v_index
  ) THEN
    SET @sql = v_stmt;
    PREPARE stmt1 FROM @sql;
    EXECUTE stmt1;
    DEALLOCATE PREPARE stmt1;
  END IF;
END //
DELIMITER ;

CALL CreateIndexIfNotExists('import_run','idx_importrun_filehash','CREATE INDEX idx_importrun_filehash ON import_run (file_hash)');

-- 2) import_error
CREATE TABLE IF NOT EXISTS import_error (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  import_run_id BIGINT,
  row_num INT,
  payload JSON,
  reason TEXT,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_import_error_run FOREIGN KEY (import_run_id) REFERENCES import_run(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3) team_alias (references teams)
CREATE TABLE IF NOT EXISTS team_alias (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  alias VARCHAR(255) NOT NULL,
  team_id BIGINT,
  source VARCHAR(64),
  valid_from DATE,
  valid_to DATE,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_alias_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT uk_team_alias UNIQUE (alias, team_id)
) ENGINE=InnoDB;

CALL CreateIndexIfNotExists('team_alias','idx_alias_alias','CREATE INDEX idx_alias_alias ON team_alias (alias)');

-- 4) match_stats (references matches)
CREATE TABLE IF NOT EXISTS match_stats (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  match_id BIGINT,
  stats JSON,
  CONSTRAINT fk_match_stats_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
  CONSTRAINT uk_match_stats UNIQUE (match_id)
) ENGINE=InnoDB;

-- 5) Alter matches: add new columns (idempotent using procedure)
DELIMITER //
DROP PROCEDURE IF EXISTS AddArchivesColumnsToMatches;
CREATE PROCEDURE AddArchivesColumnsToMatches()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'source_type'
  ) THEN
    ALTER TABLE matches ADD COLUMN source_type VARCHAR(32) DEFAULT 'CURRENT';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'import_run_id'
  ) THEN
    ALTER TABLE matches ADD COLUMN import_run_id BIGINT NULL;
    -- Add FK only if not exists
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND CONSTRAINT_NAME = 'fk_matches_import_run'
    ) THEN
      ALTER TABLE matches ADD CONSTRAINT fk_matches_import_run FOREIGN KEY (import_run_id) REFERENCES import_run(id);
    END IF;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches' AND COLUMN_NAME = 'checksum'
  ) THEN
    ALTER TABLE matches ADD COLUMN checksum VARCHAR(128) NULL;
  END IF;
END //
DELIMITER ;

CALL AddArchivesColumnsToMatches();
DROP PROCEDURE IF EXISTS AddArchivesColumnsToMatches;

-- 6) Index to support querying by source and date
CALL CreateIndexIfNotExists('matches','idx_matches_source_and_date','CREATE INDEX idx_matches_source_and_date ON matches (source_type, match_date)');

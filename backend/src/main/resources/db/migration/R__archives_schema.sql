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
DROP INDEX IF EXISTS idx_importrun_filehash ON import_run;
CREATE INDEX idx_importrun_filehash ON import_run (file_hash);

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

DROP INDEX IF EXISTS idx_alias_alias ON team_alias;
CREATE INDEX idx_alias_alias ON team_alias (alias);

-- 4) match_stats (references matches)
CREATE TABLE IF NOT EXISTS match_stats (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  match_id BIGINT,
  stats JSON,
  CONSTRAINT fk_match_stats_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
  CONSTRAINT uk_match_stats UNIQUE (match_id)
) ENGINE=InnoDB;

-- 5) Alter matches: add new columns (idempotent without procedures)
ALTER TABLE matches ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'CURRENT';
ALTER TABLE matches ADD COLUMN IF NOT EXISTS import_run_id BIGINT NULL;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS checksum VARCHAR(128) NULL;
-- Note: Foreign key creation is intentionally omitted here to keep the repeatable migration idempotent without stored procedures.

-- 6) Index to support querying by source and date
DROP INDEX IF EXISTS idx_matches_source_and_date ON matches;
CREATE INDEX idx_matches_source_and_date ON matches (source_type, match_date);

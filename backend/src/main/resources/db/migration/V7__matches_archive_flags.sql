-- Add archive-related boolean flags to matches (MySQL 8 safe)
-- Ensures JPA entity com.chambua.vismart.model.Match maps correctly
-- Note: Using two ALTER statements without IF NOT EXISTS for compatibility with some MySQL versions

ALTER TABLE matches
  ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE matches
  ADD COLUMN is_auto_corrected TINYINT(1) NOT NULL DEFAULT 0;

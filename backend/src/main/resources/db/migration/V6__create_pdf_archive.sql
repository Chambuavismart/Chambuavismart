-- Create table to store generated analysis PDFs and metadata
-- MySQL compatible DDL
CREATE TABLE IF NOT EXISTS pdf_archive (
    id BIGINT NOT NULL AUTO_INCREMENT,
    filename VARCHAR(260) NOT NULL,
    home_team VARCHAR(120) NOT NULL,
    away_team VARCHAR(120) NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    bytes LONGBLOB NOT NULL,
    request_snapshot LONGTEXT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Helpful indexes for listing and lookup
CREATE INDEX idx_pdf_archive_generated_at ON pdf_archive (generated_at);
CREATE INDEX idx_pdf_archive_teams ON pdf_archive (home_team, away_team);

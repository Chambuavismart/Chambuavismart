-- Flyway baseline migration
-- Create minimal tables required for initial scaffolding

CREATE TABLE IF NOT EXISTS app_metadata (
    id INT PRIMARY KEY AUTO_INCREMENT,
    app_name VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_metadata (app_name, version)
VALUES ('ChambuaViSmart Backend', '0.0.1-SNAPSHOT')
ON DUPLICATE KEY UPDATE version = VALUES(version);

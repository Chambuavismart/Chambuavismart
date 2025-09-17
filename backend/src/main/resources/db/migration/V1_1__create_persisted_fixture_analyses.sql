CREATE TABLE IF NOT EXISTS persisted_fixture_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    season_id BIGINT,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    home_team_name VARCHAR(255) NOT NULL,
    away_team_name VARCHAR(255) NOT NULL,
    fixture_id BIGINT,
    analysis_date DATE NOT NULL,
    user_id VARCHAR(255),
    result_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    source ENUM('INDIVIDUAL', 'BATCH') NOT NULL,
    CONSTRAINT uk_persisted_fixture_date UNIQUE (analysis_date, league_id, home_team_id, away_team_id),
    INDEX idx_analysis_date (analysis_date),
    INDEX idx_analysis_date_league (analysis_date, league_id)
);

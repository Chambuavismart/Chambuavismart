-- Flyway baseline schema for Chambua ViSmart (aligned with JPA entities)
-- Database: MySQL 8

-- 1) Leagues (unique by name+country+season)
CREATE TABLE IF NOT EXISTS leagues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    season VARCHAR(50) NOT NULL,
    CONSTRAINT uk_league_name_country_season UNIQUE (name, country, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) Teams (unique by name within a league)
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    league_id BIGINT NOT NULL,
    CONSTRAINT fk_team_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
    CONSTRAINT uk_team_name_league UNIQUE (name, league_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) Fixtures (string team names, unique by league+home+away+date_time)
CREATE TABLE IF NOT EXISTS fixtures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    round VARCHAR(64) NOT NULL,
    date_time DATETIME NOT NULL,
    home_team VARCHAR(255) NOT NULL,
    away_team VARCHAR(255) NOT NULL,
    home_score INT NULL,
    away_score INT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT fk_fixture_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
    CONSTRAINT uk_fixture_unique UNIQUE (league_id, home_team, away_team, date_time),
    INDEX idx_fixtures_league_date (league_id, date_time),
    INDEX idx_fixtures_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) Matches (historic results, unique by league+round+home+away)
CREATE TABLE IF NOT EXISTS matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    match_date DATE NOT NULL,
    round INT NOT NULL,
    home_goals INT NOT NULL,
    away_goals INT NOT NULL,
    CONSTRAINT fk_match_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id) ON DELETE RESTRICT,
    CONSTRAINT uk_match_league_round_home_away UNIQUE (league_id, round, home_team_id, away_team_id),
    INDEX idx_matches_league_round (league_id, round),
    INDEX idx_matches_date (match_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5) Optional: predictions (kept minimal)
CREATE TABLE IF NOT EXISTS fixture_predictions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    method VARCHAR(64) NULL,
    home_score DECIMAL(4,2) NULL,
    away_score DECIMAL(4,2) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prediction_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id) ON DELETE CASCADE,
    INDEX idx_prediction_fixture_created (fixture_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Note: No dev seeds here to keep baseline deterministic and CI-friendly.

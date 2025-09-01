-- Flyway baseline schema for Chambua ViSmart
-- Database: MySQL 8

-- 1) Leagues
CREATE TABLE IF NOT EXISTS leagues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(100),
    code VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) Teams
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    short_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_league FOREIGN KEY (league_id) REFERENCES leagues(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) Fixtures (scheduled games, may or may not have been played)
CREATE TABLE IF NOT EXISTS fixtures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT NOT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    kickoff DATETIME NOT NULL,
    matchweek INT,
    venue VARCHAR(150),
    status VARCHAR(30) DEFAULT 'SCHEDULED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fixtures_league FOREIGN KEY (league_id) REFERENCES leagues(id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_fixtures_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_fixtures_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) Matches (results for completed fixtures)
CREATE TABLE IF NOT EXISTS matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL UNIQUE,
    home_goals INT DEFAULT 0,
    away_goals INT DEFAULT 0,
    played_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_matches_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5) Fixture predictions (model outputs)
CREATE TABLE IF NOT EXISTS fixture_predictions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    home_win_prob DECIMAL(5,4),
    draw_prob DECIMAL(5,4),
    away_win_prob DECIMAL(5,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_predictions_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6) xG analyses
CREATE TABLE IF NOT EXISTS xg_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    match_id BIGINT,
    home_xg DECIMAL(6,3),
    away_xg DECIMAL(6,3),
    notes VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xg_match FOREIGN KEY (match_id) REFERENCES matches(id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7) Team stats (aggregated)
CREATE TABLE IF NOT EXISTS team_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    season VARCHAR(20),
    played INT DEFAULT 0,
    won INT DEFAULT 0,
    drawn INT DEFAULT 0,
    lost INT DEFAULT 0,
    goals_for INT DEFAULT 0,
    goals_against INT DEFAULT 0,
    xg_for DECIMAL(8,3),
    xg_against DECIMAL(8,3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stats_team FOREIGN KEY (team_id) REFERENCES teams(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Minimal seed data for dev to demonstrate "Fixtures Today" UI
INSERT INTO leagues (name, country, code)
VALUES ('Premier League', 'England', 'EPL');

INSERT INTO teams (league_id, name, short_name)
VALUES 
    (1, 'Arsenal', 'ARS'),
    (1, 'Chelsea', 'CHE'),
    (1, 'Liverpool', 'LIV'),
    (1, 'Manchester City', 'MCI');

-- Create two fixtures scheduled today at local noon and evening
INSERT INTO fixtures (league_id, home_team_id, away_team_id, kickoff, matchweek, venue, status)
VALUES
    (1, 1, 2, CONCAT(CURDATE(), ' 14:00:00'), 1, 'Emirates Stadium', 'SCHEDULED'),
    (1, 3, 4, CONCAT(CURDATE(), ' 19:00:00'), 1, 'Anfield', 'SCHEDULED');

-- Optional basic predictions seed
INSERT INTO fixture_predictions (fixture_id, home_win_prob, draw_prob, away_win_prob)
VALUES 
    (1, 0.52, 0.25, 0.23),
    (2, 0.35, 0.28, 0.37);

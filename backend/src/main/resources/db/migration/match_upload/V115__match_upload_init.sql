-- V1 baseline schema for ChambuaViSmart (core integrity)
-- MySQL compatible

-- Use utf8mb4
SET NAMES utf8mb4;

-- Create leagues
CREATE TABLE IF NOT EXISTS leagues (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  country VARCHAR(100) NOT NULL,
  season VARCHAR(32) NOT NULL,
  CONSTRAINT uk_league_name_country_season UNIQUE (name, country, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create teams
CREATE TABLE IF NOT EXISTS teams (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  league_id BIGINT NOT NULL,
  CONSTRAINT fk_team_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
  CONSTRAINT uk_team_name_league UNIQUE (name, league_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create fixtures
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

-- Create matches (historic results)
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
  CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id) ON DELETE CASCADE,
  CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id) ON DELETE CASCADE,
  CONSTRAINT uk_match_league_round_home_away UNIQUE (league_id, round, home_team_id, away_team_id),
  INDEX idx_matches_league_round (league_id, round),
  INDEX idx_matches_date (match_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Fixture predictions (non-ML deterministic)
CREATE TABLE IF NOT EXISTS fixture_predictions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fixture_id BIGINT NOT NULL,
  method VARCHAR(64) NOT NULL,
  home_score DECIMAL(4,2) NULL,
  away_score DECIMAL(4,2) NULL,
  market VARCHAR(64) NULL,
  confidence INT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prediction_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id) ON DELETE CASCADE,
  INDEX idx_prediction_fixture_created (fixture_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- xG analyses (history)
CREATE TABLE IF NOT EXISTS xg_analyses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  league VARCHAR(255) NOT NULL,
  home_team VARCHAR(255) NOT NULL,
  away_team VARCHAR(255) NOT NULL,
  home_xg DECIMAL(5,2) NOT NULL,
  away_xg DECIMAL(5,2) NOT NULL,
  xg_diff DECIMAL(5,2) NOT NULL,
  expected_goals DECIMAL(5,2) NOT NULL,
  actual_home_goals INT NULL,
  actual_away_goals INT NULL,
  analyzed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_xg_analyses_league_time (league, analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- team stats
CREATE TABLE IF NOT EXISTS team_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  season VARCHAR(32) NOT NULL,
  form_json JSON NULL,
  momentum_json JSON NULL,
  gf INT NULL,
  ga INT NULL,
  ppg DECIMAL(4,2) NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_stats_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
  INDEX idx_team_stats_team_season (team_id, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- h2h stats
CREATE TABLE IF NOT EXISTS h2h_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_a_id BIGINT NOT NULL,
  team_b_id BIGINT NOT NULL,
  stats_json JSON NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_h2h_team_a FOREIGN KEY (team_a_id) REFERENCES teams(id) ON DELETE CASCADE,
  CONSTRAINT fk_h2h_team_b FOREIGN KEY (team_b_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- recommendations
CREATE TABLE IF NOT EXISTS recommendations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fixture_id BIGINT NOT NULL,
  market VARCHAR(64) NOT NULL,
  suggestion VARCHAR(64) NOT NULL,
  confidence INT NULL,
  rationale TEXT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_reco_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- contextual factors
CREATE TABLE IF NOT EXISTS contextual_factors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fixture_id BIGINT NOT NULL,
  team_id BIGINT NULL,
  factor_type VARCHAR(128) NOT NULL,
  impact DECIMAL(4,2) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cf_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id) ON DELETE CASCADE,
  CONSTRAINT fk_cf_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Countries (for dropdown) - stores ISO name; we keep leagues.country as TEXT for now but can enforce via app logic
CREATE TABLE IF NOT EXISTS countries (
  code VARCHAR(2) PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed a basic set of countries (can be extended). Insert if not exists pattern.
INSERT INTO countries (code, name) VALUES
  ('GB', 'United Kingdom'),
  ('DE', 'Germany'),
  ('ES', 'Spain'),
  ('IT', 'Italy'),
  ('FR', 'France'),
  ('PT', 'Portugal'),
  ('NL', 'Netherlands'),
  ('BE', 'Belgium'),
  ('TR', 'Turkey'),
  ('GR', 'Greece')
ON DUPLICATE KEY UPDATE name = VALUES(name);

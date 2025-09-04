-- Flyway baseline schema for ChambuaViSmart
-- Creates core tables to satisfy JPA entities and integration tests.

-- Leagues
CREATE TABLE IF NOT EXISTS leagues (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  country VARCHAR(255) NOT NULL,
  season VARCHAR(64) NOT NULL,
  CONSTRAINT uk_league_name_country_season UNIQUE (name, country, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Teams
CREATE TABLE IF NOT EXISTS teams (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  league_id BIGINT NOT NULL,
  CONSTRAINT fk_team_league FOREIGN KEY (league_id) REFERENCES leagues(id),
  CONSTRAINT uk_team_name_league UNIQUE (name, league_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seasons
CREATE TABLE IF NOT EXISTS seasons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  league_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  metadata JSON NULL,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL,
  CONSTRAINT fk_season_league FOREIGN KEY (league_id) REFERENCES leagues(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_seasons_league_id ON seasons(league_id);

-- Matches
CREATE TABLE IF NOT EXISTS matches (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  league_id BIGINT NOT NULL,
  season_id BIGINT NULL,
  home_team_id BIGINT NOT NULL,
  away_team_id BIGINT NOT NULL,
  match_date DATE NOT NULL,
  round INT NOT NULL,
  home_goals INT NULL,
  away_goals INT NULL,
  CONSTRAINT fk_match_league FOREIGN KEY (league_id) REFERENCES leagues(id),
  CONSTRAINT fk_match_season FOREIGN KEY (season_id) REFERENCES seasons(id),
  CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
  CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id),
  CONSTRAINT uk_match_league_round_home_away UNIQUE (league_id, round, home_team_id, away_team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_matches_league_round ON matches(league_id, round);
CREATE INDEX idx_matches_date ON matches(match_date);

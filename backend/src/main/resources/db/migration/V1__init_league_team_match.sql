-- Flyway migration: create league, team, match tables
CREATE TABLE IF NOT EXISTS leagues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    season VARCHAR(50) NOT NULL,
    CONSTRAINT uk_league_name_country_season UNIQUE (name, country, season)
);

CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    league_id BIGINT NOT NULL,
    CONSTRAINT fk_team_league FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
    CONSTRAINT uk_team_name_league UNIQUE (name, league_id)
);

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
    CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_matches_league_round ON matches(league_id, round);
CREATE INDEX IF NOT EXISTS idx_matches_date ON matches(match_date);

-- Flyway base schema initialization for ChambuaViSmart

CREATE TABLE IF NOT EXISTS leagues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(100),
    season VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    league_id BIGINT,
    short_code VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_team_league FOREIGN KEY (league_id) REFERENCES leagues(id)
);

CREATE TABLE IF NOT EXISTS fixtures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    league_id BIGINT,
    match_date DATETIME NOT NULL,
    round VARCHAR(50),
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    status VARCHAR(50) DEFAULT 'SCHEDULED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_fix_league FOREIGN KEY (league_id) REFERENCES leagues(id),
    CONSTRAINT fk_fix_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_fix_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id)
);

CREATE TABLE IF NOT EXISTS matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    home_goals INT DEFAULT 0,
    away_goals INT DEFAULT 0,
    result VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
);

CREATE TABLE IF NOT EXISTS fixture_predictions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    home_win_prob DECIMAL(5,2),
    draw_prob DECIMAL(5,2),
    away_win_prob DECIMAL(5,2),
    predicted_result VARCHAR(10),
    rationale TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pred_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
);

CREATE TABLE IF NOT EXISTS xg_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    match_id BIGINT,
    xg_for DECIMAL(6,3),
    xg_against DECIMAL(6,3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_xg_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_xg_match FOREIGN KEY (match_id) REFERENCES matches(id)
);

CREATE TABLE IF NOT EXISTS team_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    played INT DEFAULT 0,
    won INT DEFAULT 0,
    drawn INT DEFAULT 0,
    lost INT DEFAULT 0,
    goals_for INT DEFAULT 0,
    goals_against INT DEFAULT 0,
    points INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stats_team FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE TABLE IF NOT EXISTS h2h_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    last_5_home_wins INT DEFAULT 0,
    last_5_away_wins INT DEFAULT 0,
    last_5_draws INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_h2h_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_h2h_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id)
);

CREATE TABLE IF NOT EXISTS recommendations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    bet_type VARCHAR(100),
    recommendation VARCHAR(255),
    confidence DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reco_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
);

CREATE TABLE IF NOT EXISTS contextual_factors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    factor_key VARCHAR(100) NOT NULL,
    factor_value VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ctx_fixture FOREIGN KEY (fixture_id) REFERENCES fixtures(id)
);

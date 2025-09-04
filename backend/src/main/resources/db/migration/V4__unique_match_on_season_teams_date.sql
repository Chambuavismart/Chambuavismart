-- Add unique constraint to prevent duplicates within a season for the same teams and date
ALTER TABLE matches
ADD CONSTRAINT uk_match_season_home_away_date UNIQUE (season_id, home_team_id, away_team_id, match_date);

-- Supporting index (optional in some MySQL versions, but helpful for query planner)
CREATE INDEX idx_matches_season_teams_date ON matches (season_id, home_team_id, away_team_id, match_date);

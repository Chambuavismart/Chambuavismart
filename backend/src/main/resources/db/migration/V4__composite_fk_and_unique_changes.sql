-- Add composite foreign key (league_id, season_id) -> seasons(league_id, id)
-- Replace unique constraint with (league_id, season_id, match_date, home_team_id, away_team_id)

-- Ensure supporting index on seasons(league_id, id)
CREATE INDEX IF NOT EXISTS idx_seasons_league_id_pk ON seasons(league_id, id);

-- Drop existing simple FK if present
ALTER TABLE matches DROP FOREIGN KEY fk_match_season;

-- Add composite FK (note: matches.season_id references seasons.id and must have matching league_id)
ALTER TABLE matches
  ADD CONSTRAINT fk_match_league_season
  FOREIGN KEY (league_id, season_id)
  REFERENCES seasons (league_id, id);

-- Drop old unique constraint
ALTER TABLE matches DROP INDEX uk_match_league_round_home_away;

-- Add new unique constraint across identity including season and date
ALTER TABLE matches
  ADD CONSTRAINT uk_match_league_season_date_home_away
  UNIQUE (league_id, season_id, match_date, home_team_id, away_team_id);

-- Supporting indexes
CREATE INDEX IF NOT EXISTS idx_matches_league_season ON matches(league_id, season_id);

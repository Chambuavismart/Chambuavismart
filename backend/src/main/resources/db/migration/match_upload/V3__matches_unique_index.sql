-- Enforce uniqueness of fixtures per league
-- Combination uses (league_id, home_team_id, away_team_id, match_date, round)
-- Before adding the unique index, remove any duplicate rows, keeping the lowest id per combination
DELETE m1 FROM matches m1
JOIN matches m2
  ON m1.league_id = m2.league_id
 AND m1.home_team_id = m2.home_team_id
 AND m1.away_team_id = m2.away_team_id
 AND m1.match_date = m2.match_date
 AND m1.round = m2.round
 AND m1.id > m2.id;

CREATE UNIQUE INDEX uq_matches_unique
ON matches (league_id, home_team_id, away_team_id, match_date, round);

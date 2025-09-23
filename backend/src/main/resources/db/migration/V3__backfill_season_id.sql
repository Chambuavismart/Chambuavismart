-- Backfill season_id on matches using seasons date windows
-- Attach season by (matches.league_id = seasons.league_id) and date between start_date and end_date
-- If multiple seasons match (shouldn't), picks the first by earliest start_date.

UPDATE matches m
JOIN (
    SELECT s.league_id, s.id AS season_id, s.start_date, s.end_date
    FROM seasons s
) S ON S.league_id = m.league_id
   AND (S.start_date IS NULL OR m.match_date >= S.start_date)
   AND (S.end_date IS NULL OR m.match_date <= S.end_date)
LEFT JOIN seasons s_exact ON s_exact.id = m.season_id
SET m.season_id = S.season_id
WHERE m.season_id IS NULL;

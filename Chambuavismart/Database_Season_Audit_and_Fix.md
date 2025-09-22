# Database Season Audit & Backfill Plan

This document contains SQL queries to audit and fix season_id integrity for matches, ensuring season filters work correctly.

Note: Replace placeholders :leagueId and :seasonId with actual IDs as needed. For date-range based backfill, ensure seasons table has correct start_date and end_date values.

## Distribution by season

```sql
SELECT season_id, COUNT(*) AS matches
FROM matches m
WHERE m.league_id = :leagueId
GROUP BY season_id
ORDER BY season_id;
```

## Null season_id count

```sql
SELECT COUNT(*) AS null_season_matches
FROM matches m
WHERE m.league_id = :leagueId
  AND m.season_id IS NULL;
```

## Team-level sanity by season

```sql
SELECT t.name, m.season_id, COUNT(*) AS mp
FROM matches m
JOIN teams t ON t.id IN (m.home_team_id, m.away_team_id)
WHERE m.league_id = :leagueId
  AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
GROUP BY t.name, m.season_id
ORDER BY t.name, m.season_id;
```

## Date/season range validation

```sql
-- Rows whose match_date falls inside a season but have NULL or wrong season_id
SELECT m.id, m.match_date, m.season_id AS current_season_id, s.id AS expected_season_id
FROM matches m
JOIN seasons s ON s.league_id = m.league_id
WHERE m.league_id = :leagueId
  AND m.match_date BETWEEN s.start_date AND s.end_date
  AND (m.season_id IS NULL OR m.season_id <> s.id)
ORDER BY m.match_date DESC
;
```

## Backfill season_id by date range (dry run and apply)

First run a dry-run SELECT to see what would be updated:

```sql
SELECT m.id, m.match_date, m.season_id AS current_season_id, s.id AS new_season_id
FROM matches m
JOIN seasons s ON s.league_id = m.league_id
WHERE m.league_id = :leagueId
  AND m.match_date BETWEEN s.start_date AND s.end_date
  AND (m.season_id IS NULL OR m.season_id <> s.id);
```

If correct, run the UPDATE in a transaction:

```sql
BEGIN;
UPDATE matches m
JOIN seasons s ON s.league_id = m.league_id
SET m.season_id = s.id
WHERE m.league_id = :leagueId
  AND m.match_date BETWEEN s.start_date AND s.end_date
  AND (m.season_id IS NULL OR m.season_id <> s.id);
COMMIT;
```

## Out-of-range rows

```sql
-- Matches that do not fall into any known season window
SELECT m.*
FROM matches m
LEFT JOIN seasons s ON s.league_id = m.league_id AND m.match_date BETWEEN s.start_date AND s.end_date
WHERE m.league_id = :leagueId
  AND s.id IS NULL;
```

## Application-level guard (recommendation)

- In ingestion paths, ensure completed matches (with non-null home_goals and away_goals) must have season_id, or are assigned by date to a valid season.
- Add a NOT NULL constraint to matches.season_id only after ingestion is season-aware, e.g.:

```sql
ALTER TABLE matches MODIFY season_id BIGINT NOT NULL;
```

(Do not run until apps are ready.)

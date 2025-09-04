-- Add explicit status column to matches with default SCHEDULED and backfill played rows
ALTER TABLE matches ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED';

-- Backfill: any row with both goals present is considered PLAYED
UPDATE matches SET status = 'PLAYED' WHERE home_goals IS NOT NULL AND away_goals IS NOT NULL;

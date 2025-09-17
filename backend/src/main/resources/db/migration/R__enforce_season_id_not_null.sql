-- Enforce NOT NULL on matches.season_id after backfill
ALTER TABLE matches MODIFY season_id BIGINT NOT NULL;
-- Clean up team and alias names by trimming and collapsing internal whitespace, and refresh normalized_name

-- Normalize teams.name (trim + collapse spaces)
UPDATE teams
SET name = TRIM(REGEXP_REPLACE(name, '\\s+', ' '))
WHERE name IS NOT NULL;

-- Refresh normalized_name from cleaned name
UPDATE teams
SET normalized_name = LOWER(TRIM(REGEXP_REPLACE(name, '\\s+', ' ')));

-- Normalize team_alias.alias similarly
UPDATE team_alias
SET alias = TRIM(REGEXP_REPLACE(alias, '\\s+', ' '))
WHERE alias IS NOT NULL;


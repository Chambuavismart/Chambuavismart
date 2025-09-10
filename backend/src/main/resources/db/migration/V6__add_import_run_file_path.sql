-- Add file_path column to import_run to store uploaded CSV location
ALTER TABLE import_run
  ADD COLUMN IF NOT EXISTS file_path VARCHAR(1000) NULL;

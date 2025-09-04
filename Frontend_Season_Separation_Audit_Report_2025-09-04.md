# Frontend Season Separation Audit Report (2025-09-04)

Scope: Angular services (API calls), components (League Table, Form Guide, Fixtures, Match Analysis), routing (query params), and upload workflow.

Summary of Findings and Changes

1) API Contract Alignment
- League table and form guide services already accepted an optional seasonId and sent it as `seasonId` or `combined=true`.
- Gaps: MatchUploadService (frontend) did not pass strict/dryRun/allowSeasonAutoCreate to backend unified or legacy endpoints.
- Fixes:
  - MatchUploadService now sends:
    - strict=true, dryRun=false, allowSeasonAutoCreate=false by default for both unified CSV/JSON and legacy CSV/JSON.
    - Optional overrides supported via options for unified methods.
  - Upload UI error handling improved to summarize strict out-of-window rejections with a friendly message.

2) Season Selection UI
- League Table and Form Guide:
  - Explicit “Combined (All Seasons)” option in season dropdown (sends seasonId=null; service encodes as `combined=true`).
  - seasonId preserved in URL query params; restored on load; changing season triggers full re-query and refresh.
- Match Analysis:
  - Relies on selections and fixtures; it does not currently accept seasonId (not required for this scope).
- Fixtures:
  - Added informational note: Fixtures currently don’t use season filters; season is inferred heuristically.

3) Upload Workflow Strictness + Dry-run
- Backend supports strict, dryRun, allowSeasonAutoCreate. Frontend now defaults to strict=true, dryRun=false, allowSeasonAutoCreate=false.
- UI feedback:
  - When strict rejections occur (out-of-window), the upload page shows a friendly summary like “3 matches rejected because they are outside season window (strict mode).” alongside the detailed errors list.
  - Dry-run preview is supported by service flags (not exposed as UI controls by default; can be enabled programmatically if needed).

4) Routing and State
- League Table and Form Guide preserve and hydrate seasonId in query params for shareability. Verified triggers cause component refreshes.
- Fixtures page does not use seasonId and now explicitly communicates this to avoid user confusion.

5) Tests (lightweight)
- Existing league-table component spec covers passing seasonId to the service when selection changes.
- Recommendation (future work): add unit tests with HttpTestingController for MatchUploadService to assert that flags strict/dryRun/allowSeasonAutoCreate are sent as expected; add Form Guide spec similar to league-table to validate `combined=true` behavior when seasonId=null.

Known Limitations / Next Steps
- The UI does not expose toggle controls for strict/dryRun/allowSeasonAutoCreate. Defaults are aligned with requirements; consider adding advanced toggles for admin users.
- Fixtures do not use season_id at backend; once backend adds it, plumb `seasonId` similarly and remove the informational note.

Files Changed
- frontend/src/app/services/match-upload.service.ts – added strict/dryRun/allowSeasonAutoCreate flags to all relevant requests.
- frontend/src/app/pages/match-upload.component.ts – improved error summarization for strict rejections.
- frontend/src/app/pages/fixtures.component.ts – added informational note about season inference for fixtures.
- Previously (in earlier step of this session):
  - league-table.component.ts and form-guide.component.ts updated to persist/restore seasonId in URL and re-fetch on change.

Outcome
- Frontend is now compatible with the backend season separation constraints:
  - SeasonId consistently sent where applicable; combined handled explicitly.
  - Upload endpoints receive strictness and dry-run flags as required, defaulting to strict mode.
  - UI communicates strict rejections clearly and clarifies fixtures behavior.

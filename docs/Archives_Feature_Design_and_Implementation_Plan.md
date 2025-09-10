# Archives Feature — Design, Practicality Assessment, and Implementation Plan

Last updated: 2025-09-05 20:48 (local)

## 1) Overview
The Archives feature will store historical match results from prior seasons and older leagues to power richer head‑to‑head (H2H) insights and match analysis for current fixtures. It will ingest:
- Semi‑structured block text such as:
  - Round 30 … date/time … teams … final scores … repeated
- Structured CSV datasets (e.g., “E0 (2).csv”) containing columns like Date, Time, HomeTeam, AwayTeam, FTHG, FTAG, FTR, halftime metrics, shots, cards, corners, bookmaker odds, Asian handicap, totals, etc.

Archives become a long‑term, query‑optimized store that the analysis layer can use to compute H2H trends, home/away splits, and form context for any fixture.

## 2) Why this is practical now
The current codebase already includes season‑aware logic and unified upload endpoints:
- Season separation exists and is covered by tests (SeasonFilterIntegrationTest), which indicates the backend understands seasons and filtering.
- UnifiedUploadTextController suggests an ingestion surface that we can extend to accept Archives (text and CSV) without creating an entirely new ingestion stack.
- Spring Boot + JPA stack (per backend/pom.xml and application.yml) is well suited to add new entities and repositories for archived data.
- The frontend and analysis modules already compute form guides and match analyses; adding H2H computation can reuse this infrastructure and APIs.

Conclusion: Introducing Archives is low risk and highly synergistic with current architecture.

## 3) Goals and Non‑Goals
- Goals
  - Persist reliable multi‑season historical results across leagues/competitions.
  - Provide H2H summaries (overall and venue split) to current fixtures’ analysis views.
  - Support ingestion from both unstructured rounds text and provider CSVs.
  - Ensure de‑duplication, provenance, and auditing of imports.
- Non‑Goals (initial phases)
  - Live odds ingestion/aggregation logic beyond storing provided odds snapshots.
  - Complex ETL across dozens of external schemas — start with E0 schema then add mappers.

## 4) Data sources and formats
- Unstructured rounds text (example provided in the user issue), patterns:
  - Heading: "Round XX"
  - Blocks: Date (dd.MM. HH:mm), Home team (twice in source lines), Away team (twice), Home FT goals, Away FT goals.
- Structured CSV (E0 (2).csv): includes divisions (Div), Date (dd/MM/yyyy), Time (HH:mm), teams, full time and half time goals, referees, shots, cards, corners, many pricing markets.

## 5) Existing capabilities to leverage
- Season separation and filters (backend tests prove season awareness).
- Unified upload controller to extend for Archives ingestion.
- Likely existing Team/League/Season domain models we can reference or extend.
- Existing analysis endpoints/pages that can be augmented with H2H.

## 6) Proposed data model (minimal, extensible)
Option A (Recommended): Use existing Match/Fixture model with a sourceType flag
- Add enum field `sourceType`: { CURRENT, ARCHIVE }.
- Add optional fields for extended stats (if absent) via a secondary table `match_stats` keyed by match ID.

Option B: New entity `ArchivedMatch`
- Fields: id, competitionId (or code like Div), seasonId, dateTimeUtc, homeTeamId, awayTeamId, homeGoals, awayGoals, result, halftimeHome, halftimeAway, referee, stats blob, odds blob, importRunId, checksum.
- Unique natural key: (competitionId, seasonId, date, homeTeamId, awayTeamId).

Team Canonicalization
- Table: `team_alias` (alias -> teamId, valid_from, valid_to, source). Ensures “Man United” == “Manchester United”, “CR Belouizdad” variants, etc.

Import/Audit
- Table: `import_run` with file hash, source type (CSV/TEXT), provider, row counts, successes, rejects, startedAt, finishedAt, status.
- Table: `import_error` linked to run, with rowNumber, payload, reason.

Indexes
- (homeTeamId, awayTeamId, dateTimeUtc), (teamId, dateTimeUtc), (competitionId, seasonId, dateTimeUtc), import_run(fileHash).

## 7) Ingestion pipelines
### 7.1 CSV pipeline (E0 schema first)
- Parser reads rows streaming to avoid memory spikes.
- Column mapping: Date, Time -> local datetime, normalized to UTC; Div -> competition mapping; teams -> canonical team IDs via aliases; FTHG/FTAG/FTR -> result fields; optional stats/odds captured if present.
- Idempotency: compute file hash and row checksum; skip/merge duplicates based on natural key; keep the most complete record.
- Partial failures do not abort the run; errors recorded in `import_error`.

### 7.2 Text pipeline (Rounds)
- Regex/stateful parser recognizes sequences: Round header, then repeating groups of date/time, home team (twice), away team (twice), score lines.
- Date parsing: dd.MM. HH:mm, infer year from heading context or from season window when known; allow manual override parameter `season` if needed.
- Team alias resolution; validations (goals integers, known competition, time window).

## 8) Canonicalization and season mapping
- Team alias resolver prioritizes exact alias by competition/region; falls back to fuzzy match with review queue if ambiguous.
- Competition mapping: Div codes (e.g., E0) map to known leagues.
- Season rules: compute seasonId from league rules (e.g., Premier League 2024/25 runs from 2024‑08‑01 to 2025‑06‑30). Allow override on import.

## 9) H2H Service and analysis integration
- API: `GET /archives/h2h?homeId=&awayId=&competitionId=&since=&limit=&venueScope={all|home|away}`
  - Returns recent meetings, W‑D‑L aggregates, goals for/against, average totals, last meeting date, optional xG if available later.
- Analysis integration: Existing fixture analysis calls H2HService to render panel with last N H2Hs and toggles for:
  - Competition scope: same league vs all competitions
  - Time horizon: e.g., last 2 years or last N meetings
  - Venue scope: overall vs home/away splits

## 10) Admin endpoints (ingestion)
- `POST /archives/import/csv` (multipart): params: competitionCode, season (optional), timezone, provider; response: importRun summary.
- `POST /archives/import/text` (raw text): params: competitionCode, season (or infer), timezone; response: importRun summary.
- `GET /archives/import/{runId}`: status and error report for the import.

## 11) Data quality & governance
- Validation: parseable date/time, scores non‑negative, teams mapped to IDs, competition resolvable.
- Duplicate policy: Upsert on natural key; keep the row with richer data (more non‑nulls) and newer provider priority.
- Provenance/audit retained for traceability.
- Timezone normalization to UTC; store source timezone in importRun.

## 12) Performance and scalability
- Batch insert size 500–1,000; use JDBC batching.
- Stream CSV; avoid loading full file in memory.
- Add covering indexes for common H2H queries.
- Cache H2H summaries per (homeId, awayId, competitionId, horizon) with TTL (e.g., 1–6 hours) to keep analysis fast (<300ms typical).

## 13) Rollout plan
- Phase 1 (Backend CSV):
  - Implement CSV import for E0 schema.
  - Persist archives; expose H2H API; basic tests for ingestion + H2H correctness.
- Phase 2 (Text ingestion + Admin UI):
  - Implement rounds‑text parser; add admin upload panel; team alias management UI.
- Phase 3 (Frontend H2H panel):
  - Add H2H panel to fixture view; filters for scope/horizon; integrate caching.

## 14) Acceptance criteria
- Can ingest 50k+ historical rows idempotently without duplicates.
- H2H API returns correct last N meetings and aggregates for sampled pairs.
- Fixture analysis page displays H2H within 300ms (with cache warm).
- Import runs are auditable with clear error logs for rejects.

## 15) Risks and mitigations
- Team naming inconsistencies → mitigate with alias dictionary + review queue for ambiguities.
- Season inference errors from text → allow explicit season param and sanity checks vs league calendar.
- Duplicate conflicts across providers → deterministic upsert policy with provider priority.
- Large files → stream parsing, batch writes, database indexing.

## 16) Appendix: Example E0 columns (partial)
- Core: Div, Date, Time, HomeTeam, AwayTeam, FTHG, FTAG, FTR, HTHG, HTAG, HTR, Referee
- Stats: HS, AS, HST, AST, HF, AF, HC, AC, HY, AY, HR, AR
- Odds: B365H/D/A, BWH/D/A, PSH/PSD/PSA, WHH/WHD/WHA, etc.
- Totals/Asian: B365>2.5, B365<2.5, AHh, B365AHH, B365AHA, etc.

---
This document reflects the assessed practicality, the leverageable capabilities in the current system, and a concrete, incremental plan to deliver the Archives feature end‑to‑end.

# Country or Competition Dropdown – Assessment and Implementation Plan

Prepared: 2025-09-16

This report evaluates expanding the current Country dropdown (used during match/fixture uploads to select the league’s country) into a Country or Competition selector that also includes major international and intercontinental tournaments. It summarizes how the existing feature works today, feasibility, ways to leverage existing code, and a high-level implementation approach. No code has been modified as part of this report.


## 1) Current Feature Overview

What exists today (based on repository review):

- Data model
  - Country entity/table: backend/src/main/java/com/chambua/vismart/model/Country.java
    - JPA entity mapped to table countries with fields code (ISO alpha‑2, PK) and name (unique).
  - League entity/table: backend/src/main/java/com/chambua/vismart/model/League.java
    - Fields: id, name, country, season. Unique constraint: (name, country, season) via uk_league_name_country_season.
    - Note: country is currently stored as a free‑text String on League (not a FK to Country), though a Country table exists.
  - Season entity exists (used by MatchUploadService), but league stores season as String as well for uniqueness. Seasons are also tracked in a Season table and referenced from Match; the dual use is intentional for de‑duplication.

- Upload endpoints
  - MatchUploadController: backend/src/main/java/com/chambua/vismart/controller/MatchUploadController.java
    - CSV: POST /api/matches/upload/csv with params leagueName, country, season, optional seasonId, flags.
    - Text: POST /api/matches/upload/text (JSON body includes leagueName, country, season, optional seasonId, flags).
    - Both paths look up the League by name+country+season to compute diagnostics (completed matches).
  - MatchUploadService: backend/src/main/java/com/chambua/vismart/service/MatchUploadService.java
    - Normalization: normalizeKey trims and collapses spaces for leagueName and country; normalizeSeason standardizes slashes and whitespace.
    - findOrCreateLeague(leagueName, country, season) enforces that all three are required and upserts a League row keyed by (name, country, season).
    - Season inference helpers support single‑year (e.g., 2025) or split‑year (e.g., 2024/2025) seasons, and there is logic to infer year from month for date parsing.
    - Teams are scoped to the resolved League (findOrCreateTeam uses leagueId), so league identity is fundamental to upload behavior.

- Frontend dropdown(s)
  - Static country->league UX seed: frontend/src/app/shared/country-leagues.constant.ts maps a Country string to known league names (for convenience). This appears used to drive admin upload forms with a Country dropdown and league suggestions.
  - The server does not currently validate that League.country matches an existing Country.code/name; it relies on normalized strings provided in requests.

- How the country dropdown is used today
  - Admin chooses Country and League on the frontend. Country value is sent with leagueName and season to the backend upload endpoint.
  - Backend treats country purely as part of the League’s identity. All downstream logic (match de‑duplication, team scoping, analytics) hinges on the resolved League id (unique by name, country, season).

Implications:
- Country is a string identifier forming part of the League’s composite identity. Introducing non‑country competitions means this field needs to support either a traditional country or a competition identity.


## 2) Viability Assessment: Adding Competitions to the Selector

Summary: Feasible with moderate changes. The largest impact areas are league identity, UI labeling and grouping, and ensuring parsing paths accommodate competitions that aren’t country‑bound.

- Pros
  - Opens uploads for international tournaments and continental club competitions without inventing pseudo countries.
  - Aligns data model with real‑world usage where tournaments are not country‑scoped.
  - Reuses most of the existing ingestion pipeline because the pipeline keys on League; competitions can be modeled as leagues within a different namespace/type.
  - Historical coverage (e.g., Confederations Cup) improves data richness.

- Cons / Risks
  - Schema semantics: League.country currently expects a country string. Overloading it to also store competitions blurs semantics and may require consistent naming conventions or an explicit type discriminator.
  - Backend branching: Logic that assumes “country” is a geographic country (e.g., UI labels, grouping, any downstream reports) may need to handle the competition case.
  - Data cleanliness: Without constraints, typos in competition names could fragment data. A canonical source list is important.

- Technical challenges
  - Differentiation: Need a way to distinguish Country vs. Competition for validation, listing, and grouping without breaking the (name, country, season) uniqueness.
  - Lookups and grouping: Existing APIs and DTOs (e.g., GroupedLeagueDTO has a country field) presume a country context. They should be made type‑aware or renamed semantically (e.g., contextLabel).
  - Parsing/stages: Cups and tournaments have stages (group/knockout). The MatchUploadService already has stage detection (isPlayoffStage, mapStageToRound) that can be leveraged but may require additional stage keywords for international tournaments.
  - Season handling: International tournaments may be single‑year events; continental club competitions align to seasons but may differ by calendar.

- User impact
  - Positive: Admins can directly select competitions like “UEFA – Champions League” without workarounds.
  - Usability: The dropdown should be searchable and organized by confederation groups to avoid clutter.
  - Clarity: Rename label to “Country or Competition” and visually tag items (e.g., [Competition]) to avoid confusion.

- Scalability
  - With 50+ competitions, a flat dropdown becomes unwieldy. Use grouped lists (optgroup), headings, or prefixes (e.g., UEFA – Champions League) and a client‑side search field.
  - Performance: Frontend constant list is small; even hundreds of entries are fine for client search. If this list grows or becomes dynamic, consider a backend endpoint to fetch and cache options.

Conclusion: The feature is viable. Minimal viable change can keep the League uniqueness key intact but reinterpret the country field as a "context" (country or competition). A more robust long‑term change introduces an explicit type and normalized references.


## 3) Leveraging Existing Features

- Reuse current ingestion pipeline
  - Keep using League as the unit of identity; competitions are modeled as leagues with a special context label instead of a geographic country.
  - findOrCreateLeague remains usable if we standardize the context string and season.

- Season inference and parsing
  - Existing helpers handle single‑year and split‑year seasons; tournaments commonly use single‑year seasons (e.g., 2022). Keep this unchanged.
  - Stage mapping already recognizes playoff rounds and can be extended with additional keywords (e.g., “Group A”, “Quarter-final”, “Round of 16”) as needed.

- DTOs and grouping
  - GroupedLeagueDTO currently has country, leagueName, groupLabel, options. We can populate country with a contextLabel and use groupLabel like “UEFA — Champions League”. The structure already supports UX grouping without code changes to the DTO itself.

- Frontend mapping
  - The COUNTRY_LEAGUES constant can be extended conceptually to a CONTEXT_OPTIONS map that includes both countries and competitions, grouped by confederation. In the minimal viable approach, we can simply add new top‑level keys like “UEFA” or “FIFA” and prefix item labels.

- Admin ops
  - Existing upload flows, dry‑run, strict mode, and diagnostics continue to work unchanged because they operate per League.


## 4) Implementation Recommendations (No code in this report)

Two implementation paths are provided. Start with the minimal viable approach for speed, then evolve to the robust model if/when needed.

A) Minimal Viable Approach (fastest, low risk)
- Concept
  - Treat the existing League.country String as a generalized contextLabel: either a country name (e.g., “England”) or a competition key (e.g., “UEFA — Champions League”).
  - Update frontend label to “Country or Competition”.
- UI
  - Replace (or augment) the current country dropdown with a combined, searchable list grouped by confederation. Items are strings like:
    - “FIFA — World Cup”
    - “UEFA — Champions League”
    - “CAF — Africa Cup of Nations”
    - Geographic countries remain unprefixed (e.g., “England”) or grouped under a Countries section.
  - Keep the existing country parameter name in requests for backwards compatibility; it now carries the contextLabel.
- Backend
  - No immediate schema change required. Continue to use (leagueName, country/contextLabel, season) as the unique key.
  - Consider adding a lightweight allowlist validation on the server for competition contextLabels to prevent typos during admin input (feature-flagged).
- Data hygiene
  - Publish and maintain a canonical list of competition contextLabels (see section 5).
  - Use strict mode to prevent accidental creation with misspelled contextLabels in production.
- Pros/Cons
  - Pros: No DB migration, minimal code changes, fast rollout.
  - Cons: Semantics of League.country become overloaded; some analytics/reporting labels may need wording updates.

B) Robust Model (clean semantics, future‑proof)
- Concept
  - Introduce Competition entity and an enum ContextType { COUNTRY, COMPETITION }.
  - Replace League.country String with two fields: contextType (enum) and contextKey (string or FK: countryCode when COUNTRY; competitionCode when COMPETITION). Keep a derived displayLabel for UX.
- DB changes
  - New table competitions(id/code, name, confederation, gender if needed, status).
  - Alter leagues: add context_type, context_key, drop or deprecate country.
  - Migration: For existing rows, set context_type=COUNTRY and context_key=country name (or map to Country.code if we normalize names to codes).
- API changes
  - MatchUploadController request param country renamed to contextLabel or accept both with deprecation.
  - Add an endpoint to list contexts grouped by confederation.
- Frontend
  - Fetch contexts dynamically; keep search and grouping.
- Pros/Cons
  - Pros: Clear semantics, validation by FK, easier reporting.
  - Cons: Requires migration and broader refactor.

C) Upload handling details
- If a competition is selected
  - Use the provided contextLabel as League.country for MVP or context_type/context_key for robust model.
  - Skip country-specific logic; current pipeline already does not rely on geopolitical country beyond the unique key, so no special backend branching is needed.
  - Parsing: Continue to use existing stage detection. Optionally extend keywords for international tournaments.
- If a country/league is selected (status quo)
  - Unchanged behavior.

D) Edge cases and special notes
- Hybrid competitions
  - UEFA Nations League: treat as Competition under UEFA.
  - Olympic Football Tournaments: competition under FIFA/IOC — single‑year seasons.
  - Historical/defunct: mark as active=false in a Competition table if implementing robust model; in MVP keep them in the allowlist.
- Canonical naming
  - Use standardized names exactly as listed below to prevent duplicates. Prefix with confederation for clarity.
- Season formats
  - Support single‑year (e.g., 2024) and split‑year (e.g., 2024/2025). Many competitions are single‑year; some continental club competitions align to seasons — both already supported by MatchUploadService.

E) Testing strategy
- Unit/integration tests
  - Upload success when contextLabel is a competition (e.g., UEFA — Champions League) with both played matches and fixtures.
  - Ensure findOrCreateLeague creates/leaves intact leagues with competition context.
  - H2H and analytics remain stable since they key off leagueId; regression tests for H2H repository queries.
- Frontend tests
  - Dropdown renders grouped options; search works; selected value is sent to backend as country/contextLabel.
- Data migration tests (robust model only)
  - Verify existing data maps to context_type=COUNTRY; no uniqueness collisions.

F) Migration considerations
- MVP
  - None required. Existing leagues persist; new competition‑based leagues are simply new rows with unique (leagueName, contextLabel, season).
- Robust model
  - Backfill competitions; migrate leagues; update all places that referenced League.country.


## 5) Competition List to Add (grouped by confederation)

Below is the canonical allowlist for the MVP, suitable for a grouped dropdown. Display suggestion: "CONFEDERATION — Competition".

Global (FIFA)
- FIFA — World Cup
- FIFA — Club World Cup
- FIFA — U-20 World Cup
- FIFA — U-17 World Cup
- FIFA — Women’s World Cup
- FIFA — U-20 Women’s World Cup
- FIFA — U-17 Women’s World Cup
- FIFA — Olympic Football Tournament (Men)
- FIFA — Olympic Football Tournament (Women)
- FIFA — Confederations Cup (historical)

Africa (CAF)
- CAF — Africa Cup of Nations (AFCON)
- CAF — African Nations Championship (CHAN)
- CAF — Champions League
- CAF — Confederation Cup
- CAF — Super Cup
- CAF — Africa Women Cup of Nations
- CAF — Women’s Champions League

Asia (AFC)
- AFC — Asian Cup
- AFC — Champions League Elite
- AFC — Champions League Two
- AFC — AFC Cup
- AFC — Women’s Asian Cup
- AFC — Women’s Champions League

Europe (UEFA)
- UEFA — European Championship (EURO)
- UEFA — Champions League
- UEFA — Europa League
- UEFA — Europa Conference League
- UEFA — Super Cup
- UEFA — Nations League
- UEFA — Women’s European Championship
- UEFA — Women’s Champions League

North/Central America & Caribbean (CONCACAF)
- CONCACAF — Gold Cup
- CONCACAF — Nations League
- CONCACAF — Champions Cup
- CONCACAF — Central American Cup
- CONCACAF — Caribbean Cup
- CONCACAF — W Gold Cup
- CONCACAF — W Championship

South America (CONMEBOL)
- CONMEBOL — Copa América
- CONMEBOL — Copa Libertadores
- CONMEBOL — Copa Sudamericana
- CONMEBOL — Recopa Sudamericana
- CONMEBOL — Copa América Femenina
- CONMEBOL — Copa Libertadores Femenina

Oceania (OFC)
- OFC — Nations Cup
- OFC — Champions League
- OFC — Women’s Nations Cup
- OFC — Women’s Champions League

Other / Intercontinental
- Intercontinental — Panamerican Championship (historical)
- Intercontinental — Arab Cup
- Intercontinental — Afro-Asian Cup of Nations (historical)


## 6) Suggested Next Steps and Rollout Plan

Phase 1: MVP rollout (1–2 days)
- Frontend
  - Rename label to “Country or Competition”.
  - Extend the dropdown data to include the competition list above, grouped by confederation; enable type‑ahead search.
  - Keep request param name country unchanged for compatibility.
- Backend
  - Optionally add a small allowlist validation for known competition contextLabels behind a feature flag.
  - No DB changes.
- QA
  - Smoke test uploads for a competition (e.g., UEFA — Champions League) and a country league (e.g., England — Premier League).

Phase 2: Hardening and UX polish (2–4 days)
- Backend
  - Add endpoint to serve the combined context list dynamically (confederation groupings), cached in memory.
- Frontend
  - Switch from static constant to backend list; add badges to clearly mark [Competition] vs [Country].
- Tests
  - Add integration tests around findOrCreateLeague and analytics queries for competition contexts.

Phase 3: Robust data model (as needed)
- Design and implement Competition entity, ContextType enum, leagues migration, and new APIs. Migrate UI to use typed contexts.


## 7) Risks and Mitigations

- Risk: Data fragmentation due to inconsistent labels.
  - Mitigation: Canonical allowlist, type‑ahead suggestions, optional backend validation.
- Risk: Analytics or grouping views assume country.
  - Mitigation: Treat any “country” label as a generic context; adjust labels and grouping logic in DTO composition.
- Risk: Dropdown usability with many items.
  - Mitigation: Searchable dropdown; logical grouping by confederation; consider collapsible sections.


## 8) Acceptance Criteria for MVP

- Upload forms display “Country or Competition” label.
- The combined dropdown lists both countries and the competitions above, grouped by confederation and searchable.
- Selecting a competition and uploading fixtures or results succeeds end‑to‑end, creating a League keyed by (leagueName, selected contextLabel, season) without backend errors.
- Existing uploads for country leagues continue to work unchanged.


— End of Report —

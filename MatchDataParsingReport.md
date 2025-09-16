# Match Data Parsing Report

This report documents the current raw text match data parsing capability in the ChambuaViSmart football analysis app, focusing on inputs covering past or ongoing seasons. It summarizes accepted input formats, the parser’s processing flow and assumptions (high level, without code), and an assessment of how well alternative formats (e.g., French Ligue 1, Israeli Ligat ha'Al) could fit with the current approach.

Contents
- Accepted Formats (Raw Text)
  - A. Vertical blocks with Day.Month Time header (fixtures and matches)
  - B. Single-line CSV-like text entries (matches)
  - C. CSV file upload (reference)
- Parsing Logic Overview (High Level)
- Error Handling and Validation (What the system flags and tolerates)
- Assessment of Other Formats (Feasibility and gaps)
  - Key differences in the provided examples (France and Israel)
  - Expected failure points with the current parser
  - Suggestions for robust extensions (conceptual)


Accepted Formats (Raw Text)

The backend supports two raw text ingestion paths:
- Fixtures (upcoming or played) via a vertical block format with a Day.Month Time header line.
- Matches (played and, optionally, future fixtures) via either the same vertical block format or a compact single-line, CSV-like format.

Important: For season interpretation, the parser infers the year from the provided season string (e.g., 2024/2025), using July as the split between the first and second season year.

A. Vertical blocks with Day.Month Time header

Where it’s used:
- Fixtures upload: strictly the vertical block format.
- Matches upload: the vertical block format is one of the accepted raw formats.

Header line (date/time):
- Must be a single line in the form:
  - dd.MM. HH:mm or d.M. H:mm
  - Examples: "01.09. 01:30", "1.9. 9:05", "25.12. 20:00"
- This line does not carry the year. The year is inferred from the season:
  - For season "2024/2025": months July–December map to 2024; January–June map to 2025.
  - For season "2025": all months map to 2025.

Block structure after the date/time line:
- The parser expects a fixed sequence of lines immediately following the date line:
  1. Home team (line 1)
  2. Home team (line 2, duplicate — often appears in copy-pastes; the parser tolerates and collapses a duplicated team line)
  3. Away team (line 3)
  4. Away team (line 4, duplicate — tolerated similarly)
  5. Home score (line 5) — numeric, dash, or empty
  6. Away score (line 6) — numeric, dash, or empty

- Team name handling:
  - Empty name lines are not allowed (an error is recorded).
  - Duplicate consecutive lines for the same team are detected and skipped in the matches parser.
  - Basic normalization is applied (trim, collapse whitespace, consistent casing; for fixtures, capitalization of words; for matches, a lowercased normalized key is used for deduplication/identity).

- Scores:
  - Numeric digits (e.g., 0, 1, 2, …) are accepted.
  - Dashes ("-", "–", or "—") and empty strings mean “unknown” (i.e., fixture or not yet played).
  - For fixtures ingestion, missing/dash scores are allowed and the match is treated as UPCOMING.
  - For matches ingestion, whether missing scores are allowed depends on the mode:
    - fixtureMode = false (default for played matches): scores should be present; otherwise, validation will reject the item.
    - fixtureMode = true: missing/dash scores are allowed (treated as SCHEDULED).

- Round headers (optional but recommended):
  - Lines starting with "Round" (case-insensitive) define the current round for subsequent matches.
  - Examples: "Round 1", "Round 33"
  - For matches ingestion, a valid round number must be present before the date/time block (otherwise the block will be rejected). For fixtures ingestion, the round text is recorded as-is (e.g., "Round 33"), and if absent the system stores "Round ?".

Example block that will parse successfully
Round 33
10.05. 22:00
Reims
Reims
St Etienne
St Etienne
0
2

What the parser does with the example above
- Recognizes Round 33.
- Parses 10.05. 22:00 as dd.MM. HH:mm and infers the year from season.
- Home team: Reims (duplicate collapsed).
- Away team: St Etienne (duplicate collapsed).
- Scores: 0–2.
- Status: FINISHED/PLAYED when both scores present; UPCOMING/SCHEDULED otherwise.

Notes and assumptions
- The date/time line must come immediately before the 6-line block. Interleaved non-empty lines (e.g., headers like "AET", "Standings", or country/league names) will break the block parsing.
- Accepted score lines are strictly numeric or a single dash character variant.

B. Single-line CSV-like text entries (matches)

Where it’s used:
- Matches upload supports an additional compact line format. Each match is a single line combining date, teams, and scores, separated by commas.

Line format:
- yyyy-MM-dd, Home Team - Away Team, homeScore-awayScore
- Examples:
  - 2024-08-31, Manchester United - Arsenal, 3-1
  - 2025-01-02, Leeds - Hull, 0-0
- Requirements:
  - A valid Round header must appear before such lines (e.g., "Round 5").
  - Teams are separated by a hyphen surrounded by spaces: "Home - Away".
  - Scores are numeric and separated by a hyphen: "n-n". Missing scores (dash) are treated as null and are only acceptable if fixtureMode is enabled.

C. CSV file upload (reference)

Although this report focuses on raw text, a CSV file upload path exists and uses this strict header and columns:
- Header (exact): round,date,time,home,away,home_score,away_score
- Date/time: date="dd.MM.", time="HH:mm"; the year is inferred from the season like the vertical format.


Parsing Logic Overview (High Level)

- Input normalization:
  - Split text into lines (trimming whitespace, normalizing line breaks).
  - Track the current round when encountering lines starting with "Round".

- Format detection:
  - For matches upload:
    - Try single-line CSV-like parsing if a line contains both a comma and a hyphen in the expected positions.
    - Otherwise, detect date/time header lines matching dd.MM. HH:mm.
  - For fixtures upload:
    - Only the vertical date-header + 6-line block format is recognized.

- Vertical block parsing:
  - Upon a date/time line, look ahead over the next lines to extract home/away names and scores.
  - Duplicate consecutive team lines are tolerated and collapsed.
  - Scores must be purely numeric or an accepted dash; any other content is invalid.

- Year/season inference:
  - The year is derived from the season string. For split seasons (e.g., 2024/2025), months 7–12 map to the first year; months 1–6 to the second year. For single-year seasons (e.g., 2025), that year is used.

- Team normalization:
  - Fixtures: team names are trimmed, whitespace collapsed, lowercased then word-capitalized to reduce duplicates stemming from case/spacing.
  - Matches: a normalizer applies trimmed, single-spaced, lowercased keys for identity and deduplication; the raw name is preserved for display.

- De-duplication and identity:
  - Fixtures: duplicates within the same upload batch are detected using league|dateTime|home|away (case-insensitive) to avoid repeated inserts.
  - Matches: upsert logic uses (league, date, homeTeam, awayTeam) as canonical identity with a fallback to (league, round, homeTeam, awayTeam) if date is missing; duplicates at season/date/team level are guarded against.

- Status assignment:
  - If both scores are present, items are marked as FINISHED/PLAYED.
  - If any score is missing/dash, items are marked as UPCOMING/SCHEDULED.

- Season window validation (matches path):
  - If a Season record (or season string) is provided, the system validates that dates fall within the season window:
    - For 2024/2025: [2024-07-01 .. 2025-06-30]
    - For 2025: [2025-01-01 .. 2025-12-31]
  - In strict mode, out-of-window dates are errors; otherwise, they are recorded as warnings.

- Error collection:
  - The parser accumulates all errors (and warnings) and, if any critical errors exist, returns them in the upload result without applying partial updates (unless specifically configured).


Error Handling and Validation (What the system flags and tolerates)

- Flags as errors:
  - Unrecognized lines that are neither round headers, valid date/time headers, permissible numeric/dash-only score lines, nor valid compact CSV-like lines.
  - Invalid date/time header formats (anything not matching d{1,2}.d{1,2}. H{1,2}:mm).
  - Missing team names in a block.
  - Non-numeric, non-dash score lines.
  - Vertical block started (date line) but block is incomplete (unexpected end of input).
  - For matches (non-fixture mode): blocks without both scores.
  - For matches: round not specified before a match line/block.
  - Season window violations (strict mode) or missing season resolution in strict mode.

- Tolerated:
  - Empty lines (skipped).
  - Duplicate consecutive team-name lines (collapsed).
  - Pure numeric or dash-only stray lines between blocks (these are ignored in fixtures; in matches, pure numbers are ignored unless within an expected score slot during look-ahead).


Assessment of Other Formats (Feasibility and gaps)

The provided examples from French Ligue 1 and Israeli Ligat ha'Al contain additional textual elements and structural variations that the current parser does not support. Key differences and their impact are summarized below.

Key differences observed in the examples
- Headers and sections beyond "Round":
  - Country/league headers: "FRANCE:", "Ligue 1", and sub-headers like "Ligue 1 - Relegation - Play Offs".
  - Non-round sections: "Standings", "Draw", "Semi-finals", "Quarter-finals".
  - Group names: "Championship Group", "Relegation Group".
- Additional markers inside match blocks:
  - AET (after extra time) indicating match status between date line and teams in some extracts.
- Multiple rounds concatenated, with section headers appearing between date lines and match blocks.
- Occasional line breaks or formatting quirks (e.g., a stray "2" before a team line in one Israeli example), which are not purely numeric score lines in the expected positions.

Where and why the current parser fails
- Non-round headers: Any non-empty line that is not a valid date line, a round header, or a pure number/dash will be flagged as an “Unrecognized line format”. That includes lines such as "FRANCE:", "Ligue 1", "Standings", "Draw", "Semi-finals", "Quarter-finals", and group labels.
- AET markers: If "AET" appears immediately after a date line, the parser currently interprets it as a team name candidate (because it’s non-numeric and not a round/date header), which corrupts the block extraction. In the fixtures path (strict 6-line block), "AET" becomes the home team, causing missing team errors and invalid scores.
- Playoff structures: Labels like "Relegation - Play Offs" and sub-rounds (Quarter-finals/Semi-finals) are not recognized as round headers, so they neither set a valid round number nor are they skipped; they trigger errors.
- Date/time variants: Only the dd.MM. HH:mm pattern is understood for the vertical block header, and only yyyy-MM-dd is accepted for the single-line compact format; other date styles (if present) would be rejected.
- Unexpected interleaving: The parser expects the block to be directly after the date line. Intervening annotations (e.g., "Standings" or league headers) break the block logic.

High-level suggestions to extend support (conceptual; no code changes here)
- Preprocessing and normalization layer:
  - Strip or comment-out non-match lines before parsing. For example, explicitly ignore lines matching a library of known non-match headers: country names ending with ":", league names, "Standings", "Draw", playoff stage labels, and group names.
  - Normalize common status markers (e.g., AET, FT, HT) by moving them to the end of the match block or discarding them, so they do not interrupt the block structure.

- Modular format detectors:
  - Implement a detector pipeline that scans the text and identifies which format mode to use by section:
    - Header-only sections (skip),
    - Round sections (set round context),
    - Vertical block sections (current mode),
    - Playoff bracket sections (alternative mode),
    - Single-line entries.
  - For playoffs: treat "Quarter-finals", "Semi-finals", etc., as valid round contexts (e.g., round numbers mapped to stage order) or store stage names verbatim when numeric rounds are unavailable.

- More robust vertical block parser:
  - Allow an optional status line (e.g., AET) between the date and the first team line, and ignore it (or capture it separately as metadata) before continuing.
  - Increase resilience to stray numbers: if a single-digit line appears between team names, verify whether it belongs to a score position; otherwise, skip with a warning.

- Enhanced date parsing:
  - Support alternative day/month separators or formats (e.g., "dd.MM" with optional trailing dot, or locale variants), with a strict mode toggle to retain current behavior when needed.

- Configuration-driven ignore lists:
  - Maintain league-specific ignore keywords and section headers (e.g., known group names in Israel’s playoff splits, French playoff labels) so the preprocessor can skip them safely without fragile heuristics.

- Validation and feedback improvements:
  - Accumulate and present contextual warnings for lines that were intentionally ignored (e.g., “Ignored header: Ligue 1 - Relegation - Play Offs”), aiding users in cleaning inputs while keeping ingestion robust.

Concrete examples from the provided text and their handling today
- Lines like "FRANCE:", "Ligue 1", "Standings", "Ligue 1 - Relegation - Play Offs", "Draw", "Semi-finals", "Quarter-finals", and group labels ("Championship Group", "Relegation Group") will produce “Unrecognized line format” errors in the current raw text parsers.
- A vertical block beginning with a date line that is immediately followed by "AET" will be misparsed (AET treated as a team name). The block will then either fail due to missing/invalid scores or produce incorrect team entries.
- Round headers like "Round 34" are correctly recognized. However, in the Israel examples, the presence of group headers between the round header and date line will interrupt parsing and raise errors.


Summary

- The system currently accepts:
  1) Vertical date-header blocks (dd.MM. HH:mm) followed by Home/Home/Away/Away/HomeScore/AwayScore lines, with optional duplicate team lines collapsed and scores allowing dash/empty for fixtures.
  2) Single-line CSV-like match lines in the form: yyyy-MM-dd, Home - Away, n-n (requires a preceding Round header), typically used for played matches.
  3) CSV file uploads with a strict header and dd.MM. + HH:mm time, year inferred from the season.

- The parser assumes a clean input without unrelated headers between the date line and the team/score lines. It does not currently support sectional headers (country/league), playoff stage names, standings blocks, or AET markers placed within match blocks.

- To support Ligue 1 and Ligat ha'Al extracts as-is, the main gaps are: ignoring non-match headers, recognizing playoff/group stage labels as valid round contexts or ignorable metadata, tolerating optional AET lines, and slightly broadening date parsing. These can be addressed with a preprocessing layer, modular detectors, and small relaxations in the vertical block parser—all without compromising strictness when strict mode is desired.

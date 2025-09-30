package com.chambua.vismart.service;

import com.chambua.vismart.dto.FixturesUploadRequest;
import com.chambua.vismart.dto.UploadResultDTO;
import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.FixtureRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.SeasonRepository;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FixtureUploadService {

    private final LeagueRepository leagueRepository;
    private final FixtureRepository fixtureRepository;
    private final SeasonRepository seasonRepository;

    public FixtureUploadService(LeagueRepository leagueRepository, FixtureRepository fixtureRepository, SeasonRepository seasonRepository) {
        this.leagueRepository = leagueRepository;
        this.fixtureRepository = fixtureRepository;
        this.seasonRepository = seasonRepository;
    }

    private void ensureSeasonEntity(League league, String seasonName) {
        if (league == null) return;
        if (seasonName == null || seasonName.isBlank()) return;
        var existing = seasonRepository.findByLeagueIdAndNameIgnoreCase(league.getId(), seasonName);
        if (existing.isEmpty()) {
            Season s = new Season(league, seasonName.trim(), null, null);
            seasonRepository.save(s);
        }
    }

    @Transactional
    public UploadResultDTO uploadCsv(Long leagueId, String season, boolean fullReplace, String csvText) {
        if (leagueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leagueId is required");
        }
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "League not found"));
        String effectiveSeason = (season != null && !season.isBlank()) ? season.trim() : league.getSeason();
        ensureSeasonEntity(league, effectiveSeason);

        long deleted = 0;
        if (fullReplace) {
            deleted = fixtureRepository.deleteByLeague_Id(league.getId());
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Fixture> fixtures = parseCsv(csvText, league, effectiveSeason, errors, warnings);
        if (!errors.isEmpty()) {
            UploadResultDTO fail = UploadResultDTO.fail("CSV parsing failed", errors);
            fail.setWarnings(warnings);
            return fail;
        }
        // Validate and normalize (require scores, season consistency, duplicate detection, sort by date)
        validateAndNormalize(fixtures, league, effectiveSeason, errors);
        if (!errors.isEmpty()) {
            return UploadResultDTO.fail("CSV validation failed", errors);
        }
        fixtures.sort(java.util.Comparator.comparing(Fixture::getDateTime));
        int processed = 0;
        for (Fixture f : fixtures) {
            var existingOpt = fixtureRepository.findByLeague_IdAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCaseAndDateTime(
                    league.getId(), f.getHomeTeam(), f.getAwayTeam(), f.getDateTime());
            if (existingOpt.isPresent()) {
                Fixture existing = existingOpt.get();
                existing.setRound(f.getRound());
                existing.setHomeScore(f.getHomeScore());
                existing.setAwayScore(f.getAwayScore());
                existing.setStatus(f.getStatus());
                fixtureRepository.save(existing);
            } else {
                fixtureRepository.save(f);
            }
            processed++;
        }
        String msg = String.format("CSV upload processed: %d. Deleted (replace mode): %d", processed, deleted);
        UploadResultDTO ok = UploadResultDTO.ok(processed, deleted, msg);
        // If many auto-corrections, add a hint to verify season string
        long autoCount = warnings.stream().filter(w -> w != null && w.startsWith("[Parsing][AutoCorrect]")).count();
        if (autoCount > 0) {
            ok.setWarnings(warnings);
            if (autoCount > 10) {
                java.util.ArrayList<String> w2 = new java.util.ArrayList<>(warnings);
                w2.add("Many dates auto-corrected—verify season string (e.g., use 2024/2025 for historical data)");
                ok.setWarnings(w2);
            }
        }
        return ok;
    }

    @Transactional
    public UploadResultDTO upload(FixturesUploadRequest req){
        if (req.getLeagueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leagueId is required");
        }
        League league = leagueRepository.findById(req.getLeagueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "League not found"));

        // If a season was provided and differs, we allow edit but do not change League entity here – only use for date year inference
        String season = (req.getSeason() != null && !req.getSeason().isBlank()) ? req.getSeason().trim() : league.getSeason();
        ensureSeasonEntity(league, season);

        long deleted = 0;
        if (req.isFullReplace()) {
            deleted = fixtureRepository.deleteByLeague_Id(league.getId());
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        List<Fixture> fixtures = parseFixtures(req.getRawText(), league, season, errors, warnings, ignored, req.isStrictMode());

        boolean partial = false;
        if (!errors.isEmpty()) {
            // Partial success if we parsed more than half a block per error (best-effort heuristic)
            partial = !fixtures.isEmpty() && errors.size() < fixtures.size();
            if (!partial) {
                UploadResultDTO fail = UploadResultDTO.fail("Parse errors encountered", errors);
                fail.setWarnings(warnings);
                fail.setIgnoredLines(ignored.subList(0, Math.min(10, ignored.size())));
                fail.setProcessedMatches(fixtures.size());
                return fail;
            }
        }
        // Validate and normalize (allow UPCOMING fixtures without scores)
        validateAndNormalize(fixtures, league, season, errors);
        if (!errors.isEmpty() && !partial) {
            UploadResultDTO fail = UploadResultDTO.fail("Validation failed", errors);
            fail.setWarnings(warnings);
            fail.setIgnoredLines(ignored.subList(0, Math.min(10, ignored.size())));
            fail.setProcessedMatches(fixtures.size());
            return fail;
        }
        fixtures.sort(java.util.Comparator.comparing(Fixture::getDateTime));

        int insertedOrUpdated = 0;
        if (req.isFullReplace()) {
            // bulk insert is safe after delete
            fixtureRepository.saveAll(fixtures);
            insertedOrUpdated = fixtures.size();
        } else {
            // incremental upsert by unique key (league, home, away, date_time)
            for (Fixture f : fixtures) {
                var existingOpt = fixtureRepository.findByLeague_IdAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCaseAndDateTime(
                        league.getId(), f.getHomeTeam(), f.getAwayTeam(), f.getDateTime());
                if (existingOpt.isPresent()) {
                    // Update scores/status/round if present (never silently skip)
                    Fixture existing = existingOpt.get();
                    existing.setRound(f.getRound());
                    existing.setHomeScore(f.getHomeScore());
                    existing.setAwayScore(f.getAwayScore());
                    existing.setStatus(f.getStatus());
                    fixtureRepository.save(existing);
                } else {
                    fixtureRepository.save(f);
                }
                insertedOrUpdated++;
            }
        }
        String msg = (partial ? "Partial success: " : "") + String.format("Fixtures for %s (%s) uploaded. Total processed: %d.", league.getName(), season, insertedOrUpdated);
        UploadResultDTO ok = UploadResultDTO.ok(insertedOrUpdated, deleted, msg);
        if (warnings != null && !warnings.isEmpty()) {
            long autoCount = warnings.stream().filter(w -> w != null && w.startsWith("[Parsing][AutoCorrect]")).count();
            if (autoCount > 10) {
                java.util.ArrayList<String> w2 = new java.util.ArrayList<>(warnings);
                w2.add("Many dates auto-corrected—verify season string (e.g., use 2024/2025 for historical data)");
                ok.setWarnings(w2);
            } else {
                ok.setWarnings(warnings);
            }
        }
        ok.setIgnoredLines(ignored.subList(0, Math.min(10, ignored.size())));
        ok.setProcessedMatches(fixtures.size());
        return ok;
    }

    public List<Fixture> parseFixtures(String rawText, League league, String season, List<String> errors, List<String> warnings, List<String> ignored, boolean strictMode){
        List<Fixture> out = new ArrayList<>();
        if (rawText == null) {
            errors.add("Raw text is empty");
            return out;
        }
        String[] rawLines = rawText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        // Preprocess: filter out obvious non-match lines when non-strict
        List<String> linesList = new ArrayList<>();
        for (String rl : rawLines) {
            String t = rl == null ? "" : rl.trim();
            if (t.isEmpty()) continue;
            if (!strictMode && isNonMatchHeader(t)) { ignored.add(t); continue; }
            linesList.add(t);
        }
        String[] lines = linesList.toArray(new String[0]);

        String currentRound = null;
        int i = 0;
        while (i < lines.length){
            String line = lines[i].trim();
            if (line.isEmpty()) { i++; continue; }
            if (line.toLowerCase().startsWith("round")) {
                currentRound = line;
                i++; continue;
            }
            // Expect a date line like dd.MM. HH:mm (flexible)
            if (matchesDateTime(line)){
                String dateLine = line;
                int blockStart = i + 1;
                // Optional status marker line (AET/FT/HT)
                if (blockStart < lines.length && isStatusMarker(lines[blockStart])) {
                    warnings.add("Info: Status marker '" + lines[blockStart] + "' ignored at line " + (blockStart+1));
                    blockStart++;
                }
                if (blockStart + 5 >= lines.length){
                    errors.add("Unexpected end of input after date at line " + (i+1));
                    break;
                }
                String home1 = lines[blockStart].trim();
                String home2 = lines[blockStart+1].trim();
                String away1 = lines[blockStart+2].trim();
                String away2 = lines[blockStart+3].trim();
                String hs = lines[blockStart+4].trim();
                String as = lines[blockStart+5].trim();

                String home = normalizeTeamName(home1.isEmpty() ? home2 : home1);
                String away = normalizeTeamName(away1.isEmpty() ? away2 : away1);
                if (home.equalsIgnoreCase("Postp")) {
                    if (warnings != null) warnings.add("[Upload][Skip] Skipping fixture with home team 'Postp' at lines " + (blockStart+1) + "-" + (blockStart+6));
                    i = blockStart + 6;
                    continue;
                }
                if (home.isEmpty() || away.isEmpty()){
                    errors.add("Missing team name near line " + (blockStart+1));
                }

                Integer homeScore = parseScore(hs);
                Integer awayScore = parseScore(as);
                if (homeScore == null && !isDash(hs) && !hs.isEmpty()){
                    errors.add("Invalid home score at line " + (blockStart+5));
                }
                if (awayScore == null && !isDash(as) && !as.isEmpty()){
                    errors.add("Invalid away score at line " + (blockStart+6));
                }

                try {
                    LocalDateTime dt = parseDateTime(dateLine, season);
                    Fixture f = new Fixture();
                    f.setLeague(league);
                    f.setRound(currentRound != null ? currentRound : "Round ?");
                    f.setDateTime(dt);
                    f.setHomeTeam(home);
                    f.setAwayTeam(away);
                    f.setHomeScore(homeScore);
                    f.setAwayScore(awayScore);
                    // Initial status based on scores
                    f.setStatus((homeScore == null || awayScore == null) ? FixtureStatus.UPCOMING : FixtureStatus.FINISHED);
                    // Smart date validation/correction against Nairobi today
                    java.time.LocalDate todayNairobi = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
                    boolean splitSeason = season != null && season.contains("/");
                    if (f.getDateTime() != null) {
                        java.time.LocalDate inferred = f.getDateTime().toLocalDate();
                        int mo = inferred.getMonthValue();
                        if (inferred.isAfter(todayNairobi)) {
                            if (splitSeason && homeScore != null && awayScore != null && mo >= 1 && mo <= 6) {
                                java.time.LocalDateTime orig = f.getDateTime();
                                f.setDateTime(orig.minusYears(1));
                                warnings.add("[Parsing][AutoCorrect] Shifted date: " + orig.toLocalDate() + " -> " + f.getDateTime().toLocalDate() + " (" + season + ")");
                                // keep FINISHED status
                            } else if (homeScore == null || awayScore == null) {
                                // Future fixture without score stays UPCOMING
                                f.setStatus(FixtureStatus.UPCOMING);
                                warnings.add("[Parsing][Future] Match date=" + inferred + " > today; set UPCOMING");
                            }
                        }
                    }
                    out.add(f);
                } catch (DateTimeParseException ex){
                    errors.add("Invalid date/time at line " + (i+1) + ": '" + dateLine + "'");
                }

                i = blockStart + 6; // move past the 6 lines consumed after date
                continue;
            }

            // Tolerate stray numeric or dash-only lines that may appear between blocks (often pasted scores)
            if (line.matches("^-?\\d+$") || isDash(line)) {
                i++;
                continue;
            }
            if (!strictMode && isNonMatchHeader(line)) { ignored.add(line); i++; continue; }
            // line didn't match anything meaningful
            errors.add("Unexpected line format at " + (i+1) + ": '" + line + "'");
            i++;
        }
        return out;
    }

    public List<Fixture> parseCsv(String csvText, League league, String season, List<String> errors, List<String> warnings) {
        List<Fixture> out = new ArrayList<>();
        if (csvText == null || csvText.isBlank()) {
            errors.add("CSV is empty");
            return out;
        }
        String[] lines = csvText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0) { errors.add("CSV is empty"); return out; }
        String header = lines[0].trim().toLowerCase();
        String expected = "round,date,time,home,away,home_score,away_score";
        if (!header.equals(expected)) {
            errors.add("Invalid CSV header. Expected: " + expected);
            return out;
        }
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm yyyy");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length < 7) {
                errors.add("Line " + (i+1) + ": expected 7 columns, got " + parts.length);
                continue;
            }
            String round = parts[0].trim();
            String date = parts[1].trim();
            String time = parts[2].trim();
            String home = normalizeTeamName(parts[3].trim());
            String away = normalizeTeamName(parts[4].trim());
            String hs = parts[5].trim();
            String as = parts[6].trim();
            if (home.equalsIgnoreCase("Postp")) {
                if (warnings != null) warnings.add("[Upload][Skip] CSV line " + (i+1) + ": skipping fixture with home team 'Postp'");
                continue;
            }
            if (home.isEmpty() || away.isEmpty()) {
                errors.add("Line " + (i+1) + ": missing team name");
                continue;
            }
            Integer homeScore = parseScore(hs);
            Integer awayScore = parseScore(as);
            if (homeScore == null && !hs.isEmpty() && !isDash(hs)) {
                errors.add("Line " + (i+1) + ": invalid home_score");
            }
            if (awayScore == null && !as.isEmpty() && !isDash(as)) {
                errors.add("Line " + (i+1) + ": invalid away_score");
            }
            try {
                String year = String.valueOf(resolveYearFromSeason(date + "." + time, season));
                // We'll parse with the same parser: construct "dd.MM. HH:mm yyyy"
                String composed = date + " " + time + " " + year;
                LocalDateTime dt = LocalDateTime.parse(composed, dtFmt);
                Fixture f = new Fixture();
                f.setLeague(league);
                f.setRound(round.isBlank() ? "Round ?" : round);
                f.setDateTime(dt);
                f.setHomeTeam(home);
                f.setAwayTeam(away);
                f.setHomeScore(homeScore);
                f.setAwayScore(awayScore);
                // Initial status based on provided scores
                f.setStatus((homeScore == null || awayScore == null) ? FixtureStatus.UPCOMING : FixtureStatus.FINISHED);
                // Smart date validation/correction
                java.time.LocalDate todayNairobi = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
                boolean splitSeason = season != null && season.contains("/");
                if (f.getDateTime() != null) {
                    java.time.LocalDate inferred = f.getDateTime().toLocalDate();
                    int mo = inferred.getMonthValue();
                    if (inferred.isAfter(todayNairobi)) {
                        if (splitSeason && homeScore != null && awayScore != null && mo >= 1 && mo <= 6) {
                            java.time.LocalDateTime orig = f.getDateTime();
                            f.setDateTime(orig.minusYears(1));
                            if (warnings != null) warnings.add("[Parsing][AutoCorrect] Shifted date: " + orig.toLocalDate() + " -> " + f.getDateTime().toLocalDate() + " (" + season + ")");
                        } else if (homeScore == null || awayScore == null) {
                            f.setStatus(FixtureStatus.UPCOMING);
                            if (warnings != null) warnings.add("[Parsing][Future] Match date=" + inferred + " > today; set UPCOMING");
                        }
                    }
                }
                out.add(f);
            } catch (Exception ex) {
                errors.add("Line " + (i+1) + ": invalid date/time");
            }
        }
        return out;
    }

    private String normalizeTeamName(String s) {
        if (s == null) return "";
        String t = s.trim();
        // Basic normalization to reduce duplicates from spacing/case issues
        t = t.replaceAll("\\s+", " ").toLowerCase();
        if (t.isBlank()) return t;
        // Capitalize each word
        String[] words = t.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
            if (i < words.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private boolean matchesDateTime(String line){
        // Accept 1–2 digit day, 1–2 digit month, optional trailing dot, and 1–2 digit hour (e.g., "1.9. 9:00", "01.09 13:00")
        String l = line == null ? "" : line.trim();
        return l.matches("^\\d{1,2}\\.\\d{1,2}\\.?\\s*\\d{1,2}:\\d{2}$");
    }
    private boolean isDash(String s){
        if (s == null) return false;
        String t = s.trim();
        return t.equals("-") || t.equals("–") || t.equals("—");
    }
    private Integer parseScore(String s){
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || isDash(t)) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException ex){ return null; }
    }

    private LocalDateTime parseDateTime(String dateLine, String season){
        // Normalize to canonical pattern dd.MM. HH:mm then append year
        String normalized = normalizeDateLine(dateLine);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm yyyy");
        String year = String.valueOf(resolveYearFromSeason(normalized, season));
        String toParse = normalized + " " + year;
        return LocalDateTime.parse(toParse, fmt);
    }

    private int resolveYearFromSeason(String dateLine, String season){
        // Extract month from patterns like d.M. H:mm or d.M H:mm
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.?(?:\\s+)(\\d{1,2}:\\d{2})$").matcher(dateLine.trim());
        int month = 7; // default July if parse fails
        if (m.find()) {
            month = Integer.parseInt(m.group(2));
        }
        if (season != null && season.contains("/")){
            String[] parts = season.split("/");
            int y1 = Integer.parseInt(parts[0]);
            int y2 = Integer.parseInt(parts[1]);
            return (month >= 7) ? y1 : y2;
        }
        try { return Integer.parseInt(season); } catch (Exception e){ return LocalDateTime.now().getYear(); }
    }

    private static boolean isStatusMarker(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        return t.matches("^[A-Z]{1,3}$") && (t.equals("AET") || t.equals("FT") || t.equals("HT"));
    }

    private static boolean isNonMatchHeader(String s) {
        if (s == null) return false;
        String t = s.trim();
        // Heuristics: lines with trailing colon or single-word/short section names, or containing Group/Play Offs
        if (t.matches("^[A-Z][A-Z\u00C0-\u017F\s]+:\\s*$")) return true; // country/section in caps ending with colon
        if (t.equalsIgnoreCase("Standings") || t.equalsIgnoreCase("Draw")) return true;
        if (t.toLowerCase().contains("group") || t.toLowerCase().contains("play off")) return true;
        if (t.equalsIgnoreCase("Semi-finals") || t.equalsIgnoreCase("Quarter-finals") || t.equalsIgnoreCase("Final")) return true;
        return false;
    }

    private static String normalizeDateLine(String dateLine) {
        String ln = dateLine == null ? "" : dateLine.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.?(?:\\s+)(\\d{1,2}:\\d{2})$").matcher(ln);
        if (!m.find()) return ln; // return as-is; caller will validate
        int d = Integer.parseInt(m.group(1));
        int mo = Integer.parseInt(m.group(2));
        String time = m.group(3);
        return String.format("%02d.%02d. %s", d, mo, time);
    }

    // Validation and normalization for fixtures uploads (allow upcoming fixtures without scores)
    private void validateAndNormalize(List<Fixture> fixtures, League league, String season, List<String> errors) {
        if (fixtures == null) return;
        // 1) Do NOT require numeric scores for fixtures: upcoming fixtures may have missing scores ('-').
        //    If both scores are present they must already be numeric from parsing; mixed presence is allowed and treated as UPCOMING.
        // 2) Season consistency check
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture f = fixtures.get(i);
            if (!isWithinSeason(f.getDateTime(), season)) {
                errors.add("Row #" + (i + 1) + ": date " + f.getDateTime().toLocalDate() + " is outside declared season '" + season + "'");
            }
        }
        // 3) Duplicate detection within batch (league, date, home, away)
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture f = fixtures.get(i);
            String key = league.getId() + "|" + f.getDateTime() + "|" + f.getHomeTeam().toLowerCase() + "|" + f.getAwayTeam().toLowerCase();
            if (!seen.add(key)) {
                errors.add("Duplicate match in upload at row #" + (i + 1) + ": " + f.getHomeTeam() + " vs " + f.getAwayTeam() + " on " + f.getDateTime());
            }
        }
        // 4) Round-date alignment check (non-fatal): if round numbers decrease while dates increase, log a warning
        try {
            java.util.List<Fixture> copy = new java.util.ArrayList<>(fixtures);
            copy.sort(java.util.Comparator.comparing(Fixture::getDateTime));
            Integer lastRound = null;
            for (Fixture f : copy) {
                Integer rn = parseRoundNumber(f.getRound());
                if (rn != null && lastRound != null && rn < lastRound) {
                    Logger logger = LoggerFactory.getLogger(FixtureUploadService.class);
                    logger.warn("Round-date alignment warning for league {}: round '{}' appears after later date {}. Dates will be treated as the ordering key.", league.getId(), f.getRound(), f.getDateTime());
                    break; // one warning is enough per batch
                }
                if (rn != null) lastRound = rn;
            }
        } catch (Exception ignore) {}
    }

    private boolean isWithinSeason(java.time.LocalDateTime dt, String season) {
        if (season == null || season.isBlank()) return true; // nothing to validate against
        int year = dt.getYear();
        int month = dt.getMonthValue();
        try {
            if (season.contains("/")) {
                String[] parts = season.split("/");
                int y1 = Integer.parseInt(parts[0]);
                int y2 = Integer.parseInt(parts[1]);
                // Typical season July (7) to June (6)
                if (month >= 7) {
                    return year == y1;
                } else {
                    return year == y2;
                }
            } else {
                int y = Integer.parseInt(season.trim());
                return year == y;
            }
        } catch (Exception e) {
            // If season malformed, better to fail - report as outside season
            return false;
        }
    }

    private Integer parseRoundNumber(String roundText) {
        if (roundText == null) return null;
        String s = roundText.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }
}

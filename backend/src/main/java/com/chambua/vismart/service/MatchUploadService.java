package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchIngestItem;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.MatchStatus;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.util.TeamNameNormalizer;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchUploadService {

    private static final Logger log = LoggerFactory.getLogger(MatchUploadService.class);

    // Thread-local collector for parser auto-correction notes to surface to UI as warnings
    private static final ThreadLocal<java.util.List<String>> AUTO_CORRECT_WARNINGS = ThreadLocal.withInitial(java.util.ArrayList::new);

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchDataValidationService validationService;
    private final SeasonRepository seasonRepository;
    private final CountryRepository countryRepository;

    @Value("${app.enableCompetitions:false}")
    private boolean enableCompetitions;

    private static final Set<String> COMPETITIONS = new java.util.LinkedHashSet<>(java.util.List.of(
            // Global (FIFA)
            "FIFA — World Cup",
            "FIFA — Club World Cup",
            "FIFA — U-20 World Cup",
            "FIFA — U-17 World Cup",
            "FIFA — Women’s World Cup",
            "FIFA — U-20 Women’s World Cup",
            "FIFA — U-17 Women’s World Cup",
            "FIFA — Olympic Football Tournament (Men)",
            "FIFA — Olympic Football Tournament (Women)",
            "FIFA — Confederations Cup (historical)",
            // Africa (CAF)
            "CAF — Africa Cup of Nations (AFCON)",
            "CAF — African Nations Championship (CHAN)",
            "CAF — Champions League",
            "CAF — Confederation Cup",
            "CAF — Super Cup",
            "CAF — Africa Women Cup of Nations",
            "CAF — Women’s Champions League",
            // Asia (AFC)
            "AFC — Asian Cup",
            "AFC — Champions League Elite",
            "AFC — Champions League Two",
            "AFC — AFC Cup",
            "AFC — Women’s Asian Cup",
            "AFC — Women’s Champions League",
            // Europe (UEFA)
            "UEFA — European Championship (EURO)",
            "UEFA — Champions League",
            "UEFA — Europa League",
            "UEFA — Europa Conference League",
            "UEFA — Super Cup",
            "UEFA — Nations League",
            "UEFA — Women’s European Championship",
            "UEFA — Women’s Champions League",
            // CONCACAF
            "CONCACAF — Gold Cup",
            "CONCACAF — Nations League",
            "CONCACAF — Champions Cup",
            "CONCACAF — Central American Cup",
            "CONCACAF — Caribbean Cup",
            "CONCACAF — W Gold Cup",
            "CONCACAF — W Championship",
            // CONMEBOL
            "CONMEBOL — Copa América",
            "CONMEBOL — Copa Libertadores",
            "CONMEBOL — Copa Sudamericana",
            "CONMEBOL — Recopa Sudamericana",
            "CONMEBOL — Copa América Femenina",
            "CONMEBOL — Copa Libertadores Femenina",
            // OFC
            "OFC — Nations Cup",
            "OFC — Champions League",
            "OFC — Women’s Nations Cup",
            "OFC — Women’s Champions League",
            // Other / Intercontinental
            "Intercontinental — Panamerican Championship (historical)",
            "Intercontinental — Arab Cup",
            "Intercontinental — Afro-Asian Cup of Nations (historical)"
    ));

    private static String norm(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private boolean isKnownCompetition(String label) {
        if (label == null) return false;
        String x = norm(label);
        // Normalize hyphen variants to em dash used in canonical list
        x = x.replace("-", "—").replace("–", "—");
        // Case-insensitive comparison on collapsed whitespace
        for (String c : COMPETITIONS) {
            String cc = norm(c);
            if (cc.equalsIgnoreCase(x)) return true;
        }
        return false;
    }

    private boolean isKnownCountryName(String name) {
        if (name == null || name.isBlank()) return false;
        try {
            for (var c : countryRepository.findAllByOrderByNameAsc()) {
                if (c.getName() != null && c.getName().trim().equalsIgnoreCase(name.trim())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // Additional context-aware header check: matches the selected country/competition label or known country/competition names
    private boolean isContextHeaderLine(String line, String contextLabel) {
        if (line == null) return false;
        String t = line.trim();
        if (t.isEmpty()) return false;
        // Direct match against the provided context label (from UI)
        String ctx = normalizeKey(opt(contextLabel));
        String l = normalizeKey(t);
        if (!ctx.isEmpty()) {
            if (l.equalsIgnoreCase(ctx)) return true;
            if (l.equalsIgnoreCase(ctx + ":")) return true;
        }
        // Known competition or country names (if repositories/flags are available)
        String noColon = l.endsWith(":") ? l.substring(0, l.length() - 1).trim() : l;
        try {
            if (isKnownCompetition(noColon)) return true;
        } catch (Exception ignored) {}
        try {
            if (isKnownCountryName(noColon)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    // Public helpers for controller/UI
    public boolean isCompetitionsFeatureEnabled() { return enableCompetitions; }
    public boolean isCompetitionLabel(String label) { return isKnownCompetition(label); }

    @Autowired
    public MatchUploadService(LeagueRepository leagueRepository, TeamRepository teamRepository, MatchRepository matchRepository, MatchDataValidationService validationService, SeasonRepository seasonRepository, CountryRepository countryRepository) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.validationService = validationService;
        this.seasonRepository = seasonRepository;
        this.countryRepository = countryRepository;
    }

    // Backward-compatible constructor for existing tests
    public MatchUploadService(LeagueRepository leagueRepository, TeamRepository teamRepository, MatchRepository matchRepository, MatchDataValidationService validationService, SeasonRepository seasonRepository) {
        this(leagueRepository, teamRepository, matchRepository, validationService, seasonRepository, null);
    }

    public record UploadResult(boolean success, List<String> errors, int insertedCount, long deletedCount,
                                List<UpdateLog> updated, List<SkipLog> skipped, List<WarnLog> warnings) {}

    public record UpdateLog(Long fixtureId, String homeTeam, String awayTeam, String result, String status) {}
    public record SkipLog(String homeTeam, String awayTeam, String reason) {}
    public record WarnLog(String homeTeam, String awayTeam, String reason) {}

    // Helper for smart date correction
    private record DateCorrection(java.time.LocalDate date, boolean autoCorrected, String warning) {}

    private DateCorrection correctDateIfMisInferred(java.time.LocalDate date, String seasonStr, Integer homeGoals, Integer awayGoals) {
        if (date == null) return new DateCorrection(null, false, null);
        String season = normalizeSeason(seasonStr);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
        boolean split = season != null && !season.isBlank() && season.contains("/");
        boolean scored = homeGoals != null && awayGoals != null;
        int month = date.getMonthValue();
        if (split && scored && date.isAfter(today) && month >= 1 && month <= 6) {
            java.time.LocalDate newDate = date.minusYears(1);
            String warn = "[Parsing][AutoCorrect] Shifted date: " + date + " -> " + newDate + " (" + season + ")";
            return new DateCorrection(newDate, true, warn);
        }
        if (date.isAfter(today) && !scored) {
            String warn = "[Parsing][Future] Match date=" + date + " > today; set UPCOMING";
            return new DateCorrection(date, false, warn);
        }
        return new DateCorrection(date, false, null);
    }

    @Transactional
    public UploadResult uploadCsv(String leagueName, String country, String season, Long seasonId, MultipartFile file, boolean fullReplace, boolean incrementalUpdate, boolean fixtureMode, boolean strict, boolean dryRun, boolean allowSeasonAutoCreate) {
        List<String> errors = new ArrayList<>();
        List<UpdateLog> updatedLogs = new ArrayList<>();
        List<SkipLog> skippedLogs = new ArrayList<>();
        List<WarnLog> warnLogs = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            errors.add("CSV file is empty");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        // Competition/country context validation (feature-flagged)
        String ctx = normalizeKey(opt(country));
        if (enableCompetitions) {
            boolean comp = isKnownCompetition(ctx);
            boolean cntry = isKnownCountryName(ctx);
            if (comp) {
                log.info("[Upload][Competition] Detected competition context: {}", ctx);
            } else if (!cntry) {
                log.warn("[Upload][Validation] Unknown contextLabel: {}", ctx);
                warnLogs.add(new WarnLog(null, null, "[Upload][Validation] Unknown contextLabel: " + ctx + "; check canonical spelling"));
            }
        }
        League league = findOrCreateLeague(leagueName, ctx, season);
        Season seasonEntity = null;
        if (seasonId != null) {
            seasonEntity = seasonRepository.findById(seasonId).orElse(null);
        }
        if (seasonEntity == null) {
            String normalizedSeason = normalizeSeason(season);
            if (!normalizedSeason.isBlank()) {
                seasonEntity = seasonRepository.findByLeagueIdAndNameIgnoreCase(league.getId(), normalizedSeason).orElse(null);
            }
            if (seasonEntity == null && allowSeasonAutoCreate) {
                if (normalizedSeason == null || normalizedSeason.isBlank()) {
                    throw new IllegalArgumentException("Please provide a season (e.g. 2025/2026) when creating a new league.");
                }
                Season s = new Season();
                s.setLeague(league);
                s.setName(normalizedSeason);
                seasonEntity = seasonRepository.save(s);
            }
            if (seasonEntity == null && strict) {
                throw new IllegalArgumentException("Season not found for league; provide seasonId or enable allowSeasonAutoCreate");
            }
        }

        // 1) Parse to items (no persistence yet)
        List<MatchIngestItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                errors.add("CSV has no content");
                return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
            }
            String[] cols = normalizeHeader(header);
            Map<String, Integer> rawIndex = buildIndexMap(cols);

            // Resolve indices using aliases
            Integer dateIdx = findIndex(rawIndex, DATE_ALIASES).orElse(null);
            Integer homeIdx = findIndex(rawIndex, HOME_TEAM_ALIASES).orElse(null);
            Integer awayIdx = findIndex(rawIndex, AWAY_TEAM_ALIASES).orElse(null);
            Integer hgIdx = findIndex(rawIndex, HOME_GOALS_ALIASES).orElse(null);
            Integer agIdx = findIndex(rawIndex, AWAY_GOALS_ALIASES).orElse(null);
            Optional<Integer> roundIdxOpt = findIndex(rawIndex, ROUND_ALIASES);

            List<String> missing = new ArrayList<>();
            if (dateIdx == null) missing.add("date");
            if (homeIdx == null) missing.add("homeTeam");
            if (awayIdx == null) missing.add("awayTeam");
            // Goals columns are required only for match-result uploads (not fixture mode)
            if (!fixtureMode) {
                if (hgIdx == null) missing.add("homeGoals");
                if (agIdx == null) missing.add("awayGoals");
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("CSV missing required columns (accepted aliases in brackets): " + missing +
                        " | date[date,match_date,matchdate,kickoff,played_at,playedat]; homeTeam[hometeam,home,home_team]; awayTeam[awayteam,away,away_team]; homeGoals[homegoals,fthg,home_score]; awayGoals[awaygoals,ftag,away_score]; optional round[round,matchweek,mw,gameweek,gw,matchday,week]");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = splitCsv(line);
                try {
                    LocalDate date = parseDate(getByIndex(parts, dateIdx, "date"));
                    int round = roundIdxOpt.map(idx -> safeParseInt(getByIndex(parts, idx, "round"), 1)).orElse(1);
                    String homeName = getByIndex(parts, homeIdx, "homeTeam");
                    String awayName = getByIndex(parts, awayIdx, "awayTeam");
                    Integer homeGoals = parseNullableInt(getByIndex(parts, hgIdx, "homeGoals"));
                    Integer awayGoals = parseNullableInt(getByIndex(parts, agIdx, "awayGoals"));

                    items.add(new MatchIngestItem(league.getId(), league.getName(), country, season, date, round, homeName, awayName, homeGoals, awayGoals));
                } catch (Exception ex) {
                    errors.add("Row error: " + ex.getMessage() + " | line=\"" + line + "\"");
                }
            }
        } catch (IOException e) {
            errors.add("Failed to read CSV: " + e.getMessage());
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }

        // 2) Validate
        var vr = validationService.validate(items, fixtureMode);
        if (!vr.isValid()) {
            errors.addAll(vr.errorsAsStrings());
            warnLogs.addAll(vr.warningsAsStrings().stream().map(w -> new WarnLog(null, null, w)).toList());
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }

        // 2b) Season date-window validation (strict by default)
        LocalDate winStart = null;
        LocalDate winEnd = null;
        if (seasonEntity != null) {
            winStart = seasonEntity.getStartDate();
            winEnd = seasonEntity.getEndDate();
        }
        if ((winStart == null || winEnd == null)) {
            String seasonStr = seasonEntity != null ? seasonEntity.getName() : normalizeSeason(season);
            if (seasonStr != null && !seasonStr.isBlank()) {
                if (seasonStr.matches("^\\d{4}/\\d{4}$")) {
                    int y1 = Integer.parseInt(seasonStr.substring(0,4));
                    int y2 = Integer.parseInt(seasonStr.substring(5,9));
                    winStart = LocalDate.of(y1, 7, 1);
                    winEnd = LocalDate.of(y2, 6, 30);
                } else if (seasonStr.matches("^\\d{4}$")) {
                    int y = Integer.parseInt(seasonStr);
                    winStart = LocalDate.of(y, 1, 1);
                    winEnd = LocalDate.of(y, 12, 31);
                }
            }
        }
        if (strict && seasonEntity == null) {
            errors.add("Season not resolved; cannot validate window in strict mode");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        if (winStart != null && winEnd != null) {
            for (MatchIngestItem it : items) {
                LocalDate d = it.getDate();
                if (d != null && (d.isBefore(winStart) || d.isAfter(winEnd))) {
                    String msg = "Out-of-window match date " + d + " not within [" + winStart + ".." + winEnd + "] for season";
                    if (strict) {
                        errors.add(msg + " | Round " + it.getRound() + ": " + it.getHomeTeamName() + " vs " + it.getAwayTeamName());
                    } else {
                        warnLogs.add(new WarnLog(it.getHomeTeamName(), it.getAwayTeamName(), msg));
                    }
                }
            }
            if (strict && !errors.isEmpty()) {
                return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
            }
        }

        if (dryRun) {
            // Preview only – do not persist
            return new UploadResult(errors.isEmpty(), errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }

        // 3) Persist
        long deleted = 0;
        if (fullReplace) {
            deleted = matchRepository.deleteByLeague(league);
        }
        int inserted = 0;
        Set<String> seenKeys = new HashSet<>();
        for (MatchIngestItem it : items) {
            LocalDate date = it.getDate();
            int round = Optional.ofNullable(it.getRound()).orElse(1);
            String homeName = it.getHomeTeamName();
            String awayName = it.getAwayTeamName();
            Integer homeGoals = fixtureMode ? it.getHomeGoals() : Optional.ofNullable(it.getHomeGoals()).orElse(0);
            Integer awayGoals = fixtureMode ? it.getAwayGoals() : Optional.ofNullable(it.getAwayGoals()).orElse(0);

            if (incrementalUpdate) {
                Optional<Team> homeOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, homeName.trim());
                Optional<Team> awayOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, awayName.trim());
                if (homeOpt.isEmpty() || awayOpt.isEmpty()) {
                    skippedLogs.add(new SkipLog(homeName, awayName, "No matching fixture found (team not found)"));
                } else {
                    Team home = homeOpt.get();
                    Team away = awayOpt.get();
                    String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                    if (seenKeys.add(key)) {
                        Optional<Match> byRound = matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId());
                        Match target = byRound.orElseGet(() -> {
                            List<Match> lst = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(league.getId(), home.getId(), away.getId());
                            return lst.stream().filter(m -> m.getHomeGoals() != null && m.getAwayGoals() != null && m.getHomeGoals() == 0 && m.getAwayGoals() == 0)
                                    .findFirst().orElse(null);
                        });
                        if (target == null) {
                            // Try to match a null-goals fixture as fallback
                            if (byRound.isEmpty()) {
                                java.util.List<Match> lst2 = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(league.getId(), home.getId(), away.getId());
                                target = lst2.stream().filter(m -> m.getHomeGoals() == null && m.getAwayGoals() == null).findFirst().orElse(null);
                            }
                        }
                        if (target == null) {
                            // No existing placeholder match found – create it from this incremental result
                            int ins = upsertMatch(league, home, away, date, round, homeGoals, awayGoals, seasonEntity);
                            // Find the newly created/updated row for logging (best-effort by date if provided)
                            java.util.Optional<Match> created = (date != null)
                                    ? matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(league.getId(), home.getId(), away.getId(), date)
                                    : matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId());
                            Long mid = created.map(Match::getId).orElse(null);
                            updatedLogs.add(new UpdateLog(mid, homeName, awayName, String.valueOf(homeGoals) + "-" + String.valueOf(awayGoals), ins > 0 ? "Created" : "Updated"));
                        } else {
                            if (date != null && target.getDate() != null && !date.equals(target.getDate())) {
                                warnLogs.add(new WarnLog(homeName, awayName, "Date mismatch"));
                            }
                            boolean changed = false;
                            if (date != null && !date.equals(target.getDate())) { target.setDate(date); changed = true; }
                            if (!Objects.equals(homeGoals, target.getHomeGoals()) || !Objects.equals(awayGoals, target.getAwayGoals())) {
                                target.setHomeGoals(homeGoals);
                                target.setAwayGoals(awayGoals);
                                // set status based on goals
                                if (homeGoals != null && awayGoals != null) {
                                    target.setStatus(MatchStatus.PLAYED);
                                } else {
                                    target.setStatus(MatchStatus.SCHEDULED);
                                }
                                changed = true;
                            }
                            if (seasonEntity != null && (target.getSeason() == null || !seasonEntity.getId().equals(target.getSeason().getId()))) { target.setSeason(seasonEntity); changed = true; }
                            if (changed) {
                                matchRepository.save(target);
                            }
                            updatedLogs.add(new UpdateLog(target.getId(), homeName, awayName, homeGoals + "-" + awayGoals, "Finished"));
                        }
                    }
                }
            } else {
                Team home = findOrCreateTeam(league, homeName);
                Team away = findOrCreateTeam(league, awayName);
                String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                if (seenKeys.add(key)) {
                    inserted += upsertMatch(league, home, away, date, round, homeGoals, awayGoals, seasonEntity);
                }
            }
        }
        boolean ok = errors.isEmpty();
        return new UploadResult(ok, errors, inserted, deleted, updatedLogs, skippedLogs, warnLogs);
    }

    @Transactional
    public UploadResult uploadText(String leagueName, String country, String season, Long seasonId, String text, boolean fullReplace, boolean incrementalUpdate, boolean fixtureMode, boolean autoCreateTeams, boolean strict, boolean dryRun, boolean allowSeasonAutoCreate) {
        List<String> errors = new ArrayList<>();
        List<UpdateLog> updatedLogs = new ArrayList<>();
        List<SkipLog> skippedLogs = new ArrayList<>();
        List<WarnLog> warnLogs = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            errors.add("Text content is empty");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        // Competition/country context validation (feature-flagged)
        String ctx = normalizeKey(opt(country));
        if (enableCompetitions) {
            boolean comp = isKnownCompetition(ctx);
            boolean cntry = isKnownCountryName(ctx);
            if (comp) {
                log.info("[Upload][Competition] Detected competition context: {}", ctx);
            } else if (!cntry) {
                log.warn("[Upload][Validation] Unknown contextLabel: {}", ctx);
                warnLogs.add(new WarnLog(null, null, "[Upload][Validation] Unknown contextLabel: " + ctx + "; check canonical spelling"));
            }
        }
        League league = findOrCreateLeague(leagueName, ctx, season);
        Season seasonEntity = null;
        if (seasonId != null) {
            seasonEntity = seasonRepository.findById(seasonId).orElse(null);
        }
        // If seasonId wasn't provided or season not found, resolve Season entity by league + season name
        if (seasonEntity == null) {
            String normalizedSeason = normalizeSeason(season);
            if (normalizedSeason != null && !normalizedSeason.isBlank()) {
                seasonEntity = seasonRepository.findByLeagueIdAndNameIgnoreCase(league.getId(), normalizedSeason).orElse(null);
            }
            if (seasonEntity == null && allowSeasonAutoCreate) {
                if (normalizedSeason == null || normalizedSeason.isBlank()) {
                    throw new IllegalArgumentException("Please provide a season (e.g. 2025/2026) when creating a new league.");
                }
                Season s = new Season();
                s.setLeague(league);
                s.setName(normalizedSeason);
                seasonEntity = seasonRepository.save(s);
            }
            if (seasonEntity == null && strict) {
                throw new IllegalArgumentException("Season not found for league; provide seasonId or enable allowSeasonAutoCreate");
            }
        }

        // 1) Parse to items first
        List<MatchIngestItem> items = new ArrayList<>();
        int currentRound = -1;
        String[] lines = text.split("\r?\n");
        // Prepare auto-correction warnings collection for this thread
        try { AUTO_CORRECT_WARNINGS.get().clear(); } catch (Exception ignored) {}

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            // Ignore non-match headers/sections (heuristic) in non-strict behavior (strict is handled at validation level)
            // Ignore explicit context header lines (country or competition label) in non-strict mode
            if (!strict && isContextHeaderLine(line, country)) {
                warnLogs.add(new WarnLog(null, null, "Ignored line: " + line));
                continue;
            }
            if (isLikelyNonMatchHeader(line)) {
                warnLogs.add(new WarnLog(null, null, "Ignored line: " + line));
                continue;
            }

            // Playoff stages (e.g., Final, Semi-finals, Quarter-finals) are treated as round context
            if (isPlayoffStage(line)) {
                currentRound = mapStageToRound(line);
                if (currentRound <= 0) {
                    errors.add("Invalid playoff stage header: " + line);
                }
                continue;
            }

            if (line.toLowerCase().startsWith("round")) {
                currentRound = parseRoundNumber(line);
                if (currentRound <= 0) {
                    errors.add("Invalid round header: " + line);
                }
                continue;
            }

            if (line.contains(",") && line.contains("-")) {
                try {
                    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    String[] segments = Arrays.stream(line.split(",")).map(String::trim).toArray(String[]::new);
                    if (segments.length < 3) {
                        throw new IllegalArgumentException("Line must have date, teams, score: " + line);
                    }
                    LocalDate date = LocalDate.parse(segments[0], df);
                    String[] teamsPart = segments[1].split("-");
                    if (teamsPart.length != 2) throw new IllegalArgumentException("Teams part must be 'Home - Away'");
                    String homeName = teamsPart[0].trim();
                    String awayName = teamsPart[1].trim();
                    String[] scorePart = segments[2].split("-");
                    if (scorePart.length != 2) throw new IllegalArgumentException("Score must be 'home-away'");
                    String hgStr = scorePart[0].trim();
                    String agStr = scorePart[1].trim();
                    Integer homeGoals = parseNullableInt(hgStr);
                    Integer awayGoals = parseNullableInt(agStr);
                    // Do not coerce nulls to 0; non-fixture mode requires goals to be provided and valid via validation.

                    if (currentRound <= 0) {
                        errors.add("Round not specified for line: " + line);
                        continue;
                    }
                    items.add(new MatchIngestItem(league.getId(), league.getName(), country, season, date, currentRound, homeName, awayName, homeGoals, awayGoals));
                    continue;
                } catch (Exception ex) {
                    // fall through to try vertical parsing and also record the error later if fails
                }
            }

            if (isDayMonthTimeLine(line)) {
                try {
                    LocalDate date = parseDayMonthToDate(line, season);
                    String homeName = null;
                    String awayName = null;
                    Integer homeGoals = null;
                    Integer awayGoals = null;

                    int j = i + 1;
                    int maxLookahead = 12;
                    int steps = 0;
                    // Optional status marker directly after date
                    if (j < lines.length) {
                        String maybeStatus = lines[j] == null ? "" : lines[j].trim();
                        if (isStatusMarker(maybeStatus)) {
                            warnLogs.add(new WarnLog(null, null, "Ignored status marker '" + maybeStatus + "' after date " + line));
                            j++; steps++;
                        }
                    }
                    while (j < lines.length && steps < maxLookahead) {
                        String lnRaw = lines[j];
                        String ln = lnRaw == null ? "" : lnRaw.trim();
                        if (!ln.isEmpty()) {
                            if (ln.toLowerCase().startsWith("round") || isDayMonthTimeLine(ln)) {
                                break;
                            }
                            if (homeName == null) {
                                if (!isPureNumber(ln)) {
                                    homeName = ln;
                                    if (j + 1 < lines.length) {
                                        String nextLn = lines[j + 1] == null ? "" : lines[j + 1].trim();
                                        if (!nextLn.isEmpty() && nextLn.equalsIgnoreCase(homeName)) { j++; steps++; }
                                    }
                                }
                            } else if (awayName == null) {
                                if (!isPureNumber(ln)) {
                                    if (!ln.equalsIgnoreCase(homeName)) {
                                        awayName = ln;
                                        if (j + 1 < lines.length) {
                                            String nextLn = lines[j + 1] == null ? "" : lines[j + 1].trim();
                                            if (!nextLn.isEmpty() && nextLn.equalsIgnoreCase(awayName)) { j++; steps++; }
                                        }
                                    }
                                }
                            } else if (homeGoals == null) {
                                if (isPureNumber(ln)) {
                                    homeGoals = Integer.parseInt(ln);
                                } else if (ln.equals("-") || ln.equals("–") || ln.equals("—")) {
                                    homeGoals = null; // placeholder for fixture
                                }
                            } else if (awayGoals == null) {
                                if (isPureNumber(ln)) {
                                    awayGoals = Integer.parseInt(ln);
                                    j++; steps++; break;
                                } else if (ln.equals("-") || ln.equals("–") || ln.equals("—")) {
                                    awayGoals = null;
                                    j++; steps++; break;
                                }
                            }
                        }
                        j++; steps++;
                    }

                    if (currentRound <= 0) {
                        errors.add("Round not specified for block starting at date: " + line);
                        i = j - 1;
                        continue;
                    }
                    if (homeName == null || awayName == null || (!fixtureMode && (homeGoals == null || awayGoals == null))) {
                        errors.add("Could not parse match block after date: " + line);
                        i = j - 1;
                        continue;
                    }

                    items.add(new MatchIngestItem(league.getId(), league.getName(), country, season, date, currentRound, homeName, awayName, homeGoals, awayGoals));
                    i = j - 1;
                    continue;
                } catch (Exception ex) {
                    errors.add("Line error: " + ex.getMessage() + " | line=\"" + line + "\"");
                    continue;
                }
            }

            if (isPureNumber(line)) { continue; }
            if (strict) {
                errors.add("Unrecognized line format: " + line);
            } else {
                warnLogs.add(new WarnLog(null, null, "Ignored line: " + line));
            }
        }

        // Drain auto-correction warnings collected during parsing
        try {
            java.util.List<String> acw = AUTO_CORRECT_WARNINGS.get();
            if (acw != null && !acw.isEmpty()) {
                for (String w : acw) { warnLogs.add(new WarnLog(null, null, w)); }
                acw.clear();
            }
        } catch (Exception ignored) {}

        // 2) Validate
        var vr = validationService.validate(items, fixtureMode);
        if (!vr.isValid()) {
            errors.addAll(vr.errorsAsStrings());
            warnLogs.addAll(vr.warningsAsStrings().stream().map(w -> new WarnLog(null, null, w)).toList());
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }

        // 2b) Season date-window validation (strict by default)
        LocalDate winStart = null;
        LocalDate winEnd = null;
        if (seasonEntity != null) {
            winStart = seasonEntity.getStartDate();
            winEnd = seasonEntity.getEndDate();
        }
        if ((winStart == null || winEnd == null)) {
            String seasonStr = seasonEntity != null ? seasonEntity.getName() : normalizeSeason(season);
            if (seasonStr != null && !seasonStr.isBlank()) {
                if (seasonStr.matches("^\\d{4}/\\d{4}$")) {
                    int y1 = Integer.parseInt(seasonStr.substring(0,4));
                    int y2 = Integer.parseInt(seasonStr.substring(5,9));
                    winStart = LocalDate.of(y1, 7, 1);
                    winEnd = LocalDate.of(y2, 6, 30);
                } else if (seasonStr.matches("^\\d{4}$")) {
                    int y = Integer.parseInt(seasonStr);
                    winStart = LocalDate.of(y, 1, 1);
                    winEnd = LocalDate.of(y, 12, 31);
                }
            }
        }
        if (strict && seasonEntity == null) {
            errors.add("Season not resolved; cannot validate window in strict mode");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        if (winStart != null && winEnd != null) {
            for (MatchIngestItem it : items) {
                LocalDate d = it.getDate();
                if (d != null && (d.isBefore(winStart) || d.isAfter(winEnd))) {
                    String msg = "Out-of-window match date " + d + " not within [" + winStart + ".." + winEnd + "] for season";
                    if (strict) {
                        errors.add(msg + " | Round " + it.getRound() + ": " + it.getHomeTeamName() + " vs " + it.getAwayTeamName());
                    } else {
                        warnLogs.add(new WarnLog(it.getHomeTeamName(), it.getAwayTeamName(), msg));
                    }
                }
            }
            if (strict && !errors.isEmpty()) {
                return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
            }
        }

        if (dryRun) {
            // Preview only – do not persist
            return new UploadResult(errors.isEmpty(), errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }

        // 3) Persist
        long deleted = 0;
        if (fullReplace) {
            deleted = matchRepository.deleteByLeague(league);
        }
        int inserted = 0;
        Set<String> seenKeys = new HashSet<>();
        for (MatchIngestItem it : items) {
            LocalDate date = it.getDate();
            int round = Optional.ofNullable(it.getRound()).orElse(1);
            String homeName = it.getHomeTeamName();
            String awayName = it.getAwayTeamName();
            Integer homeGoals = fixtureMode ? it.getHomeGoals() : Optional.ofNullable(it.getHomeGoals()).orElse(0);
            Integer awayGoals = fixtureMode ? it.getAwayGoals() : Optional.ofNullable(it.getAwayGoals()).orElse(0);

            if (incrementalUpdate) {
                Optional<Team> homeOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, homeName.trim());
                Optional<Team> awayOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, awayName.trim());
                if (homeOpt.isEmpty() || awayOpt.isEmpty()) {
                    skippedLogs.add(new SkipLog(homeName, awayName, "No matching fixture found (team not found)"));
                } else {
                    Team home = homeOpt.get();
                    Team away = awayOpt.get();
                    String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                    if (seenKeys.add(key)) {
                        Optional<Match> byRound = matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId());
                        Match target = byRound.orElseGet(() -> {
                            List<Match> lst = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(league.getId(), home.getId(), away.getId());
                            return lst.stream().filter(m -> m.getHomeGoals() != null && m.getAwayGoals() != null && m.getHomeGoals() == 0 && m.getAwayGoals() == 0)
                                    .findFirst().orElse(null);
                        });
                        if (target == null) {
                            // Try to match a null-goals fixture as fallback
                            if (byRound.isEmpty()) {
                                java.util.List<Match> lst2 = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(league.getId(), home.getId(), away.getId());
                                target = lst2.stream().filter(m -> m.getHomeGoals() == null && m.getAwayGoals() == null).findFirst().orElse(null);
                            }
                        }
                        if (target == null) {
                            // No existing placeholder match found – create it from this incremental result
                            int ins = upsertMatch(league, home, away, date, round, homeGoals, awayGoals, seasonEntity);
                            // Find the newly created/updated row for logging (best-effort by date if provided)
                            java.util.Optional<Match> created = (date != null)
                                    ? matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(league.getId(), home.getId(), away.getId(), date)
                                    : matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId());
                            Long mid = created.map(Match::getId).orElse(null);
                            updatedLogs.add(new UpdateLog(mid, homeName, awayName, String.valueOf(homeGoals) + "-" + String.valueOf(awayGoals), ins > 0 ? "Created" : "Updated"));
                        } else {
                            if (date != null && target.getDate() != null && !date.equals(target.getDate())) {
                                warnLogs.add(new WarnLog(homeName, awayName, "Date mismatch"));
                            }
                            boolean changed = false;
                            if (date != null && !date.equals(target.getDate())) { target.setDate(date); changed = true; }
                            if (!Objects.equals(homeGoals, target.getHomeGoals()) || !Objects.equals(awayGoals, target.getAwayGoals())) {
                                target.setHomeGoals(homeGoals);
                                target.setAwayGoals(awayGoals);
                                // set status based on goals
                                if (homeGoals != null && awayGoals != null) {
                                    target.setStatus(MatchStatus.PLAYED);
                                } else {
                                    target.setStatus(MatchStatus.SCHEDULED);
                                }
                                changed = true;
                            }
                            if (changed) matchRepository.save(target);
                            updatedLogs.add(new UpdateLog(target.getId(), homeName, awayName, homeGoals + "-" + awayGoals, "Finished"));
                        }
                    }
                }
            } else {
                if (autoCreateTeams) {
                    // For historical/old season uploads, accept newly promoted or previously untracked teams
                    Team home = findOrCreateTeam(league, homeName);
                    Team away = findOrCreateTeam(league, awayName);
                    String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                    if (seenKeys.add(key)) {
                        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
                        boolean autoCorrectedNow = false;
                        String seasonStr = (seasonEntity != null && seasonEntity.getName() != null) ? seasonEntity.getName() : normalizeSeason(season);
                        if (date != null && seasonStr != null && seasonStr.contains("/") && date.isAfter(today) && date.getMonthValue() <= 6 && homeGoals != null && awayGoals != null) {
                            java.time.LocalDate original = date;
                            date = date.minusYears(1);
                            autoCorrectedNow = true;
                            warnLogs.add(new WarnLog(homeName, awayName, "[Parsing][AutoCorrect] Shifted date: " + original + " -> " + date + " (" + seasonStr + ")"));
                        } else if (date != null && date.isAfter(today) && (homeGoals == null || awayGoals == null)) {
                            warnLogs.add(new WarnLog(homeName, awayName, "[Parsing][Future] Match date=" + date + " > today; set UPCOMING"));
                        }
                        inserted += upsertMatch(league, home, away, date, round, homeGoals, awayGoals, seasonEntity);
                        if (autoCorrectedNow && date != null) {
                            java.util.Optional<Match> created = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(league.getId(), home.getId(), away.getId(), date);
                            if (created.isPresent() && !created.get().isAutoCorrected()) {
                                Match m = created.get();
                                m.setAutoCorrected(true);
                                matchRepository.save(m);
                            }
                        }
                    }
                } else {
                    // Strict team validation for Raw Text Upload: ensure teams already exist in this league (name-based for backward compatibility)
                    Optional<Team> homeOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, homeName.trim());
                    Optional<Team> awayOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, awayName.trim());
                    boolean missing = false;
                    boolean homeMissing = false;
                    boolean awayMissing = false;
                    if (homeOpt.isEmpty()) {
                        warnLogs.add(new WarnLog(homeName, "", "Skipped unrecognized team entry: " + homeName));
                        missing = true;
                        homeMissing = true;
                    }
                    if (awayOpt.isEmpty()) {
                        warnLogs.add(new WarnLog(awayName, "", "Skipped unrecognized team entry: " + awayName));
                        missing = true;
                        awayMissing = true;
                    }
                    if (missing) {
                        // Record a skipped match with explicit reason so UI can show which one and why
                        String reason;
                        if (homeMissing && awayMissing) {
                            reason = "Unrecognized teams (both home and away not found)";
                        } else if (homeMissing) {
                            reason = "Unrecognized home team";
                        } else {
                            reason = "Unrecognized away team";
                        }
                        skippedLogs.add(new SkipLog(homeName, awayName, reason));
                        // Skip saving this match since a team is unrecognized
                        continue;
                    }
                    Team home = homeOpt.get();
                    Team away = awayOpt.get();
                    String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                    if (seenKeys.add(key)) {
                        inserted += upsertMatch(league, home, away, date, round, homeGoals, awayGoals, seasonEntity);
                    }
                }
            }
        }
        // Success: true if no errors OR partial success (some items parsed/persisted despite ignorable errors)
        boolean partialOk = !errors.isEmpty() && (!items.isEmpty() || inserted > 0) && errors.size() < Math.max(1, (items.isEmpty() ? inserted : items.size()));
        boolean ok = errors.isEmpty() || partialOk;
        return new UploadResult(ok, errors, inserted, deleted, updatedLogs, skippedLogs, warnLogs);
    }

    private static boolean isDayMonthTimeLine(String line) {
        String l = line.trim();
        // Accept dd.MM. HH:mm, d.M. H:mm, or without the second dot before space
        return l.matches("^\\d{1,2}\\.\\d{1,2}\\.?\\s*\\d{1,2}:\\d{2}$");
    }

    private static boolean isPureNumber(String line) {
        return line != null && line.trim().matches("^-?\\d+$");
    }

    private static LocalDate parseDayMonthToDate(String dayMonthTime, String season) {
        // dayMonthTime like "01.09. 01:30". We ignore time, infer year from season.
        String[] parts = dayMonthTime.trim().split("\\s+");
        String dm = parts[0]; // e.g., 01.09.
        dm = dm.endsWith(".") ? dm.substring(0, dm.length()-1) : dm; // remove trailing dot
        String[] dmParts = dm.split("\\.");
        if (dmParts.length < 2) throw new IllegalArgumentException("Invalid date format: " + dayMonthTime);
        int day = Integer.parseInt(dmParts[0]);
        int month = Integer.parseInt(dmParts[1]);
        int year = inferYearFromSeason(month, season);
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException dte) {
            // Auto-correct mechanism: handle cases like 29.02 when inferred year is not a leap year
            if (month == 2 && day == 29) {
                // Prefer adjacent year based on season: if Jan–Jun, try next year; if Jul–Dec, try previous year
                int altYear;
                String s = season == null ? "" : season.trim();
                if (s.matches("^\\d{4}/\\d{4}$")) {
                    altYear = (month <= 6) ? year + 1 : year - 1;
                } else {
                    // Single-year season or unknown: try next year first
                    altYear = year + 1;
                }
                try {
                    LocalDate corrected = LocalDate.of(altYear, month, day);
                    try { AUTO_CORRECT_WARNINGS.get().add("[AutoCorrect] Adjusted date '" + dayMonthTime + "' (season='" + season + "') to '" + corrected + "'"); } catch (Exception ignored) {}
                    return corrected;
                } catch (java.time.DateTimeException dte2) {
                    // As a last resort, map Feb 29 to Feb 28 of the inferred year to avoid skipping
                    try {
                        LocalDate corrected = LocalDate.of(year, 2, 28);
                        try { AUTO_CORRECT_WARNINGS.get().add("[AutoCorrect] Adjusted date '" + dayMonthTime + "' (season='" + season + "') to '" + corrected + "' (fallback)"); } catch (Exception ignored) {}
                        return corrected;
                    } catch (java.time.DateTimeException ignored) {
                        // Give up with original error context
                    }
                }
            }
            // Re-throw with a clearer message including season context
            throw new IllegalArgumentException("Invalid day/month '" + dm + "' for inferred season '" + season + "'", dte);
        }
    }

    private static int inferYearFromSeason(int month, String season) {
        // season "2025/2026" or "2025". If month >=7, use first year; else use second (or same) year.
        if (season == null || season.isBlank()) return LocalDate.now().getYear();
        String s = season.trim();
        if (s.matches("^\\d{4}/\\d{4}$")) {
            int y1 = Integer.parseInt(s.substring(0,4));
            int y2 = Integer.parseInt(s.substring(5,9));
            return month >= 7 ? y1 : y2;
        }
        if (s.matches("^\\d{4}$")) {
            return Integer.parseInt(s);
        }
        // Fallback: current year
        return LocalDate.now().getYear();
    }

    private League findOrCreateLeague(String leagueName, String country, String season) {
        String ln = normalizeKey(opt(leagueName));
        String c = normalizeKey(opt(country));
        String s = normalizeSeason(opt(season));
        if (ln.isEmpty() || c.isEmpty() || s.isEmpty()) {
            throw new IllegalArgumentException("League name, country and season are required");
        }
        return leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(ln, c, s)
                .orElseGet(() -> {
                    try {
                        return leagueRepository.save(new League(ln, c, s));
                    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        // Another row with the same unique key already exists (or whitespace/case normalization mismatch).
                        // Re-fetch and return existing instead of failing with 500.
                        return leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(ln, c, s)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    private Team findOrCreateTeam(League league, String name) {
        String raw = opt(name);
        if (raw.isEmpty()) throw new IllegalArgumentException("Team name is required");
        String trimmed = raw.trim();
        // 1) Try exact name match within league (backward compatible with legacy tests/mocks)
        Optional<Team> byName = teamRepository.findByLeagueAndNameIgnoreCase(league, trimmed);
        if (byName.isPresent()) return byName.get();
        // 2) Try normalized name lookup
        String normalized = TeamNameNormalizer.normalize(raw);
        log.info("Resolving team (leagueId={}): raw='{}', normalized='{}'", league.getId(), raw, normalized);
        Optional<Team> byNorm = teamRepository.findByNormalizedNameAndLeagueId(normalized, league.getId());
        if (byNorm.isPresent()) return byNorm.get();
        // 3) Create if still not found
        log.info("Creating new team in league {} with name: raw='{}', normalized='{}'", league.getId(), raw, normalized);
        Team created = new Team(raw, league);
        Team saved = teamRepository.save(created);
        return saved != null ? saved : created;
    }

    private static String[] normalizeHeader(String header) {
        String[] cols = splitCsv(header);
        for (int i = 0; i < cols.length; i++) {
            // trim, lower-case, remove non-alphanumeric characters to normalize
            cols[i] = cols[i].trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        return cols;
    }

    private static Map<String, Integer> buildIndexMap(String[] cols) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            map.put(cols[i], i);
        }
        return map;
    }

    private static Optional<Integer> findIndex(Map<String, Integer> idx, List<String> aliases) {
        for (String a : aliases) {
            Integer i = idx.get(a);
            if (i != null) return Optional.of(i);
        }
        return Optional.empty();
    }

    private static String getByIndex(String[] parts, int index, String key) {
        if (index < 0 || index >= parts.length) throw new IllegalArgumentException("Missing column: " + key);
        return parts[index].trim();
    }

    private static int safeParseInt(String v, int def) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }

    private static Integer parseNullableInt(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return null;
        // Treat dashes as unknown/fixture -> null
        if (t.equals("-") || t.equals("–") || t.equals("—")) return null;
        try { return Integer.parseInt(t); } catch (Exception e) { return null; }
    }

    private static String[] splitCsv(String line) {
        // Simple split; assumes no quoted commas
        return line.split(",");
    }

    private static LocalDate parseDate(String v) {
        v = v.trim();
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );
        for (DateTimeFormatter f : fmts) {
            try { return LocalDate.parse(v, f); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Invalid date: " + v);
    }

    private static int parseRoundNumber(String line) {
        String digits = line.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        return Integer.parseInt(digits);
    }

    private static String opt(String s) { return s == null ? "" : s.trim(); }

    private static String normalizeKey(String s) {
        // Trim and collapse internal whitespace to a single space
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        return t;
    }

    private static String normalizeSeason(String s) {
        if (s == null) return "";
        String t = s.trim();
        // Replace common slash variants and collapse spaces
        t = t.replace('／', '/').replace('∕', '/').replace('⁄', '/').replace('\\', '/');
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    private static boolean isStatusMarker(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        // Treat common short status markers as ignorable between date and teams
        return t.matches("^[A-Z]{2,3}$") && (t.equals("AET") || t.equals("FT") || t.equals("HT") || t.equals("PEN") || t.equals("ET"));
    }

    private static boolean isLikelyNonMatchHeader(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        if (t.toLowerCase().startsWith("round")) return false;
        // Treat playoff stage labels (Final/Semi-finals/Quarter-finals) as valid round context, not headers
        if (isPlayoffStage(t)) return false;
        // country or section header with colon, standings/draw, group/play offs keywords
        if (t.matches("^[A-Z][A-Z\\u00C0-\\u017F\\s-]+:?$")) return true;
        String tl = t.toLowerCase();
        if (tl.equals("standings") || tl.equals("draw")) return true;
        if (tl.contains("group") || tl.contains("play off") || tl.contains("apertura") || tl.contains("clausura")) return true;
        // Common competition/league title lines without punctuation (e.g., "Serie B", "Premier League")
        if (!tl.matches(".*[0-9].*") && !tl.contains(":") && !tl.contains("-") ) {
            // keywords indicating competition names
            String[] kw = new String[]{"league","serie","division","primera","segunda","championship","national","cup","bundesliga","ligue","eredivisie","superliga","super league","superlig","lig","super lig","premier","tournament"};
            for (String k : kw) {
                if (tl.contains(k)) return true;
            }
        }
        return false;
    }

    private static boolean isPlayoffStage(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        // normalize hyphens and spaces
        t = t.replace("–", "-").replace("—", "-");
        t = t.replaceAll("\\s+", " ");
        // Accept common stage names and fractional knockout rounds, and allow being part of a longer header (e.g., "Primera Nacional - Super final")
        if (t.contains("super final")) return true;
        if (t.contains("additional match") || t.contains("additional game") || t.contains("additional playoff")) return true;
        if (t.contains("winners stage") || t.contains("losers stage")) return true; // qualification branches
        if (t.contains("qualification")) return true; // general qualification header with stages
        if (t.contains("final") && !t.contains("quarter") && !t.contains("semi") && !t.contains("1/")) return true; // plain "final" inside text
        if (t.contains("semi-finals") || t.contains("semi finals") || t.contains("semifinals")) return true;
        if (t.contains("quarter-finals") || t.contains("quarter finals") || t.contains("quarterfinals")) return true;
        // Fractional rounds like 1/2-finals, 1/4-finals, 1/8-finals, 1/16-finals, etc.
        if (t.matches(".*\\b1/2-?finals\\b.*")) return true;
        if (t.matches(".*\\b1/4-?finals\\b.*")) return true;
        if (t.matches(".*\\b1/8-?finals\\b.*")) return true;
        if (t.matches(".*\\b1/16-?finals\\b.*")) return true;
        if (t.matches(".*\\b1/32-?finals\\b.*")) return true;
        if (t.matches(".*\\b1/64-?finals\\b.*")) return true;
        return false;
    }

    private static int mapStageToRound(String s) {
        if (s == null) return -1;
        String t = s.trim().toLowerCase();
        t = t.replace("–", "-").replace("—", "-");
        t = t.replaceAll("\\s+", " ");
        // Map extended stages to synthetic round numbers (higher = later stage)
        if (t.contains("super final")) return 999; // treat as final
        if (t.contains("final") && !t.contains("quarter") && !t.contains("semi") && !t.contains("1/")) return 999;
        if (t.contains("semi-finals") || t.contains("semi finals") || t.contains("semifinals") || t.contains("1/2-finals")) return 998;
        if (t.contains("quarter-finals") || t.contains("quarter finals") || t.contains("quarterfinals") || t.contains("1/4-finals")) return 997;
        if (t.contains("1/8-finals")) return 996; // Round of 16
        if (t.contains("1/16-finals")) return 995;
        if (t.contains("1/32-finals")) return 994;
        if (t.contains("1/64-finals")) return 993;
        if (t.contains("additional match") || t.contains("additional game") || t.contains("additional playoff")) return 992; // tie-breaker/additional
        if (t.contains("winners stage")) return 991; // qualification winners path
        if (t.contains("losers stage")) return 990;  // qualification losers path
        if (t.contains("qualification")) return 989; // generic qualification
        return -1;
    }

    /**
     * Upsert a match using canonical identity (league, date, home, away).
     * If date is null (should not happen after validation), fallback to (league, round, home, away).
     * Returns 1 if a new row was inserted, 0 if an existing row was updated or nothing changed.
     */
    private int upsertMatch(League league, Team home, Team away, LocalDate date, int round, Integer homeGoals, Integer awayGoals, Season season) {
        Integer hgToSave = homeGoals;
        Integer agToSave = awayGoals;
        if (date != null) {
            final Integer fHg = hgToSave;
            final Integer fAg = agToSave;
            return matchRepository
                    .findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(league.getId(), home.getId(), away.getId(), date)
                    .map(existing -> {
                        boolean changed = false;
                        if (!Objects.equals(round, existing.getRound())) { existing.setRound(round); changed = true; }
                        if (!Objects.equals(fHg, existing.getHomeGoals())) { existing.setHomeGoals(fHg); changed = true; }
                        if (!Objects.equals(fAg, existing.getAwayGoals())) { existing.setAwayGoals(fAg); changed = true; }
                        if (season != null && (existing.getSeason() == null || !season.getId().equals(existing.getSeason().getId()))) { existing.setSeason(season); changed = true; }
                        if (homeGoals != null && awayGoals != null) {
                            existing.setStatus(MatchStatus.PLAYED);
                        } else {
                            existing.setStatus(MatchStatus.SCHEDULED);
                        }
                        if (changed) { matchRepository.save(existing); }
                        return 0;
                    })
                    .orElseGet(() -> {
                        return matchRepository
                                .findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId())
                                .map(existing -> {
                                    boolean changed = false;
                                    if (existing.getDate() == null || !existing.getDate().equals(date)) { existing.setDate(date); changed = true; }
                                    if (!Objects.equals(fHg, existing.getHomeGoals())) { existing.setHomeGoals(fHg); changed = true; }
                                    if (!Objects.equals(fAg, existing.getAwayGoals())) { existing.setAwayGoals(fAg); changed = true; }
                                    if (fHg != null && fAg != null) { existing.setStatus(MatchStatus.PLAYED); } else { existing.setStatus(MatchStatus.SCHEDULED); }
                                    if (changed) { matchRepository.save(existing); }
                                    return 0;
                                })
                                .orElseGet(() -> {
                                    if (season != null && date != null) {
                                        var existingSame = matchRepository.findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(season.getId(), home.getId(), away.getId(), date);
                                        if (existingSame.isPresent()) {
                                            throw new IllegalArgumentException("Duplicate match for season/date/teams: " + season.getName() + ", " + date + ", " + home.getName() + " vs " + away.getName());
                                        }
                                    }
                                    Match m = new Match(league, home, away, date, round, fHg, fAg);
                                    if (season != null) m.setSeason(season);
                                    if (m.getStatus() == null) {
                                        m.setStatus((fHg != null && fAg != null) ? MatchStatus.PLAYED : MatchStatus.SCHEDULED);
                                    }
                                    matchRepository.save(m);
                                    return 1;
                                });
                    });
        }
        final Integer fHg2 = hgToSave;
        final Integer fAg2 = agToSave;
        return matchRepository
                .findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId())
                .map(existing -> {
                    boolean changed = false;
                    if (!Objects.equals(fHg2, existing.getHomeGoals())) { existing.setHomeGoals(fHg2); changed = true; }
                    if (!Objects.equals(fAg2, existing.getAwayGoals())) { existing.setAwayGoals(fAg2); changed = true; }
                    if (season != null && (existing.getSeason() == null || !season.getId().equals(existing.getSeason().getId()))) { existing.setSeason(season); changed = true; }
                    if (fHg2 != null && fAg2 != null) { existing.setStatus(MatchStatus.PLAYED); } else { existing.setStatus(MatchStatus.SCHEDULED); }
                    if (changed) { matchRepository.save(existing); }
                    return 0;
                })
                .orElseGet(() -> {
                    if (season != null && date != null) {
                        var existingSame = matchRepository.findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(season.getId(), home.getId(), away.getId(), date);
                        if (existingSame.isPresent()) {
                            throw new IllegalArgumentException("Duplicate match for season/date/teams: " + (season.getName() == null ? season.getId() : season.getName()) + ", " + date + ", " + home.getName() + " vs " + away.getName());
                        }
                    }
                    Match m = new Match(league, home, away, date, round, fHg2, fAg2);
                    if (season != null) m.setSeason(season);
                    if (m.getStatus() == null) { m.setStatus((fHg2 != null && fAg2 != null) ? MatchStatus.PLAYED : MatchStatus.SCHEDULED); }
                    matchRepository.save(m);
                    return 1;
                });
    }

    // Aliases for common data sources (e.g., football-data.co.uk)
    private static final List<String> DATE_ALIASES = List.of("date", "matchdate", "match_date", "kickoff", "playedat", "played_at");
    private static final List<String> HOME_TEAM_ALIASES = List.of("hometeam", "home", "home_team");
    private static final List<String> AWAY_TEAM_ALIASES = List.of("awayteam", "away", "away_team");
    private static final List<String> HOME_GOALS_ALIASES = List.of("homegoals", "fthg", "home_score", "homefulltimegoals");
    private static final List<String> AWAY_GOALS_ALIASES = List.of("awaygoals", "ftag", "away_score", "awayfulltimegoals");
    private static final List<String> ROUND_ALIASES = List.of("round", "matchweek", "mw", "gameweek", "gw", "matchday", "week");
}

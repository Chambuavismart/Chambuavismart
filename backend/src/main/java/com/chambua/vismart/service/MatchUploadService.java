package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchIngestItem;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
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

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchDataValidationService validationService;

    public MatchUploadService(LeagueRepository leagueRepository, TeamRepository teamRepository, MatchRepository matchRepository, MatchDataValidationService validationService) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.validationService = validationService;
    }

    public record UploadResult(boolean success, List<String> errors, int insertedCount, long deletedCount,
                                List<UpdateLog> updated, List<SkipLog> skipped, List<WarnLog> warnings) {}

    public record UpdateLog(Long fixtureId, String homeTeam, String awayTeam, String result, String status) {}
    public record SkipLog(String homeTeam, String awayTeam, String reason) {}
    public record WarnLog(String homeTeam, String awayTeam, String reason) {}

    @Transactional
    public UploadResult uploadCsv(String leagueName, String country, String season, MultipartFile file, boolean fullReplace, boolean incrementalUpdate) {
        List<String> errors = new ArrayList<>();
        List<UpdateLog> updatedLogs = new ArrayList<>();
        List<SkipLog> skippedLogs = new ArrayList<>();
        List<WarnLog> warnLogs = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            errors.add("CSV file is empty");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        League league = findOrCreateLeague(leagueName, country, season);

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
            if (hgIdx == null) missing.add("homeGoals");
            if (agIdx == null) missing.add("awayGoals");
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
        var vr = validationService.validate(items);
        if (!vr.isValid()) {
            errors.addAll(vr.errorsAsStrings());
            warnLogs.addAll(vr.warningsAsStrings().stream().map(w -> new WarnLog(null, null, w)).toList());
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
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
            int homeGoals = Optional.ofNullable(it.getHomeGoals()).orElse(0);
            int awayGoals = Optional.ofNullable(it.getAwayGoals()).orElse(0);

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
                            skippedLogs.add(new SkipLog(homeName, awayName, "No matching fixture found"));
                        } else {
                            if (date != null && target.getDate() != null && !date.equals(target.getDate())) {
                                warnLogs.add(new WarnLog(homeName, awayName, "Date mismatch"));
                            }
                            boolean changed = false;
                            if (date != null && !date.equals(target.getDate())) { target.setDate(date); changed = true; }
                            if (!Objects.equals(homeGoals, target.getHomeGoals()) || !Objects.equals(awayGoals, target.getAwayGoals())) {
                                target.setHomeGoals(homeGoals);
                                target.setAwayGoals(awayGoals);
                                changed = true;
                            }
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
                    inserted += upsertMatch(league, home, away, date, round, homeGoals, awayGoals);
                }
            }
        }
        boolean ok = errors.isEmpty();
        return new UploadResult(ok, errors, inserted, deleted, updatedLogs, skippedLogs, warnLogs);
    }

    @Transactional
    public UploadResult uploadText(String leagueName, String country, String season, String text, boolean fullReplace, boolean incrementalUpdate) {
        List<String> errors = new ArrayList<>();
        List<UpdateLog> updatedLogs = new ArrayList<>();
        List<SkipLog> skippedLogs = new ArrayList<>();
        List<WarnLog> warnLogs = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            errors.add("Text content is empty");
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
        }
        League league = findOrCreateLeague(leagueName, country, season);

        // 1) Parse to items first
        List<MatchIngestItem> items = new ArrayList<>();
        int currentRound = -1;
        String[] lines = text.split("\r?\n");

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

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
                    int homeGoals = (hgStr.isEmpty() || hgStr.equals("-") || hgStr.equals("–") || hgStr.equals("—")) ? 0 : Integer.parseInt(hgStr);
                    int awayGoals = (agStr.isEmpty() || agStr.equals("-") || agStr.equals("–") || agStr.equals("—")) ? 0 : Integer.parseInt(agStr);

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
                                if (isPureNumber(ln)) { homeGoals = Integer.parseInt(ln); }
                            } else if (awayGoals == null) {
                                if (isPureNumber(ln)) { awayGoals = Integer.parseInt(ln); j++; steps++; break; }
                            }
                        }
                        j++; steps++;
                    }

                    if (currentRound <= 0) {
                        errors.add("Round not specified for block starting at date: " + line);
                        i = j - 1;
                        continue;
                    }
                    if (homeName == null || awayName == null || homeGoals == null || awayGoals == null) {
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
            errors.add("Unrecognized line format: " + line);
        }

        // 2) Validate
        var vr = validationService.validate(items);
        if (!vr.isValid()) {
            errors.addAll(vr.errorsAsStrings());
            warnLogs.addAll(vr.warningsAsStrings().stream().map(w -> new WarnLog(null, null, w)).toList());
            return new UploadResult(false, errors, 0, 0, updatedLogs, skippedLogs, warnLogs);
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
            int homeGoals = Optional.ofNullable(it.getHomeGoals()).orElse(0);
            int awayGoals = Optional.ofNullable(it.getAwayGoals()).orElse(0);

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
                            skippedLogs.add(new SkipLog(homeName, awayName, "No matching fixture found"));
                        } else {
                            if (date != null && target.getDate() != null && !date.equals(target.getDate())) {
                                warnLogs.add(new WarnLog(homeName, awayName, "Date mismatch"));
                            }
                            boolean changed = false;
                            if (date != null && !date.equals(target.getDate())) { target.setDate(date); changed = true; }
                            if (!Objects.equals(homeGoals, target.getHomeGoals()) || !Objects.equals(awayGoals, target.getAwayGoals())) {
                                target.setHomeGoals(homeGoals);
                                target.setAwayGoals(awayGoals);
                                changed = true;
                            }
                            if (changed) matchRepository.save(target);
                            updatedLogs.add(new UpdateLog(target.getId(), homeName, awayName, homeGoals + "-" + awayGoals, "Finished"));
                        }
                    }
                }
            } else {
                Team home = findOrCreateTeam(league, homeName);
                Team away = findOrCreateTeam(league, awayName);
                String key = league.getId() + ":" + home.getId() + ":" + away.getId() + ":" + round;
                if (seenKeys.add(key)) {
                    inserted += upsertMatch(league, home, away, date, round, homeGoals, awayGoals);
                }
            }
        }
        boolean ok = errors.isEmpty();
        return new UploadResult(ok, errors, inserted, deleted, updatedLogs, skippedLogs, warnLogs);
    }

    private static boolean isDayMonthTimeLine(String line) {
        String l = line.trim();
        // Patterns like 01.09. 01:30 or 1.9. 1:30
        return l.matches("^\\d{1,2}\\.\\d{1,2}\\.\\s*\\d{1,2}:\\d{2}$");
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
        return LocalDate.of(year, month, day);
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
        String n = opt(name);
        if (n.isEmpty()) throw new IllegalArgumentException("Team name is required");
        return teamRepository.findByLeagueAndNameIgnoreCase(league, n)
                .orElseGet(() -> teamRepository.save(new Team(n, league)));
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
        // Treat dashes as placeholders for fixtures (0 goals)
        if (t.equals("-") || t.equals("–") || t.equals("—")) return 0;
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

    /**
     * Upsert a match using canonical identity (league, date, home, away).
     * If date is null (should not happen after validation), fallback to (league, round, home, away).
     * Returns 1 if a new row was inserted, 0 if an existing row was updated or nothing changed.
     */
    private int upsertMatch(League league, Team home, Team away, LocalDate date, int round, Integer homeGoals, Integer awayGoals) {
        if (date != null) {
            return matchRepository
                    .findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(league.getId(), home.getId(), away.getId(), date)
                    .map(existing -> {
                        boolean changed = false;
                        if (!Objects.equals(round, existing.getRound())) { existing.setRound(round); changed = true; }
                        if (homeGoals != null && !Objects.equals(homeGoals, existing.getHomeGoals())) { existing.setHomeGoals(homeGoals); changed = true; }
                        if (awayGoals != null && !Objects.equals(awayGoals, existing.getAwayGoals())) { existing.setAwayGoals(awayGoals); changed = true; }
                        if (changed) { matchRepository.save(existing); }
                        return 0; // not a new insert
                    })
                    .orElseGet(() -> {
                        matchRepository.save(new Match(league, home, away, date, round, homeGoals, awayGoals));
                        return 1; // inserted
                    });
        }
        // Fallback: round-based
        return matchRepository
                .findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(league.getId(), round, home.getId(), away.getId())
                .map(existing -> {
                    boolean changed = false;
                    if (homeGoals != null && !Objects.equals(homeGoals, existing.getHomeGoals())) { existing.setHomeGoals(homeGoals); changed = true; }
                    if (awayGoals != null && !Objects.equals(awayGoals, existing.getAwayGoals())) { existing.setAwayGoals(awayGoals); changed = true; }
                    if (changed) { matchRepository.save(existing); }
                    return 0; // not a new insert
                })
                .orElseGet(() -> {
                    matchRepository.save(new Match(league, home, away, date, round, homeGoals, awayGoals));
                    return 1; // inserted
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

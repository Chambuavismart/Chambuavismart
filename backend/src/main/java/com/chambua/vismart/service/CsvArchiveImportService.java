package com.chambua.vismart.service;

import com.chambua.vismart.dto.ImportRunSummaryDTO;
import com.chambua.vismart.model.*;
import com.chambua.vismart.repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CsvArchiveImportService {

    @Value("${vismart.archives.imports-dir:data/imports/archives}")
    private String archivesImportDir;

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchRepository matchRepository;
    private final ImportRunRepository importRunRepository;
    private final ImportErrorRepository importErrorRepository;

    public CsvArchiveImportService(LeagueRepository leagueRepository,
                                   SeasonRepository seasonRepository,
                                   TeamRepository teamRepository,
                                   TeamAliasRepository teamAliasRepository,
                                   MatchRepository matchRepository,
                                   ImportRunRepository importRunRepository,
                                   ImportErrorRepository importErrorRepository) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.matchRepository = matchRepository;
        this.importRunRepository = importRunRepository;
        this.importErrorRepository = importErrorRepository;
    }

    @Transactional
    public ImportRunSummaryDTO importCsv(MultipartFile file,
                                         String competitionCode,
                                         String seasonName,
                                         String timezone,
                                         String provider) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(competitionCode, "competitionCode is required");

        ZoneId sourceZone = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("Europe/London");
        String filename = file.getOriginalFilename();
        String fileHash = sha256Hex(file.getBytes());

        ImportRun run = new ImportRun();
        run.setFileHash(fileHash);
        run.setProvider(provider);
        run.setSourceType("CSV");
        run.setFilename(filename);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("competitionCode", competitionCode);
        if (seasonName != null) params.put("season", seasonName);
        params.put("timezone", sourceZone.getId());
        run.setParams(toJson(params));
        run.setRowsTotal(0);
        run.setRowsSuccess(0);
        run.setRowsFailed(0);
        run.setStartedAt(Instant.now());
        run.setStatus("IN_PROGRESS");
        run.setCreatedBy("system");
        run = importRunRepository.save(run);

        // Save original file to configured folder and store path
        try {
            Path baseDir = Path.of(archivesImportDir);
            Files.createDirectories(baseDir);
            String safeName = (filename == null || filename.isBlank()) ? ("upload-" + run.getId() + ".csv") : filename;
            Path target = baseDir.resolve(run.getId() + "_" + safeName).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            run.setFilePath(target.toAbsolutePath().toString());
            importRunRepository.save(run);
        } catch (IOException ioEx) {
            // Do not fail the entire import if file save fails; proceed but leave filePath null
        }

        // Resolve or create league and season
        String country = deriveCountryFromCompetitionCode(competitionCode);
        String leagueName = competitionCode; // minimal: use code as name
        String effectiveSeason = (seasonName != null && !seasonName.isBlank()) ? seasonName : deriveSeasonFromFilename(filename).orElse("Unknown");
        League league = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(leagueName, country, effectiveSeason)
                .orElseGet(() -> leagueRepository.save(new League(leagueName, country, effectiveSeason)));
        Season season = seasonRepository.findByLeagueIdAndNameIgnoreCase(league.getId(), effectiveSeason)
                .orElseGet(() -> seasonRepository.save(new Season(league, effectiveSeason, null, null)));

        int batchSize = 500;
        List<Match> batch = new ArrayList<>(batchSize);
        List<ImportError> errors = new ArrayList<>();
        int total = 0, success = 0, failed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVFormat fmt = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            try (CSVParser parser = new CSVParser(reader, fmt)) {
                int rowNum = 1; // header is skipped; start from 1 for readability
                for (CSVRecord rec : parser) {
                    rowNum++;
                    total++;
                    try {
                        String div = get(rec, "Div");
                        String dateStr = get(rec, "Date");
                        String timeStr = opt(rec, "Time");
                        String homeName = get(rec, "HomeTeam");
                        String awayName = get(rec, "AwayTeam");
                        String fthgStr = opt(rec, "FTHG");
                        String ftagStr = opt(rec, "FTAG");
                        String ftr = opt(rec, "FTR");

                        // Parse date/time; E0 often uses dd/MM/yy
                        LocalDate matchDate = parseDate(dateStr);
                        if (timeStr != null && !timeStr.isBlank()) {
                            // we keep LocalDate in model; still use time for checksum normalization to UTC date
                            LocalTime lt = parseTime(timeStr);
                            ZonedDateTime zdt = ZonedDateTime.of(matchDate, lt, sourceZone);
                            Instant utc = zdt.toInstant();
                            matchDate = LocalDateTime.ofInstant(utc, ZoneOffset.UTC).toLocalDate();
                        }

                        Team home = resolveTeam(league, homeName);
                        Team away = resolveTeam(league, awayName);

                        Integer homeGoals = toIntOrNull(fthgStr);
                        Integer awayGoals = toIntOrNull(ftagStr);

                        Match match = new Match();
                        match.setLeague(league);
                        match.setSeason(season);
                        match.setHomeTeam(home);
                        match.setAwayTeam(away);
                        match.setDate(matchDate);
                        match.setRound(0);
                        match.setHomeGoals(homeGoals);
                        match.setAwayGoals(awayGoals);
                        match.setStatus((homeGoals != null && awayGoals != null) ? MatchStatus.PLAYED : MatchStatus.SCHEDULED);
                        match.setSourceType(SourceType.ARCHIVE);
                        match.setImportRun(run);

                        String checksum = checksumFor(league.getId(), season.getName(), matchDate, home.getId(), away.getId());
                        match.setChecksum(checksum);

                        if (matchRepository.existsByChecksum(checksum)) {
                            // skip duplicate
                        } else {
                            batch.add(match);
                            success++;
                        }

                        if (batch.size() >= batchSize) {
                            matchRepository.saveAll(batch);
                            batch.clear();
                        }
                    } catch (Exception rowEx) {
                        failed++;
                        ImportError ie = new ImportError();
                        ie.setImportRun(run);
                        ie.setRowNumber(rowNum);
                        ie.setPayload(rec != null ? rec.toString() : null);
                        ie.setReason(rowEx.getMessage());
                        ie.setCreatedAt(Instant.now());
                        errors.add(ie);
                        if (errors.size() >= 100) {
                            importErrorRepository.saveAll(errors);
                            errors.clear();
                        }
                    }
                }
            }
        }

        if (!batch.isEmpty()) matchRepository.saveAll(batch);
        if (!errors.isEmpty()) importErrorRepository.saveAll(errors);

        run.setRowsTotal(total);
        run.setRowsSuccess(success);
        run.setRowsFailed(failed);
        run.setFinishedAt(Instant.now());
        run.setStatus("COMPLETED");
        importRunRepository.save(run);

        return new ImportRunSummaryDTO(
                run.getId(),
                run.getStatus(),
                run.getRowsTotal(),
                run.getRowsSuccess(),
                run.getRowsFailed(),
                run.getProvider(),
                competitionCode,
                run.getFilename(),
                run.getCreatedBy(),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }

    private Team resolveTeam(League league, String name) {
        String trimmed = name != null ? name.trim() : "";
        return teamAliasRepository.findByAlias(trimmed)
                .map(TeamAlias::getTeam)
                .or(() -> teamRepository.findByLeagueAndNameIgnoreCase(league, trimmed))
                .orElseGet(() -> teamRepository.save(new Team(trimmed, league)));
    }

    private static String get(CSVRecord rec, String key) {
        String v = rec.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required column: " + key);
        return v.trim();
    }

    private static String opt(CSVRecord rec, String key) {
        try {
            String v = rec.get(key);
            return v == null ? null : v.trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        String t = s.trim();
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter f : fmts) {
            try { return LocalDate.parse(t, f); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Unparseable date: " + s);
    }

    private static LocalTime parseTime(String s) {
        String t = s.trim();
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H.mm")
        );
        for (DateTimeFormatter f : fmts) {
            try { return LocalTime.parse(t, f); } catch (Exception ignored) {}
        }
        return LocalTime.NOON; // default if unknown
    }

    private static Integer toIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String checksumFor(Long leagueId, String season, LocalDate date, Long homeId, Long awayId) {
        String raw = leagueId + "|" + season + "|" + date + "|" + homeId + "|" + awayId;
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append('"').append(':');
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v.toString());
            else sb.append('"').append(escapeJson(v.toString())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Optional<String> deriveSeasonFromFilename(String filename) {
        if (filename == null) return Optional.empty();
        // naive extraction like E0_2024-2025.csv
        String lower = filename.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("202");
        if (idx >= 0) {
            String sub = filename.substring(idx);
            // until dot
            int dot = sub.indexOf('.');
            if (dot > 0) sub = sub.substring(0, dot);
            return Optional.of(sub);
        }
        return Optional.empty();
    }

    private static String deriveCountryFromCompetitionCode(String code) {
        if (code == null) return "UNKNOWN";
        String c = code.toUpperCase(Locale.ROOT);
        if (c.startsWith("E")) return "England";
        if (c.startsWith("SC")) return "Scotland";
        if (c.startsWith("D")) return "Germany";
        if (c.startsWith("I")) return "Italy";
        if (c.startsWith("F")) return "France";
        if (c.startsWith("SP")) return "Spain";
        return "UNKNOWN";
    }

    // Preview CSV without persisting anything. Reads up to 'limit' rows.
    public com.chambua.vismart.dto.CsvPreviewResponse previewCsv(org.springframework.web.multipart.MultipartFile file, int limit) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        int max = (limit <= 0 || limit > 20) ? 20 : limit;
        List<String> headers = new ArrayList<>();
        List<com.chambua.vismart.dto.CsvPreviewRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVFormat fmt = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            try (CSVParser parser = new CSVParser(reader, fmt)) {
                headers = new ArrayList<>(parser.getHeaderNames());
                int rowNum = 1; // header skipped; first data line is 2 in human terms
                for (CSVRecord rec : parser) {
                    rowNum++;
                    if (rows.size() >= max) break;

                    // collect values in header order
                    List<String> values = new ArrayList<>();
                    for (String h : headers) {
                        String v;
                        try { v = rec.get(h); } catch (IllegalArgumentException e) { v = null; }
                        values.add(v);
                    }

                    String status = "ok";
                    String reason = null;
                    try {
                        String dateStr = get(rec, "Date");
                        // validate date
                        LocalDate matchDate = parseDate(dateStr);

                        String fthgStr = opt(rec, "FTHG");
                        String ftagStr = opt(rec, "FTAG");
                        Integer hg = toIntOrNull(fthgStr);
                        Integer ag = toIntOrNull(ftagStr);

                        if ((fthgStr != null && hg == null) || (ftagStr != null && ag == null)) {
                            status = "error";
                            reason = "Non-numeric goals";
                        }
                        String ftr = opt(rec, "FTR");
                        if (reason == null && ftr != null && (hg == null || ag == null)) {
                            status = "warn";
                            reason = "Result present but goals missing";
                        }
                    } catch (Exception ex) {
                        status = "error";
                        reason = ex.getMessage();
                    }

                    rows.add(new com.chambua.vismart.dto.CsvPreviewRow(rowNum, values, status, reason));
                }
            }
        }

        return new com.chambua.vismart.dto.CsvPreviewResponse(headers, rows);
    }
}

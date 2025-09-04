package com.chambua.vismart.controller;

import com.chambua.vismart.controller.UnifiedUploadController.UploadType;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.MatchUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads-text")
@CrossOrigin(origins = "*")
@Deprecated
public class UnifiedUploadTextController {

    private final MatchUploadService service;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;

    public UnifiedUploadTextController(MatchUploadService service, LeagueRepository leagueRepository, MatchRepository matchRepository) {
        this.service = service;
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
    }

    public record JsonUploadRequest(UploadType uploadType,
                                    Long leagueId,
                                    Long seasonId,
                                    Boolean autoDetectSeason,
                                    String leagueName,
                                    String country,
                                    String season,
                                    String text) {}

    @PostMapping(path = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadMatchesText(@RequestBody JsonUploadRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing body"));
        try {
            boolean fullReplace = false;
            boolean incremental = false;
            boolean fixtureMode = false;
            if (req.uploadType() == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "uploadType is required"));
            switch (req.uploadType()) {
                case NEW_LEAGUE -> fullReplace = true;
                case FULL_REPLACE -> fullReplace = true;
                case INCREMENTAL -> incremental = true;
                case FIXTURE -> fixtureMode = true;
                case HISTORICAL -> { /* defaults already set */ }
            }

            String seasonToUse = (req.autoDetectSeason() != null && req.autoDetectSeason()) ? tryDetectSeasonFromText(req.text(), req.season()) : req.season();
            boolean autoCreateTeams = (req.uploadType() == UploadType.HISTORICAL);
                        var result = service.uploadText(req.leagueName(), req.country(), seasonToUse, req.seasonId(), req.text(), fullReplace, incremental, fixtureMode, autoCreateTeams);

            var leagueOpt = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(normalizeKey(req.leagueName()), normalizeKey(req.country()), normalizeSeason(req.season()));
            long completedAllTime = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNull(l.getId()))
                    .orElse(0L);
            // Treat any match with scores as completed regardless of its date (historical uploads)
            long completedUpToToday = completedAllTime;

            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "inserted", result.insertedCount(),
                    "deleted", result.deletedCount(),
                    "errors", result.errors(),
                    "updated", result.updated(),
                    "skipped", result.skipped(),
                    "warnings", result.warnings(),
                    "completed", completedAllTime,
                    "completedUpToToday", completedUpToToday
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }
    private static String normalizeSeason(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('／', '/').replace('∕', '/').replace('⁄', '/').replace('\\', '/');
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    private static String tryDetectSeasonFromText(String text, String fallbackSeason) {
        if (text == null || text.isBlank()) return fallbackSeason;
        String[] lines = text.split("\r?\n");
        DateTimeFormatter[] fmts = new DateTimeFormatter[]{
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d.M.") // e.g., 1.9. from vertical blocks
        };
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;
            // CSV-like horizontal line starting with yyyy-MM-dd
            for (DateTimeFormatter f : fmts) {
                try {
                    if (f == DateTimeFormatter.ofPattern("d.M.")) {
                        if (line.matches("^\\d{1,2}\\.\\d{1,2}\\.($|\\s).*$")) {
                            // infer year based on month around current date
                            String[] dmParts = line.split("\\.");
                            int day = Integer.parseInt(dmParts[0]);
                            int month = Integer.parseInt(dmParts[1]);
                            LocalDate approx = LocalDate.of(LocalDate.now().getYear(), month, Math.min(day, 28));
                            return toSeasonString(approx);
                        }
                    } else {
                        if (line.matches("^\\d{4}-\\d{2}-\\d{2}.*$")) {
                            LocalDate date = LocalDate.parse(line.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                            return toSeasonString(date);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return fallbackSeason;
    }

    private static String toSeasonString(LocalDate date) {
        int y = date.getYear();
        int m = date.getMonthValue();
        if (m >= 7) return y + "/" + (y + 1);
        return (y - 1) + "/" + y;
    }
}

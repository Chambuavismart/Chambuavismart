package com.chambua.vismart.controller;

import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.MatchUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "*")
public class UnifiedUploadController {

    private final MatchUploadService service;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;

    public UnifiedUploadController(MatchUploadService service, LeagueRepository leagueRepository, MatchRepository matchRepository) {
        this.service = service;
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
    }

    public enum UploadType { NEW_LEAGUE, FULL_REPLACE, INCREMENTAL, FIXTURE, HISTORICAL }

    @PostMapping(path = "/matches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMatches(
            @RequestParam UploadType uploadType,
            @RequestParam(required = false) Long leagueId, // reserved for future use
            @RequestParam(required = false) Long seasonId,
            @RequestParam(defaultValue = "false") boolean autoDetectSeason, // reserved for future use
            @RequestParam String leagueName,
            @RequestParam String country,
            @RequestParam String season,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            boolean fullReplace = false;
            boolean incremental = false;
            boolean fixtureMode = false;
            switch (uploadType) {
                case NEW_LEAGUE -> fullReplace = true;
                case FULL_REPLACE -> fullReplace = true;
                case INCREMENTAL -> incremental = true;
                case FIXTURE -> fixtureMode = true;
                case HISTORICAL -> { /* defaults already set */ }
            }

            String seasonToUse = autoDetectSeason ? tryDetectSeasonFromCsv(file, season) : season;
            var result = service.uploadCsv(leagueName, country, seasonToUse, seasonId, file, fullReplace, incremental, fixtureMode);

            var leagueOpt = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(normalizeKey(leagueName), normalizeKey(country), normalizeSeason(season));
            long completedAllTime = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNull(l.getId()))
                    .orElse(0L);
            long completedUpToToday = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNullAndDateLessThanEqual(l.getId(), LocalDate.now()))
                    .orElse(0L);

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

    // Minimal normalization mirroring service behavior
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

    private static String tryDetectSeasonFromCsv(MultipartFile file, String fallbackSeason) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length == 0) continue;
                String dateStr = parts[0].trim();
                LocalDate date = parseDateLoose(dateStr);
                if (date != null) return toSeasonString(date);
            }
        } catch (IOException ignored) {}
        return fallbackSeason;
    }

    private static LocalDate parseDateLoose(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        for (DateTimeFormatter f : new DateTimeFormatter[]{
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        }) {
            try { return LocalDate.parse(v.trim(), f); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static String toSeasonString(LocalDate date) {
        int y = date.getYear();
        int m = date.getMonthValue();
        if (m >= 7) return y + "/" + (y + 1);
        return (y - 1) + "/" + y;
    }
}

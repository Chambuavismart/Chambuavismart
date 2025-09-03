package com.chambua.vismart.controller;

import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.MatchUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/matches/upload")
@CrossOrigin(origins = "*")
public class MatchUploadController {

    private final MatchUploadService service;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;

    public MatchUploadController(MatchUploadService service, LeagueRepository leagueRepository, MatchRepository matchRepository) {
        this.service = service;
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
    }

    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCsv(
            @RequestParam String leagueName,
            @RequestParam String country,
            @RequestParam String season,
            @RequestParam(defaultValue = "true") boolean fullReplace,
            @RequestParam(defaultValue = "false") boolean incrementalUpdate,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            var result = service.uploadCsv(leagueName, country, season, file, fullReplace, incrementalUpdate);
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

    public record TextUploadRequest(String leagueName, String country, String season, String text, Boolean fullReplace, Boolean incrementalUpdate) {}

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

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadText(@RequestBody TextUploadRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing body"));
        boolean fullReplace = req.fullReplace() == null || req.fullReplace();
        boolean incrementalUpdate = req.incrementalUpdate() != null && req.incrementalUpdate();
        try {
            var result = service.uploadText(req.leagueName(), req.country(), req.season(), req.text(), fullReplace, incrementalUpdate);
            var leagueOpt = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(normalizeKey(req.leagueName()), normalizeKey(req.country()), normalizeSeason(req.season()));
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
}

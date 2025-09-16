package com.chambua.vismart.controller;

import com.chambua.vismart.model.MatchStatus;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.CountryRepository;
import com.chambua.vismart.service.MatchUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches/upload")
@CrossOrigin(origins = "*")
public class MatchUploadController {

    private final MatchUploadService service;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final CountryRepository countryRepository;

    public MatchUploadController(MatchUploadService service, LeagueRepository leagueRepository, MatchRepository matchRepository, CountryRepository countryRepository) {
        this.service = service;
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.countryRepository = countryRepository;
    }

    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCsv(
            @RequestParam String leagueName,
            @RequestParam String country,
            @RequestParam String season,
            @RequestParam(name = "seasonId", required = false) Long seasonId,
            @RequestParam(defaultValue = "true") boolean fullReplace,
            @RequestParam(defaultValue = "false") boolean incrementalUpdate,
            @RequestParam(defaultValue = "false") boolean fixtureMode,
            @RequestParam(defaultValue = "true") boolean strict,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean allowSeasonAutoCreate,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            var result = service.uploadCsv(leagueName, country, season, seasonId, file, fullReplace, incrementalUpdate, fixtureMode, strict, dryRun, allowSeasonAutoCreate);
            var leagueOpt = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(normalizeKey(leagueName), normalizeKey(country), normalizeSeason(season));
            long completedAllTime = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndStatus(l.getId(), MatchStatus.PLAYED))
                    .orElse(0L);
            long completedUpToToday = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndStatusAndDateLessThanEqual(l.getId(), MatchStatus.PLAYED, LocalDate.now()))
                    .orElse(0L);

            // Server time diagnostics
            java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zoneId);
            String serverTimeDisplay = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " (" + zoneId.getId() + ")";

            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "inserted", result.insertedCount(),
                    "deleted", result.deletedCount(),
                    "errors", result.errors(),
                    "updated", result.updated(),
                    "skipped", result.skipped(),
                    "warnings", result.warnings(),
                    "completed", completedAllTime,
                    "completedUpToToday", completedUpToToday,
                    "serverTime", serverTimeDisplay
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    public record TextUploadRequest(String leagueName, String country, String season, Long seasonId, String text, Boolean fullReplace, Boolean incrementalUpdate, Boolean fixtureMode, Boolean strict, Boolean dryRun, Boolean allowSeasonAutoCreate) {}

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
        boolean fixtureMode = req.fixtureMode() != null && req.fixtureMode();
        try {
            boolean autoCreateTeams = fullReplace && !incrementalUpdate; // allow creating teams for new/full uploads
            var result = service.uploadText(req.leagueName(), req.country(), req.season(), req.seasonId(), req.text(), fullReplace, incrementalUpdate, fixtureMode, autoCreateTeams, req.strict() == null || req.strict(), req.dryRun() != null && req.dryRun(), req.allowSeasonAutoCreate() != null && req.allowSeasonAutoCreate());
            var leagueOpt = leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(normalizeKey(req.leagueName()), normalizeKey(req.country()), normalizeSeason(req.season()));
            long completedAllTime = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndStatus(l.getId(), MatchStatus.PLAYED))
                    .orElse(0L);
            long completedUpToToday = leagueOpt
                    .map(l -> matchRepository.countByLeagueIdAndStatusAndDateLessThanEqual(l.getId(), MatchStatus.PLAYED, LocalDate.now()))
                    .orElse(0L);

            // Server time diagnostics
            java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zoneId);
            String serverTimeDisplay = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " (" + zoneId.getId() + ")";

            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "inserted", result.insertedCount(),
                    "deleted", result.deletedCount(),
                    "errors", result.errors(),
                    "updated", result.updated(),
                    "skipped", result.skipped(),
                    "warnings", result.warnings(),
                    "completed", completedAllTime,
                    "completedUpToToday", completedUpToToday,
                    "serverTime", serverTimeDisplay
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
    @GetMapping(path = "/api/options/contexts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Cacheable("contextsOptions")
    public ResponseEntity<?> getContexts() {
        try {
            List<String> countries = countryRepository.findAllByOrderByNameAsc().stream().map(c -> c.getName()).toList();
            Map<String, List<String>> competitions = Map.of(
                    "global", List.of(
                            "FIFA — World Cup",
                            "FIFA — Club World Cup",
                            "FIFA — U-20 World Cup",
                            "FIFA — U-17 World Cup",
                            "FIFA — Women’s World Cup",
                            "FIFA — U-20 Women’s World Cup",
                            "FIFA — U-17 Women’s World Cup",
                            "FIFA — Olympic Football Tournament (Men)",
                            "FIFA — Olympic Football Tournament (Women)",
                            "FIFA — Confederations Cup (historical)"
                    ),
                    "africa", List.of(
                            "CAF — Africa Cup of Nations (AFCON)",
                            "CAF — African Nations Championship (CHAN)",
                            "CAF — Champions League",
                            "CAF — Confederation Cup",
                            "CAF — Super Cup",
                            "CAF — Africa Women Cup of Nations",
                            "CAF — Women’s Champions League"
                    ),
                    "asia", List.of(
                            "AFC — Asian Cup",
                            "AFC — Champions League Elite",
                            "AFC — Champions League Two",
                            "AFC — AFC Cup",
                            "AFC — Women’s Asian Cup",
                            "AFC — Women’s Champions League"
                    ),
                    "europe", List.of(
                            "UEFA — European Championship (EURO)",
                            "UEFA — Champions League",
                            "UEFA — Europa League",
                            "UEFA — Europa Conference League",
                            "UEFA — Super Cup",
                            "UEFA — Nations League",
                            "UEFA — Women’s European Championship",
                            "UEFA — Women’s Champions League"
                    ),
                    "concacaf", List.of(
                            "CONCACAF — Gold Cup",
                            "CONCACAF — Nations League",
                            "CONCACAF — Champions Cup",
                            "CONCACAF — Central American Cup",
                            "CONCACAF — Caribbean Cup",
                            "CONCACAF — W Gold Cup",
                            "CONCACAF — W Championship"
                    ),
                    "conmebol", List.of(
                            "CONMEBOL — Copa América",
                            "CONMEBOL — Copa Libertadores",
                            "CONMEBOL — Copa Sudamericana",
                            "CONMEBOL — Recopa Sudamericana",
                            "CONMEBOL — Copa América Femenina",
                            "CONMEBOL — Copa Libertadores Femenina"
                    ),
                    "ofc", List.of(
                            "OFC — Nations Cup",
                            "OFC — Champions League",
                            "OFC — Women’s Nations Cup",
                            "OFC — Women’s Champions League"
                    ),
                    "other", List.of(
                            "Intercontinental — Panamerican Championship (historical)",
                            "Intercontinental — Arab Cup",
                            "Intercontinental — Afro-Asian Cup of Nations (historical)"
                    )
            );
            return ResponseEntity.ok(Map.of(
                    "countries", countries,
                    "competitions", competitions
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }
}

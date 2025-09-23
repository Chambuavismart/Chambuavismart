package com.chambua.vismart.controller;

import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.AdminAuditRepository;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.service.DataNormalizationService;
import com.chambua.vismart.service.FixtureUploadService;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.League;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminDiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminDiagnosticsController.class);

    private final MatchRepository matchRepository;
    private final DataNormalizationService normalizationService;
    private final AdminAuditRepository adminAuditRepository;
    private final FixtureUploadService fixtureUploadService;
    private final SeasonRepository seasonRepository;

    public AdminDiagnosticsController(MatchRepository matchRepository,
                                      DataNormalizationService normalizationService,
                                      AdminAuditRepository adminAuditRepository,
                                      FixtureUploadService fixtureUploadService,
                                      SeasonRepository seasonRepository) {
        this.matchRepository = matchRepository;
        this.normalizationService = normalizationService;
        this.adminAuditRepository = adminAuditRepository;
        this.fixtureUploadService = fixtureUploadService;
        this.seasonRepository = seasonRepository;
    }

    @PostMapping("/normalize")
    public Map<String, Object> normalize(@RequestParam(value = "today", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
                                         @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
                                         @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {
        if (today == null) today = LocalDate.now();
        var res = normalizationService.normalizePastScoredMatches(today, dryRun, confirm);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dryRun", res.dryRun);
        resp.put("expectedAffected", res.expectedAffected);
        resp.put("sampleIds", res.sampleIds);
        resp.put("updated", res.updatedRows);
        resp.put("today", today.toString());
        resp.put("success", true);
        // Audit
        try {
            if (adminAuditRepository != null) {
                com.chambua.vismart.model.AdminAudit audit = new com.chambua.vismart.model.AdminAudit();
                audit.setAction("normalize");
                audit.setParams(String.format("{\"today\":\"%s\",\"dryRun\":%s,\"confirm\":%s}", today, String.valueOf(dryRun), String.valueOf(confirm)));
                audit.setAffectedCount(res.dryRun ? res.expectedAffected : res.updatedRows);
                adminAuditRepository.save(audit);
            }
        } catch (Exception ignore) {}
        return resp;
    }

    @GetMapping("/anomalies")
    public Map<String, Object> anomalies(@RequestParam(value = "today", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
                                         @RequestParam(value = "sampleLimit", defaultValue = "10") int sampleLimit) {
        if (today == null) today = LocalDate.now();
        int limit = Math.max(1, Math.min(sampleLimit, 50));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", today.toString());

        try {
            long futurePlayed = matchRepository.countPlayedWithFutureDate(today);
            List<Long> futurePlayedIds = matchRepository.findPlayedWithFutureDate(today).stream()
                    .limit(limit).map(m -> m.getId()).collect(Collectors.toList());
            out.put("playedFutureDateCount", futurePlayed);
            out.put("playedFutureDateSampleIds", futurePlayedIds);
        } catch (Exception e) {
            log.warn("[ANOMALY] Error fetching 'played with future date': {}", e.toString());
        }

        try {
            long goalsNotPlayed = matchRepository.countWithGoalsButNotPlayedPast(today);
            List<Long> sample = matchRepository.findWithGoalsButNotPlayedPast(today).stream()
                    .limit(limit).map(m -> m.getId()).collect(Collectors.toList());
            out.put("scoredButNotPlayedPastCount", goalsNotPlayed);
            out.put("scoredButNotPlayedPastSampleIds", sample);
        } catch (Exception e) {
            log.warn("[ANOMALY] Error fetching 'scores but not played': {}", e.toString());
        }

        try {
            long playedNullDate = matchRepository.countPlayedWithNullDate();
            List<Long> sample = matchRepository.findPlayedWithNullDate().stream()
                    .limit(limit).map(m -> m.getId()).collect(Collectors.toList());
            out.put("playedNullDateCount", playedNullDate);
            out.put("playedNullDateSampleIds", sample);
        } catch (Exception e) {
            log.warn("[ANOMALY] Error fetching 'played with null date': {}", e.toString());
        }

        try {
            long playedNullRound = matchRepository.countPlayedWithNullRound();
            List<Long> sample = matchRepository.findPlayedWithNullRound().stream()
                    .limit(limit).map(m -> m.getId()).collect(Collectors.toList());
            out.put("playedNullRoundCount", playedNullRound);
            out.put("playedNullRoundSampleIds", sample);
        } catch (Exception e) {
            log.warn("[ANOMALY] Error fetching 'played with null round': {}", e.toString());
        }

        return out;
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "size", defaultValue = "20") int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        var pageable = org.springframework.data.domain.PageRequest.of(p, s);
        var pg = adminAuditRepository.findRecent(pageable);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("page", pg.getNumber());
        resp.put("size", pg.getSize());
        resp.put("totalElements", pg.getTotalElements());
        resp.put("totalPages", pg.getTotalPages());
        resp.put("items", pg.getContent());
        return resp;
    }

    @PostMapping(value = "/reingest", consumes = {"text/plain", "application/json"})
    public Map<String, Object> reingest(@RequestParam("leagueId") Long leagueId,
                                        @RequestParam(value = "seasonName", required = false) String seasonName,
                                        @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
                                        @RequestParam(value = "confirm", defaultValue = "false") boolean confirm,
                                        @RequestParam(value = "sourceType", defaultValue = "raw-text") String sourceType,
                                        @RequestBody(required = false) String rawText) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");
        String seasonResolvedName = seasonName;
        Long seasonId = null;
        try {
            if (seasonResolvedName == null || seasonResolvedName.isBlank()) {
                var latest = seasonRepository.findLatestWithPlayedMatchesByLeagueId(leagueId).orElse(null);
                if (latest == null) {
                    latest = seasonRepository.findTopByLeagueIdOrderByStartDateDesc(leagueId).orElse(null);
                }
                if (latest != null) {
                    seasonId = latest.getId();
                    seasonResolvedName = latest.getName();
                }
            } else {
                var found = seasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, seasonResolvedName).orElse(null);
                if (found != null) { seasonId = found.getId(); }
            }
        } catch (Exception ignore) {}
        if (seasonId == null) {
            resp.put("success", false);
            resp.put("message", "Season could not be resolved for leagueId=" + leagueId);
            return resp;
        }
        resp.put("seasonResolved", seasonResolvedName);
        resp.put("seasonId", seasonId);

        long existing = 0L;
        try { existing = matchRepository.countBySeasonId(seasonId); } catch (Exception ignore) {}
        resp.put("existingMatches", existing);

        int wouldIngest = 0;
        List<String> sampleParsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            if ("csv".equalsIgnoreCase(sourceType)) {
                var sampleWarns = new ArrayList<String>();
                var parsed = fixtureUploadService.parseCsv(rawText == null ? "" : rawText, null, seasonResolvedName, new ArrayList<>(), sampleWarns);
                wouldIngest = parsed == null ? 0 : parsed.size();
                parsed.stream().limit(5).forEach(f -> sampleParsed.add("Round " + f.getRound() + ": " + f.getHomeTeam() + " vs " + f.getAwayTeam()));
                if (!sampleWarns.isEmpty()) warnings.addAll(sampleWarns);
            } else {
                var errors = new ArrayList<String>();
                var warns = new ArrayList<String>();
                var ignored = new ArrayList<String>();
                // We don't have League entity here for parse; the helper tolerates null league for name normalization in our implementation
                var parsed = fixtureUploadService.parseFixtures(rawText == null ? "" : rawText, null, seasonResolvedName, errors, warns, ignored, false);
                wouldIngest = parsed == null ? 0 : parsed.size();
                warnings.addAll(warns);
                parsed.stream().limit(5).forEach(f -> sampleParsed.add("Round " + f.getRound() + ": " + (f.getHomeTeam()!=null?f.getHomeTeam():"?") + " vs " + (f.getAwayTeam()!=null?f.getAwayTeam():"?")));
            }
        } catch (Exception e) {
            warnings.add("Parse error: " + e.getMessage());
        }
        resp.put("wouldIngest", wouldIngest);
        resp.put("sampleParsed", sampleParsed);

        long archived = 0L;
        int ingested = 0;
        if (!dryRun) {
            if (!confirm) throw new IllegalStateException("confirm=true required for non-dry-run reingest");
            // Soft-archive existing matches in the target season
            try { archived = matchRepository.archiveBySeasonId(seasonId); } catch (Exception e) { warnings.add("Archive error: " + e.getMessage()); }
            try {
                if ("csv".equalsIgnoreCase(sourceType)) {
                    var res = fixtureUploadService.uploadCsv(leagueId, seasonResolvedName, false, rawText == null ? "" : rawText);
                    ingested = (res != null ? res.getInserted() : 0);
                    if (res != null && res.getWarnings() != null) warnings.addAll(res.getWarnings());
                } else {
                    com.chambua.vismart.dto.FixturesUploadRequest req = new com.chambua.vismart.dto.FixturesUploadRequest();
                    req.setLeagueId(leagueId);
                    req.setSeason(seasonResolvedName);
                    req.setFullReplace(false);
                    req.setRawText(rawText == null ? "" : rawText);
                    req.setStrictMode(false);
                    var res = fixtureUploadService.upload(req);
                    ingested = (res != null ? res.getInserted() : 0);
                    if (res != null && res.getWarnings() != null) warnings.addAll(res.getWarnings());
                }
            } catch (Exception e) {
                warnings.add("Ingest error: " + e.getMessage());
            }
        }

        resp.put("archived", archived);
        resp.put("ingested", ingested);
        resp.put("warnings", warnings);
        resp.put("success", true);

        // Audit
        try {
            com.chambua.vismart.model.AdminAudit audit = new com.chambua.vismart.model.AdminAudit();
            audit.setAction("reingest");
            audit.setParams(String.format("{\"leagueId\":%d,\"seasonName\":\"%s\",\"dryRun\":%s}", leagueId, seasonResolvedName, String.valueOf(dryRun)));
            audit.setAffectedCount(dryRun ? (long) wouldIngest : (long) ingested);
            adminAuditRepository.save(audit);
        } catch (Exception ignore) {}

        return resp;
    }
}

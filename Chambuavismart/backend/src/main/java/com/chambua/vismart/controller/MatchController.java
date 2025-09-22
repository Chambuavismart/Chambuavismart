package com.chambua.vismart.controller;

import com.chambua.vismart.dto.TeamResultsBreakdownResponse;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.H2HService;
import com.chambua.vismart.service.FormGuideService;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.AnalysisRequest;
import com.chambua.vismart.service.LaTeXService;
import com.chambua.vismart.repository.AdminAuditRepository;
import com.chambua.vismart.service.PdfArchiveService;
import com.chambua.vismart.model.PdfArchive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "*")
public class MatchController {

    private final MatchRepository matchRepository;
    private final H2HService h2hService;
    private final com.chambua.vismart.config.FeatureFlags featureFlags;
    private final FormGuideService formGuideService;
    private final SeasonRepository seasonRepository;
    private final LaTeXService laTeXService;
    private final PdfArchiveService pdfArchiveService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AdminAuditRepository adminAuditRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.repository.TeamRepository teamRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags, FormGuideService formGuideService, SeasonRepository seasonRepository, LaTeXService laTeXService, PdfArchiveService pdfArchiveService) {
        this.matchRepository = matchRepository;
        this.h2hService = h2hService;
        this.featureFlags = featureFlags;
        this.formGuideService = formGuideService;
        this.seasonRepository = seasonRepository;
        this.laTeXService = laTeXService;
        this.pdfArchiveService = pdfArchiveService;
    }

    // Overload for tests without PdfArchiveService
    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags, FormGuideService formGuideService, SeasonRepository seasonRepository, LaTeXService laTeXService) {
        this(matchRepository, h2hService, featureFlags, formGuideService, seasonRepository, laTeXService, null);
    }

    // Backward-compatible constructor for existing tests (H2H form endpoint will be unavailable)
    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags) {
        this(matchRepository, h2hService, featureFlags, null, null, null, null);
    }

    @GetMapping("/played/total")
    public long getTotalPlayedMatches() {
        long v = 0L;
        try {
            v = matchRepository.countByResultIsNotNull();
        } catch (Exception ignored) { /* fall through */ }
        if (v <= 0L) {
            try { v = matchRepository.countByResultIsNotNullNative(); } catch (Exception ignored2) {}
        }
        return v;
    }

    @GetMapping("/played/team/{teamId}/total")
    public long getPlayedTotalByTeam(@PathVariable("teamId") Long teamId) {
        if (teamId == null) return 0L;
        return matchRepository.countPlayedByTeam(teamId);
    }

    @GetMapping("/played/team/by-name/total")
    public long getPlayedTotalByTeamName(@RequestParam("name") String name) {
        if (name == null || name.trim().isEmpty()) return 0L;
        return matchRepository.countPlayedByTeamName(name.trim());
    }

    @GetMapping("/played/team/by-name/breakdown")
    public TeamResultsBreakdownResponse getResultsBreakdownByTeamName(@RequestParam("name") String name) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchController.class);
        long t0 = System.currentTimeMillis();
        if (name == null || name.trim().isEmpty()) {
            logger.warn("[Breakdown][REQ] Empty name provided");
            return new TeamResultsBreakdownResponse(0,0,0,0,0,0,0, null, 0);
        }
        String q = name.trim();
        long total = matchRepository.countPlayedByTeamName(q);
        long wins = matchRepository.countWinsByTeamName(q);
        long draws = matchRepository.countDrawsByTeamName(q);
        long losses = matchRepository.countLossesByTeamName(q);
        long btts = matchRepository.countBttsByTeamName(q);
        long over25 = matchRepository.countOver25ByTeamName(q);
        long over15 = matchRepository.countOver15ByTeamName(q);
        // Safety: ensure consistency even if data anomalies
        if (losses + wins + draws != total) {
            long computedLosses = Math.max(0, total - wins - draws);
            logger.warn("[Breakdown][{}] Inconsistent totals: total={} wins={} draws={} losses={} -> recomputedLosses={}", q, total, wins, draws, losses, computedLosses);
            losses = computedLosses;
        }
        // Compute longest-ever W/D/L streak across all played matches for this team
        String longestType = null;
        int longestCount = 0;
        try {
            java.util.List<com.chambua.vismart.model.Match> recent = matchRepository.findRecentPlayedByTeamName(q);
            if (recent == null || recent.isEmpty()) {
                logger.info("[Breakdown][{}] No played matches found; longest streak unavailable", q);
            } else {
                // Traverse from oldest to newest to compute consecutive sequences
                String currentType = null;
                int currentCount = 0;
                int skipped = 0;
                for (int i = recent.size() - 1; i >= 0; i--) {
                    com.chambua.vismart.model.Match m = recent.get(i);
                    Integer hg = m.getHomeGoals();
                    Integer ag = m.getAwayGoals();
                    if (hg == null || ag == null) { skipped++; continue; }
                    boolean isHome = false;
                    try {
                        isHome = m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().equalsIgnoreCase(q);
                    } catch (Exception e) {
                        // Defensive: if lazy loading failed for any reason
                        logger.warn("[Breakdown][{}] Could not access homeTeam during streak calc: {}", q, e.toString());
                    }
                    String type;
                    if (hg.equals(ag)) {
                        type = "D";
                    } else if ((isHome && hg > ag) || (!isHome && ag > hg)) {
                        type = "W";
                    } else {
                        type = "L";
                    }
                    if (currentType == null || !type.equals(currentType)) {
                        currentType = type;
                        currentCount = 1;
                    } else {
                        currentCount += 1;
                    }
                    if (currentCount > longestCount) {
                        longestCount = currentCount;
                        longestType = currentType;
                    }
                }
                logger.info("[Breakdown][{}] Longest streak computed: type={} count={} (skippedAnomalies={})", q, longestType, longestCount, skipped);
            }
        } catch (Exception ex) {
            logger.warn("[Breakdown][{}] Exception while computing longest streak: {}", q, ex.toString());
        }
        TeamResultsBreakdownResponse resp = new TeamResultsBreakdownResponse(total, wins, draws, losses, btts, over25, over15, longestType, longestCount);
        long took = System.currentTimeMillis() - t0;
        logger.info("[Breakdown][Resp][{}] total={} W/D/L={}/{}/{} BTTS={} O15={} O25={} longest={}{} took={}ms", q, total, wins, draws, losses, btts, over15, over25, (longestType==null?"-":longestType+":"), longestCount, took);
        return resp;
    }

    // --- H2H suggestions: only pairs that actually played ---
    @GetMapping("/h2h/suggest")
    public List<H2HSuggestion> suggestH2H(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 3) return List.of();
        String q = query.trim();
        List<Object[]> raw = matchRepository.findDistinctPlayedPairsByNameContains(q);
        // Deduplicate by canonicalized unordered pair (case-insensitive)
        Set<String> seen = new HashSet<>();
        List<H2HSuggestion> out = new ArrayList<>();
        for (Object[] pair : raw) {
            String a = Objects.toString(pair[0], "");
            String b = Objects.toString(pair[1], "");
            if (a.isBlank() || b.isBlank() || a.equalsIgnoreCase(b)) continue;
            String key = a.compareToIgnoreCase(b) <= 0 ? (a.toLowerCase(Locale.ROOT) + "|" + b.toLowerCase(Locale.ROOT)) : (b.toLowerCase(Locale.ROOT) + "|" + a.toLowerCase(Locale.ROOT));
            if (seen.add(key)) {
                // Keep the original case as returned (best-effort)
                String first = a;
                String second = b;
                // Normalize display order alphabetically for predictability (doesn't affect orientation options we show client-side)
                if (a.compareToIgnoreCase(b) > 0) {
                    first = b;
                    second = a;
                }
                out.add(new H2HSuggestion(first, second));
            }
        }
        // Limit to a reasonable number
        return out.size() > 30 ? out.subList(0, 30) : out;
    }

    // --- H2H matches list for chosen orientation ---
    @GetMapping("/h2h/matches")
    public List<H2HMatchDto> getH2HMatches(@RequestParam(name = "home", required = false) String homeName,
                                           @RequestParam(name = "away", required = false) String awayName,
                                           @RequestParam(name = "homeId", required = false) Long homeId,
                                           @RequestParam(name = "awayId", required = false) Long awayId,
                                           @RequestParam(name = "seasonId", required = false) Long seasonId,
                                           @RequestParam(name = "limit", required = false) Integer limit) {
        long start = System.currentTimeMillis();
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchController.class);
        DateTimeFormatter df = DateTimeFormatter.ISO_DATE;
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        logger.info("[H2H_MATCHES][REQ] home='{}' away='{}' homeId={} awayId={} seasonId={} limit={}", homeName, awayName, homeId, awayId, seasonId, lim);
        List<Match> list = java.util.Collections.emptyList();
        String path = "none";
        // Preferred: ID-based oriented H2H across ALL seasons (ignore seasonId for oriented history)
        if (homeId != null && awayId != null) {
            try {
                list = matchRepository.findH2HByTeamIds(homeId, awayId);
                path = "ID+ALL_SEASONS_ORIENTED";
            } catch (Exception ex) {
                logger.warn("[H2H_MATCHES][ERR][ID+ALL_SEASONS] {}", ex.toString());
                list = java.util.Collections.emptyList();
            }
            // If ID-based path returned nothing, try resolving names from IDs and query oriented-by-name across all seasons
            if ((list == null || list.isEmpty()) && teamRepository != null) {
                try {
                    String hn = teamRepository.findById(homeId).map(t -> t.getName()).orElse(null);
                    String an = teamRepository.findById(awayId).map(t -> t.getName()).orElse(null);
                    if (hn != null && !hn.isBlank() && an != null && !an.isBlank()) {
                        try {
                            list = matchRepository.findPlayedByExactNames(hn.trim(), an.trim());
                            path = "ID_RESOLVED_TO_NAMES_EXACT";
                        } catch (Exception ignoredExact) { list = java.util.Collections.emptyList(); }
                        if (list == null || list.isEmpty()) {
                            try {
                                list = matchRepository.findPlayedByFuzzyNames(hn.trim(), an.trim());
                                path = "ID_RESOLVED_TO_NAMES_FUZZY";
                            } catch (Exception ignoredFuzzy) { list = java.util.Collections.emptyList(); }
                        }
                        logger.info("[H2H_MATCHES][FallbackFromIds] Resolved names '{}' vs '{}' -> {} matches", hn, an, (list!=null?list.size():0));
                    }
                } catch (Exception ex) {
                    logger.warn("[H2H_MATCHES][ERR][ID_RESOLVE_NAMES] {}", ex.toString());
                }
            }
        }
        // Fallback: name-based (across seasons/leagues as implemented in H2HService)
        if ((list == null || list.isEmpty()) && homeName != null && !homeName.isBlank() && awayName != null && !awayName.isBlank()) {
            String home = homeName.trim();
            String away = awayName.trim();
            try { list = h2hService.getH2HByNames(home, away); path = "NAME"; } catch (Exception ex) { logger.warn("[H2H_MATCHES][ERR][NAME] {}", ex.toString()); list = java.util.Collections.emptyList(); }
        }
        if (list == null) list = java.util.Collections.emptyList();
        List<H2HMatchDto> out = new ArrayList<>(Math.min(lim, list.size()));
        int count = 0;
        for (Match m : list) {
            if (count++ >= lim) break;
            String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
            out.add(new H2HMatchDto(
                    m.getDate() != null ? m.getDate().getYear() : null,
                    m.getDate() != null ? df.format(m.getDate()) : null,
                    m.getHomeTeam() != null ? m.getHomeTeam().getName() : null,
                    m.getAwayTeam() != null ? m.getAwayTeam().getName() : null,
                    result,
                    (m.getSeason() != null && m.getSeason().getName() != null) ? m.getSeason().getName() : (m.getSeason() != null ? String.valueOf(m.getSeason().getId()) : null)
            ));
        }
        logger.info("[H2H_MATCHES][RESP] path={} total={} returned={} ms={}", path, list.size(), out.size(), (System.currentTimeMillis()-start));
        return out;
    }

    // --- H2H total count ignoring orientation (by names) ---
    @GetMapping("/h2h/count-any-orientation")
    public long getH2HCountAnyOrientation(@RequestParam("teamA") String teamA,
                                          @RequestParam("teamB") String teamB) {
        if (teamA == null || teamB == null) return 0L;
        String a = teamA.trim();
        String b = teamB.trim();
        if (a.isEmpty() || b.isEmpty()) return 0L;
        try {
            return matchRepository.countH2HByNamesAnyOrientation(a, b);
        } catch (Exception ignored) { return 0L; }
    }

    // --- H2H matches ignoring orientation (by names) ---
    @GetMapping("/h2h/matches-any-orientation")
    public List<H2HMatchDto> getH2HMatchesAnyOrientation(@RequestParam("teamA") String teamA,
                                                         @RequestParam("teamB") String teamB,
                                                         @RequestParam(name = "limit", required = false) Integer limit) {
        int lim = (limit == null || limit <= 0) ? 200 : Math.min(limit, 500);
        if (teamA == null || teamB == null) return java.util.Collections.emptyList();
        String a = teamA.trim();
        String b = teamB.trim();
        if (a.isEmpty() || b.isEmpty()) return java.util.Collections.emptyList();
        List<Match> list;
        try {
            list = matchRepository.findH2HByNamesAnyOrientation(a, b);
        } catch (Exception e) {
            list = java.util.Collections.emptyList();
        }
        DateTimeFormatter df = DateTimeFormatter.ISO_DATE;
        List<H2HMatchDto> out = new ArrayList<>(Math.min(lim, list.size()));
        int count = 0;
        for (Match m : list) {
            if (count++ >= lim) break;
            String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
            out.add(new H2HMatchDto(
                    m.getDate() != null ? m.getDate().getYear() : null,
                    m.getDate() != null ? df.format(m.getDate()) : null,
                    m.getHomeTeam() != null ? m.getHomeTeam().getName() : null,
                    m.getAwayTeam() != null ? m.getAwayTeam().getName() : null,
                    result,
                    (m.getSeason() != null && m.getSeason().getName() != null) ? m.getSeason().getName() : (m.getSeason() != null ? String.valueOf(m.getSeason().getId()) : null)
            ));
        }
        return out;
    }

    // --- Team last-5 form by team name (across seasons/leagues) ---
    @GetMapping("/form/by-name")
    public com.chambua.vismart.dto.FormSummary getFormByTeamName(@RequestParam("name") String name) {
        if (!featureFlags.isPredictiveH2HPhase1Enabled()) {
            return new com.chambua.vismart.dto.FormSummary();
        }
        com.chambua.vismart.dto.FormSummary summary = new com.chambua.vismart.dto.FormSummary();
        if (name == null || name.trim().isEmpty()) return summary;
        String q = name.trim();
        try {
            // Determine latest available season in which this team has played
            List<Long> seasonIds = matchRepository.findSeasonIdsForTeamNameOrdered(q);
            List<Match> list = java.util.Collections.emptyList();
            if (seasonIds != null && !seasonIds.isEmpty()) {
                // Try the latest season that actually has matches; iterate in order until non-empty
                for (Long sid : seasonIds) {
                    if (sid == null) continue;
                    try {
                        List<Match> attempt = matchRepository.findRecentPlayedByTeamNameAndSeason(q, sid);
                        if (attempt != null && !attempt.isEmpty()) { list = attempt; break; }
                    } catch (Exception ignoredLoop) {}
                }
            }
            if (list == null || list.isEmpty()) {
                // Fallback to any recent matches across seasons if none found in the latest season(s)
                list = matchRepository.findRecentPlayedByTeamName(q);
            }
            if (list == null || list.isEmpty()) return summary;
            java.util.ArrayList<String> results = new java.util.ArrayList<>();
            java.util.ArrayList<Double> ppg = new java.util.ArrayList<>();
            int wins = 0, draws = 0;
            int cumPoints = 0; int counted = 0;
            for (Match m : list) {
                if (results.size() >= 5) break;
                Integer hg = m.getHomeGoals();
                Integer ag = m.getAwayGoals();
                if (hg == null || ag == null) continue;
                boolean isHome = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().equalsIgnoreCase(q));
                int my = isHome ? hg : ag;
                int opp = isHome ? ag : hg;
                int pts;
                if (my > opp) { results.add("W"); wins++; pts = 3; }
                else if (my == opp) { results.add("D"); draws++; pts = 1; }
                else { results.add("L"); pts = 0; }
                counted++; cumPoints += pts;
                double p = counted > 0 ? ((double) cumPoints) / counted : 0.0;
                ppg.add(Math.round(p * 10.0) / 10.0);
            }
            String streak = "0";
            if (!results.isEmpty()) {
                String first = results.get(0);
                int count = 1;
                for (int i = 1; i < results.size(); i++) {
                    if (results.get(i).equals(first)) count++; else break;
                }
                streak = count + first;
            }
            int valid = results.size();
            int winRate = (valid > 0) ? (int)Math.round((wins * 100.0) / valid) : 0;
            int points = wins * 3 + draws;
            summary = new com.chambua.vismart.dto.FormSummary(results, streak, winRate, points);
            summary.setPpgSeries(ppg);
        } catch (Exception ignored) {}
        return summary;
    }

    public record H2HSuggestion(String teamA, String teamB) {}
    public record H2HMatchDto(Integer year, String date, String homeTeam, String awayTeam, String result, String season) {}

    // --- Resolve seasonId by leagueId + seasonName (helper for universal ID-based calls) ---
    @GetMapping("/season-id")
    public Long resolveSeasonId(@RequestParam("leagueId") Long leagueId, @RequestParam("seasonName") String seasonName) {
        if (leagueId == null) return null;
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchController.class);
        // Try exact season match first when provided
        if (seasonName != null && !seasonName.isBlank()) {
            var exact = seasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, seasonName.trim())
                    .orElse(null);
            if (exact != null) return exact.getId();
            logger.warn("[SEASON_ID] Season '{}' not found for leagueId={}; falling back to latest.", seasonName, leagueId);
        }
        // Fallback: latest season for the league (by startDate desc)
        var latest = seasonRepository.findTopByLeagueIdOrderByStartDateDesc(leagueId).orElse(null);
        if (latest != null) {
            logger.info("[SEASON_ID][Fallback] leagueId={}, id={}, name='{}'", leagueId, latest.getId(), latest.getName());
            return latest.getId();
        }
        return null;
    }

    // --- H2H team forms using FormGuideService (season-aware, limit 5) ---
    @GetMapping("/h2h/form")
    public List<H2HFormTeamResponse> getH2HForms(@RequestParam(name = "homeId", required = false) Long homeId,
                                                 @RequestParam(name = "awayId", required = false) Long awayId,
                                                 @RequestParam(name = "seasonId", required = false) Long seasonId,
                                                 @RequestParam(name = "home", required = false) String homeName,
                                                 @RequestParam(name = "away", required = false) String awayName,
                                                 @RequestParam(name = "leagueId", required = false) Long leagueId,
                                                 @RequestParam(name = "seasonName", required = false) String seasonName,
                                                 @RequestParam(name = "autoSeason", defaultValue = "false") boolean autoSeason,
                                                 @RequestParam(name = "limit", defaultValue = "5") int limit) {
        long start = System.currentTimeMillis();
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchController.class);
        int lim = (limit <= 0) ? 5 : Math.min(limit, 10);

        // If IDs are provided (preferred), use them exclusively
        if (homeId != null && awayId != null && seasonId != null) {
            // Derive leagueId from seasonId
            Long lid = null;
            try {
                lid = seasonRepository.findById(seasonId).map(s -> s.getLeague() != null ? s.getLeague().getId() : null).orElse(null);
            } catch (Exception ignored) {}
            if (lid == null) return List.of();
            try {
                List<FormGuideRowDTO> rows = formGuideService.computeForTeams(lid, seasonId, lim, java.util.Arrays.asList(homeId, awayId));
                FormGuideRowDTO homeRow = rows.stream().filter(r -> Objects.equals(r.getTeamId(), homeId)).findFirst().orElse(null);
                FormGuideRowDTO awayRow = rows.stream().filter(r -> Objects.equals(r.getTeamId(), awayId)).findFirst().orElse(null);
                List<H2HFormTeamResponse> out = new ArrayList<>(2);
                if (homeRow != null) out.add(buildTeamResponseById(homeRow, seasonId, lim));
                if (awayRow != null) out.add(buildTeamResponseById(awayRow, seasonId, lim));
                logger.info("[H2H_FORM][RESP_ID] leagueId={}, seasonId={}, teams={}, ms={}", lid, seasonId, out.size(), (System.currentTimeMillis()-start));
                return out;
            } catch (Exception ex) {
                logger.error("[H2H_FORM][RESP_ID][ERROR] leagueId={}, seasonId={}, err={}", lid, seasonId, ex.toString());
                return List.of();
            }
        }

        // Backward-compatible: resolve by names within provided leagueId+seasonName (or latest season if seasonName missing), then use IDs thereafter
        if (homeName == null || homeName.isBlank() || awayName == null || awayName.isBlank() || leagueId == null) {
            return List.of();
        }
        String hn = homeName.trim();
        String an = awayName.trim();
        String sname = (seasonName == null ? "" : seasonName.trim());
        logger.info("[H2H_FORM][REQ_NAME] leagueId={}, seasonName='{}', limit={}, home='{}', away='{}'", leagueId, sname, lim, hn, an);
        Long sid = null;
        if (!sname.isBlank()) {
            sid = seasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, sname)
                    .map(s -> s.getId())
                    .orElse(null);
            if (sid == null) {
                logger.warn("[H2H_FORM][REQ_NAME] Season '{}' not found for leagueId={}; attempting latest season fallback.", sname, leagueId);
                if (autoSeason) {
                    sid = seasonRepository.findLatestWithPlayedMatchesByLeagueId(leagueId).map(s -> s.getId()).orElse(null);
                    if (sid != null) {
                        logger.info("[H2H_FORM][REQ_NAME][FallbackLatestWithPlayed] leagueId={}, seasonId={}.", leagueId, sid);
                    }
                }
            }
        }
        if (sid == null) {
            if (autoSeason) {
                sid = seasonRepository.findLatestWithPlayedMatchesByLeagueId(leagueId).map(s -> s.getId()).orElse(null);
                if (sid != null) {
                    logger.info("[H2H_FORM][REQ_NAME][AutoSeasonLatestWithPlayed] leagueId={}, seasonId={}.", leagueId, sid);
                }
            }
        }
        if (sid == null) {
            sid = seasonRepository.findTopByLeagueIdOrderByStartDateDesc(leagueId)
                    .map(s -> s.getId())
                    .orElse(null);
            if (sid == null) {
                logger.warn("[H2H_FORM][REQ_NAME] No seasons available for leagueId={}; aborting name-based form fetch.", leagueId);
                return List.of();
            } else {
                logger.warn("[H2H_FORM][REQ_NAME][FallbackLatestNoPlayed] leagueId={}, seasonId={}.", leagueId, sid);
            }
        }
        try {
            List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, sid, lim, FormGuideService.Scope.OVERALL);
            FormGuideRowDTO homeRow = rows.stream().filter(r -> r.getTeamName() != null && r.getTeamName().equalsIgnoreCase(hn)).findFirst().orElse(null);
            if (homeRow == null) {
                homeRow = rows.stream().filter(r -> r.getTeamName() != null && r.getTeamName().toLowerCase().contains(hn.toLowerCase())).findFirst().orElse(null);
            }
            FormGuideRowDTO awayRow = rows.stream().filter(r -> r.getTeamName() != null && r.getTeamName().equalsIgnoreCase(an)).findFirst().orElse(null);
            if (awayRow == null) {
                awayRow = rows.stream().filter(r -> r.getTeamName() != null && r.getTeamName().toLowerCase().contains(an.toLowerCase())).findFirst().orElse(null);
            }
            List<H2HFormTeamResponse> out = new ArrayList<>(2);
            if (homeRow != null) out.add(buildTeamResponseById(homeRow, sid, lim));
            if (awayRow != null) out.add(buildTeamResponseById(awayRow, sid, lim));
            logger.info("[H2H_FORM][RESP_NAME] leagueId={}, seasonId={}, teams={}, ms={}", leagueId, sid, out.size(), (System.currentTimeMillis()-start));
            return out;
        } catch (Exception ex) {
            logger.error("[H2H_FORM][RESP_NAME][ERROR] leagueId={}, seasonId={}, err={}", leagueId, sid, ex.toString());
            return List.of();
        }
    }

    private H2HFormTeamResponse buildTeamResponseById(FormGuideRowDTO row, Long seasonId, int limit) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchController.class);
        // Compose last5 metrics
        String streak = computeStreak(row.getLastResults());
        int winRate = computePercent(row.getW(), row.getMp());
        double ppg = row.getPpg();
        int btts = row.getBttsPct();
        int over25 = row.getOver25Pct();

        // Resolve season name for response
        String seasonResolved = null;
        try { seasonResolved = seasonRepository.findById(seasonId).map(s -> s.getName()).orElse(null); } catch (Exception ignored) {}

        // Validation pre-check: count played in season
        long playedCount = 0L;
        try { playedCount = matchRepository.countPlayedByTeamAndSeason(row.getTeamId(), seasonId); } catch (Exception ignored) {}
        if (playedCount == 0L) {
            logger.info("[H2H_FORM][Validation] No played matches for {} in season {}.", row.getTeamName(), (seasonResolved != null ? seasonResolved : String.valueOf(seasonId)));
        }

        // Fetch last N matches by teamId within the specified season (already ordered desc in query)
        List<Match> all = matchRepository.findRecentPlayedByTeamIdAndSeason(row.getTeamId(), seasonId);
        java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
        List<Long> excludedIds = new ArrayList<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Match m : all) {
            if (matches.size() >= limit) break;
            java.time.LocalDate md = m.getDate();
            if (md == null || md.isAfter(now)) {
                excludedIds.add(m.getId());
                continue;
            }
            seen.add(m.getId());
            String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("year", md.getYear());
            dto.put("date", md.toString());
            String homeName = null;
            String awayName = null;
            try { homeName = (m.getHomeTeam() != null ? m.getHomeTeam().getName() : null); } catch (Exception ex) { logger.warn("[Last5][Lazy] homeTeam name not initialized for matchId={}", m.getId()); }
            try { awayName = (m.getAwayTeam() != null ? m.getAwayTeam().getName() : null); } catch (Exception ex) { logger.warn("[Last5][Lazy] awayTeam name not initialized for matchId={}", m.getId()); }
            dto.put("homeTeam", homeName);
            dto.put("awayTeam", awayName);
            dto.put("result", result);
            matches.add(dto);
        }
        // If season-scoped list is sparse and backend flagged fallback, fetch recent matches from the team's domestic league (strict domestic fallback)
        if (matches.size() < Math.min(3, limit) && row.isFallback()) {
            try {
                Long domesticLeagueId = null;
                String domesticLeagueName = null;
                if (teamRepository != null) {
                    var proj = teamRepository.findTeamProjectionById(row.getTeamId());
                    if (proj != null) {
                        domesticLeagueId = proj.getLeagueId();
                        domesticLeagueName = proj.getLeagueName();
                    }
                }
                if (domesticLeagueId != null) {
                    List<Match> domestic = matchRepository.findRecentPlayedByTeamIdAndLeague(row.getTeamId(), domesticLeagueId);
                    for (Match m : domestic) {
                        if (matches.size() >= limit) break;
                        if (m == null || m.getId() == null) continue;
                        if (seen.contains(m.getId())) continue; // avoid duplicates if any
                        java.time.LocalDate md = m.getDate();
                        if (md == null || md.isAfter(now)) {
                            excludedIds.add(m.getId());
                            continue;
                        }
                        String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
                        Map<String, Object> dto = new LinkedHashMap<>();
                        dto.put("year", md.getYear());
                        dto.put("date", md.toString());
                        String homeName = null;
                        String awayName = null;
                        try { homeName = (m.getHomeTeam() != null ? m.getHomeTeam().getName() : null); } catch (Exception ex) { logger.warn("[Last5][Lazy] homeTeam name not initialized for matchId={}", m.getId()); }
                        try { awayName = (m.getAwayTeam() != null ? m.getAwayTeam().getName() : null); } catch (Exception ex) { logger.warn("[Last5][Lazy] awayTeam name not initialized for matchId={}", m.getId()); }
                        dto.put("homeTeam", homeName);
                        dto.put("awayTeam", awayName);
                        dto.put("result", result);
                        matches.add(dto);
                    }
                    logger.info("[Last5][FallbackMatches] Used domestic league recent matches for teamId={} (leagueId={}, added {} entries)", row.getTeamId(), domesticLeagueId, Math.max(0, matches.size()));
                }
            } catch (Exception ex) {
                logger.warn("[Last5][FallbackMatches][Error] teamId={}, err={}", row.getTeamId(), ex.toString());
            }
        }
        if (!excludedIds.isEmpty()) {
            logger.info("[Last5][Validation] Excluded matches for teamId={}, seasonId={}: IDs={} due to null or future date", row.getTeamId(), seasonId, excludedIds);
        }
        // matchesAvailable info
        String matchesAvailable = matches.size() + " of " + limit + " matches available";

        // Response includes both teamId and teamName for display
        Map<String, Object> last5 = new LinkedHashMap<>();
        // Streak must be based on the most recent match and extend backwards until broken (e.g., 3W, 1D, 2L)
        last5.put("streak", streak);
        // Provide compact recent-sequence for UI badges (most recent first), with fillers handled client-side if needed
        last5.put("recent", row.getLastResults());
        last5.put("winRate", winRate);
        last5.put("pointsPerGame", ppg);
        last5.put("bttsPercent", btts);
        last5.put("over25Percent", over25);
        last5.put("fallback", row.isFallback());
        String teamIdStr = row.getTeamId() != null ? String.valueOf(row.getTeamId()) : null;
        String teamName = row.getTeamName();
        // Ensure sourceLeague is set without triggering lazy initialization (needed for note text)
        String sourceLeague = row.getSourceLeague();
        Long sourceLeagueId = null;
        if ((sourceLeague == null || sourceLeague.isBlank()) && teamRepository != null) {
            try {
                var proj = teamRepository.findTeamProjectionById(row.getTeamId());
                if (proj != null) { sourceLeague = proj.getLeagueName(); sourceLeagueId = proj.getLeagueId(); }
                logger.info("[Last5][SourceLeague] teamId={}, resolved='{}' via projection", row.getTeamId(), sourceLeague);
            } catch (Exception ex) {
                logger.warn("[Last5][SourceLeague][Error] teamId={}, err={}", row.getTeamId(), ex.toString());
            }
        }
        String note;
        if (row.isFallback()) {
            // Use domestic league phrasing to avoid implying cross-competition mixing
            String leagueLabel = (sourceLeague != null && !sourceLeague.isBlank()) ? sourceLeague : "domestic league";
            note = "Recent form for " + teamName + " — last up to 5 played matches from " + leagueLabel + " (fallback due to limited data in this season). Points use W=3, D=1, L=0.";
            try {
                if (adminAuditRepository != null) {
                    com.chambua.vismart.model.AdminAudit audit = new com.chambua.vismart.model.AdminAudit();
                    audit.setAction("last5_fallback_domestic");
                    audit.setParams("{\"teamId\": " + teamIdStr + ", \"seasonId\": " + seasonId + ", \"league\": \"" + leagueLabel + "\"}");
                    audit.setAffectedCount(1L);
                    adminAuditRepository.save(audit);
                }
            } catch (Exception ignoredAudit) {}
        } else {
            note = "Recent form for " + teamName + " — last up to 5 played matches in season " + (seasonResolved != null ? seasonResolved : String.valueOf(seasonId)) + ". Points use W=3, D=1, L=0.";
        }
        logger.info("[Last5][Response] teamId={}, matches={}, sourceLeague={}", row.getTeamId(), matches.size(), sourceLeague);
        return new H2HFormTeamResponse(teamIdStr, teamName, last5, matches, seasonResolved, matchesAvailable, note, sourceLeague);
    }

    private String computeStreak(List<String> last) {
        if (last == null || last.isEmpty()) return "0";
        String first = last.get(0);
        int count = 1;
        for (int i = 1; i < last.size(); i++) { if (Objects.equals(first, last.get(i))) count++; else break; }
        return count + (first != null ? first : "");
    }

    private int computePercent(int part, int total) {
        if (total <= 0) return 0;
        return (int) Math.round((part * 100.0) / total);
    }

    private String formatFormString(List<String> seq) {
        // Convert list like [W,W,D] into compact string with fillers to length 5
        int max = 5;
        StringBuilder sb = new StringBuilder();
        if (seq != null) {
            for (int i = 0; i < Math.min(max, seq.size()); i++) sb.append(seq.get(i));
        }
        while (sb.length() < max) sb.append('•');
        return sb.toString();
    }

    // --- Diagnostics: Verify Correct Scores (server-side Poisson grid 0..10) ---
    @GetMapping("/verify-correct-scores")
    public List<Map<String, Object>> verifyCorrectScores(
            @RequestParam("homeTeamId") Long homeTeamId,
            @RequestParam("awayTeamId") Long awayTeamId,
            @RequestParam(value = "leagueId", required = false) Long leagueId) {
        try {
            if (homeTeamId == null || awayTeamId == null) return List.of();
            List<Match> matches;
            if (leagueId != null) {
                matches = matchRepository.findHeadToHead(leagueId, homeTeamId, awayTeamId);
            } else {
                List<Match> a = matchRepository.findH2HByTeamIds(homeTeamId, awayTeamId);
                List<Match> b = matchRepository.findH2HByTeamIds(awayTeamId, homeTeamId);
                matches = new ArrayList<>();
                if (a != null) matches.addAll(a);
                if (b != null) matches.addAll(b);
            }
            if (matches == null) matches = List.of();

            // Compute per-team average goals using orientation-aware mapping
            int aCount = 0, bCount = 0;
            double aFor = 0, bFor = 0;
            for (Match m : matches) {
                Integer hg = m.getHomeGoals();
                Integer ag = m.getAwayGoals();
                if (hg == null || ag == null) continue;
                if (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && m.getHomeTeam().getId().equals(homeTeamId)) {
                    aFor += hg; aCount++;
                } else if (m.getAwayTeam() != null && m.getAwayTeam().getId() != null && m.getAwayTeam().getId().equals(homeTeamId)) {
                    aFor += ag; aCount++;
                }
                if (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && m.getHomeTeam().getId().equals(awayTeamId)) {
                    bFor += hg; bCount++;
                } else if (m.getAwayTeam() != null && m.getAwayTeam().getId() != null && m.getAwayTeam().getId().equals(awayTeamId)) {
                    bFor += ag; bCount++;
                }
            }
            double leagueAvg = 1.4d;
            boolean aFallback = aCount < 3;
            boolean bFallback = bCount < 3;
            double aAvg = aFallback ? leagueAvg : (aCount > 0 ? (aFor / aCount) : leagueAvg);
            double bAvg = bFallback ? leagueAvg : (bCount > 0 ? (bFor / bCount) : leagueAvg);

            // Apply home/away modifiers like frontend
            double lambdaA = aAvg * 1.15d;
            double lambdaB = bAvg * 0.95d;

            // Build 0..10 grid
            int MAX = 10;
            double[] cacheA = new double[MAX + 1];
            double[] cacheB = new double[MAX + 1];
            for (int i = 0; i <= MAX; i++) cacheA[i] = poisson(lambdaA, i);
            for (int j = 0; j <= MAX; j++) cacheB[j] = poisson(lambdaB, j);

            List<double[]> raw = new ArrayList<>(); // [h, a, p]
            double total = 0.0d;
            for (int h = 0; h <= MAX; h++) {
                for (int a = 0; a <= MAX; a++) {
                    double p = cacheA[h] * cacheB[a];
                    total += p;
                    raw.add(new double[]{h, a, p});
                }
            }
            if (total <= 0) return List.of();
            // Normalize, sort desc
            raw.sort((u, v) -> Double.compare(v[2], u[2]));

            List<Map<String, Object>> top = new ArrayList<>();
            int limit = Math.min(3, raw.size());
            for (int i = 0; i < limit; i++) {
                double[] r = raw.get(i);
                String score = ((int) r[0]) + "-" + ((int) r[1]);
                double prob = (r[2] / total) * 100.0d;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", score);
                item.put("probability", Math.round(prob * 10.0d) / 10.0d);
                top.add(item);
            }
            // Compute Over 3.5 using same grid normalization: 1 - P(total <= 3)
            double sumLe3 = 0.0d;
            for (double[] cell : raw) {
                int h = (int) cell[0];
                int a = (int) cell[1];
                if (h + a <= 3) sumLe3 += cell[2];
            }
            double over35 = (total > 0.0d) ? (1.0d - (sumLe3 / total)) : 0.0d;
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("over35", Math.round(over35 * 1000.0d) / 10.0d);
            top.add(extra);
            return top;
        } catch (Exception e) {
            return List.of();
        }
    }

    private double poisson(double lambda, int k) {
        return (Math.exp(-lambda) * Math.pow(lambda, k)) / factorial(k);
    }
    private double factorial(int n) {
        if (n <= 1) return 1.0d;
        double f = 1.0d;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    public record H2HFormTeamResponse(String teamId, String teamName, Map<String, Object> last5, List<Map<String, Object>> matches, String seasonResolved, String matchesAvailable, String note, String sourceLeague) {}

    private String computeStreakInsightText(String teamName, String targetPattern) {
        if (teamName == null || teamName.isBlank() || targetPattern == null || targetPattern.isBlank() || "0".equals(targetPattern)) {
            return teamName + ": no active streak detected.";
        }
        java.util.List<Match> list;
        try { list = matchRepository.findRecentPlayedByTeamName(teamName.trim()); } catch (Exception ex) { list = java.util.Collections.emptyList(); }
        if (list == null || list.isEmpty()) return teamName + ": no match history found for streak insight.";
        java.util.List<Match> chron = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(chron);
        String prevType = null; int prevCount = 0;
        int totalInstances = 0, nextW = 0, nextD = 0, nextL = 0, nextBTTS = 0, nextOv15 = 0, nextOv25 = 0, nextOv35 = 0;
        for (Match m : chron) {
            Integer hg = m.getHomeGoals(); Integer ag = m.getAwayGoals(); if (hg == null || ag == null) continue;
            boolean isHome = false; try { isHome = m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().equalsIgnoreCase(teamName); } catch (Exception ignored) {}
            int my = isHome ? hg : ag; int opp = isHome ? ag : hg;
            String res = (my > opp) ? "W" : (my == opp ? "D" : "L");
            String pre = (prevType == null) ? "0" : (prevCount + prevType);
            if (!"0".equals(pre) && pre.equalsIgnoreCase(targetPattern)) {
                totalInstances++;
                if ("W".equals(res)) nextW++; else if ("D".equals(res)) nextD++; else nextL++;
                int total = my + opp; if (my > 0 && opp > 0) nextBTTS++; if (total >= 2) nextOv15++; if (total >= 3) nextOv25++; if (total >= 4) nextOv35++;
            }
            if (prevType == null || !prevType.equals(res)) { prevType = res; prevCount = 1; } else { prevCount++; }
        }
        if (totalInstances <= 0) return teamName + " has had 0 prior instances of a " + targetPattern + " streak across recorded matches.";
        int wPct = (int) Math.round((nextW * 100.0) / totalInstances);
        int dPct = (int) Math.round((nextD * 100.0) / totalInstances);
        int lPct = Math.max(0, 100 - (wPct + dPct));
        int bttsPct = (int) Math.round((nextBTTS * 100.0) / totalInstances);
        int o15Pct = (int) Math.round((nextOv15 * 100.0) / totalInstances);
        int o25Pct = (int) Math.round((nextOv25 * 100.0) / totalInstances);
        int o35Pct = (int) Math.round((nextOv35 * 100.0) / totalInstances);
        return teamName + " has had " + totalInstances + " instances of a " + targetPattern +
                " streak. Of the matches that followed: " + wPct + "% were wins, " + dPct + "% were draws, " + lPct + "% were losses. " +
                o35Pct + "% were Over 3.5, " + o25Pct + "% were Over 2.5, " + o15Pct + "% were Over 1.5, and " + bttsPct + "% were BTTS.";
    }

    // --- PDF generation endpoint ---
    @PostMapping("/generate-analysis-pdf")
    public ResponseEntity<byte[]> generateAnalysisPdf(@RequestBody AnalysisRequest request) {
        try {
            // Enrich request with streak insight summaries if last-5 streaks and team names are present
            try {
                if (request != null && request.getH2h() != null) {
                    var h2h = request.getH2h();
                    String teamA = (request.getTeamA() != null) ? request.getTeamA().getName() : null;
                    String teamB = (request.getTeamB() != null) ? request.getTeamB().getName() : null;
                    String patA = (h2h.getLast5TeamA() != null) ? h2h.getLast5TeamA().getStreak() : null;
                    String patB = (h2h.getLast5TeamB() != null) ? h2h.getLast5TeamB().getStreak() : null;
                    if (teamA != null && patA != null && !patA.isBlank() && !"0".equals(patA)) {
                        String txt = computeStreakInsightText(teamA, patA);
                        h2h.setStreakInsightA(txt);
                    }
                    if (teamB != null && patB != null && !patB.isBlank() && !"0".equals(patB)) {
                        String txt = computeStreakInsightText(teamB, patB);
                        h2h.setStreakInsightB(txt);
                    }
                }
            } catch (Exception ignored) {}
            byte[] pdf = laTeXService.generateAnalysisPdf(request);
            String homeRaw = request.getTeamA()!=null? request.getTeamA().getName() : "Team A";
            String awayRaw = request.getTeamB()!=null? request.getTeamB().getName() : "Team B";
            String home = homeRaw != null ? homeRaw.trim() : "Team A";
            String away = awayRaw != null ? awayRaw.trim() : "Team B";
            java.time.ZoneId tz = java.time.ZoneId.systemDefault();
            // Analysis date-time for filename (local tz)
            String analysisStamp = java.time.ZonedDateTime.now(tz).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"));
            // Optional fixture date (date-only preferred)
            String fixturePart = null;
            try {
                String src = request.getSource();
                String fx = request.getFixtureDate();
                if (fx != null && !fx.isBlank() && src != null && (src.equalsIgnoreCase("fixtures") || src.equalsIgnoreCase("home") || src.equalsIgnoreCase("home-today") || src.equalsIgnoreCase("today"))) {
                    java.time.OffsetDateTime odt;
                    try { odt = java.time.OffsetDateTime.parse(fx); }
                    catch (Exception e1) {
                        try { odt = java.time.LocalDateTime.parse(fx).atOffset(java.time.ZoneOffset.UTC); } catch (Exception e2) {
                            try { odt = java.time.LocalDate.parse(fx).atStartOfDay().atOffset(java.time.ZoneOffset.UTC); } catch (Exception e3) { odt = null; }
                        }
                    }
                    if (odt != null) {
                        String d = odt.atZoneSameInstant(tz).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        fixturePart = " - Fixture " + d;
                    }
                }
            } catch (Exception ignored) {}
            java.util.function.Function<String,String> clean = (s) -> s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]+", " ").replaceAll("\n|\r", " ").trim();
            String teamsTitle = (clean.apply(home) + " VS " + clean.apply(away)).replaceAll("\\s+", " ").trim();
            String baseName = teamsTitle + " - Analysis " + analysisStamp + (fixturePart != null ? fixturePart : "");
            // Final sanitize for filename: collapse spaces to single, replace spaces with underscores or hyphens as preferred
            String filenameSafe = baseName.replaceAll("\u00A0", " ").replaceAll("\\s+", " ").replace('"', ' ').trim();
            // Replace spaces with underscores to be URL/FS friendly
            String filename = filenameSafe.replace(' ', '_') + ".pdf";
            // Persist generated PDF to archive (best-effort; do not fail the response if DB fails)
            try { if (pdfArchiveService != null) { pdfArchiveService.save(request, pdf, filename, "application/pdf"); } } catch (Exception ignore) {}
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception ex) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analysis.pdf");
            byte[] fallback = ("PDF generation error: " + ex.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.status(500).headers(headers).body(fallback);
        }
    }

    // --- Analysis PDFs: archive list and retrieval ---
    @GetMapping("/analysis-pdfs")
    public Map<String, Object> listAnalysisPdfs(@RequestParam(name = "page", defaultValue = "0") int page,
                                                @RequestParam(name = "size", defaultValue = "20") int size) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (pdfArchiveService == null) { resp.put("content", List.of()); resp.put("page", 0); resp.put("size", 0); resp.put("totalElements", 0); resp.put("totalPages", 0); return resp; }
        var pg = pdfArchiveService.list(page, size);
        java.util.List<Map<String,Object>> items = new ArrayList<>();
        for (com.chambua.vismart.model.PdfArchive a : pg.getContent()) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("filename", a.getFilename());
            m.put("homeTeam", a.getHomeTeam());
            m.put("awayTeam", a.getAwayTeam());
            m.put("generatedAt", a.getGeneratedAt());
            m.put("sizeBytes", a.getSizeBytes());
            items.add(m);
        }
        resp.put("content", items);
        resp.put("page", pg.getNumber());
        resp.put("size", pg.getSize());
        resp.put("totalElements", pg.getTotalElements());
        resp.put("totalPages", pg.getTotalPages());
        return resp;
    }

    @GetMapping("/analysis-pdfs/{id}")
    public ResponseEntity<byte[]> downloadAnalysisPdf(@PathVariable("id") Long id) {
        if (pdfArchiveService == null) return ResponseEntity.notFound().build();
        return pdfArchiveService.get(id)
                .map(a -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_PDF);
                    h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + a.getFilename());
                    return ResponseEntity.ok().headers(h).body(a.getBytes());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/analysis-pdfs/{id}/inline")
    public ResponseEntity<byte[]> inlineAnalysisPdf(@PathVariable("id") Long id) {
        if (pdfArchiveService == null) return ResponseEntity.notFound().build();
        return pdfArchiveService.get(id)
                .map(a -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_PDF);
                    h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + a.getFilename());
                    return ResponseEntity.ok().headers(h).body(a.getBytes());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // --- Merge multiple archived analysis PDFs into a single PDF ---
    @PostMapping("/analysis-pdfs/merge")
    public ResponseEntity<byte[]> mergeAnalysisPdfs(@RequestBody Map<String, Object> body) {
        if (pdfArchiveService == null) return ResponseEntity.notFound().build();
        try {
            java.util.List<?> rawIds = body == null ? java.util.List.of() : (java.util.List<?>) body.get("ids");
            java.util.List<Long> ids = new java.util.ArrayList<>();
            if (rawIds != null) {
                for (Object o : rawIds) {
                    if (o instanceof Number n) ids.add(n.longValue());
                    else if (o instanceof String s) { try { ids.add(Long.parseLong(s)); } catch (Exception ignored) {} }
                }
            }
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest().body("No PDF IDs provided".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            java.util.List<byte[]> pdfs = new java.util.ArrayList<>();
            for (Long id : ids) {
                var opt = pdfArchiveService.get(id);
                if (opt.isPresent()) {
                    var a = opt.get();
                    byte[] b = a.getBytes();
                    if (b != null && b.length > 0) pdfs.add(b);
                }
            }
            if (pdfs.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Merge using iText 8
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
            com.itextpdf.kernel.pdf.PdfDocument target = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            com.itextpdf.kernel.utils.PdfMerger merger = new com.itextpdf.kernel.utils.PdfMerger(target);
            for (byte[] srcBytes : pdfs) {
                com.itextpdf.kernel.pdf.PdfReader reader = new com.itextpdf.kernel.pdf.PdfReader(new java.io.ByteArrayInputStream(srcBytes));
                // Some PDFs might be corrupted; guard with try/catch and continue
                try (com.itextpdf.kernel.pdf.PdfDocument src = new com.itextpdf.kernel.pdf.PdfDocument(reader)) {
                    int pages = src.getNumberOfPages();
                    if (pages > 0) {
                        merger.merge(src, 1, pages);
                    }
                } catch (Exception e) {
                    // skip this source on error
                }
            }
            target.close();
            byte[] merged = baos.toByteArray();

            String ts = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm"));
            String filename = "Fixture_Analysis_Combined_" + ts + ".pdf";
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            return ResponseEntity.ok().headers(h).body(merged);
        } catch (Exception ex) {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Fixture_Analysis_Combined.pdf\"");
            byte[] fallback = ("Merge error: " + ex.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.status(500).headers(h).body(fallback);
        }
    }
}

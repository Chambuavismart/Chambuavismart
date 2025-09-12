package com.chambua.vismart.controller;

import com.chambua.vismart.dto.TeamResultsBreakdownResponse;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.H2HService;
import com.chambua.vismart.service.FormGuideService;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.dto.FormGuideRowDTO;
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

    @org.springframework.beans.factory.annotation.Autowired
    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags, FormGuideService formGuideService, SeasonRepository seasonRepository) {
        this.matchRepository = matchRepository;
        this.h2hService = h2hService;
        this.featureFlags = featureFlags;
        this.formGuideService = formGuideService;
        this.seasonRepository = seasonRepository;
    }

    // Backward-compatible constructor for existing tests (H2H form endpoint will be unavailable)
    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags) {
        this(matchRepository, h2hService, featureFlags, null, null);
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
        if (name == null || name.trim().isEmpty()) return new TeamResultsBreakdownResponse(0,0,0,0,0,0);
        String q = name.trim();
        long total = matchRepository.countPlayedByTeamName(q);
        long wins = matchRepository.countWinsByTeamName(q);
        long draws = matchRepository.countDrawsByTeamName(q);
        long losses = matchRepository.countLossesByTeamName(q);
        long btts = matchRepository.countBttsByTeamName(q);
        long over25 = matchRepository.countOver25ByTeamName(q);
        // Safety: ensure consistency even if data anomalies
        if (losses + wins + draws != total) {
            long computedLosses = Math.max(0, total - wins - draws);
            losses = computedLosses;
        }
        return new TeamResultsBreakdownResponse(total, wins, draws, losses, btts, over25);
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
        // Preferred: ID + season-scoped path
        if (homeId != null && awayId != null && seasonId != null) {
            try {
                // Align with analysis service: prefer league-scoped season query
                Long lid = null;
                try {
                    var sOpt = seasonRepository.findById(seasonId);
                    if (sOpt.isPresent() && sOpt.get().getLeague() != null && sOpt.get().getLeague().getId() != null) {
                        lid = sOpt.get().getLeague().getId();
                    }
                } catch (Exception ignored) { /* fallback below */ }
                if (lid != null) {
                    list = matchRepository.findHeadToHeadBySeason(lid, seasonId, homeId, awayId);
                    path = "ID+SEASON+LEAGUE";
                }
                // Fallback: season-only, both orientations (scores or PLAYED)
                if (list == null || list.isEmpty()) {
                    list = matchRepository.findH2HByTeamIdsAndSeason(homeId, awayId, seasonId);
                    if (path.equals("none")) path = "ID+SEASON"; else path += ">ID+SEASON";
                }
                // New: Fallback to league-only within resolved league (cross-season within same league)
                if ((list == null || list.isEmpty()) && lid != null) {
                    try {
                        list = matchRepository.findHeadToHead(lid, homeId, awayId);
                        path += ">ID+LEAGUE";
                    } catch (Exception ignoredFallback) { /* keep empty */ }
                }
            } catch (Exception ex) { logger.warn("[H2H_MATCHES][ERR][ID+SEASON] {}", ex.toString()); list = java.util.Collections.emptyList(); }
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
            List<FormGuideRowDTO> rows = formGuideService.compute(lid, seasonId, lim, FormGuideService.Scope.OVERALL);
            FormGuideRowDTO homeRow = rows.stream().filter(r -> Objects.equals(r.getTeamId(), homeId)).findFirst().orElse(null);
            FormGuideRowDTO awayRow = rows.stream().filter(r -> Objects.equals(r.getTeamId(), awayId)).findFirst().orElse(null);
            List<H2HFormTeamResponse> out = new ArrayList<>(2);
            if (homeRow != null) out.add(buildTeamResponseById(homeRow, seasonId, lim));
            if (awayRow != null) out.add(buildTeamResponseById(awayRow, seasonId, lim));
            logger.info("[H2H_FORM][RESP_ID] leagueId={}, seasonId={}, teams={}, ms={}", lid, seasonId, out.size(), (System.currentTimeMillis()-start));
            return out;
        }

        // Backward-compatible: resolve by names within provided leagueId+seasonName, then use IDs thereafter
        if (homeName == null || homeName.isBlank() || awayName == null || awayName.isBlank() || leagueId == null || seasonName == null || seasonName.isBlank()) {
            return List.of();
        }
        String hn = homeName.trim();
        String an = awayName.trim();
        logger.info("[H2H_FORM][REQ_NAME] leagueId={}, seasonName='{}', limit={}, home='{}', away='{}'", leagueId, seasonName, lim, hn, an);
        Long sid = seasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, seasonName)
                .map(s -> s.getId())
                .orElse(null);
        if (sid == null) {
            logger.warn("[H2H_FORM][STRICT_NO_SEASON] Requested season '{}' not found for leagueId={}; no fallback will be applied.", seasonName, leagueId);
            return List.of();
        }
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
    }

    private H2HFormTeamResponse buildTeamResponseById(FormGuideRowDTO row, Long seasonId, int limit) {
        // Compose last5 metrics
        String streak = computeStreak(row.getLastResults());
        int winRate = computePercent(row.getW(), row.getMp());
        double ppg = row.getPpg();
        int btts = row.getBttsPct();
        int over25 = row.getOver25Pct();
        // Fetch last N matches by teamId within the specified season (already ordered desc in query)
        List<Match> all = matchRepository.findRecentPlayedByTeamIdAndSeason(row.getTeamId(), seasonId);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Match m : all) {
            if (matches.size() >= limit) break;
            String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("year", m.getDate() != null ? m.getDate().getYear() : null);
            dto.put("date", m.getDate() != null ? m.getDate().toString() : null);
            dto.put("homeTeam", m.getHomeTeam() != null ? m.getHomeTeam().getName() : null);
            dto.put("awayTeam", m.getAwayTeam() != null ? m.getAwayTeam().getName() : null);
            dto.put("result", result);
            matches.add(dto);
        }
        // Response includes both teamId and teamName for display
        Map<String, Object> last5 = new LinkedHashMap<>();
        last5.put("streak", formatFormString(row.getLastResults()));
        last5.put("winRate", winRate);
        last5.put("pointsPerGame", ppg);
        last5.put("bttsPercent", btts);
        last5.put("over25Percent", over25);
        String teamIdStr = row.getTeamId() != null ? String.valueOf(row.getTeamId()) : null;
        String teamName = row.getTeamName();
        return new H2HFormTeamResponse(teamIdStr, teamName, last5, matches);
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

    public record H2HFormTeamResponse(String teamId, String teamName, Map<String, Object> last5, List<Map<String, Object>> matches) {}
}

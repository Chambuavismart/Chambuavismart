package com.chambua.vismart.controller;

import com.chambua.vismart.dto.TeamDto;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.service.TeamService;
import com.chambua.vismart.util.TeamNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private final TeamRepository teamRepository;
    private final TeamService teamService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.repository.FixtureRepository fixtureRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.service.FixtureService fixtureService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.repository.AdminAuditRepository adminAuditRepository;

    // Inject MatchRepository for activity-based canonical team selection when multiple global candidates exist
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.repository.MatchRepository matchRepository;

    // Primary leagues prioritized when resolving ambiguous team names globally
    private static final java.util.Set<String> PRIMARY_LEAGUES = java.util.Set.of(
            "La Liga", "Premier League", "Serie A", "Bundesliga", "Ligue 1",
            "Eredivisie", "Primeira Liga", "Scottish Premiership", "LaLiga", "Liga NOS"
    );

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.service.TeamOutcomeService teamOutcomeService;

    public TeamController(TeamRepository teamRepository, TeamService teamService) {
        this.teamRepository = teamRepository;
        this.teamService = teamService;
    }

    // New: last-N played matches (most recent first) for a team by name, summarized as briefs
    @GetMapping("/{teamName}/last-played-list")
    public org.springframework.http.ResponseEntity<java.util.List<com.chambua.vismart.dto.LastMatchBriefDTO>> getLastPlayedList(
            @PathVariable("teamName") String teamName,
            @RequestParam(value = "limit", required = false, defaultValue = "2") int limit
    ) {
        String tn = teamName == null ? null : teamName.trim();
        if (tn == null || tn.length() < 2) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Collections.emptyList());
        }
        int lim = Math.max(1, Math.min(limit, 20));
        java.util.List<com.chambua.vismart.model.Match> recent;
        try {
            recent = matchRepository.findRecentPlayedByTeamName(tn);
        } catch (Exception e) {
            recent = java.util.Collections.emptyList();
        }
        java.util.ArrayList<com.chambua.vismart.dto.LastMatchBriefDTO> out = new java.util.ArrayList<>();
        if (recent != null) {
            for (com.chambua.vismart.model.Match m : recent) {
                if (out.size() >= lim) break;
                try {
                    Integer hg = m.getHomeGoals();
                    Integer ag = m.getAwayGoals();
                    if (hg == null || ag == null) continue;
                    String home = m.getHomeTeam() != null ? m.getHomeTeam().getName() : null;
                    String away = m.getAwayTeam() != null ? m.getAwayTeam().getName() : null;
                    boolean isHome = home != null && home.equalsIgnoreCase(tn);
                    boolean isAway = !isHome && away != null && away.equalsIgnoreCase(tn);
                    if (!isHome && !isAway) {
                        // as a soft match, accept contains
                        isHome = home != null && tn != null && home.toLowerCase().contains(tn.toLowerCase());
                        isAway = !isHome && away != null && tn != null && away.toLowerCase().contains(tn.toLowerCase());
                    }
                    String opponent = isHome ? (away != null ? away : "?") : (home != null ? home : "?");
                    int my = isHome ? hg : (isAway ? ag : hg);
                    int opp = isHome ? ag : (isAway ? hg : ag);
                    String res = (my > opp) ? "W" : (my == opp ? "D" : "L");
                    String score = (isHome ? hg : ag) + "-" + (isHome ? ag : hg);
                    java.time.LocalDate date = m.getDate();
                    String seasonLabel = null;
                    try {
                        com.chambua.vismart.model.Season s = m.getSeason();
                        if (s != null) {
                            try {
                                java.lang.reflect.Method getName = s.getClass().getMethod("getName");
                                Object nameVal = getName.invoke(s);
                                if (nameVal != null) seasonLabel = String.valueOf(nameVal);
                            } catch (Exception ignored) {}
                            if (seasonLabel == null) {
                                try {
                                    java.lang.reflect.Method getSeason = s.getClass().getMethod("getSeason");
                                    Object sv = getSeason.invoke(s);
                                    if (sv != null) seasonLabel = String.valueOf(sv);
                                } catch (Exception ignored2) {}
                            }
                            if (seasonLabel == null) {
                                try {
                                    java.lang.reflect.Method getStart = s.getClass().getMethod("getStartDate");
                                    java.lang.reflect.Method getEnd = s.getClass().getMethod("getEndDate");
                                    Object st = getStart.invoke(s);
                                    Object en = getEnd.invoke(s);
                                    if (st instanceof java.time.LocalDate) {
                                        int y1 = ((java.time.LocalDate) st).getYear();
                                        Integer y2 = null;
                                        if (en instanceof java.time.LocalDate) y2 = ((java.time.LocalDate) en).getYear();
                                        seasonLabel = y1 + (y2 != null ? "/" + y2 : "");
                                    }
                                } catch (Exception ignored3) {}
                            }
                        }
                        if (seasonLabel == null && m.getLeague() != null) {
                            try {
                                java.lang.reflect.Method getSeasonStr = m.getLeague().getClass().getMethod("getSeason");
                                Object sv = getSeasonStr.invoke(m.getLeague());
                                if (sv != null) seasonLabel = String.valueOf(sv);
                            } catch (Exception ignored4) {}
                        }
                    } catch (Exception ignored5) {}
                    com.chambua.vismart.dto.LastMatchBriefDTO dto = new com.chambua.vismart.dto.LastMatchBriefDTO(date, seasonLabel, opponent, res, score);
                    out.add(dto);
                } catch (Exception ignored) {}
            }
        }
        return org.springframework.http.ResponseEntity.ok(out);
    }

    private TeamDto toDto(Team team) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                null,
                team.getLeague() != null ? team.getLeague().getId() : null,
                team.getLeague() != null ? team.getLeague().getName() : null
        );
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTeams(@RequestParam("query") String query,
                                         @RequestParam(name = "leagueId", required = false) Long leagueId) {
        if (query == null || query.trim().length() < 3) return ResponseEntity.ok(List.of());
        String raw = query.trim();
        String normalized = TeamNameNormalizer.normalize(raw);
        log.info("[Team][Search] raw='{}', normalized='{}', leagueId={}", raw, normalized, leagueId);
        // Use lightweight projection to avoid lazy-loading; map to DTOs
        var list = (leagueId != null
                ? teamRepository.searchByNameWithCountryAndLeague(normalized, raw, leagueId)
                : teamRepository.searchByNameWithCountry(normalized, raw))
                .stream()
                .limit(20)
                .map(p -> new TeamDto(p.getId(), p.getName(), null, p.getLeagueId(), p.getLeagueName()))
                .collect(Collectors.toList());
        if (leagueId != null && list.isEmpty()) {
            log.warn("[Team][Search][Fallback] Global search for query='{}' (leagueId={})", raw, leagueId);
            list = teamRepository.searchByNameWithCountry(normalized, raw)
                    .stream()
                    .limit(10)
                    .map(p -> new TeamDto(p.getId(), p.getName(), null, p.getLeagueId(), p.getLeagueName()))
                    .collect(Collectors.toList());
        }
        log.info("[Team][Search][Resp] size={}", list.size());
        return ResponseEntity.ok(list);
    }

    // Duplicate-safe exact resolver by name or alias (case-insensitive)
    @GetMapping("/by-name")
    public ResponseEntity<?> findByName(@RequestParam String name,
                                        @RequestParam(name = "leagueId", required = false) Long leagueId,
                                        @RequestParam(name = "teamId", required = false) Long teamId) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing name"));
        }
        String raw = name.trim();
        String normalized = TeamNameNormalizer.normalize(raw);
        // Fast-path: explicit teamId retry to resolve ambiguity
        if (teamId != null) {
            var opt = teamRepository.findById(teamId);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Team not found", "teamId", teamId));
            }
            Team t = opt.get();
            if (leagueId != null && (t.getLeague() == null || !leagueId.equals(t.getLeague().getId()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "Team does not belong to provided league",
                                "teamId", teamId,
                                "leagueId", leagueId
                        ));
            }
            log.info("[Team][ByName][ID] teamId={}, leagueId={} -> OK", teamId, leagueId);
            return ResponseEntity.ok(toDto(t));
        }
        log.info("[Team][ByName] raw='{}', normalized='{}', leagueId={}", raw, normalized, leagueId);
        // First try league-scoped match
        List<Team> leagueMatches = (leagueId != null)
                ? teamRepository.findByNameOrAliasWithLeague(normalized, raw).stream()
                    .filter(t -> t.getLeague() != null && Objects.equals(t.getLeague().getId(), leagueId))
                    .toList()
                : List.of();
        if (leagueId == null) {
            leagueMatches = teamRepository.findByNameOrAliasWithLeague(normalized, raw);
        }
        if (leagueMatches != null && !leagueMatches.isEmpty()) {
            if (leagueMatches.size() == 1) {
                Team t = leagueMatches.get(0);
                log.info("[Team][ByName][Resp] id={}, name='{}'", t.getId(), t.getName());
                return ResponseEntity.ok(toDto(t));
            } else {
                log.warn("[Team][ByName] multiple candidates for raw='{}' (normalized='{}', leagueId={}): {}", raw, normalized, leagueId, leagueMatches.size());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "Multiple teams found",
                                "count", leagueMatches.size(),
                                "candidates", leagueMatches.stream().map(this::toDto).toList()
                        ));
            }
        }
        // Fallback to global resolution
        List<Team> global = teamRepository.findByNameOrAliasWithLeague(normalized, raw);
        if (global == null || global.isEmpty()) {
            // Diagnostics: check if a trimmed-name equality would have matched, indicating possible normalization mismatch
            long trimmedEqCount = (leagueId != null)
                    ? teamRepository.countByTrimmedNameIgnoreCaseAndLeagueId(raw, leagueId)
                    : teamRepository.countByTrimmedNameIgnoreCase(raw);
            long anomalyCount = 0L;
            try { anomalyCount = teamRepository.countSpaceAnomalies(); } catch (Exception ignored) {}
            log.warn("[Team][ByName] not found (global) for raw='{}', normalized='{}' (leagueId={}). trimmedEqCount={}, spaceAnomalies={}", raw, normalized, leagueId, trimmedEqCount, anomalyCount);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Team not found", "name", raw));
        }
        // Log and audit fallback usage
        log.warn("[Team][ByName][Fallback] No match in leagueId={}; used global for name='{}' ({} result(s))", leagueId, raw, global.size());
        try {
            if (adminAuditRepository != null) {
                com.chambua.vismart.model.AdminAudit audit = new com.chambua.vismart.model.AdminAudit();
                audit.setAction("team_resolution_fallback");
                audit.setParams("{\"teamName\": \"" + raw.replace("\"","\\\"") + "\", \"leagueId\": " + leagueId + "}");
                audit.setAffectedCount(1L);
                adminAuditRepository.save(audit);
            }
        } catch (Exception ignored) {}

        // Heuristic: choose a canonical team when multiple global candidates exist
        Team chosen = null;
        if (global.size() == 1) {
            chosen = global.get(0);
        } else {
            // Prefer teams in primary leagues
            List<Team> primaries = global.stream()
                    .filter(t -> t.getLeague() != null && t.getLeague().getName() != null && PRIMARY_LEAGUES.contains(t.getLeague().getName()))
                    .toList();
            List<Team> pool = (!primaries.isEmpty()) ? primaries : global;
            // If matchRepository available, pick the one with highest number of played matches
            if (matchRepository != null) {
                chosen = pool.stream()
                        .max((a, b) -> {
                            long ca = 0L, cb = 0L;
                            try { ca = (a.getId() != null) ? matchRepository.countPlayedByTeam(a.getId()) : 0L; } catch (Exception ignoredCount) {}
                            try { cb = (b.getId() != null) ? matchRepository.countPlayedByTeam(b.getId()) : 0L; } catch (Exception ignoredCount2) {}
                            return java.lang.Long.compare(ca, cb);
                        })
                        .orElse(pool.get(0));
            } else {
                chosen = pool.get(0);
            }
            List<Long> ids = global.stream().map(Team::getId).filter(Objects::nonNull).toList();
            String leagueName = chosen.getLeague() != null ? chosen.getLeague().getName() : null;
            log.warn("[Team][ByName][Global][Resolved] Multiple matches for name='{}' (leagueId={}) -> selected id={} from league='{}' (candidates={})",
                    raw, leagueId, chosen.getId(), leagueName, ids);
            // Audit the global resolution selection
            try {
                if (adminAuditRepository != null) {
                    com.chambua.vismart.model.AdminAudit audit = new com.chambua.vismart.model.AdminAudit();
                    audit.setAction("team_resolution_global");
                    audit.setParams("{\"name\": \"" + raw.replace("\"","\\\"") + "\", \"leagueId\": " + leagueId + ", \"selectedId\": " + chosen.getId() + ", \"selectedLeague\": \"" + (leagueName != null ? leagueName.replace("\"","\\\"") : "") + "\"}");
                    audit.setAffectedCount(1L);
                    adminAuditRepository.save(audit);
                }
            } catch (Exception ignoredAudit) {}
        }
        return ResponseEntity.ok(toDto(chosen));
    }

    // New feature: Prior Match Outcome → Next Match Outcome Distribution
    @GetMapping("/{teamName}/prior-outcomes")
    public ResponseEntity<?> getPriorOutcomeDistribution(@PathVariable("teamName") String teamName,
                                                         @RequestParam(name = "leagueId", required = false) Long leagueId) {
        if (teamName == null || teamName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing teamName"));
        }
        try {
            List<Team> candidates = teamService.findTeamsByName(teamName, leagueId);
            if (candidates == null || candidates.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Team not found", "name", teamName));
            }
            Team team;
            if (candidates.size() == 1) {
                team = candidates.get(0);
            } else {
                // Prefer within leagueId if provided
                if (leagueId != null) {
                    var scoped = candidates.stream().filter(t -> t.getLeague() != null && Objects.equals(t.getLeague().getId(), leagueId)).toList();
                    team = scoped != null && !scoped.isEmpty() ? scoped.get(0) : candidates.get(0);
                } else {
                    // Fallback to team with most played matches
                    if (matchRepository != null) {
                        team = candidates.stream().max((a,b) -> {
                            long ca = 0L, cb = 0L;
                            try { ca = (a.getId() != null) ? matchRepository.countPlayedByTeam(a.getId()) : 0L; } catch (Exception ignored) {}
                            try { cb = (b.getId() != null) ? matchRepository.countPlayedByTeam(b.getId()) : 0L; } catch (Exception ignored2) {}
                            return Long.compare(ca, cb);
                        }).orElse(candidates.get(0));
                    } else {
                        team = candidates.get(0);
                    }
                }
            }
            if (teamOutcomeService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Service unavailable"));
            }
            var resp = teamOutcomeService.computePriorOutcomeDistribution(team.getId());
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("[Team][PriorOutcomes] error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

        // Last played match summary for a given team name (case-insensitive across leagues)
        @GetMapping("/{teamName}/last-played")
        public ResponseEntity<?> getLastPlayedByName(@PathVariable("teamName") String teamName) {
            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "Missing teamName"));
            }
            String name = teamName.trim();
            if (matchRepository == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of("error", "MatchRepository unavailable"));
            }
            java.util.List<com.chambua.vismart.model.Match> list;
            try {
                var slice = matchRepository.findRecentPlayedByTeamName(name, org.springframework.data.domain.PageRequest.of(0, 1));
                list = (slice != null ? slice.getContent() : java.util.List.of());
            } catch (Exception e) {
                list = matchRepository.findRecentPlayedByTeamName(name);
            }
            if (list == null || list.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "No played matches found for team", "team", name));
            }
            var m = list.get(0);
            boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().trim().equalsIgnoreCase(name));
            Integer gf = home ? m.getHomeGoals() : m.getAwayGoals();
            Integer ga = home ? m.getAwayGoals() : m.getHomeGoals();
            int gfi = gf == null ? 0 : gf;
            int gai = ga == null ? 0 : ga;
            String score = gfi + "-" + gai;
            Integer hg = m.getHomeGoals();
            Integer ag = m.getAwayGoals();
            String result;
            if (hg == null || ag == null) result = "Draw"; else {
                int cmp = Integer.compare(home ? hg : ag, home ? ag : hg);
                result = cmp > 0 ? "Win" : (cmp == 0 ? "Draw" : "Loss");
            }
            String opp = home ? (m.getAwayTeam() != null ? m.getAwayTeam().getName() : "") : (m.getHomeTeam() != null ? m.getHomeTeam().getName() : "");
            var dto = new com.chambua.vismart.dto.LastPlayedSummary(name, result, score, opp, m.getDate());
            return ResponseEntity.ok(dto);
        }

        // Top-2 longest outcome streaks over last 40 played matches by team name
        @GetMapping("/{teamName}/last40-top-streaks")
        public ResponseEntity<?> getTopStreaksLast40(@PathVariable("teamName") String teamName) {
            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "Missing teamName"));
            }
            if (teamOutcomeService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of("error", "Service unavailable"));
            }
            try {
                var resp = teamOutcomeService.computeTopStreaksLast40ByTeamName(teamName.trim());
                return ResponseEntity.ok(resp);
            } catch (Exception ex) {
                log.error("[Team][TopStreaksLast40] error", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", "Internal error"));
            }
        }

    public record TeamSuggestion(Long id, String name, String country, Long leagueId) {}

    @GetMapping("/{teamId}/streak-summary")
    public ResponseEntity<?> getStreakSummary(@PathVariable("teamId") Long teamId) {
        return buildStreakSummaryForTeam(teamId, null);
    }

    @GetMapping("/streak-summary/simulate")
    public ResponseEntity<?> simulateStreakSummary(@RequestParam("homeId") Long homeId,
                                                   @RequestParam("awayId") Long awayId) {
        if (homeId == null || awayId == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Missing homeId or awayId"));
        }
        if (homeId.equals(awayId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("error", "Home and away teams must be different"));
        }
        // Build summary from home team's perspective and override fixture context and upcoming info
        ResponseEntity<?> base = buildStreakSummaryForTeam(homeId, awayId);
        return base;
    }

    private ResponseEntity<?> buildStreakSummaryForTeam(Long teamId, Long simulateOpponentId) {
        if (teamId == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Missing teamId"));
        }
        var optTeam = teamRepository.findById(teamId);
        if (optTeam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "Team not found", "teamId", teamId));
        }
        if (matchRepository == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of("error", "MatchRepository unavailable"));
        }
        // Fetch played matches for this team by NAME (to aggregate across possible duplicate IDs), then process oldest -> newest
        String selectedTeamName = null;
        try { selectedTeamName = optTeam.get().getName(); } catch (Exception ignoredName) {}
        // Limit the historical depth for performance; enough to compute meaningful streaks
        int limit = 240;
        java.util.List<com.chambua.vismart.model.Match> list;
        if (selectedTeamName != null && !selectedTeamName.isBlank()) {
            var slice = matchRepository.findRecentPlayedByTeamName(selectedTeamName, org.springframework.data.domain.PageRequest.of(0, limit));
            list = (slice != null ? slice.getContent() : java.util.List.of());
        } else {
            list = matchRepository.findRecentPlayedByTeamId(teamId);
        }
        if (list == null) list = java.util.List.of();
        java.util.List<com.chambua.vismart.model.Match> asc = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(asc);

        // Build selected team's pre-match longest-to-date map: date -> (longestType, longestCount)
        java.util.NavigableMap<java.time.LocalDate, java.util.Map.Entry<String,Integer>> selPreMap = new java.util.TreeMap<>();
        String curType = null; int curCount = 0; String longestType = null; int longestCount = 0;
        java.time.LocalDate curStart = null; java.time.LocalDate longestStart = null; java.time.LocalDate longestEnd = null;
        for (var m : asc) {
            // Pre-match snapshot for this date
            if (m.getDate() != null) {
                selPreMap.put(m.getDate(), new java.util.AbstractMap.SimpleEntry<>(longestType, longestCount));
            }
            // Update with this match result
            if (m.getHomeGoals() != null && m.getAwayGoals() != null) {
                boolean isHome = false;
                try { isHome = m.getHomeTeam() != null && m.getHomeTeam().getName() != null && selectedTeamName != null && m.getHomeTeam().getName().equalsIgnoreCase(selectedTeamName); } catch (Exception ignoredHome) {}
                String thisType;
                if (m.getHomeGoals().equals(m.getAwayGoals())) {
                    thisType = "D";
                } else if ((isHome && m.getHomeGoals() > m.getAwayGoals()) || (!isHome && m.getAwayGoals() > m.getHomeGoals())) {
                    thisType = "W";
                } else {
                    thisType = "L";
                }
                java.time.LocalDate md = m.getDate();
                if (curType == null || !thisType.equals(curType)) {
                    curType = thisType; curCount = 1; curStart = md;
                } else {
                    curCount += 1;
                }
                if (curCount > longestCount) { longestCount = curCount; longestType = curType; longestStart = curStart; longestEnd = md; }
            }
        }
        // Selected team's OVERALL longest-ever streak type (used for classification below)
        final String selectedOverallLongestType = longestType;

        // Opponent caches: pre-match longest map and OVERALL longest-ever type by opponent name (lowercased)
        java.util.Map<String, java.util.NavigableMap<java.time.LocalDate, java.util.Map.Entry<String,Integer>>> oppCache = new java.util.HashMap<>();
        java.util.Map<String, String> oppOverallTypeCache = new java.util.HashMap<>();
        java.util.Map<String, java.time.LocalDate[]> oppLongestRangeCache = new java.util.HashMap<>();
        java.util.Map<String, Integer> oppOverallCountCache = new java.util.HashMap<>();
        // Pre-collect unique opponent names from selected team timeline
        java.util.Set<String> oppNamesSet = new java.util.HashSet<>();
        for (var m : asc) {
            String h = null, a = null;
            try { if (m.getHomeTeam() != null) h = m.getHomeTeam().getName(); } catch (Exception ignored) {}
            try { if (m.getAwayTeam() != null) a = m.getAwayTeam().getName(); } catch (Exception ignored) {}
            boolean isHomeSel = h != null && selectedTeamName != null && h.equalsIgnoreCase(selectedTeamName);
            String oppName = isHomeSel ? a : h;
            if (oppName != null && !oppName.isBlank() && !"?".equals(oppName)) oppNamesSet.add(oppName.trim().toLowerCase());
        }
        if (!oppNamesSet.isEmpty()) {
            java.util.List<String> oppNames = new java.util.ArrayList<>(oppNamesSet);
            // Batch load once
            java.util.List<com.chambua.vismart.model.Match> oppAll = matchRepository.findRecentPlayedByAnyTeamNames(oppNames);
            // Group by canonical lowercase opponent name
            java.util.Map<String, java.util.List<com.chambua.vismart.model.Match>> byOpp = new java.util.HashMap<>();
            if (oppAll != null) {
                for (var om : oppAll) {
                    String h = null, a = null;
                    try { if (om.getHomeTeam() != null && om.getHomeTeam().getName() != null) h = om.getHomeTeam().getName().trim().toLowerCase(); } catch (Exception ignored) {}
                    try { if (om.getAwayTeam() != null && om.getAwayTeam().getName() != null) a = om.getAwayTeam().getName().trim().toLowerCase(); } catch (Exception ignored) {}
                    if (h != null && oppNamesSet.contains(h)) byOpp.computeIfAbsent(h, k -> new java.util.ArrayList<>()).add(om);
                    if (a != null && oppNamesSet.contains(a)) byOpp.computeIfAbsent(a, k -> new java.util.ArrayList<>()).add(om);
                }
            }
            // For each opponent, compute pre-match map and overall longest with range
            for (var e : byOpp.entrySet()) {
                String key = e.getKey();
                java.util.List<com.chambua.vismart.model.Match> listByOpp = e.getValue();
                listByOpp.sort((m1, m2) -> {
                    int cmp = java.util.Objects.compare(m1.getDate(), m2.getDate(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                    if (cmp != 0) return cmp;
                    return java.util.Objects.compare(m1.getRound(), m2.getRound(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                });
                String oCurType = null; int oCurCount = 0; String oLongestType = null; int oLongestCount = 0;
                java.time.LocalDate oCurStart = null; java.time.LocalDate oLongestStart = null; java.time.LocalDate oLongestEnd = null;
                java.util.NavigableMap<java.time.LocalDate, java.util.Map.Entry<String,Integer>> map = new java.util.TreeMap<>();
                for (var om : listByOpp) {
                    boolean oHome = false;
                    String oHomeName = null;
                    try { if (om.getHomeTeam() != null) oHomeName = om.getHomeTeam().getName(); } catch (Exception ignored) {}
                    if (oHomeName != null && oHomeName.equalsIgnoreCase(key)) oHome = true;
                    if (om.getDate() != null) map.put(om.getDate(), new java.util.AbstractMap.SimpleEntry<>(oLongestType, oLongestCount));
                    if (om.getHomeGoals() != null && om.getAwayGoals() != null) {
                        String oThisType;
                        if (om.getHomeGoals().equals(om.getAwayGoals())) {
                            oThisType = "D";
                        } else if ((oHome && om.getHomeGoals() > om.getAwayGoals()) || (!oHome && om.getAwayGoals() > om.getHomeGoals())) {
                            oThisType = "W";
                        } else {
                            oThisType = "L";
                        }
                        java.time.LocalDate od = om.getDate();
                        if (oCurType == null || !oThisType.equals(oCurType)) { oCurType = oThisType; oCurCount = 1; oCurStart = od; } else { oCurCount += 1; }
                        if (oCurCount > oLongestCount) { oLongestCount = oCurCount; oLongestType = oCurType; oLongestStart = oCurStart; oLongestEnd = od; }
                    }
                }
                oppCache.put(key, map);
                oppOverallTypeCache.put(key, oLongestType);
                oppLongestRangeCache.put(key, new java.time.LocalDate[]{oLongestStart, oLongestEnd});
                oppOverallCountCache.put(key, oLongestCount);
            }
        }
        java.util.function.Function<String, java.util.NavigableMap<java.time.LocalDate, java.util.Map.Entry<String,Integer>>> buildOpp = (oppName) -> {
            if (oppName == null || oppName.isBlank() || "?".equals(oppName)) return new java.util.TreeMap<>();
            String key = oppName.trim().toLowerCase();
            return oppCache.getOrDefault(key, new java.util.TreeMap<>());
        };

        // Group counters
        long sameTotal = 0, sameWins = 0, sameDraws = 0, sameLosses = 0, sameOver15 = 0, sameOver25 = 0, sameBtts = 0;
        long diffTotal = 0, diffWins = 0, diffDraws = 0, diffLosses = 0, diffOver15 = 0, diffOver25 = 0, diffBtts = 0;
        // Track most common win scorelines per bucket and by opponent type (from selected team perspective)
        java.util.Map<String, Integer> sameWinScoreMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> diffWinScoreMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> winVsWMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> winVsDMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> winVsLMap = new java.util.HashMap<>();
        // New: By opponent overall type: tally draw and loss scorelines as well
        java.util.Map<String, Integer> drawVsWMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> drawVsDMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> drawVsLMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> lossVsWMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> lossVsDMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> lossVsLMap = new java.util.HashMap<>();
        // New: overall most common scorelines (all results) per bucket
        java.util.Map<String, Integer> sameAllScoreMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> diffAllScoreMap = new java.util.HashMap<>();
        // New: per subcategory (A overall type vs B overall type) maps of scoreline -> count
        java.util.Map<String, java.util.Map<String,Integer>> subcategoryMaps = new java.util.HashMap<>();

        for (var m : asc) {
            if (m.getHomeGoals() == null || m.getAwayGoals() == null) continue; // only played
            java.time.LocalDate d = m.getDate();
            if (d == null) continue;
            boolean isHome = false;
            try { isHome = m.getHomeTeam() != null && m.getHomeTeam().getName() != null && selectedTeamName != null && m.getHomeTeam().getName().equalsIgnoreCase(selectedTeamName); } catch (Exception ignoredIH) {}
            String oppName = isHome ? (m.getAwayTeam() != null ? m.getAwayTeam().getName() : null) : (m.getHomeTeam() != null ? m.getHomeTeam().getName() : null);
            // Classification: USE OVERALL (all-time) longest streak types for both teams
            // Ensure opponent cache is populated
            buildOpp.apply(oppName);
            String aType = selectedOverallLongestType;
            String bType = (oppName != null) ? oppOverallTypeCache.getOrDefault(oppName.trim().toLowerCase(), null) : null;
            if (aType == null || bType == null) continue;

            boolean same = aType.equals(bType);
            int hg = m.getHomeGoals();
            int ag = m.getAwayGoals();
            boolean win, draw, loss;
            if (hg == ag) { draw = true; win = false; loss = false; }
            else if ((isHome && hg > ag) || (!isHome && ag > hg)) { win = true; draw = false; loss = false; }
            else { win = false; draw = false; loss = true; }
            boolean over15 = (hg + ag) >= 2;
            boolean over25 = (hg + ag) >= 3;
            boolean btts = (hg > 0 && ag > 0);

            int teamGoals = isHome ? hg : ag;
            int oppGoals = isHome ? ag : hg;
            String scoreline = teamGoals + "-" + oppGoals; // from selected team perspective

            // Update subcategory map by overall types
            String subKey = (aType != null ? aType : "?") + "v" + (bType != null ? bType : "?");
            var subMap = subcategoryMaps.get(subKey);
            if (subMap == null) { subMap = new java.util.HashMap<>(); subcategoryMaps.put(subKey, subMap); }
            subMap.put(scoreline, subMap.getOrDefault(scoreline, 0) + 1);

            if (same) {
                sameTotal++;
                // All results scoreline freq for same bucket
                sameAllScoreMap.put(scoreline, sameAllScoreMap.getOrDefault(scoreline, 0) + 1);
                if (win) {
                    sameWins++;
                    sameWinScoreMap.put(scoreline, sameWinScoreMap.getOrDefault(scoreline, 0) + 1);
                } else if (draw) {
                    sameDraws++;
                } else {
                    sameLosses++;
                }
                if (over15) sameOver15++;
                if (over25) sameOver25++;
                if (btts) sameBtts++;
            } else {
                diffTotal++;
                // All results scoreline freq for different bucket
                diffAllScoreMap.put(scoreline, diffAllScoreMap.getOrDefault(scoreline, 0) + 1);
                if (win) {
                    diffWins++;
                    diffWinScoreMap.put(scoreline, diffWinScoreMap.getOrDefault(scoreline, 0) + 1);
                } else if (draw) {
                    diffDraws++;
                } else {
                    diffLosses++;
                }
                if (over15) diffOver15++;
                if (over25) diffOver25++;
                if (btts) diffBtts++;
            }

            // By opponent overall type: tally wins, draws, and losses
            if (win) {
                if ("W".equals(bType)) {
                    winVsWMap.put(scoreline, winVsWMap.getOrDefault(scoreline, 0) + 1);
                } else if ("D".equals(bType)) {
                    winVsDMap.put(scoreline, winVsDMap.getOrDefault(scoreline, 0) + 1);
                } else if ("L".equals(bType)) {
                    winVsLMap.put(scoreline, winVsLMap.getOrDefault(scoreline, 0) + 1);
                }
            } else if (draw) {
                if ("W".equals(bType)) {
                    drawVsWMap.put(scoreline, drawVsWMap.getOrDefault(scoreline, 0) + 1);
                } else if ("D".equals(bType)) {
                    drawVsDMap.put(scoreline, drawVsDMap.getOrDefault(scoreline, 0) + 1);
                } else if ("L".equals(bType)) {
                    drawVsLMap.put(scoreline, drawVsLMap.getOrDefault(scoreline, 0) + 1);
                }
            } else { // loss
                if ("W".equals(bType)) {
                    lossVsWMap.put(scoreline, lossVsWMap.getOrDefault(scoreline, 0) + 1);
                } else if ("D".equals(bType)) {
                    lossVsDMap.put(scoreline, lossVsDMap.getOrDefault(scoreline, 0) + 1);
                } else if ("L".equals(bType)) {
                    lossVsLMap.put(scoreline, lossVsLMap.getOrDefault(scoreline, 0) + 1);
                }
            }
        }

        com.chambua.vismart.dto.StreakSummaryResponse resp = new com.chambua.vismart.dto.StreakSummaryResponse();
        // Populate selected team overall longest type and its date range
        try {
            resp.setSelectedTeamType(selectedOverallLongestType);
            resp.setSelectedTypeFrom(longestStart != null ? longestStart.toString() : null);
            resp.setSelectedTypeTo(longestEnd != null ? longestEnd.toString() : null);
            resp.setSelectedTeamCount(longestCount);
        } catch (Exception ignoredMeta) {}
        java.util.function.BiConsumer<com.chambua.vismart.dto.StreakSummaryResponse.Stats, long[]> fill = (stats, arr) -> {
            long total = arr[0];
            java.util.function.BiFunction<Long, Long, Integer> pct = (num, den) -> den <= 0 ? 0 : (int) Math.round((num * 100.0) / den);
            stats.setWinPercent(pct.apply(arr[1], total));
            stats.setDrawPercent(pct.apply(arr[2], total));
            stats.setLossPercent(pct.apply(arr[3], total));
            stats.setOver15Percent(pct.apply(arr[4], total));
            stats.setOver25Percent(pct.apply(arr[5], total));
            stats.setBttsPercent(pct.apply(arr[6], total));
            // Provide absolute counts in the response as well
            stats.setTotal((int) total);
            stats.setWinCount((int) arr[1]);
            stats.setDrawCount((int) arr[2]);
            stats.setLossCount((int) arr[3]);
            stats.setOver15Count((int) arr[4]);
            stats.setOver25Count((int) arr[5]);
            stats.setBttsCount((int) arr[6]);
        };
        fill.accept(resp.getSameStreak(), new long[]{sameTotal, sameWins, sameDraws, sameLosses, sameOver15, sameOver25, sameBtts});
        fill.accept(resp.getDifferentStreak(), new long[]{diffTotal, diffWins, diffDraws, diffLosses, diffOver15, diffOver25, diffBtts});

        // Compute most common win scorelines
        java.util.function.BiConsumer<java.util.Map<String,Integer>, com.chambua.vismart.dto.StreakSummaryResponse.Stats> applyModeToStats = (map, stats) -> {
            if (map == null || map.isEmpty()) return;
            String bestKey = null; int bestCount = 0;
            for (var e : map.entrySet()) {
                int c = e.getValue() != null ? e.getValue() : 0;
                if (c > bestCount || (c == bestCount && bestKey != null && e.getKey() != null && e.getKey().compareTo(bestKey) < 0)) {
                    bestCount = c; bestKey = e.getKey();
                }
            }
            if (bestKey != null && bestCount > 0) {
                stats.setMostCommonWinScoreline(bestKey);
                stats.setMostCommonWinCount(bestCount);
            }
        };
        applyModeToStats.accept(sameWinScoreMap, resp.getSameStreak());
        applyModeToStats.accept(diffWinScoreMap, resp.getDifferentStreak());

        // By opponent type
        java.util.function.Function<java.util.Map<String,Integer>, com.chambua.vismart.dto.StreakSummaryResponse.ScorelineStat> mode = (map) -> {
            if (map == null || map.isEmpty()) return null;
            String bestKey = null; int bestCount = 0;
            for (var e : map.entrySet()) {
                int c = e.getValue() != null ? e.getValue() : 0;
                if (c > bestCount || (c == bestCount && bestKey != null && e.getKey() != null && e.getKey().compareTo(bestKey) < 0)) {
                    bestCount = c; bestKey = e.getKey();
                }
            }
            return (bestKey != null && bestCount > 0) ? new com.chambua.vismart.dto.StreakSummaryResponse.ScorelineStat(bestKey, bestCount) : null;
        };
        resp.setWinScorelineVsW(mode.apply(winVsWMap));
        resp.setWinScorelineVsD(mode.apply(winVsDMap));
        resp.setWinScorelineVsL(mode.apply(winVsLMap));
        // New: set draw and loss scoreline modes by opponent type
        resp.setDrawScorelineVsW(mode.apply(drawVsWMap));
        resp.setDrawScorelineVsD(mode.apply(drawVsDMap));
        resp.setDrawScorelineVsL(mode.apply(drawVsLMap));
        resp.setLossScorelineVsW(mode.apply(lossVsWMap));
        resp.setLossScorelineVsD(mode.apply(lossVsDMap));
        resp.setLossScorelineVsL(mode.apply(lossVsLMap));

        // New: overall most common scorelines lists (top 5)
        java.util.function.BiFunction<java.util.Map<String,Integer>, Integer, java.util.List<com.chambua.vismart.dto.StreakSummaryResponse.ScorelineStat>> topList = (map, topN) -> {
            if (map == null || map.isEmpty()) return java.util.List.of();
            return map.entrySet().stream()
                    .sorted((a,b) -> {
                        int cmp = Integer.compare(b.getValue(), a.getValue());
                        if (cmp != 0) return cmp;
                        String ak = a.getKey() != null ? a.getKey() : "";
                        String bk = b.getKey() != null ? b.getKey() : "";
                        return ak.compareTo(bk);
                    })
                    .limit(topN != null && topN > 0 ? topN : 5)
                    .map(e -> new com.chambua.vismart.dto.StreakSummaryResponse.ScorelineStat(e.getKey(), e.getValue()))
                    .collect(java.util.stream.Collectors.toList());
        };
        resp.setSameTopScorelines(topList.apply(sameAllScoreMap, 5));
        resp.setDifferentTopScorelines(topList.apply(diffAllScoreMap, 5));

        // New: subcategory modes for AType vs BType
        resp.setScorelineWvW(mode.apply(subcategoryMaps.get("WvW")));
        resp.setScorelineDvD(mode.apply(subcategoryMaps.get("DvD")));
        resp.setScorelineLvL(mode.apply(subcategoryMaps.get("LvL")));
        resp.setScorelineWvD(mode.apply(subcategoryMaps.get("WvD")));
        resp.setScorelineDvW(mode.apply(subcategoryMaps.get("DvW")));
        resp.setScorelineWvL(mode.apply(subcategoryMaps.get("WvL")));
        resp.setScorelineLvW(mode.apply(subcategoryMaps.get("LvW")));
        resp.setScorelineDvL(mode.apply(subcategoryMaps.get("DvL")));
        resp.setScorelineLvD(mode.apply(subcategoryMaps.get("LvD")));

        // Simulation branch: if simulateOpponentId provided, override context and upcoming and return
        if (simulateOpponentId != null) {
            try {
                var oppOpt = teamRepository.findById(simulateOpponentId);
                String awayNameSim = oppOpt.map(Team::getName).orElse(null);
                // Compute opponent overall longest type by NAME (aggregate across duplicate IDs)
                String oppOverallTypeById = null;
                java.time.LocalDate oppFrom = null; java.time.LocalDate oppTo = null;
                int oppLongestCountById = 0;
                try {
                    java.util.List<com.chambua.vismart.model.Match> oppMatches;
                    if (awayNameSim != null && !awayNameSim.isBlank()) {
                        oppMatches = matchRepository.findRecentPlayedByTeamName(awayNameSim);
                    } else {
                        oppMatches = matchRepository.findRecentPlayedByTeamId(simulateOpponentId);
                    }
                    if (oppMatches != null && !oppMatches.isEmpty()) {
                        java.util.List<com.chambua.vismart.model.Match> oppAsc = new java.util.ArrayList<>(oppMatches);
                        java.util.Collections.reverse(oppAsc);
                        String oCurType = null; int oCurCount = 0; String oLongestType = null; int oLongestCount = 0;
                        java.time.LocalDate oCurStart = null; java.time.LocalDate oLongestStart = null; java.time.LocalDate oLongestEnd = null;
                        for (var om : oppAsc) {
                            if (om.getHomeGoals() != null && om.getAwayGoals() != null) {
                                boolean oHome = false;
                                try { oHome = om.getHomeTeam() != null && om.getHomeTeam().getName() != null && awayNameSim != null && om.getHomeTeam().getName().equalsIgnoreCase(awayNameSim); } catch (Exception ignoredN) {}
                                String oThisType;
                                if (om.getHomeGoals().equals(om.getAwayGoals())) { oThisType = "D"; }
                                else if ((oHome && om.getHomeGoals() > om.getAwayGoals()) || (!oHome && om.getAwayGoals() > om.getHomeGoals())) { oThisType = "W"; }
                                else { oThisType = "L"; }
                                java.time.LocalDate od = om.getDate();
                                if (oCurType == null || !oThisType.equals(oCurType)) { oCurType = oThisType; oCurCount = 1; oCurStart = od; } else { oCurCount += 1; }
                                if (oCurCount > oLongestCount) { oLongestCount = oCurCount; oLongestType = oCurType; oLongestStart = oCurStart; oLongestEnd = od; }
                            }
                        }
                        oppFrom = oLongestStart; oppTo = oLongestEnd;
                        oppOverallTypeById = oLongestType;
                        oppLongestCountById = oLongestCount;
                    }
                } catch (Exception ignoredOpp) {}
                String aTypeS = selectedOverallLongestType;
                String bTypeS = oppOverallTypeById;
                if (aTypeS != null && bTypeS != null) {
                    resp.setFixtureContext(aTypeS.equals(bTypeS) ? "same_streak" : "different_streak");
                }
                // Populate opponent overall longest type and date range for simulation
                try {
                    resp.setOpponentTeamType(oppOverallTypeById);
                    resp.setOpponentTypeFrom(oppFrom != null ? oppFrom.toString() : null);
                    resp.setOpponentTypeTo(oppTo != null ? oppTo.toString() : null);
                    // Expose opponent overall longest count
                    resp.setOpponentTeamCount(oppLongestCountById);
                } catch (Exception ignoredMetaS) {}
                var infoS = new com.chambua.vismart.dto.StreakSummaryResponse.FixtureInfo();
                infoS.setDate("Simulated");
                infoS.setHomeTeam(optTeam.get().getName());
                infoS.setAwayTeam(awayNameSim);
                resp.setUpcoming(infoS);
            } catch (Exception ignoredSim) {}
            return ResponseEntity.ok(resp);
        }

        // Fixture context: prefer Fixtures service (upcoming/live) then fall back to Matches table
        try {
            String teamNameForFixtures = null;
            try { teamNameForFixtures = optTeam.get().getName(); } catch (Exception ignoredName) {}

            com.chambua.vismart.model.Fixture fx = null;
            if (fixtureService != null && teamNameForFixtures != null && !teamNameForFixtures.isBlank()) {
                fx = fixtureService.findEarliestActiveByTeamNameFlexible(teamNameForFixtures).orElse(null);
            }
            // Repository fallback with strict future filter to avoid past/LIVE fixtures
            if (fx == null && fixtureRepository != null && teamNameForFixtures != null && !teamNameForFixtures.isBlank()) {
                var flist = fixtureRepository.findEarliestActiveByTeamName(teamNameForFixtures);
                if (flist != null && !flist.isEmpty()) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    for (var f : flist) {
                        try {
                            if (f != null && f.getStatus() == com.chambua.vismart.model.FixtureStatus.UPCOMING && f.getDateTime() != null && f.getDateTime().isAfter(now)) {
                                fx = f;
                                break;
                            }
                        } catch (Exception ignoredF) {}
                    }
                }
            }

            if (fx != null) {
                java.time.LocalDate fd = fx.getDateTime() != null ? fx.getDateTime().toLocalDate() : null;
                // Overall longest types
                String homeName = fx.getHomeTeam();
                String awayName = fx.getAwayTeam();
                String oppName = null;
                if (teamNameForFixtures != null) {
                    if (homeName != null && homeName.equalsIgnoreCase(teamNameForFixtures)) oppName = awayName;
                    else if (awayName != null && awayName.equalsIgnoreCase(teamNameForFixtures)) oppName = homeName;
                }
                buildOpp.apply(oppName);
                String aType = selectedOverallLongestType;
                String bType = (oppName != null) ? oppOverallTypeCache.getOrDefault(oppName.trim().toLowerCase(), null) : null;
                if (aType != null && bType != null) {
                    resp.setFixtureContext(aType.equals(bType) ? "same_streak" : "different_streak");
                }
                // Populate opponent type, count and date range (overall longest)
                try {
                    resp.setOpponentTeamType(bType);
                    String key = (oppName != null) ? oppName.trim().toLowerCase() : null;
                    Integer bCount = (key != null) ? oppOverallCountCache.getOrDefault(key, null) : null;
                    resp.setOpponentTeamCount(bCount);
                    java.time.LocalDate[] rng = (key != null) ? oppLongestRangeCache.get(key) : null;
                    resp.setOpponentTypeFrom(rng != null && rng[0] != null ? rng[0].toString() : null);
                    resp.setOpponentTypeTo(rng != null && rng[1] != null ? rng[1].toString() : null);
                } catch (Exception ignoredR) {}
                // Populate upcoming fixture details using Fixture
                try {
                    var info = new com.chambua.vismart.dto.StreakSummaryResponse.FixtureInfo();
                    info.setDate(fd != null ? fd.toString() : null);
                    info.setHomeTeam(homeName);
                    info.setAwayTeam(awayName);
                    try { info.setLeague(fx.getLeague() != null ? fx.getLeague().getName() : null); } catch (Exception ignoredL) {}
                    try {
                        String seasonName = null;
                        try { seasonName = fx.getLeague() != null ? fx.getLeague().getSeason() : null; } catch (Exception ignoredS) {}
                        info.setSeason(seasonName);
                    } catch (Exception ignoredS2) {}
                    resp.setUpcoming(info);
                } catch (Exception ignoredInfo) {}
            } else {
                // Fallback to matches table (scheduled rows) — ensure strictly future by date
                try {
                    java.time.LocalDate from = java.time.LocalDate.now().plusDays(1); // avoid earlier-today selections
                    java.util.List<com.chambua.vismart.model.Match> upcoming = matchRepository.findUpcomingByTeam(teamId, from);
                    if (upcoming != null && !upcoming.isEmpty()) {
                        var f = upcoming.get(0);
                        java.time.LocalDate fd = f.getDate();
                        // Overall longest types
                        String oppName2 = (f.getHomeTeam() != null && f.getHomeTeam().getId() != null && f.getHomeTeam().getId().equals(teamId))
                                ? (f.getAwayTeam() != null ? f.getAwayTeam().getName() : null)
                                : (f.getHomeTeam() != null ? f.getHomeTeam().getName() : null);
                        buildOpp.apply(oppName2);
                        String aType2 = selectedOverallLongestType;
                        String bType2 = (oppName2 != null) ? oppOverallTypeCache.getOrDefault(oppName2.trim().toLowerCase(), null) : null;
                        if (aType2 != null && bType2 != null) {
                            resp.setFixtureContext(aType2.equals(bType2) ? "same_streak" : "different_streak");
                        }
                        // Populate opponent type, count and date range (overall longest) for fallback branch
                        try {
                            resp.setOpponentTeamType(bType2);
                            String key2 = (oppName2 != null) ? oppName2.trim().toLowerCase() : null;
                            Integer bCount2 = (key2 != null) ? oppOverallCountCache.getOrDefault(key2, null) : null;
                            resp.setOpponentTeamCount(bCount2);
                            java.time.LocalDate[] rng2 = (key2 != null) ? oppLongestRangeCache.get(key2) : null;
                            resp.setOpponentTypeFrom(rng2 != null && rng2[0] != null ? rng2[0].toString() : null);
                            resp.setOpponentTypeTo(rng2 != null && rng2[1] != null ? rng2[1].toString() : null);
                        } catch (Exception ignoredRFb) {}
                        try {
                            var info2 = new com.chambua.vismart.dto.StreakSummaryResponse.FixtureInfo();
                            info2.setDate(fd != null ? fd.toString() : null);
                            info2.setHomeTeam(f.getHomeTeam() != null ? f.getHomeTeam().getName() : null);
                            info2.setAwayTeam(f.getAwayTeam() != null ? f.getAwayTeam().getName() : null);
                            info2.setLeague(f.getLeague() != null ? f.getLeague().getName() : null);
                            info2.setSeason(f.getSeason() != null ? f.getSeason().getName() : null);
                            resp.setUpcoming(info2);
                        } catch (Exception ignoredInfo2) {}
                    }
                } catch (Exception ignoredFallback) {}
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(resp);
    }
}

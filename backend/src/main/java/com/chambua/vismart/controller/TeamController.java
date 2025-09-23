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
    private com.chambua.vismart.repository.AdminAuditRepository adminAuditRepository;

    // Inject MatchRepository for activity-based canonical team selection when multiple global candidates exist
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chambua.vismart.repository.MatchRepository matchRepository;

    // Primary leagues prioritized when resolving ambiguous team names globally
    private static final java.util.Set<String> PRIMARY_LEAGUES = java.util.Set.of(
            "La Liga", "Premier League", "Serie A", "Bundesliga", "Ligue 1",
            "Eredivisie", "Primeira Liga", "Scottish Premiership", "LaLiga", "Liga NOS"
    );

    public TeamController(TeamRepository teamRepository, TeamService teamService) {
        this.teamRepository = teamRepository;
        this.teamService = teamService;
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

    public record TeamSuggestion(Long id, String name, String country, Long leagueId) {}
}

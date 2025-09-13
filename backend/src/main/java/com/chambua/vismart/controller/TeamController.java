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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private final TeamRepository teamRepository;
    private final TeamService teamService;

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
        var stream = (leagueId != null
                ? teamRepository.searchByNameWithCountryAndLeague(normalized, raw, leagueId).stream()
                : teamRepository.searchByNameWithCountry(normalized, raw).stream());
        var list = stream
                .limit(20)
                .map(p -> new TeamDto(p.getId(), p.getName(), null, p.getLeagueId(), p.getLeagueName()))
                .collect(Collectors.toList());
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
        List<Team> matches = teamService.findTeamsByName(raw, leagueId);
        if (matches.isEmpty()) {
            // Diagnostics: check if a trimmed-name equality would have matched, indicating possible normalization mismatch
            long trimmedEqCount = (leagueId != null)
                    ? teamRepository.countByTrimmedNameIgnoreCaseAndLeagueId(raw, leagueId)
                    : teamRepository.countByTrimmedNameIgnoreCase(raw);
            long anomalyCount = 0L;
            try { anomalyCount = teamRepository.countSpaceAnomalies(); } catch (Exception ignored) {}
            log.warn("[Team][ByName] not found for raw='{}', normalized='{}' (leagueId={}). trimmedEqCount={}, spaceAnomalies={}", raw, normalized, leagueId, trimmedEqCount, anomalyCount);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Team not found", "name", raw));
        }
        if (matches.size() == 1) {
            Team t = matches.get(0);
            log.info("[Team][ByName][Resp] id={}, name='{}'", t.getId(), t.getName());
            return ResponseEntity.ok(toDto(t));
        }
        log.warn("[Team][ByName] multiple candidates for raw='{}' (normalized='{}', leagueId={}): {}", raw, normalized, leagueId, matches.size());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "Multiple teams found",
                        "count", matches.size(),
                        "candidates", matches.stream().map(this::toDto).toList()
                ));
    }

    public record TeamSuggestion(Long id, String name, String country, Long leagueId) {}
}

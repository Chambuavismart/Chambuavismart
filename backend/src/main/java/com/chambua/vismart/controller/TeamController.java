package com.chambua.vismart.controller;

import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping("/search")
    public List<TeamSuggestion> searchTeams(@RequestParam("query") String query,
                                            @RequestParam(name = "leagueId", required = false) Long leagueId) {
        if (query == null || query.trim().length() < 3) return List.of();
        String q = query.trim();
        log.info("[Team][Search] query='{}', leagueId={}", q, leagueId);
        // Case-insensitive contains search; optionally scope to league
        var stream = (leagueId != null
                ? teamRepository.searchByNameWithCountryAndLeague(q, leagueId).stream()
                : teamRepository.searchByNameWithCountry(q).stream());
        var list = stream
                .limit(20)
                .map(p -> new TeamSuggestion(p.getId(), p.getName(), p.getCountry()))
                .collect(Collectors.toList());
        log.info("[Team][Search][Resp] size={}", list.size());
        return list;
    }

    // Exact resolver by official name or alias (case-insensitive). Returns the first match if duplicates exist.
    @GetMapping("/by-name")
    public TeamSuggestion findByName(@RequestParam("name") String name,
                                     @RequestParam(name = "leagueId", required = false) Long leagueId) {
        if (name == null || name.trim().isEmpty()) return null;
        String n = name.trim();
        log.info("[Team][ByName] name='{}', leagueId={}", n, leagueId);
        var opt = (leagueId != null)
                ? teamRepository.findByNameOrAliasInLeague(n, leagueId)
                : teamRepository.findByNameOrAlias(n);
        var res = opt
                .map(t -> new TeamSuggestion(t.getId(), t.getName(), null))
                .orElse(null);
        if (res == null) log.warn("[Team][ByName] not found for '{}' (leagueId={})", n, leagueId);
        else log.info("[Team][ByName][Resp] id={}, name='{}'", res.id(), res.name());
        return res;
    }

    public record TeamSuggestion(Long id, String name, String country) {}
}

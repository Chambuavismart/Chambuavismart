package com.chambua.vismart.controller;

import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.TeamRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
public class TeamController {

    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping("/search")
    public List<TeamSuggestion> searchTeams(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 3) return List.of();
        String q = query.trim();
        // Global, case-insensitive contains search across teams
        return teamRepository.searchByNameWithCountry(q).stream()
                .limit(20)
                .map(p -> new TeamSuggestion(p.getId(), p.getName(), p.getCountry()))
                .collect(Collectors.toList());
    }

    public record TeamSuggestion(Long id, String name, String country) {}
}

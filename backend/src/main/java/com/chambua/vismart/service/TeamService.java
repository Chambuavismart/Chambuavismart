package com.chambua.vismart.service;

import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.util.TeamNameNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Team save(Team team) {
        if (team == null) return null;
        team.setNormalizedName(TeamNameNormalizer.normalize(team.getName()));
        return teamRepository.save(team);
    }

    /**
     * Find teams by exact name or alias (case-insensitive). If leagueId is provided, results are filtered
     * to that league to avoid ambiguity when duplicates exist across leagues.
     */
    public List<Team> findTeamsByName(String name, Long leagueId) {
        if (name == null || name.trim().isEmpty()) return List.of();
        String raw = name.trim();
        String normalized = TeamNameNormalizer.normalize(raw);
        // First, fetch all matches globally with league eagerly loaded
        List<Team> all = teamRepository.findByNameOrAliasWithLeague(normalized, raw);
        if (leagueId == null) {
            return all;
        }
        final Long lid = leagueId;
        List<Team> scoped = all.stream()
                .filter(t -> t.getLeague() != null && Objects.equals(t.getLeague().getId(), lid))
                .collect(Collectors.toList());
        // Fallback: if no team found in the provided league, return global list to allow cross-league resolution
        return scoped.isEmpty() ? all : scoped;
    }
}

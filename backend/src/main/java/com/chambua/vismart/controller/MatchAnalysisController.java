package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.service.MatchAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/match-analysis")
@CrossOrigin(origins = "*")
public class MatchAnalysisController {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchAnalysisService matchAnalysisService;

    public MatchAnalysisController(LeagueRepository leagueRepository, TeamRepository teamRepository, TeamAliasRepository teamAliasRepository, MatchAnalysisService matchAnalysisService) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.matchAnalysisService = matchAnalysisService;
    }

    @PostMapping("/analyze")
    public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req) {
        if (req.getLeagueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leagueId is required");
        }
        if (req.getSeasonId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seasonId is required");
        }
        League league = leagueRepository.findById(req.getLeagueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));

        Long homeId = req.getHomeTeamId();
        Long awayId = req.getAwayTeamId();
        String homeName = req.getHomeTeamName();
        String awayName = req.getAwayTeamName();

        // If names provided but IDs missing, try to resolve IDs from repository (best-effort)
        if ((homeId == null || awayId == null) && homeName != null && awayName != null) {
            String hn = homeName.trim();
            String an = awayName.trim();
            // 1) exact league-scoped name match
            homeId = teamRepository.findByLeagueAndNameIgnoreCase(league, hn).map(Team::getId).orElse(null);
            awayId = teamRepository.findByLeagueAndNameIgnoreCase(league, an).map(Team::getId).orElse(null);
            // 2) alias fallback if still unresolved
            if (homeId == null) {
                homeId = teamAliasRepository.findByAliasIgnoreCase(hn).map(a -> a.getTeam() != null ? a.getTeam().getId() : null).orElse(null);
            }
            if (awayId == null) {
                awayId = teamAliasRepository.findByAliasIgnoreCase(an).map(a -> a.getTeam() != null ? a.getTeam().getId() : null).orElse(null);
            }
            // 3) partial contains fallback within league
            if (homeId == null) {
                homeId = teamRepository.findByLeagueAndNameContainingIgnoreCase(league, hn).map(Team::getId).orElse(null);
            }
            if (awayId == null) {
                awayId = teamRepository.findByLeagueAndNameContainingIgnoreCase(league, an).map(Team::getId).orElse(null);
            }
            homeName = hn;
            awayName = an;
        }

        // If IDs provided but names missing, resolve names for response clarity
        if ((homeName == null || awayName == null) && homeId != null && awayId != null) {
            Team home = teamRepository.findById(homeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home team not found"));
            Team away = teamRepository.findById(awayId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Away team not found"));
            homeName = home.getName();
            awayName = away.getName();
        }

        // Validate we have at least names
        if (homeName == null || awayName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "homeTeamId/awayTeamId or homeTeamName/awayTeamName are required");
        }

        try {
            // Call deterministic analyzer with season scope
            return matchAnalysisService.analyzeDeterministic(
                    league.getId(),
                    homeId,
                    awayId,
                    req.getSeasonId(),
                    league.getName(),
                    homeName.trim(),
                    awayName.trim(),
                    req.isRefresh()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}

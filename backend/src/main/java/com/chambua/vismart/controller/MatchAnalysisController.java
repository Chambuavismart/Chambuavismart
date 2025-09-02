package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamRepository;
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
    private final MatchAnalysisService matchAnalysisService;

    public MatchAnalysisController(LeagueRepository leagueRepository, TeamRepository teamRepository, MatchAnalysisService matchAnalysisService) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.matchAnalysisService = matchAnalysisService;
    }

    @PostMapping("/analyze")
    public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req) {
        if (req.getLeagueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leagueId is required");
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
            homeId = teamRepository.findByLeagueAndNameIgnoreCase(league, hn).map(Team::getId).orElse(null);
            awayId = teamRepository.findByLeagueAndNameIgnoreCase(league, an).map(Team::getId).orElse(null);
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

        // Call deterministic analyzer with optional cache usage
        return matchAnalysisService.analyzeDeterministic(
                league.getId(),
                homeId,
                awayId,
                league.getName(),
                homeName.trim(),
                awayName.trim(),
                req.isRefresh()
        );
    }
}

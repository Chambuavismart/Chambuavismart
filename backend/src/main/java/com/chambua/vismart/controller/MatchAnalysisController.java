package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.service.MatchAnalysisService;
import com.chambua.vismart.util.TeamNameNormalizer;
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
        // seasonId is optional: fallback to current season in service layer
        League league = leagueRepository.findById(req.getLeagueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        String analysisType = req.getAnalysisType();
        if (analysisType == null || analysisType.isBlank()) analysisType = "match";
        analysisType = analysisType.trim().toLowerCase();
        if (!analysisType.equals("match") && !analysisType.equals("fixtures")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysisType must be 'fixtures' or 'match'");
        }

        Long homeId = req.getHomeTeamId();
        Long awayId = req.getAwayTeamId();
        String homeName = req.getHomeTeamName();
        String awayName = req.getAwayTeamName();

        // If names provided but IDs missing, try to resolve IDs from repository (best-effort)
        if ((homeId == null || awayId == null) && homeName != null && awayName != null) {
            String hn = homeName.trim();
            String an = awayName.trim();
            String hnNorm = TeamNameNormalizer.normalize(hn);
            String anNorm = TeamNameNormalizer.normalize(an);
            // 1) exact league-scoped name match (normalized)
            homeId = teamRepository.findByNormalizedNameAndLeagueId(hnNorm, league.getId()).map(Team::getId).orElse(null);
            awayId = teamRepository.findByNormalizedNameAndLeagueId(anNorm, league.getId()).map(Team::getId).orElse(null);
            // 2) alias fallback if still unresolved
            if (homeId == null) {
                homeId = teamAliasRepository.findByAliasIgnoreCase(hn).map(a -> a.getTeam() != null ? a.getTeam().getId() : null).orElse(null);
            }
            if (awayId == null) {
                awayId = teamAliasRepository.findByAliasIgnoreCase(an).map(a -> a.getTeam() != null ? a.getTeam().getId() : null).orElse(null);
            }
            // 3) partial contains fallback within league (use legacy contains for now)
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
            // Call deterministic analyzer with season scope (null allowed)
            return matchAnalysisService.analyzeDeterministic(
                    league.getId(),
                    homeId,
                    awayId,
                    req.getSeasonId(),
                    league.getName(),
                    homeName.trim(),
                    awayName.trim(),
                    req.isRefresh(),
                    analysisType
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}

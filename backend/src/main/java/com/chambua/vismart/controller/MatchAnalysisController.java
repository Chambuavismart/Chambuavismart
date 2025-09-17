package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.model.PersistedFixtureAnalysis;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.repository.PersistedFixtureAnalysisRepository;
import com.chambua.vismart.service.MatchAnalysisService;
import com.chambua.vismart.util.TeamNameNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/match-analysis")
@CrossOrigin(origins = "*")
public class MatchAnalysisController {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchAnalysisService matchAnalysisService;
    private final PersistedFixtureAnalysisRepository persistedRepo;
    private final ObjectMapper objectMapper;

    @Value("${chambua.persistedDaily.enabled:false}")
    private boolean persistedDailyEnabled;

    public MatchAnalysisController(LeagueRepository leagueRepository, TeamRepository teamRepository, TeamAliasRepository teamAliasRepository, MatchAnalysisService matchAnalysisService, PersistedFixtureAnalysisRepository persistedRepo, ObjectMapper objectMapper) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.matchAnalysisService = matchAnalysisService;
        this.persistedRepo = persistedRepo;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/analyze")
    public MatchAnalysisResponse analyze(@RequestBody MatchAnalysisRequest req, @RequestHeader(value = "X-User-Id", required = false) String userId) {
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
            // Call deterministic analyzer with season scope
            MatchAnalysisResponse response = matchAnalysisService.analyzeDeterministic(
                    league.getId(),
                    homeId,
                    awayId,
                    req.getSeasonId(),
                    league.getName(),
                    homeName.trim(),
                    awayName.trim(),
                    req.isRefresh()
            );
            if (persistedDailyEnabled && homeId != null && awayId != null) {
                persistAnalysis(response, req, league.getId(), homeId, awayId, homeName.trim(), awayName.trim(), userId);
            }
            return response;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void persistAnalysis(MatchAnalysisResponse response, MatchAnalysisRequest req, Long leagueId, Long homeId, Long awayId, String homeName, String awayName, String userId) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
            Instant now = Instant.now();
            String json = objectMapper.writeValueAsString(response);
            var existingOpt = persistedRepo.findFirstByAnalysisDateAndLeagueIdAndHomeTeamIdAndAwayTeamId(today, leagueId, homeId, awayId);
            if (existingOpt.isPresent()) {
                PersistedFixtureAnalysis existing = existingOpt.get();
                existing.setResultJson(json);
                existing.setUpdatedAt(now);
                existing.setUserId(userId);
                persistedRepo.save(existing);
            } else {
                PersistedFixtureAnalysis entity = PersistedFixtureAnalysis.builder()
                        .leagueId(leagueId)
                        .seasonId(req.getSeasonId())
                        .homeTeamId(homeId)
                        .awayTeamId(awayId)
                        .homeTeamName(homeName)
                        .awayTeamName(awayName)
                        .fixtureId(null)
                        .analysisDate(today)
                        .userId(userId)
                        .resultJson(json)
                        .createdAt(now)
                        .updatedAt(now)
                        .source(PersistedFixtureAnalysis.AnalysisSource.INDIVIDUAL)
                        .build();
                persistedRepo.save(entity);
            }
        } catch (JsonProcessingException e) {
            // Swallow persistence failure to not break user flow
            // Optionally log via a logger if available
        }
    }
}

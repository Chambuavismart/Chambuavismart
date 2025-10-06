package com.chambua.vismart.controller;

import com.chambua.vismart.dto.RecommendationSummary;
import com.chambua.vismart.service.RecommendationOrchestratorService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {

    private final RecommendationOrchestratorService orchestrator;

    public RecommendationController(RecommendationOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    // Example: GET /api/recommendations/fixture?fixtureId=123&leagueId=1&seasonId=2025&homeTeamId=10&awayTeamId=20&leagueName=Premier%20League&homeTeamName=Arsenal&awayTeamName=Chelsea
    @GetMapping("/fixture")
    public RecommendationSummary recommendForFixture(@RequestParam(required = false) Long fixtureId,
                                                     @RequestParam Long leagueId,
                                                     @RequestParam(required = false) Long seasonId,
                                                     @RequestParam(required = false) Long homeTeamId,
                                                     @RequestParam(required = false) Long awayTeamId,
                                                     @RequestParam(required = false) String leagueName,
                                                     @RequestParam(required = false) String homeTeamName,
                                                     @RequestParam(required = false) String awayTeamName) {
        return orchestrator.recommend(fixtureId, leagueId, seasonId, homeTeamId, awayTeamId, leagueName, homeTeamName, awayTeamName);
    }
}

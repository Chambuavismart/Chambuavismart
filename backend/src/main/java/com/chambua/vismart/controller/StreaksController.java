package com.chambua.vismart.controller;

import com.chambua.vismart.dto.StreakInsight;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.service.MatchAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/streaks")
@CrossOrigin(origins = "*")
public class StreaksController {

    private final MatchAnalysisService matchAnalysisService;
    private final TeamRepository teamRepository;

    public StreaksController(MatchAnalysisService matchAnalysisService, TeamRepository teamRepository) {
        this.matchAnalysisService = matchAnalysisService;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/over15")
    public com.chambua.vismart.dto.Over15StreakProfile getOver15Profile(@RequestParam(value = "teamId", required = false) Long teamId,
                                                                        @RequestParam(value = "teamName", required = false) String teamName) {
        return matchAnalysisService.buildOver15StreakProfile(teamId, teamName);
    }

    /**
     * Lightweight endpoint to compute current-streak-based insight for a single team.
     * Prefer teamId when available; teamName is a fallback.
     */
    @GetMapping("/insight")
    public StreakInsight getInsight(@RequestParam(value = "teamId", required = false) Long teamId,
                                    @RequestParam(value = "teamName", required = false) String teamName) {
        return matchAnalysisService.buildCurrentStreakInsight(teamId, teamName);
    }

    /**
     * Batch variant. Accepts comma-separated teamIds and/or teamNames.
     * If both sets are provided, results are concatenated in the order: teamIds first then teamNames.
     */
    @GetMapping("/insights")
    public List<StreakInsight> getInsights(@RequestParam(value = "teamIds", required = false) String teamIdsCsv,
                                           @RequestParam(value = "teamNames", required = false) String teamNamesCsv) {
        List<StreakInsight> out = new ArrayList<>();
        if (teamIdsCsv != null && !teamIdsCsv.isBlank()) {
            List<Long> ids = Arrays.stream(teamIdsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Long.parseLong(s); } catch (NumberFormatException ex) { return null; }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            for (Long id : ids) {
                String name = null;
                try {
                    Team t = teamRepository.findById(id).orElse(null);
                    if (t != null) name = t.getName();
                } catch (Exception ignored) {}
                out.add(matchAnalysisService.buildCurrentStreakInsight(id, name));
            }
        }
        if (teamNamesCsv != null && !teamNamesCsv.isBlank()) {
            List<String> names = Arrays.stream(teamNamesCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            for (String name : names) {
                out.add(matchAnalysisService.buildCurrentStreakInsight(null, name));
            }
        }
        return out;
    }
}

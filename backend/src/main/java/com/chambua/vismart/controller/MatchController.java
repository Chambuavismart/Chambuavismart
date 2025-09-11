package com.chambua.vismart.controller;

import com.chambua.vismart.dto.TeamResultsBreakdownResponse;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.H2HService;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "*")
public class MatchController {

    private final MatchRepository matchRepository;
    private final H2HService h2hService;
    private final com.chambua.vismart.config.FeatureFlags featureFlags;

    public MatchController(MatchRepository matchRepository, H2HService h2hService, com.chambua.vismart.config.FeatureFlags featureFlags) {
        this.matchRepository = matchRepository;
        this.h2hService = h2hService;
        this.featureFlags = featureFlags;
    }

    @GetMapping("/played/total")
    public long getTotalPlayedMatches() {
        return matchRepository.countByResultIsNotNull();
    }

    @GetMapping("/played/team/{teamId}/total")
    public long getPlayedTotalByTeam(@PathVariable("teamId") Long teamId) {
        if (teamId == null) return 0L;
        return matchRepository.countPlayedByTeam(teamId);
    }

    @GetMapping("/played/team/by-name/total")
    public long getPlayedTotalByTeamName(@RequestParam("name") String name) {
        if (name == null || name.trim().isEmpty()) return 0L;
        return matchRepository.countPlayedByTeamName(name.trim());
    }

    @GetMapping("/played/team/by-name/breakdown")
    public TeamResultsBreakdownResponse getResultsBreakdownByTeamName(@RequestParam("name") String name) {
        if (name == null || name.trim().isEmpty()) return new TeamResultsBreakdownResponse(0,0,0,0,0,0);
        String q = name.trim();
        long total = matchRepository.countPlayedByTeamName(q);
        long wins = matchRepository.countWinsByTeamName(q);
        long draws = matchRepository.countDrawsByTeamName(q);
        long losses = matchRepository.countLossesByTeamName(q);
        long btts = matchRepository.countBttsByTeamName(q);
        long over25 = matchRepository.countOver25ByTeamName(q);
        // Safety: ensure consistency even if data anomalies
        if (losses + wins + draws != total) {
            long computedLosses = Math.max(0, total - wins - draws);
            losses = computedLosses;
        }
        return new TeamResultsBreakdownResponse(total, wins, draws, losses, btts, over25);
    }

    // --- H2H suggestions: only pairs that actually played ---
    @GetMapping("/h2h/suggest")
    public List<H2HSuggestion> suggestH2H(@RequestParam("query") String query) {
        if (query == null || query.trim().length() < 3) return List.of();
        String q = query.trim();
        List<Object[]> raw = matchRepository.findDistinctPlayedPairsByNameContains(q);
        // Deduplicate by canonicalized unordered pair (case-insensitive)
        Set<String> seen = new HashSet<>();
        List<H2HSuggestion> out = new ArrayList<>();
        for (Object[] pair : raw) {
            String a = Objects.toString(pair[0], "");
            String b = Objects.toString(pair[1], "");
            if (a.isBlank() || b.isBlank() || a.equalsIgnoreCase(b)) continue;
            String key = a.compareToIgnoreCase(b) <= 0 ? (a.toLowerCase(Locale.ROOT) + "|" + b.toLowerCase(Locale.ROOT)) : (b.toLowerCase(Locale.ROOT) + "|" + a.toLowerCase(Locale.ROOT));
            if (seen.add(key)) {
                // Keep the original case as returned (best-effort)
                String first = a;
                String second = b;
                // Normalize display order alphabetically for predictability (doesn't affect orientation options we show client-side)
                if (a.compareToIgnoreCase(b) > 0) {
                    first = b;
                    second = a;
                }
                out.add(new H2HSuggestion(first, second));
            }
        }
        // Limit to a reasonable number
        return out.size() > 30 ? out.subList(0, 30) : out;
    }

    // --- H2H matches list for chosen orientation ---
    @GetMapping("/h2h/matches")
    public List<H2HMatchDto> getH2HMatches(@RequestParam("home") String homeName,
                                           @RequestParam("away") String awayName) {
        if (homeName == null || homeName.isBlank() || awayName == null || awayName.isBlank()) return List.of();
        String home = homeName.trim();
        String away = awayName.trim();
        var list = h2hService.getH2HByNames(home, away);
        DateTimeFormatter df = DateTimeFormatter.ISO_DATE;
        List<H2HMatchDto> out = new ArrayList<>(list.size());
        for (Match m : list) {
            String result = (m.getHomeGoals() != null && m.getAwayGoals() != null) ? (m.getHomeGoals() + "-" + m.getAwayGoals()) : "-";
            out.add(new H2HMatchDto(
                    m.getDate() != null ? m.getDate().getYear() : null,
                    m.getDate() != null ? df.format(m.getDate()) : null,
                    m.getHomeTeam() != null ? m.getHomeTeam().getName() : null,
                    m.getAwayTeam() != null ? m.getAwayTeam().getName() : null,
                    result
            ));
        }
        return out;
    }

    // --- Team last-5 form by team name (across seasons/leagues) ---
    @GetMapping("/form/by-name")
    public com.chambua.vismart.dto.FormSummary getFormByTeamName(@RequestParam("name") String name) {
        if (!featureFlags.isPredictiveH2HPhase1Enabled()) {
            return new com.chambua.vismart.dto.FormSummary();
        }
        com.chambua.vismart.dto.FormSummary summary = new com.chambua.vismart.dto.FormSummary();
        if (name == null || name.trim().isEmpty()) return summary;
        String q = name.trim();
        try {
            // Determine latest available season in which this team has played
            List<Long> seasonIds = matchRepository.findSeasonIdsForTeamNameOrdered(q);
            List<Match> list;
            if (seasonIds != null && !seasonIds.isEmpty()) {
                Long latestSeasonId = seasonIds.get(0);
                list = matchRepository.findRecentPlayedByTeamNameAndSeason(q, latestSeasonId);
            } else {
                // Fallback to any recent matches if no season data linked
                list = matchRepository.findRecentPlayedByTeamName(q);
            }
            if (list == null || list.isEmpty()) return summary;
            java.util.ArrayList<String> results = new java.util.ArrayList<>();
            java.util.ArrayList<Double> ppg = new java.util.ArrayList<>();
            int wins = 0, draws = 0;
            int cumPoints = 0; int counted = 0;
            for (Match m : list) {
                if (results.size() >= 5) break;
                Integer hg = m.getHomeGoals();
                Integer ag = m.getAwayGoals();
                if (hg == null || ag == null) continue;
                boolean isHome = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().equalsIgnoreCase(q));
                int my = isHome ? hg : ag;
                int opp = isHome ? ag : hg;
                int pts;
                if (my > opp) { results.add("W"); wins++; pts = 3; }
                else if (my == opp) { results.add("D"); draws++; pts = 1; }
                else { results.add("L"); pts = 0; }
                counted++; cumPoints += pts;
                double p = counted > 0 ? ((double) cumPoints) / counted : 0.0;
                ppg.add(Math.round(p * 10.0) / 10.0);
            }
            String streak = "0";
            if (!results.isEmpty()) {
                String first = results.get(0);
                int count = 1;
                for (int i = 1; i < results.size(); i++) {
                    if (results.get(i).equals(first)) count++; else break;
                }
                streak = count + first;
            }
            int valid = results.size();
            int winRate = (valid > 0) ? (int)Math.round((wins * 100.0) / valid) : 0;
            int points = wins * 3 + draws;
            summary = new com.chambua.vismart.dto.FormSummary(results, streak, winRate, points);
            summary.setPpgSeries(ppg);
        } catch (Exception ignored) {}
        return summary;
    }

    public record H2HSuggestion(String teamA, String teamB) {}
    public record H2HMatchDto(Integer year, String date, String homeTeam, String awayTeam, String result) {}
}

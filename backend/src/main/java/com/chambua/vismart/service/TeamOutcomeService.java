package com.chambua.vismart.service;

import com.chambua.vismart.dto.PriorOutcomeResponse;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.MatchStatus;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TeamOutcomeService {
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public TeamOutcomeService(MatchRepository matchRepository, TeamRepository teamRepository) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Compute top two longest outcome (W/D/L) streaks over the most recent 40 played matches for a given team name.
     * Ties are broken by more recent end date.
     */
    public com.chambua.vismart.dto.TopOutcomeStreaksResponse computeTopStreaksLast40ByTeamName(String teamName) {
        String tn = teamName == null ? null : teamName.trim();
        if (tn == null || tn.isEmpty()) {
            return new com.chambua.vismart.dto.TopOutcomeStreaksResponse("Unknown", 0, java.util.List.of());
        }
        java.util.List<com.chambua.vismart.model.Match> recent = matchRepository.findRecentPlayedByTeamName(tn);
        if (recent == null || recent.isEmpty()) {
            return new com.chambua.vismart.dto.TopOutcomeStreaksResponse(tn, 0, java.util.List.of());
        }
        // repository returns desc by date; reverse to chronological to compute streaks
        java.util.Collections.reverse(recent);
        // Build sequence of outcomes and dates (most ancient -> most recent)
        java.util.ArrayList<String> outcomes = new java.util.ArrayList<>();
        java.util.ArrayList<java.time.LocalDate> dates = new java.util.ArrayList<>();
        for (com.chambua.vismart.model.Match m : recent) {
            if (m.getStatus() != com.chambua.vismart.model.MatchStatus.PLAYED) continue;
            Integer hg = m.getHomeGoals();
            Integer ag = m.getAwayGoals();
            if (hg == null || ag == null) continue;
            boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().trim().equalsIgnoreCase(tn));
            int my = home ? (hg == null ? 0 : hg) : (ag == null ? 0 : ag);
            int opp = home ? (ag == null ? 0 : ag) : (hg == null ? 0 : hg);
            String res = (my > opp) ? "W" : (my == opp ? "D" : "L");
            outcomes.add(res);
            dates.add(m.getDate());
        }
        // Consider only the most recent 40
        int n = outcomes.size();
        int startIdx = Math.max(0, n - 40);
        java.util.List<String> lastOutcomes = outcomes.subList(startIdx, n);
        java.util.List<java.time.LocalDate> lastDates = dates.subList(startIdx, n);
        // Compress into streaks
        record St(String outcome, int length, java.time.LocalDate start, java.time.LocalDate end) {}
        java.util.ArrayList<St> streaks = new java.util.ArrayList<>();
        String curOutcome = null; int curLen = 0; java.time.LocalDate stDate = null; java.time.LocalDate enDate = null;
        for (int i = 0; i < lastOutcomes.size(); i++) {
            String o = lastOutcomes.get(i);
            java.time.LocalDate d = lastDates.get(i);
            if (curOutcome == null || !curOutcome.equals(o)) {
                if (curOutcome != null) {
                    streaks.add(new St(curOutcome, curLen, stDate, enDate));
                }
                curOutcome = o; curLen = 1; stDate = d; enDate = d;
            } else {
                curLen++; enDate = d;
            }
        }
        if (curOutcome != null) streaks.add(new St(curOutcome, curLen, stDate, enDate));
        // Sort streaks by length desc, then end date desc (more recent first)
        streaks.sort((a, b) -> {
            int c = Integer.compare(b.length(), a.length());
            if (c != 0) return c;
            java.time.LocalDate be = b.end(); java.time.LocalDate ae = a.end();
            if (be == null && ae == null) return 0;
            if (be == null) return 1;
            if (ae == null) return -1;
            return be.compareTo(ae);
        });
        java.util.List<com.chambua.vismart.dto.OutcomeStreakDTO> top = new java.util.ArrayList<>();
        for (int i = 0; i < streaks.size() && i < 2; i++) {
            St s = streaks.get(i);
            top.add(new com.chambua.vismart.dto.OutcomeStreakDTO(s.outcome(), s.length(), s.start(), s.end()));
        }
        return new com.chambua.vismart.dto.TopOutcomeStreaksResponse(tn, lastOutcomes.size(), top);
    }

    public PriorOutcomeResponse computePriorOutcomeDistribution(Long teamId) {
        var teamOpt = teamRepository.findById(teamId);
        if (teamOpt.isEmpty()) {
            return new PriorOutcomeResponse("Unknown", List.of());
        }
        String teamName = teamOpt.get().getName();
        // Use NAME-based retrieval to include all matches across duplicate team IDs/leagues
        List<Match> matches = matchRepository.findRecentPlayedByTeamName(teamName);
        if (matches == null || matches.isEmpty()) {
            return new PriorOutcomeResponse(teamName, List.of());
        }
        // ensure chronological order
        Collections.reverse(matches); // repository returns desc by date

        record Key(String priorResult, String priorScore) {}
        Map<Key, int[]> buckets = new LinkedHashMap<>(); // [wins, draws, losses]

        Match prev = null;
        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.PLAYED || m.getHomeGoals() == null || m.getAwayGoals() == null) {
                continue;
            }
            if (prev == null) { prev = m; continue; }
            // prior result from prev, next result from m; both in the perspective of the resolved team name
            String priorResult = resultForName(prev, teamName);
            String priorScore = scorelineForName(prev, teamName);
            String nextResult = resultForName(m, teamName);
            Key key = new Key(priorResult, priorScore);
            int[] arr = buckets.computeIfAbsent(key, k -> new int[3]);
            switch (nextResult) {
                case "Win" -> arr[0]++;
                case "Draw" -> arr[1]++;
                case "Loss" -> arr[2]++;
            }
            prev = m;
        }

        List<PriorOutcomeResponse.PriorOutcomeStat> list = new ArrayList<>();
        for (var e : buckets.entrySet()) {
            int wins = e.getValue()[0];
            int draws = e.getValue()[1];
            int losses = e.getValue()[2];
            int total = wins + draws + losses;
            if (total == 0) continue;
            double winPct = round2(100.0 * wins / total);
            double drawPct = round2(100.0 * draws / total);
            double lossPct = round2(100.0 * losses / total);
            var stat = new PriorOutcomeResponse.PriorOutcomeStat(
                    e.getKey().priorResult(),
                    e.getKey().priorScore(),
                    new PriorOutcomeResponse.NextResults(winPct, drawPct, lossPct)
            );
            stat.setSampleSize(total);
            list.add(stat);
        }

        return new PriorOutcomeResponse(teamName, list);
    }

    private static String scorelineForTeam(Match m, Long teamId) {
        Integer gf; Integer ga;
        boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && m.getHomeTeam().getId().equals(teamId));
        if (home) {
            gf = m.getHomeGoals(); ga = m.getAwayGoals();
        } else {
            gf = m.getAwayGoals(); ga = m.getHomeGoals();
        }
        int gfi = gf == null ? 0 : gf;
        int gai = ga == null ? 0 : ga;
        return gfi + "-" + gai;
    }

    private static String resultForTeam(Match m, Long teamId) {
        boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && m.getHomeTeam().getId().equals(teamId));
        Integer hg = m.getHomeGoals();
        Integer ag = m.getAwayGoals();
        if (hg == null || ag == null) return "Draw"; // should not happen for PLAYED; neutral default
        int cmp = Integer.compare(home ? hg : ag, home ? ag : hg);
        return cmp > 0 ? "Win" : (cmp == 0 ? "Draw" : "Loss");
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String scorelineForName(Match m, String teamName) {
        String tn = teamName == null ? "" : teamName.trim();
        boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().trim().equalsIgnoreCase(tn));
        Integer gf = home ? m.getHomeGoals() : m.getAwayGoals();
        Integer ga = home ? m.getAwayGoals() : m.getHomeGoals();
        int gfi = gf == null ? 0 : gf;
        int gai = ga == null ? 0 : ga;
        return gfi + "-" + gai;
    }

    private static String resultForName(Match m, String teamName) {
        String tn = teamName == null ? "" : teamName.trim();
        boolean home = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null && m.getHomeTeam().getName().trim().equalsIgnoreCase(tn));
        Integer hg = m.getHomeGoals();
        Integer ag = m.getAwayGoals();
        if (hg == null || ag == null) return "Draw"; // neutral default
        int cmp = Integer.compare(home ? hg : ag, home ? ag : hg);
        return cmp > 0 ? "Win" : (cmp == 0 ? "Draw" : "Loss");
    }
}

package com.chambua.vismart.service;

import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class H2HService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final com.chambua.vismart.config.FeatureFlags featureFlags;

    public H2HService(MatchRepository matchRepository, TeamRepository teamRepository, com.chambua.vismart.config.FeatureFlags featureFlags) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.featureFlags = featureFlags;
    }

    /**
     * Resolve names (including aliases) to team IDs and fetch H2H by IDs.
     * Falls back to name-based repository methods if resolution fails.
     */
    public List<Match> getH2HByNames(String homeName, String awayName) {
        if (homeName == null || awayName == null) return List.of();
        String home = homeName.trim();
        String away = awayName.trim();
        if (home.isEmpty() || away.isEmpty()) return List.of();

        // Try resolving names via TeamRepository using list-returning method to avoid NonUniqueResultException
        Team homeTeam = resolveTeamSafely(home);
        Team awayTeam = resolveTeamSafely(away);

        if (homeTeam != null && awayTeam != null) {
            List<Long> homeIds = resolveAllTeamIds(home);
            List<Long> awayIds = resolveAllTeamIds(away);
            if (!homeIds.isEmpty() && !awayIds.isEmpty()) {
                List<Match> bySets = matchRepository.findH2HByTeamIdSetsAllLeagues(homeIds, awayIds);
                if (!bySets.isEmpty()) return bySets;
            }
            // As a lightweight path, also try the single-ID method
            List<Match> byIds = matchRepository.findH2HByTeamIds(homeTeam.getId(), awayTeam.getId());
            if (!byIds.isEmpty()) return byIds;
        }

        // Fallback to existing name-based queries to preserve current behavior
        List<Match> exact = matchRepository.findPlayedByExactNames(home, away);
        if (!exact.isEmpty()) return exact;
        return matchRepository.findPlayedByFuzzyNames(home, away);
    }
    private List<Long> resolveAllTeamIds(String input) {
        if (input == null) return List.of();
        String name = input.trim();
        if (name.isEmpty()) return List.of();
        return teamRepository.findAllByNameOrAliasIgnoreCase(name)
                .stream()
                .map(Team::getId)
                .distinct()
                .sorted()
                .toList();
    }
    private Team resolveTeamSafely(String input) {
        if (input == null) return null;
        String name = input.trim();
        if (name.isEmpty()) return null;
        // Fetch all candidates by exact name or alias (case-insensitive)
        List<Team> candidates = teamRepository.findAllByNameOrAliasIgnoreCase(name);
        if (candidates == null || candidates.isEmpty()) return null;
        // Prefer exact name matches over alias-only matches
        Team exact = candidates.stream()
                .filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(name))
                .min((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElse(null);
        if (exact != null) return exact;
        // Otherwise, pick deterministically the smallest ID to keep behavior stable
        return candidates.stream()
                .min((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElse(null);
    }
    
    /**
     * Compute Goal Differential summary from historical H2H, oriented to the first team (homeName) provided.
     * If fewer than 3 valid matches exist, marks insufficientData=true and leaves aggregate/avg possibly null.
     */
    public com.chambua.vismart.dto.GoalDifferentialSummary computeGoalDifferentialByNames(String homeName, String awayName) {
            if (!featureFlags.isPredictiveH2HPhase1Enabled()) {
                com.chambua.vismart.dto.GoalDifferentialSummary out = new com.chambua.vismart.dto.GoalDifferentialSummary();
                out.setInsufficientData(true);
                return out;
            }
        com.chambua.vismart.dto.GoalDifferentialSummary out = new com.chambua.vismart.dto.GoalDifferentialSummary();
        try {
            if (homeName == null || homeName.isBlank() || awayName == null || awayName.isBlank()) {
                out.setInsufficientData(true);
                return out;
            }
            String refTeam = homeName.trim();
            List<com.chambua.vismart.model.Match> list = getH2HByNames(homeName, awayName);
            if (list == null || list.isEmpty()) {
                out.setInsufficientData(true);
                return out;
            }
            java.util.ArrayList<Integer> per = new java.util.ArrayList<>();
            int agg = 0;
            int valid = 0;
            for (com.chambua.vismart.model.Match m : list) {
                String hn = (m.getHomeTeam() != null && m.getHomeTeam().getName() != null) ? m.getHomeTeam().getName() : "";
                String an = (m.getAwayTeam() != null && m.getAwayTeam().getName() != null) ? m.getAwayTeam().getName() : "";
                Integer hg = m.getHomeGoals();
                Integer ag = m.getAwayGoals();
                // Handle missing/invalid safely: if either side goal is null, skip this match from GD
                if (hg == null || ag == null) continue;
                int scored;
                int conceded;
                if (hn.equalsIgnoreCase(refTeam)) {
                    scored = hg; conceded = ag;
                } else if (an.equalsIgnoreCase(refTeam)) {
                    scored = ag; conceded = hg;
                } else {
                    // If the provided name does not match either side (alias not resolved), best-effort: assume perspective is home side
                    scored = hg != null ? hg : 0;
                    conceded = ag != null ? ag : 0;
                }
                int gd = scored - conceded;
                per.add(gd);
                agg += gd;
                valid++;
            }
            out.setPerMatchGD(per);
            if (valid < 3) {
                out.setInsufficientData(true);
                if (valid > 0) {
                    out.setAggregateGD(agg);
                    out.setAvgGD(valid > 0 ? (double) agg / valid : null);
                }
                return out;
            }
            out.setInsufficientData(false);
            out.setAggregateGD(agg);
            out.setAvgGD(valid > 0 ? (double) agg / valid : 0.0);
            return out;
        } catch (Exception e) {
            out.setInsufficientData(true);
            return out;
        }
    }

    /**
     * Generate a plain-text insights summary combining GD, recent streaks and PPG trend.
     */
    public String generateInsightsText(String homeName, String awayName) {
            if (!featureFlags.isPredictiveH2HPhase1Enabled()) {
                return "Limited match history available.";
            }
        StringBuilder sb = new StringBuilder();
        boolean hasAny = false;
        try {
            String hn = homeName != null ? homeName.trim() : "";
            String an = awayName != null ? awayName.trim() : "";
            if (hn.isEmpty() || an.isEmpty()) return "Limited match history available.";
            // GD
            try {
                com.chambua.vismart.dto.GoalDifferentialSummary gd = computeGoalDifferentialByNames(hn, an);
                if (gd != null && gd.getAggregateGD() != null) {
                    int agg = gd.getAggregateGD();
                    String signed = (agg > 0 ? "+" + agg : String.valueOf(agg));
                    sb.append(hn).append(" has ").append(signed).append(" GD in H2H");
                    hasAny = true;
                }
            } catch (Exception ignored) {}
            // Recent streaks and PPG trends for each team (last-5 across all matches)
            java.util.function.Function<String, com.chambua.vismart.dto.FormSummary> fetchForm = (name) -> {
                try {
                    java.util.List<com.chambua.vismart.model.Match> recent = matchRepository.findRecentPlayedByTeamName(name);
                    java.util.ArrayList<String> results = new java.util.ArrayList<>();
                    java.util.ArrayList<Double> ppg = new java.util.ArrayList<>();
                    int wins = 0, draws = 0; int cum = 0; int cnt = 0;
                    for (com.chambua.vismart.model.Match m : recent) {
                        if (results.size() >= 5) break;
                        Integer hg = m.getHomeGoals(); Integer ag = m.getAwayGoals();
                        if (hg == null || ag == null) continue;
                        boolean isHome = (m.getHomeTeam()!=null && m.getHomeTeam().getName()!=null && m.getHomeTeam().getName().equalsIgnoreCase(name));
                        int my = isHome ? hg : ag; int opp = isHome ? ag : hg;
                        int pts;
                        if (my > opp) { results.add("W"); wins++; pts = 3; }
                        else if (my == opp) { results.add("D"); draws++; pts = 1; }
                        else { results.add("L"); pts = 0; }
                        cnt++; cum += pts; double p = cnt>0 ? ((double)cum)/cnt : 0.0; ppg.add(Math.round(p*10.0)/10.0);
                    }
                    String streak = "0";
                    if (!results.isEmpty()) {
                        String first = results.get(0); int c=1; for (int i=1;i<results.size();i++){ if (results.get(i).equals(first)) c++; else break; }
                        streak = c + first;
                    }
                    com.chambua.vismart.dto.FormSummary fs = new com.chambua.vismart.dto.FormSummary(results, streak,
                            (results.size()>0 ? (int)Math.round((wins*100.0)/results.size()) : 0), wins*3 + draws);
                    fs.setPpgSeries(ppg);
                    return fs;
                } catch (Exception e) { return new com.chambua.vismart.dto.FormSummary(); }
            };
            com.chambua.vismart.dto.FormSummary hf = fetchForm.apply(hn);
            com.chambua.vismart.dto.FormSummary af = fetchForm.apply(an);
            // Streak phrasing
            java.util.function.Function<com.chambua.vismart.dto.FormSummary, String> streakText = (fs) -> {
                String s = fs.getCurrentStreak();
                if (s == null || s.equals("0")) return null;
                if (s.toUpperCase().endsWith("W")) return s.substring(0, s.length()-1) + "W in a row";
                if (s.toUpperCase().endsWith("D")) return s.substring(0, s.length()-1) + "D in a row";
                if (s.toUpperCase().endsWith("L")) return s.substring(0, s.length()-1) + "L in a row";
                return s;
            };
            String hs = streakText.apply(hf);
            String as = streakText.apply(af);
            if (hs != null) { if (hasAny) sb.append("; "); sb.append(hn).append(" on ").append(hs); hasAny = true; }
            if (as != null) { if (hasAny) sb.append("; "); sb.append(an).append(" on ").append(as); hasAny = true; }
            // PPG trend text
            java.util.function.Function<com.chambua.vismart.dto.FormSummary, String> ppgTrend = (fs) -> {
                java.util.List<Double> ser = fs.getPpgSeries();
                if (ser == null || ser.size() < 2) return null;
                double start = ser.get(ser.size()-1) != null ? ser.get(ser.size()-1) : 0.0;
                double end = ser.get(0) != null ? ser.get(0) : 0.0;
                return String.format("%.1f → %.1f", start, end);
            };
            String hpt = ppgTrend.apply(hf);
            String apt = ppgTrend.apply(af);
            if (hpt != null) { if (hasAny) sb.append("; "); sb.append(hn).append(" improved from ").append(hpt).append(" PPG"); hasAny = true; }
            if (apt != null) { if (hasAny) sb.append("; "); sb.append(an).append(" improved from ").append(apt).append(" PPG"); hasAny = true; }
        } catch (Exception ignored) {}
        if (!hasAny) return "Limited match history available.";
        return sb.toString();
    }
}

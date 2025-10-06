package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.dto.RecommendationSummary;
import com.chambua.vismart.dto.StreakInsight;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Orchestrates fixture recommendations by merging Fixture Analysis and Streak Insights.
 * Non-invasive: uses existing MatchAnalysisService and internal streak computations.
 */
@Service
public class RecommendationOrchestratorService {

    private final MatchAnalysisService matchAnalysisService;

    // Simple in-memory cache (fixtureId -> cached entry)
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MILLIS = 5 * 60 * 1000; // 5 minutes
    private final ExecutorService pool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() / 2));

    public RecommendationOrchestratorService(MatchAnalysisService matchAnalysisService) {
        this.matchAnalysisService = matchAnalysisService;
    }

    public RecommendationSummary recommend(Long fixtureId,
                                           Long leagueId,
                                           Long seasonId,
                                           Long homeTeamId,
                                           Long awayTeamId,
                                           String leagueName,
                                           String homeTeamName,
                                           String awayTeamName) {
        String key = cacheKey(fixtureId, leagueId, seasonId, homeTeamId, awayTeamId, homeTeamName, awayTeamName);
        CacheEntry ce = cache.get(key);
        long now = System.currentTimeMillis();
        if (ce != null && (now - ce.timestamp) < TTL_MILLIS) {
            return ce.summary;
        }

        // Orchestrate in parallel with timeouts
        Future<MatchAnalysisResponse> faFuture = pool.submit(() ->
                matchAnalysisService.analyzeDeterministic(
                        leagueId,
                        homeTeamId,
                        awayTeamId,
                        seasonId,
                        leagueName,
                        homeTeamName,
                        awayTeamName,
                        false,
                        "fixtures"
                )
        );
        // Use last-5 form to infer current streak pattern, then compute streak insight
        Future<StreakInsight> homeStreakFuture = pool.submit(() -> computeCurrentStreakInsight(homeTeamId, homeTeamName));
        Future<StreakInsight> awayStreakFuture = pool.submit(() -> computeCurrentStreakInsight(awayTeamId, awayTeamName));

        MatchAnalysisResponse mar = null;
        StreakInsight hStreak = null;
        StreakInsight aStreak = null;
        try {
            mar = faFuture.get(7, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        try {
            hStreak = homeStreakFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        try {
            aStreak = awayStreakFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        RecommendationSummary out = fuse(mar, hStreak, aStreak);
        out.setFixtureId(fixtureId);
        out.setLeagueId(leagueId);
        out.setSeasonId(seasonId);
        out.setLeagueName(leagueName);
        out.setHomeTeam(homeTeamName);
        out.setAwayTeam(awayTeamName);

        cache.put(key, new CacheEntry(out));
        return out;
    }

    private StreakInsight computeCurrentStreakInsight(Long teamId, String teamName) {
        // Derive current streak using existing private helper via reflection, then compute streak insight
        String pattern = "0";
        try {
            java.lang.reflect.Method mForm = MatchAnalysisService.class.getDeclaredMethod("computeFormLastFive", Long.class, String.class);
            mForm.setAccessible(true);
            com.chambua.vismart.dto.FormSummary fs = (com.chambua.vismart.dto.FormSummary) mForm.invoke(matchAnalysisService, teamId, teamName);
            if (fs != null && fs.getCurrentStreak() != null && !fs.getCurrentStreak().isBlank()) {
                pattern = fs.getCurrentStreak();
            }
        } catch (Exception ignored) {}
        // computeStreakInsight expects a target pattern like "3W". When pattern is "0", it will return an empty summary.
        try {
            java.lang.reflect.Method m = MatchAnalysisService.class.getDeclaredMethod("computeStreakInsight", Long.class, String.class, String.class);
            m.setAccessible(true);
            return (StreakInsight) m.invoke(matchAnalysisService, teamId, teamName, pattern);
        } catch (Exception e) {
            // Fallback: no streak
            StreakInsight si = new StreakInsight();
            si.setTeamName(teamName);
            si.setPattern("0");
            si.setSummaryText(teamName + ": no active streak detected.");
            return si;
        }
    }

    private RecommendationSummary fuse(MatchAnalysisResponse mar, StreakInsight home, StreakInsight away) {
        RecommendationSummary r = new RecommendationSummary();
        r.setMatchAnalysis(mar);
        r.setHomeStreak(home);
        r.setAwayStreak(away);

        // Outcome lean from MatchAnalysis primary probabilities when available
        RecommendationSummary.OutcomeLean lean = RecommendationSummary.OutcomeLean.UNKNOWN;
        int baseConf = 40; // baseline when FA available
        if (mar != null && mar.getWinProbabilities() != null) {
            int h = mar.getWinProbabilities().getHomeWin();
            int d = mar.getWinProbabilities().getDraw();
            int a = mar.getWinProbabilities().getAwayWin();
            if (h >= d && h >= a) lean = RecommendationSummary.OutcomeLean.HOME;
            else if (a >= h && a >= d) lean = RecommendationSummary.OutcomeLean.AWAY;
            else lean = RecommendationSummary.OutcomeLean.DRAW;
            // base confidence proportional to margin
            int top = Math.max(h, Math.max(d, a));
            int second = (h + d + a) - top - Math.min(h, Math.min(d, a));
            int margin = Math.max(0, top - second);
            baseConf = Math.min(60, 30 + margin); // 30..60
        } else {
            baseConf = 20; // partial without FA
        }
        r.setOutcomeLean(lean);

        // BTTS/O-U recommendations using FA rates first, then streaks
        String btts = null;
        String ou = null;
        Integer ouProb = null;
        if (mar != null) {
            int bttsPct = mar.getBttsProbability();
            int o25 = mar.getOver25Probability();
            if (bttsPct > 58) btts = "Yes"; else if (bttsPct < 42) btts = "No"; else btts = "Lean";
            if (o25 > 58) { ou = "Over 2.5"; ouProb = o25; }
            else if (o25 < 42) { ou = "Under 2.5"; ouProb = 100 - o25; }
            else { ou = "2.5 borderline"; ouProb = null; }
        }
        // refine with streaks if available and sufficiently sampled
        int alignBonus = 0;
        int streakComponent = 0;
        boolean divergence = false;
        List<String> rationale = new ArrayList<>();

        boolean homeMissing = (home == null || home.getInstances() <= 0);
        boolean awayMissing = (away == null || away.getInstances() <= 0);
        if (homeMissing) {
            r.setHomeStreakNote("No streak data available for Home — prediction weighted mainly from Fixture Analysis.");
        }
        if (awayMissing) {
            r.setAwayStreakNote("No streak data available for Away — prediction weighted mainly from Fixture Analysis.");
        }

        if (home != null) {
            SampleLevelPair p = sampleLevel(home.getInstances());
            r.setHomeStreakSampleLevel(p.level);
            r.setHomeStreakInstances(home.getInstances());
            if (p.level == RecommendationSummary.SampleSizeLevel.HIGH && lean == RecommendationSummary.OutcomeLean.HOME && home.getNextWinPct() >= 55) {
                alignBonus += 10; streakComponent += 10;
                rationale.add("Home’s current pattern often leads to wins next game (" + home.getNextWinPct() + "% across " + home.getInstances() + " instances).");
            }
            if (p.level != RecommendationSummary.SampleSizeLevel.LOW && btts != null) {
                if ("Yes".equals(btts) && home.getBttsPct() >= 55) { alignBonus += 4; streakComponent += 4; }
                if ("No".equals(btts) && home.getBttsPct() <= 45) { alignBonus += 4; streakComponent += 4; }
            }
        }
        if (away != null) {
            SampleLevelPair p = sampleLevel(away.getInstances());
            r.setAwayStreakSampleLevel(p.level);
            r.setAwayStreakInstances(away.getInstances());
            if (p.level == RecommendationSummary.SampleSizeLevel.HIGH && lean == RecommendationSummary.OutcomeLean.AWAY && away.getNextWinPct() >= 55) {
                alignBonus += 10; streakComponent += 10;
                rationale.add("Away’s current pattern often produces wins next match (" + away.getNextWinPct() + "% over " + away.getInstances() + " samples).");
            }
            if (p.level != RecommendationSummary.SampleSizeLevel.LOW && btts != null) {
                if ("Yes".equals(btts) && away.getBttsPct() >= 55) { alignBonus += 4; streakComponent += 4; }
                if ("No".equals(btts) && away.getBttsPct() <= 45) { alignBonus += 4; streakComponent += 4; }
            }
        }
        // Detect divergence when streak suggests opposite of FA lean with reasonable samples
        if (lean == RecommendationSummary.OutcomeLean.HOME && away != null && away.getNextWinPct() >= 50 && sampleLevel(away.getInstances()).level != RecommendationSummary.SampleSizeLevel.LOW) {
            divergence = true;
        }
        if (lean == RecommendationSummary.OutcomeLean.AWAY && home != null && home.getNextWinPct() >= 50 && sampleLevel(home.getInstances()).level != RecommendationSummary.SampleSizeLevel.LOW) {
            divergence = true;
        }
        r.setDivergenceWarning(divergence);
        int adjustment = 0;
        if (divergence) {
            r.setDivergenceNote("Fixture Analysis and Streaks point different ways — review tabs below.");
            // apply a small penalty to net streak contribution
            if (alignBonus > 0) alignBonus = Math.max(0, alignBonus - 8);
            adjustment -= 5;
        }
        // If streaks are missing, explicitly add stabilization so confidence doesn't collapse
        if (homeMissing && awayMissing) {
            adjustment += 15; // give Fixture Analysis more authority
        } else if (homeMissing || awayMissing) {
            adjustment += 8;
        }

        int confidence = Math.min(100, Math.max(0, baseConf + alignBonus + adjustment));
        r.setOutcomeConfidence(confidence);
        r.setFixtureConfidenceComponent(baseConf);
        r.setStreakConfidenceComponent(streakComponent);
        r.setAdjustmentConfidence(adjustment);
        String cbd = "Confidence: " + confidence + "/100 (Fixture Analysis base = " + baseConf + ", Streak Insights = " + streakComponent + ", adjustments " + (adjustment >= 0 ? "+" : "") + adjustment + ")";
        if (homeMissing && awayMissing) {
            cbd += ". Streak Insights unavailable for both teams.";
        } else if (homeMissing || awayMissing) {
            cbd += ". Streak Insights unavailable for one team.";
        }
        r.setConfidenceBreakdownText(cbd);

        // Correct score shortlist: build from expected goals and lean if available
        List<String> cs = new ArrayList<>();
        if (mar != null && mar.getExpectedGoals() != null) {
            double xh = mar.getExpectedGoals().getHome();
            double xa = mar.getExpectedGoals().getAway();
            if (lean == RecommendationSummary.OutcomeLean.HOME) {
                cs = Arrays.asList("1-0", "2-0", "2-1");
            } else if (lean == RecommendationSummary.OutcomeLean.AWAY) {
                cs = Arrays.asList("0-1", "0-2", "1-2");
            } else if (lean == RecommendationSummary.OutcomeLean.DRAW) {
                cs = Arrays.asList("1-1", "0-0", "2-2");
            } else {
                cs = Arrays.asList("1-0", "1-1", "0-1");
            }
            // minor tweak with xG totals
            double tot = xh + xa;
            if (tot < 2.1) {
                // favor lower scores
                if (!cs.contains("0-0")) cs.add("0-0");
            } else if (tot > 3.0) {
                if (lean == RecommendationSummary.OutcomeLean.HOME && !cs.contains("3-1")) cs.add("3-1");
                if (lean == RecommendationSummary.OutcomeLean.AWAY && !cs.contains("1-3")) cs.add("1-3");
                if (lean == RecommendationSummary.OutcomeLean.DRAW && !cs.contains("2-2")) cs.add("2-2");
            }
        } else {
            cs = Arrays.asList("1-0", "1-1", "0-1");
        }
        r.setCorrectScoreShortlist(cs);

        r.setBttsRecommendation(btts != null ? btts : "Lean");
        r.setOverUnderRecommendation(ou != null ? ou : "2.5 borderline");
        r.setOverUnderProbability(ouProb);
        // Context snippet for score shortlist
        String csCtx;
        if (lean == RecommendationSummary.OutcomeLean.AWAY) {
            csCtx = "Likely scores reflect Away lean";
        } else if (lean == RecommendationSummary.OutcomeLean.HOME) {
            csCtx = "Likely scores reflect Home lean";
        } else if (lean == RecommendationSummary.OutcomeLean.DRAW) {
            csCtx = "Likely scores reflect a balanced match (Draw lean)";
        } else {
            csCtx = "Likely scores reflect a tight contest";
        }
        if (ou != null) {
            if (ou.startsWith("Under")) csCtx += " + Under 2.5 goal trend (low scoring)";
            else if (ou.startsWith("Over")) csCtx += " + Over 2.5 goal trend (higher scoring)";
        }
        r.setCorrectScoreContext(csCtx + ".");

        // Build rationale (plain language)
        if (mar != null && mar.getH2hSummary() != null) {
            rationale.add("Across the last " + mar.getH2hSummary().getLastN() + " H2H meetings, Home averages " + round1(mar.getH2hSummary().getPpgHome()) + " PPG vs Away’s " + round1(mar.getH2hSummary().getPpgAway()) + ".");
        }
        if (mar != null && mar.getFormSummary() != null) {
            rationale.add("Recent form baseline (1X2) tilts " + mar.getFormSummary().getHomeWin() + "% Home / " + mar.getFormSummary().getDraw() + "% Draw / " + mar.getFormSummary().getAwayWin() + "% Away.");
        }
        if (homeMissing && awayMissing) {
            rationale.add("No streak insight data available for either team; Fixture Analysis carries most of the weight.");
        } else {
            if (homeMissing) rationale.add("No Home streak insight available.");
            if (awayMissing) rationale.add("No Away streak insight available.");
        }
        r.setRationale(rationale);

        // Contribution details and counts
        Integer matchesCount = null;
        if (mar != null) {
            if (mar.getHeadToHeadMatches() != null && !mar.getHeadToHeadMatches().isEmpty()) {
                matchesCount = mar.getHeadToHeadMatches().size();
            } else if (mar.getH2hSummary() != null) {
                matchesCount = mar.getH2hSummary().getLastN();
            }
        }
        r.setAnalysisMatchesCount(matchesCount);

        java.util.List<String> faFactors = new java.util.ArrayList<>();
        if (mar != null) {
            if (mar.getH2hSummary() != null) {
                faFactors.add("H2H window: last " + mar.getH2hSummary().getLastN() + " matches (PPG H=" + round1(mar.getH2hSummary().getPpgHome()) + ", A=" + round1(mar.getH2hSummary().getPpgAway()) + ")");
            }
            if (mar.getFormSummary() != null) {
                faFactors.add("Form baseline 1X2 = " + mar.getFormSummary().getHomeWin() + "/" + mar.getFormSummary().getDraw() + "/" + mar.getFormSummary().getAwayWin());
            }
            faFactors.add("BTTS baseline = " + (mar.getBttsProbability()) + "%");
            faFactors.add("Over 2.5 baseline = " + (mar.getOver25Probability()) + "%");
        }
        r.setFixtureAnalysisFactors(faFactors);

        java.util.List<String> siFactors = new java.util.ArrayList<>();
        if (home != null) {
            String hp = home.getPattern() != null ? home.getPattern() : "0";
            siFactors.add("Home streak " + hp + ": instances=" + (home.getInstances()) + ", next W/D/L=" + home.getNextWinPct() + "/" + home.getNextDrawPct() + "/" + home.getNextLossPct() + "%, BTTS=" + home.getBttsPct() + "%, O2.5=" + home.getOver25Pct() + "%");
        } else if (homeMissing) {
            siFactors.add("Home: no streak data available.");
        }
        if (away != null) {
            String ap = away.getPattern() != null ? away.getPattern() : "0";
            siFactors.add("Away streak " + ap + ": instances=" + (away.getInstances()) + ", next W/D/L=" + away.getNextWinPct() + "/" + away.getNextDrawPct() + "/" + away.getNextLossPct() + "%, BTTS=" + away.getBttsPct() + "%, O2.5=" + away.getOver25Pct() + "%");
        } else if (awayMissing) {
            siFactors.add("Away: no streak data available.");
        }
        r.setStreakInsightFactors(siFactors);

        return r;
    }

    private static class SampleLevelPair {
        RecommendationSummary.SampleSizeLevel level;
        int bucket;
        SampleLevelPair(RecommendationSummary.SampleSizeLevel l, int b) { level = l; bucket = b; }
    }

    private SampleLevelPair sampleLevel(Integer instances) {
        if (instances == null || instances <= 0) return new SampleLevelPair(RecommendationSummary.SampleSizeLevel.UNKNOWN, 0);
        if (instances < 30) return new SampleLevelPair(RecommendationSummary.SampleSizeLevel.LOW, 1);
        if (instances < 100) return new SampleLevelPair(RecommendationSummary.SampleSizeLevel.MEDIUM, 2);
        return new SampleLevelPair(RecommendationSummary.SampleSizeLevel.HIGH, 3);
    }

    private String cacheKey(Long fixtureId, Long leagueId, Long seasonId, Long homeId, Long awayId, String homeName, String awayName) {
        return String.valueOf(Objects.requireNonNullElse(fixtureId, -1L)) + "|" + leagueId + "|" + seasonId + "|" +
                Objects.requireNonNullElse(homeId, -1L) + "|" + Objects.requireNonNullElse(awayId, -1L) + "|" +
                String.valueOf(Objects.requireNonNullElse(homeName, "")) + "|" + String.valueOf(Objects.requireNonNullElse(awayName, ""));
    }

    private static class CacheEntry {
        final RecommendationSummary summary;
        final long timestamp;
        CacheEntry(RecommendationSummary s) { this.summary = s; this.timestamp = System.currentTimeMillis(); }
    }

    private static String round1(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}

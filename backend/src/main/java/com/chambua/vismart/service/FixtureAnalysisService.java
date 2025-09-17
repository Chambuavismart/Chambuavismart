package com.chambua.vismart.service;

import com.chambua.vismart.dto.FixtureAnalysisResponse;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.model.TeamAlias;
import com.chambua.vismart.repository.MatchAnalysisResultRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FixtureAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(FixtureAnalysisService.class);

    private final MatchAnalysisService matchAnalysisService;
    private final FormGuideService formGuideService;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final SeasonResolutionService seasonService;
    private final MatchAnalysisResultRepository cacheRepo;

    @Autowired
    public FixtureAnalysisService(MatchAnalysisService matchAnalysisService, FormGuideService formGuideService,
                                  TeamRepository teamRepository, TeamAliasRepository teamAliasRepository,
                                  SeasonResolutionService seasonService, MatchAnalysisResultRepository cacheRepo) {
        this.matchAnalysisService = matchAnalysisService;
        this.formGuideService = formGuideService;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.seasonService = seasonService;
        this.cacheRepo = cacheRepo;
    }

    @Transactional(readOnly = true)
    public MatchAnalysisResponse analyzeFixture(Long leagueId, Long seasonId, Long homeTeamId, String homeTeamName,
                                                Long awayTeamId, String awayTeamName, boolean refresh) {
        log.info("[FixtureAnalysis][START] leagueId={} seasonId={} homeId={} awayId={} homeName='{}' awayName='{}' refresh={}", leagueId, seasonId, homeTeamId, awayTeamId, homeTeamName, awayTeamName, refresh);
        String hn = homeTeamName;
        String an = awayTeamName;
        // Resolve team IDs if not provided
        Long resolvedHomeId = homeTeamId != null ? homeTeamId : resolveTeamId(hn, leagueId);
        Long resolvedAwayId = awayTeamId != null ? awayTeamId : resolveTeamId(an, leagueId);
        log.debug("[FixtureAnalysis][TeamResolve] resolved homeId={} awayId={} (leagueId={})", resolvedHomeId, resolvedAwayId, leagueId);
        if (resolvedHomeId == null || resolvedAwayId == null) {
            String missing = (resolvedHomeId == null ? hn : an);
            log.warn("[FixtureAnalysis][TeamResolution] Failed: teamName='{}', leagueId={} (homeId={}, awayId={})", missing, leagueId, String.valueOf(resolvedHomeId), String.valueOf(resolvedAwayId));
            throw new IllegalArgumentException("Skipped: Team '" + missing + "' not found in league " + leagueId);
        }
        // Resolve season if null. To allow cache usage and align H2H when caller didn't specify season and no refresh,
        // we intentionally keep seasonId null so downstream cache in MatchAnalysisService can be leveraged.
        Long sid = (seasonId != null) ? seasonId : (refresh ? seasonService.resolveSeasonId(leagueId, null).orElse(null) : null);
        log.debug("[FixtureAnalysis][SeasonResolve] inputSeasonId={} resolvedSeasonId={} refresh={}", seasonId, sid, refresh);

        // For parity with match-analysis, optionally could check a per-day cache; for now use same cacheRepo as single path
        if (!refresh && leagueId != null && sid == null) {
            var cached = cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(leagueId, resolvedHomeId, resolvedAwayId);
            if (cached.isPresent()) {
                try {
                    MatchAnalysisResponse r = new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached.get().getResultJson(), MatchAnalysisResponse.class);
                    if (r != null) r.setCacheHit(true);
                    log.info("[FixtureAnalysis][CACHE] HIT leagueId={} homeId={} awayId={}", leagueId, resolvedHomeId, resolvedAwayId);
                    return r;
                } catch (Exception ignore) { /* fallthrough */ }
            } else {
                log.debug("[FixtureAnalysis][CACHE] MISS leagueId={} homeId={} awayId={}", leagueId, resolvedHomeId, resolvedAwayId);
            }
        }

        // Use MatchAnalysisService unified engine
        MatchAnalysisResponse matchResp = matchAnalysisService.analyzeDeterministic(
                leagueId, resolvedHomeId, resolvedAwayId, sid, null, hn, an, refresh);

        // Ensure over 1.5 and 3.5 plus notes/correct scores filled if missing
        if (matchResp.getOver15Probability() == null || matchResp.getOver35Probability() == null || matchResp.getCorrectScores() == null) {
            double lambdaHome = matchResp.getExpectedGoals() != null ? matchResp.getExpectedGoals().getHome() : 1.2;
            double lambdaAway = matchResp.getExpectedGoals() != null ? matchResp.getExpectedGoals().getAway() : 1.2;
            if (matchResp.getOver15Probability() == null) matchResp.setOver15Probability((int)Math.round(calculateOverGoals(lambdaHome, lambdaAway, 1.5) * 100.0));
            if (matchResp.getOver35Probability() == null) matchResp.setOver35Probability((int)Math.round(calculateOverGoals(lambdaHome, lambdaAway, 3.5) * 100.0));
            if (matchResp.getCorrectScores() == null || matchResp.getCorrectScores().isEmpty()) {
                matchResp.setCorrectScores(calculateCorrectScores(lambdaHome, lambdaAway).stream()
                        .map(s -> new MatchAnalysisResponse.CorrectScorePrediction(s.getScore(), s.getProbability()))
                        .collect(Collectors.toList()));
            }
            if (matchResp.getNotes() == null) matchResp.setNotes("Based on form, H2H, and league adjustments");
            log.debug("[FixtureAnalysis][PostFill] over15={} over35={} correctScores={}", matchResp.getOver15Probability(), matchResp.getOver35Probability(), matchResp.getCorrectScores()!=null?matchResp.getCorrectScores().size():0);
        }
        log.info("[FixtureAnalysis][END] home='{}' away='{}' HW/DW/AW={}/{}/{} BTTS={} O2.5={} xG=({},{}) cacheHit={} h2hAlpha={}",
                matchResp.getHomeTeam(), matchResp.getAwayTeam(),
                matchResp.getWinProbabilities()!=null?matchResp.getWinProbabilities().getHomeWin():0,
                matchResp.getWinProbabilities()!=null?matchResp.getWinProbabilities().getDraw():0,
                matchResp.getWinProbabilities()!=null?matchResp.getWinProbabilities().getAwayWin():0,
                matchResp.getBttsProbability(), matchResp.getOver25Probability(),
                matchResp.getExpectedGoals()!=null?matchResp.getExpectedGoals().getHome():0.0,
                matchResp.getExpectedGoals()!=null?matchResp.getExpectedGoals().getAway():0.0,
                matchResp.getCacheHit(), matchResp.getH2hAlpha());
        return matchResp;
    }

    private FixtureAnalysisResponse mapToFixtureResponse(MatchAnalysisResponse matchResp) {
        FixtureAnalysisResponse resp = new FixtureAnalysisResponse();
        // W/D/L as doubles 0..1
        FixtureAnalysisResponse.CorrectScorePrediction dummy = null; // just to keep import
        int hw = matchResp.getWinProbabilities() != null ? matchResp.getWinProbabilities().getHomeWin() : 0;
        int dr = matchResp.getWinProbabilities() != null ? matchResp.getWinProbabilities().getDraw() : 0;
        int aw = matchResp.getWinProbabilities() != null ? matchResp.getWinProbabilities().getAwayWin() : 0;
        resp.setWinDrawLossProbs(new double[]{hw/100.0, dr/100.0, aw/100.0});
        resp.setBttsProbability(matchResp.getBttsProbability()/100.0);
        // Over 2.5 is present; 1.5 and 3.5 we can compute from xG if needed
        double lambdaHome = matchResp.getExpectedGoals() != null ? matchResp.getExpectedGoals().getHome() : 1.2;
        double lambdaAway = matchResp.getExpectedGoals() != null ? matchResp.getExpectedGoals().getAway() : 1.2;
        resp.setOver25Probability(matchResp.getOver25Probability()/100.0);
        resp.setOver15Probability(calculateOverGoals(lambdaHome, lambdaAway, 1.5));
        resp.setOver35Probability(calculateOverGoals(lambdaHome, lambdaAway, 3.5));
        resp.setExpectedGoals(new double[]{lambdaHome, lambdaAway});
        resp.setNotes(matchResp.getNotes() != null ? matchResp.getNotes() : "Based on form, H2H, and league adjustments");
        if (matchResp.getCorrectScores() != null) {
            resp.setCorrectScores(matchResp.getCorrectScores().stream()
                    .map(s -> new FixtureAnalysisResponse.CorrectScorePrediction(s.getScore(), s.getProbability()))
                    .collect(Collectors.toList()));
        } else {
            resp.setCorrectScores(calculateCorrectScores(lambdaHome, lambdaAway));
        }
        return resp;
    }

    private Long resolveTeamId(String teamName, Long leagueId) {
        if (teamName == null || leagueId == null) return null;
        String raw = teamName.trim();
        if (raw.isEmpty()) return null;
        String norm = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String cleaned = norm.replace(".", "").replace("-", " ")
                .replaceAll("(?i)\bFC\b", "").replaceAll("(?i)\bSC\b", "")
                .replaceAll("\\s+", " ").trim();
        // 1) Repository helper that considers aliases within league
        try {
            Optional<Team> t = teamRepository.findByNameOrAliasInLeague(cleaned, leagueId);
            if (t.isPresent()) return t.get().getId();
        } catch (Exception ignored) {}
        // 2) Exact name (case-insensitive)
        try {
            List<Team> exact = teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, cleaned);
            if (!exact.isEmpty()) return exact.get(0).getId();
        } catch (Exception ignored) {}
        // 3) Contains (case-insensitive)
        try {
            List<Team> contains = teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(leagueId, cleaned);
            if (!contains.isEmpty()) return contains.get(0).getId();
        } catch (Exception ignored) {}
        // 4) Alias table (case-insensitive), try multiple variants
        try {
            // Prefer all-matches alias method if available
            java.util.List<TeamAlias> aliases = teamAliasRepository.findAllByAliasIgnoreCase(cleaned);
            if (aliases != null && !aliases.isEmpty()) {
                for (TeamAlias a : aliases) {
                    if (a.getTeam() != null && a.getTeam().getId() != null) return a.getTeam().getId();
                }
            }
        } catch (Exception ignored) {}
        // Try raw too
        try {
            java.util.List<TeamAlias> aliases = teamAliasRepository.findAllByAliasIgnoreCase(raw);
            if (aliases != null && !aliases.isEmpty()) {
                for (TeamAlias a : aliases) {
                    if (a.getTeam() != null && a.getTeam().getId() != null) return a.getTeam().getId();
                }
            }
        } catch (Exception ignored) {}
        // 5) Fuzzy matching within league (Levenshtein distance <= 2 on normalized names)
        try {
            String target = cleaned.toLowerCase(java.util.Locale.ROOT);
            String token = target;
            int sp = target.indexOf(' ');
            if (sp > 0) token = target.substring(0, sp);
            java.util.List<Team> candidates = new java.util.ArrayList<>();
            try { candidates.addAll(teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(leagueId, cleaned)); } catch (Exception ignored2) {}
            if (candidates.isEmpty() && token.length() >= 3) {
                try { candidates.addAll(teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(leagueId, token)); } catch (Exception ignored2) {}
            }
            int best = Integer.MAX_VALUE; Long bestId = null; String bestName = null;
            for (Team t : candidates) {
                String nm = t.getName() != null ? t.getName() : "";
                String norm2 = java.text.Normalizer.normalize(nm, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace(".", "").replace("-", " ")
                        .replaceAll("(?i)\\bFC\\b", "").replaceAll("(?i)\\bSC\\b", "")
                        .replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
                int d = levenshtein(target, norm2);
                if (d < best) { best = d; bestId = t.getId(); bestName = nm; }
            }
            if (best <= 2 && bestId != null) {
                log.info("[FixtureAnalysis][TeamResolution] Fuzzy match '{}'→'{}' (dist={}) in league {}", raw, bestName, best, leagueId);
                return bestId;
            }
        } catch (Exception ignored) {}
        log.warn("[FixtureAnalysis][TeamResolution] No match for '{}' (cleaned='{}') in league {}", raw, cleaned, leagueId);
        return null;
    }

    private int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length(); int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    private List<FixtureAnalysisResponse.CorrectScorePrediction> calculateCorrectScores(double lambdaHome, double lambdaAway) {
        List<FixtureAnalysisResponse.CorrectScorePrediction> scores = new ArrayList<>();
        for (int home = 0; home <= 5; home++) {
            for (int away = 0; away <= 5; away++) {
                double prob = poissonProbability(lambdaHome, home) * poissonProbability(lambdaAway, away);
                if (prob > 0.01) {
                    scores.add(new FixtureAnalysisResponse.CorrectScorePrediction(home + "-" + away, prob));
                }
            }
        }
        return scores.stream().sorted((a, b) -> Double.compare(b.getProbability(), a.getProbability())).limit(5).collect(Collectors.toList());
    }

    private double poissonProbability(double lambda, int k) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private int factorial(int n) {
        int f = 1;
        for (int i=2;i<=n;i++) f*=i;
        return f;
    }

    private double calculateOverGoals(double lambdaHome, double lambdaAway, double threshold) {
        double prob = 0.0;
        for (int home = 0; home <= 10; home++) {
            for (int away = 0; away <= 10; away++) {
                if (home + away > threshold) {
                    prob += poissonProbability(lambdaHome, home) * poissonProbability(lambdaAway, away);
                }
            }
        }
        return prob;
    }
}

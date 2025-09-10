package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.dto.QuickInsightItem;
import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.FixtureRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class QuickInsightsService { 
    @org.springframework.beans.factory.annotation.Value("${insights.thresholds.win:60}")
    private int winThreshold;
    @org.springframework.beans.factory.annotation.Value("${insights.thresholds.draw:30}")
    private int drawThreshold;
    @org.springframework.beans.factory.annotation.Value("${insights.thresholds.btts:60}")
    private int bttsThreshold;
    @org.springframework.beans.factory.annotation.Value("${insights.thresholds.over25:60}")
    private int over25Threshold;
    @org.springframework.beans.factory.annotation.Value("${insights.topPicks.count:5}")
    private int topPicksCount;
    private final FixtureRepository fixtureRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchAnalysisService matchAnalysisService;
    private final SeasonService seasonService;

    public QuickInsightsService(FixtureRepository fixtureRepository,
                                LeagueRepository leagueRepository,
                                TeamRepository teamRepository,
                                TeamAliasRepository teamAliasRepository,
                                MatchAnalysisService matchAnalysisService,
                                SeasonService seasonService) {
        this.fixtureRepository = fixtureRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.matchAnalysisService = matchAnalysisService;
        this.seasonService = seasonService;
    }

    public com.chambua.vismart.dto.QuickInsightsResponse getQuickInsightsNext48Hours() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime end = now.plusHours(48);
        List<Fixture> fixtures = fixtureRepository
                .findByDateTimeGreaterThanEqualAndDateTimeLessThanOrderByLeague_NameAscDateTimeAsc(now, end);
        // Fallback: if UTC window yields no fixtures (possible timezone storage mismatch), try system-default window
        if (fixtures == null || fixtures.isEmpty()) {
            java.time.LocalDateTime nowLocal = java.time.LocalDateTime.now();
            java.time.LocalDateTime endLocal = nowLocal.plusHours(48);
            fixtures = fixtureRepository
                    .findByDateTimeGreaterThanEqualAndDateTimeLessThanOrderByLeague_NameAscDateTimeAsc(nowLocal, endLocal);
        }

        List<QuickInsightItem> high = new ArrayList<>();
        for (Fixture f : fixtures) {
            try {
                League league = f.getLeague();
                if (league == null) continue;
                Long leagueId = league.getId();
                if (leagueId == null) continue;

                // Resolve team IDs within the league if possible
                Long homeId = teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, safe(f.getHomeTeam()))
                        .stream().findFirst().map(t -> t.getId()).orElse(null);
                Long awayId = teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, safe(f.getAwayTeam()))
                        .stream().findFirst().map(t -> t.getId()).orElse(null);
                String homeName = safe(f.getHomeTeam());
                String awayName = safe(f.getAwayTeam());

                // Determine season context (current season for league)
                Long seasonId = seasonService.findCurrentSeason(leagueId).map(com.chambua.vismart.model.Season::getId).orElse(null);

                // Run blended analysis using our deterministic analyzer
                MatchAnalysisResponse mar = matchAnalysisService.analyzeDeterministic(
                        leagueId,
                        homeId,
                        awayId,
                        seasonId,
                        league.getName(),
                        homeName,
                        awayName,
                        false
                );

                List<String> reasons = computeTriggers(mar);
                if (!reasons.isEmpty()) {
                    Instant ko = f.getDateTime() != null ? f.getDateTime().toInstant(ZoneOffset.UTC) : null;
                    high.add(new QuickInsightItem(
                            f.getId(),
                            leagueId,
                            league.getName(),
                            homeName,
                            awayName,
                            ko,
                            reasons
                    ));
                }
            } catch (Exception ignored) {
                // Skip problematic fixture gracefully
            }
        }
        // If none met high-interest, build top picks by strongest probability across dimensions
        List<QuickInsightItem> top = new ArrayList<>();
        if (high.isEmpty() && fixtures != null && !fixtures.isEmpty()) {
            class ScoredItem { QuickInsightItem item; int score; }
            List<ScoredItem> scored = new ArrayList<>();
            for (Fixture f : fixtures) {
                try {
                    League league = f.getLeague();
                    if (league == null) continue;
                    Long leagueId = league.getId();
                    if (leagueId == null) continue;
                    Long homeId = teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, safe(f.getHomeTeam())).stream().findFirst().map(t -> t.getId()).orElse(null);
                    Long awayId = teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, safe(f.getAwayTeam())).stream().findFirst().map(t -> t.getId()).orElse(null);
                    String homeName = safe(f.getHomeTeam());
                    String awayName = safe(f.getAwayTeam());
                    Long seasonId = seasonService.findCurrentSeason(leagueId).map(com.chambua.vismart.model.Season::getId).orElse(null);
                    MatchAnalysisResponse mar = matchAnalysisService.analyzeDeterministic(leagueId, homeId, awayId, seasonId, league.getName(), homeName, awayName, false);
                    if (mar == null) continue;
                    int best = 0;
                    String trigger = null;
                    if (mar.getWinProbabilities() != null) {
                        int h = mar.getWinProbabilities().getHomeWin();
                        int d = mar.getWinProbabilities().getDraw();
                        int a = mar.getWinProbabilities().getAwayWin();
                        if (h > best) { best = h; trigger = "Home win probability " + h + "%"; }
                        if (a > best) { best = a; trigger = "Away win probability " + a + "%"; }
                        if (d > best) { best = d; trigger = "Draw probability " + d + "%"; }
                    }
                    if (mar.getBttsProbability() > best) { best = mar.getBttsProbability(); trigger = "BTTS probability " + mar.getBttsProbability() + "%"; }
                    if (mar.getOver25Probability() > best) { best = mar.getOver25Probability(); trigger = "Over 2.5 probability " + mar.getOver25Probability() + "%"; }
                    Instant ko = f.getDateTime() != null ? f.getDateTime().toInstant(java.time.ZoneOffset.UTC) : null;
                    QuickInsightItem qi = new QuickInsightItem(f.getId(), leagueId, league.getName(), homeName, awayName, ko, trigger == null ? java.util.List.of() : java.util.List.of(trigger));
                    ScoredItem si = new ScoredItem();
                    si.item = qi; si.score = best;
                    scored.add(si);
                } catch (Exception ignored) {}
            }
            scored.sort((x,y) -> Integer.compare(y.score, x.score));
            int limit = Math.max(3, topPicksCount);
            for (int i=0; i<scored.size() && i<limit; i++) top.add(scored.get(i).item);
        }
        return new com.chambua.vismart.dto.QuickInsightsResponse(high, top);
    }

    private List<String> computeTriggers(MatchAnalysisResponse mar) {
        List<String> reasons = new ArrayList<>();
        if (mar == null) return reasons;
        var win = mar.getWinProbabilities();
        if (win != null) {
            if (win.getHomeWin() >= winThreshold) reasons.add("Home win probability " + win.getHomeWin() + "%");
            if (win.getAwayWin() >= winThreshold) reasons.add("Away win probability " + win.getAwayWin() + "%");
            if (win.getDraw() >= drawThreshold) reasons.add("Draw probability " + win.getDraw() + "%");
        }
        if (mar.getBttsProbability() >= bttsThreshold) reasons.add("BTTS probability " + mar.getBttsProbability() + "%");
        if (mar.getOver25Probability() >= over25Threshold) reasons.add("Over 2.5 probability " + mar.getOver25Probability() + "%");
        // Simultaneous BTTS Yes and Over 2.5 Yes recommendation
        if (mar.getBttsProbability() >= bttsThreshold && mar.getOver25Probability() >= over25Threshold) {
            reasons.add("System recommends BTTS = Yes and Over 2.5 = Yes");
        }
        // Deduplicate if overlaps
        return reasons.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}

package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.MatchAnalysisResultRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchAnalysisServiceTest {

    @Mock
    private MatchAnalysisResultRepository cacheRepo;

    @Mock
    private FormGuideService formGuideService;

    @Mock
    private SeasonService seasonService;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private LeagueTableService leagueTableService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchAnalysisService service;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        service = new MatchAnalysisService(cacheRepo, objectMapper, formGuideService, seasonService, matchRepository, leagueTableService);
        // No cache by default
        given(cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(anyLong(), anyLong(), anyLong()))
                .willReturn(Optional.empty());
        given(matchRepository.findHeadToHead(anyLong(), anyLong(), anyLong())).willReturn(List.of());
                given(leagueTableService.computeTableBySeasonId(anyLong(), anyLong())).willReturn(List.of());
    }

    private FormGuideRowDTO row(long teamId, String name, int mp, int bttsPct, int over25Pct) {
        // params: teamId, teamName, mp, totalMp, w, d, l, gf, ga, pts, ppg, lastResults, bttsPct, over15Pct, over25Pct, over35Pct
        return new FormGuideRowDTO(teamId, name, mp, mp, 0, 0, 0, 0, 0, 0, 0.0, List.of(), bttsPct, 0, over25Pct, 0);
    }

    private FormGuideRowDTO rowPpg(long teamId, String name, int mp, double ppg, int bttsPct, int over25Pct) {
        return new FormGuideRowDTO(teamId, name, mp, mp, 0, 0, 0, 0, 0, 0, ppg, List.of(), bttsPct, 0, over25Pct, 0);
    }

    private FormGuideRowDTO rowGfGa(long teamId, String name, int mp, int gf, int ga) {
        return new FormGuideRowDTO(teamId, name, mp, mp, 0, 0, 0, gf, ga, 0, 0.0, List.of(), 0, 0, 0, 0);
    }

    private FormGuideRowDTO rowWithWeightedGoals(long teamId, String name, int mp, int gf, int ga, double avgGfWeighted, double avgGaWeighted) {
        FormGuideRowDTO dto = new FormGuideRowDTO(teamId, name, mp, mp, 0, 0, 0, gf, ga, 0, 0.0, List.of(), 0, 0, 0, 0);
        dto.setAvgGfWeighted(avgGfWeighted);
        dto.setAvgGaWeighted(avgGaWeighted);
        return dto;
    }

    @Test
    void computesBttsAndOver25UsingPoissonFromXg() {
        Long leagueId = 1L; Long homeTeamId = 10L; Long awayTeamId = 20L;
        Season season = new Season(); season.setId(100L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Configure weighted splits to yield xG_home=1.50 and xG_away=1.13
        FormGuideRowDTO home = row(10L, "Team A", 6, 0, 0);
        FormGuideRowDTO away = row(20L, "Team B", 6, 0, 0);
        home.setWeightedHomeMatches(5);
        away.setWeightedAwayMatches(5);
        home.setWeightedHomeGoalsFor(1.6); // with away GA 1.4 -> xG_home = (1.6+1.4)/2 = 1.5
        away.setWeightedAwayGoalsAgainst(1.4);
        away.setWeightedAwayGoalsFor(1.0); // with home GA 1.26 -> xG_away = (1.0+1.26)/2 = 1.13
        home.setWeightedHomeGoalsAgainst(1.26);
        given(formGuideService.compute(eq(leagueId), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "Premier League", "Team A", "Team B", true);

        assertEquals(46, resp.getWinProbabilities().getHomeWin(), 1, "Home win should be ~46% by Poisson");
        assertEquals(26, resp.getWinProbabilities().getDraw(), 1, "Draw should be ~26% by Poisson");
        assertEquals(29, resp.getWinProbabilities().getAwayWin(), 1, "Away win should be ~29% by Poisson");
        assertEquals(53, resp.getBttsProbability(), 1, "BTTS should be ~53% by Poisson");
        assertEquals(49, resp.getOver25Probability(), 1, "Over 2.5 should be ~49% by Poisson");
    }

    @Test
    void defaultNeutralXgLeadsToMidrangeBTTSAndOver25() {
        Long leagueId = 2L; Long homeTeamId = 11L; Long awayTeamId = 22L;
        Season season = new Season(); season.setId(200L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // No weighted splits provided -> xG defaults to 1.5 for both teams per service logic
        FormGuideRowDTO teamA = row(11L, "Alpha", 1, 0, 0);
        FormGuideRowDTO teamB = row(22L, "Beta", 6, 0, 0);
        given(formGuideService.compute(eq(leagueId), eq(200L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(teamA, teamB));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League X", "Alpha", "Beta", true);

        // For λ_h=1.5, λ_a=1.5, BTTS is about 60% and Over2.5 about 57%
        assertTrue(resp.getBttsProbability() >= 58 && resp.getBttsProbability() <= 62, "BTTS should be around 60% for neutral xG");
        assertTrue(resp.getOver25Probability() >= 55 && resp.getOver25Probability() <= 60, "Over2.5 should be around 57% for neutral xG");
    }

    @Test
    void xgDrivesWinLossSkew_strongHomeWeakerAway() {
        Long leagueId = 3L; Long homeTeamId = 100L; Long awayTeamId = 200L;
        Season season = new Season(); season.setId(300L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Configure xG: home ~2.2, away ~0.8
        FormGuideRowDTO home = row(100L, "Home", 6, 0, 0);
        FormGuideRowDTO away = row(200L, "Away", 6, 0, 0);
        home.setWeightedHomeMatches(5);
        away.setWeightedAwayMatches(5);
        home.setWeightedHomeGoalsFor(2.4); // with away GA 2.0 -> xG_home = 2.2
        away.setWeightedAwayGoalsAgainst(2.0);
        away.setWeightedAwayGoalsFor(0.6); // with home GA 1.0 -> xG_away = 0.8
        home.setWeightedHomeGoalsAgainst(1.0);
        given(formGuideService.compute(eq(leagueId), eq(300L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(h > a, "Home should be greater than away when xG much higher");
        assertTrue(h >= 60 && a <= 15, "Expected strong skew (home >= 60, away <= 15)");
        assertEquals(100, h + d + a, "Probabilities should sum to 100");
    }

    @Test
    void xgEqual_balancedWithRealisticDrawShare() {
        Long leagueId = 4L; Long homeTeamId = 101L; Long awayTeamId = 201L;
        Season season = new Season(); season.setId(400L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        FormGuideRowDTO home = row(101L, "Home", 6, 0, 0);
        FormGuideRowDTO away = row(201L, "Away", 6, 0, 0);
        home.setWeightedHomeMatches(5);
        away.setWeightedAwayMatches(5);
        // Set xG ~1.2 for both
        home.setWeightedHomeGoalsFor(1.3); away.setWeightedAwayGoalsAgainst(1.1);
        away.setWeightedAwayGoalsFor(1.1); home.setWeightedHomeGoalsAgainst(1.3);
        given(formGuideService.compute(eq(leagueId), eq(400L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(Math.abs(h - a) <= 2, "Home and away should be approximately equal");
        assertTrue(d >= 23 && d <= 30, "Draw should be in the mid‑20s for moderate xG");
        assertEquals(100, h + d + a, "Probabilities should sum to 100");
    }

    @Test
    void neutralXgProducesBalancedWdl() {
        Long leagueId = 5L; Long homeTeamId = 102L; Long awayTeamId = 202L;
        Season season = new Season(); season.setId(500L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // No informative splits: rely on defaults xG=1.5 each
        FormGuideRowDTO home = row(102L, "Home", 1, 0, 0);
        FormGuideRowDTO away = row(202L, "Away", 6, 0, 0);
        given(formGuideService.compute(eq(leagueId), eq(500L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(Math.abs(h - a) <= 2, "Home and away should be approximately equal at neutral xG");
        assertTrue(d >= 23 && d <= 30, "Draw should be in the mid‑20s at neutral xG");
        assertEquals(100, h + d + a, "Probabilities should sum to 100");
    }

    @Test
    void xG_case1_strongHomeAttack_vs_weakAwayDefense() {
        Long leagueId = 6L; Long homeTeamId = 301L; Long awayTeamId = 302L;
        Season season = new Season(); season.setId(600L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Use weighted averages to drive xG per current logic
        FormGuideRowDTO home = rowWithWeightedGoals(301L, "HomeStrong", 6, 12, 3, 2.0, 0.5);
        FormGuideRowDTO away = rowWithWeightedGoals(302L, "AwayWeakDef", 6, 3, 12, 1.0, 2.0);
        given(formGuideService.compute(eq(leagueId), eq(600L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeStrong", "AwayWeakDef", true);

        assertEquals(2.0, resp.getExpectedGoals().getHome(), 0.0001, "xG_home should be 2.0");
    }

    @Test
    void xG_case2_bothDefensive_lowValuesUnderOne() {
        Long leagueId = 7L; Long homeTeamId = 401L; Long awayTeamId = 402L;
        Season season = new Season(); season.setId(700L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Provide weighted averages to reflect defensive sides
        FormGuideRowDTO home = rowWithWeightedGoals(401L, "HomeDef", 6, 3, 4, 0.5, 0.67);
        FormGuideRowDTO away = rowWithWeightedGoals(402L, "AwayDef", 6, 3, 4, 0.5, 0.67);
        given(formGuideService.compute(eq(leagueId), eq(700L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeDef", "AwayDef", true);

        assertTrue(resp.getExpectedGoals().getHome() < 1.0, "xG_home should be < 1.0 for defensive sides");
        assertTrue(resp.getExpectedGoals().getAway() < 1.0, "xG_away should be < 1.0 for defensive sides");
        assertTrue(resp.getExpectedGoals().getHome() >= 0.3 && resp.getExpectedGoals().getAway() >= 0.3, "xG values should be clamped to >= 0.3");
    }

    @Test
    void xG_case3_insufficientMatches_defaultsApplied() {
        Long leagueId = 8L; Long homeTeamId = 501L; Long awayTeamId = 502L;
        Season season = new Season(); season.setId(800L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Home mp < 2 currently defaults to neutral 1.5/1.5 unless weighted averages provided
        FormGuideRowDTO home = rowGfGa(501L, "HomeFew", 1, 2, 1);
        FormGuideRowDTO away = rowGfGa(502L, "AwayOK", 6, 10, 8);
        given(formGuideService.compute(eq(leagueId), eq(800L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeFew", "AwayOK", true);

        assertEquals(1.5, resp.getExpectedGoals().getHome(), 0.0001, "xG_home should default to 1.5 when insufficient matches");
        assertEquals(1.5, resp.getExpectedGoals().getAway(), 0.0001, "xG_away should default to 1.5 when insufficient matches");
    }

    @Test
    void xG_usesWeightedGoalsWhenProvided() {
        Long leagueId = 9L; Long homeTeamId = 601L; Long awayTeamId = 602L;
        Season season = new Season(); season.setId(900L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Unweighted rates would be 1.0, but weighted push to 2.0
        FormGuideRowDTO home = rowWithWeightedGoals(601L, "Home", 6, 6, 6, 2.0, 0.8);
        FormGuideRowDTO away = rowWithWeightedGoals(602L, "Away", 6, 6, 6, 1.2, 2.0);
        given(formGuideService.compute(eq(leagueId), eq(900L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        // xG_home should use (home avgGF_w + away avgGA_w)/2 = (2.0 + 2.0)/2 = 2.0
        assertEquals(2.0, resp.getExpectedGoals().getHome(), 0.0001);
        // xG_away should use (away avgGF_w + home avgGA_w)/2 = (1.2 + 0.8)/2 = 1.0
        assertEquals(1.0, resp.getExpectedGoals().getAway(), 0.0001);
    }
}

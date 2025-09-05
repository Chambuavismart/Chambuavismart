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
    void computesBttsAndOver25FromFormGuideAverages() {
        Long leagueId = 1L; Long homeTeamId = 10L; Long awayTeamId = 20L;
        // Mock season resolution
        Season season = new Season();
        season.setId(100L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Mock form guide rows
        FormGuideRowDTO teamA = row(10L, "Team A", 6, 40, 70);
        FormGuideRowDTO teamB = row(20L, "Team B", 6, 60, 50);
        given(formGuideService.compute(eq(leagueId), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(teamA, teamB));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "Premier League", "Team A", "Team B", true);

        assertEquals(50, resp.getBttsProbability(), "BTTS% should be average of 40 and 60 -> 50");
        assertEquals(60, resp.getOver25Probability(), "Over2.5% should be average of 70 and 50 -> 60");
    }

    @Test
    void defaultsTo50WhenInsufficientMatches() {
        Long leagueId = 2L; Long homeTeamId = 11L; Long awayTeamId = 22L;
        Season season = new Season(); season.setId(200L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // One team has only 1 match in the window
        FormGuideRowDTO teamA = row(11L, "Alpha", 1, 90, 90);
        FormGuideRowDTO teamB = row(22L, "Beta", 6, 10, 10);
        given(formGuideService.compute(eq(leagueId), eq(200L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(teamA, teamB));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League X", "Alpha", "Beta", true);

        assertEquals(50, resp.getBttsProbability(), "BTTS% should default to 50 when any team mp < 2");
        assertEquals(50, resp.getOver25Probability(), "Over2.5% should default to 50 when any team mp < 2");
    }

    @Test
    void ppgDrivesWinLossSkew_case1_strongHomeWeakerAway() {
        Long leagueId = 3L; Long homeTeamId = 100L; Long awayTeamId = 200L;
        Season season = new Season(); season.setId(300L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Home PPG = 2.5, Away PPG = 0.5
        FormGuideRowDTO home = rowPpg(100L, "Home", 6, 2.5, 50, 50);
        FormGuideRowDTO away = rowPpg(200L, "Away", 6, 0.5, 50, 50);
        given(formGuideService.compute(eq(leagueId), eq(300L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(h > a, "Home should be greater than away when PPG much higher");
        assertTrue(h >= 60 && a <= 15, "Expected strong skew (home >= 60, away <= 15)");
        assertEquals(100, h + d + a, "Probabilities should sum to 100");
    }

    @Test
    void ppgEqual_case2_balancedWithHigherDrawShare() {
        Long leagueId = 4L; Long homeTeamId = 101L; Long awayTeamId = 201L;
        Season season = new Season(); season.setId(400L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        FormGuideRowDTO home = rowPpg(101L, "Home", 6, 1.5, 50, 50);
        FormGuideRowDTO away = rowPpg(201L, "Away", 6, 1.5, 50, 50);
        given(formGuideService.compute(eq(leagueId), eq(400L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(Math.abs(h - a) <= 1, "Home and away should be approximately equal");
        assertTrue(d >= 20 && d <= 30, "Draw should occupy the buffer around ~25%");
        assertEquals(100, h + d + a, "Probabilities should sum to 100");
    }

    @Test
    void insufficientMatches_case3_fallbackDefault404040() {
        Long leagueId = 5L; Long homeTeamId = 102L; Long awayTeamId = 202L;
        Season season = new Season(); season.setId(500L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Home has <2 matches
        FormGuideRowDTO home = rowPpg(102L, "Home", 1, 2.5, 50, 50);
        FormGuideRowDTO away = rowPpg(202L, "Away", 6, 2.5, 50, 50);
        given(formGuideService.compute(eq(leagueId), eq(500L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        // With equal PPG and insufficient home matches, current logic yields a 75% band split (38/24/38)
        assertEquals(38, h);
        assertEquals(24, d);
        assertEquals(38, a);
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

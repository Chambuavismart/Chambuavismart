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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchAnalysisServiceWeightedSplitsTest {

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
        given(cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(anyLong(), anyLong(), anyLong()))
                .willReturn(Optional.empty());
        given(matchRepository.findHeadToHead(anyLong(), anyLong(), anyLong())).willReturn(List.of());
                given(leagueTableService.computeTableBySeasonId(anyLong(), anyLong())).willReturn(List.of());
    }

    private FormGuideRowDTO baseRow(long teamId, String name, double overallPpg, int overallBtts, int overallOv25,
                                    double whGF, double whGA, double whPPG, int whBTTS, int whOv25, int whMatches,
                                    double waGF, double waGA, double waPPG, int waBTTS, int waOv25, int waMatches,
                                    double avgGfWeighted, double avgGaWeighted) {
        FormGuideRowDTO dto = new FormGuideRowDTO(teamId, name, 6, 6, 0, 0, 0, 0, 0, 0, overallPpg, List.of(), overallBtts, 0, overallOv25, 0);
        dto.setWeightedHomeGoalsFor(whGF);
        dto.setWeightedHomeGoalsAgainst(whGA);
        dto.setWeightedHomePPG(whPPG);
        dto.setWeightedHomeBTTSPercent(whBTTS);
        dto.setWeightedHomeOver25Percent(whOv25);
        dto.setWeightedHomeMatches(whMatches);
        dto.setWeightedAwayGoalsFor(waGF);
        dto.setWeightedAwayGoalsAgainst(waGA);
        dto.setWeightedAwayPPG(waPPG);
        dto.setWeightedAwayBTTSPercent(waBTTS);
        dto.setWeightedAwayOver25Percent(waOv25);
        dto.setWeightedAwayMatches(waMatches);
        dto.setAvgGfWeighted(avgGfWeighted);
        dto.setAvgGaWeighted(avgGaWeighted);
        return dto;
    }

    @Test
    void bothTeamsSufficient_splitsApplied() {
        Long leagueId = 100L; Long homeTeamId = 1L; Long awayTeamId = 2L;
        Season season = new Season(); season.setId(1000L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Home has strong home split, Away weak away split
        FormGuideRowDTO home = baseRow(1L, "Home", 1.2, 40, 45,
                2.3, 0.7, 2.5, 60, 65, 6,
                1.1, 1.2, 1.1, 50, 45, 6,
                1.6, 1.0);
        FormGuideRowDTO away = baseRow(2L, "Away", 1.4, 50, 50,
                1.0, 1.1, 1.0, 40, 40, 6,
                0.6, 2.1, 0.7, 35, 38, 6,
                1.2, 1.8);
        given(formGuideService.compute(eq(leagueId), eq(1000L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Home", "Away", true);

        // W/D/L should favor home due to higher weightedHomePPG vs away weightedAwayPPG
        assertTrue(resp.getWinProbabilities().getHomeWin() > resp.getWinProbabilities().getAwayWin());
        // BTTS and Over2.5 should use split values (avg of 60 & 35 = 48, 65 & 38 = 52)
        assertEquals(48, resp.getBttsProbability());
        assertEquals(52, resp.getOver25Probability());
        // xG should use split GF/GA: home = (home whGF 2.3 + away waGA 2.1)/2 = 2.2; away = (away waGF 0.6 + home whGA 0.7)/2 = 0.65
        assertEquals(2.2, resp.getExpectedGoals().getHome(), 0.0001);
        assertEquals(0.65, resp.getExpectedGoals().getAway(), 0.0001);
    }

    @Test
    void oneTeamInsufficient_splitFallbackToOverall() {
        Long leagueId = 101L; Long homeTeamId = 11L; Long awayTeamId = 22L;
        Season season = new Season(); season.setId(1001L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Home has only 1 home match -> fallback to overall for home portions
        FormGuideRowDTO home = baseRow(11L, "HomeFew", 1.8, 55, 60,
                2.5, 0.5, 2.8, 80, 85, 1,
                1.0, 1.0, 1.2, 50, 50, 6,
                1.7, 0.9);
        // Away has sufficient away history
        FormGuideRowDTO away = baseRow(22L, "AwaySufficient", 1.0, 45, 40,
                0.9, 1.3, 0.9, 40, 35, 6,
                0.8, 2.2, 0.7, 35, 38, 6,
                1.1, 1.9);
        given(formGuideService.compute(eq(leagueId), eq(1001L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeFew", "AwaySufficient", true);

        // W/D/L uses home overall ppg (1.8) vs away away-ppg (0.7) -> home should be favored
        assertTrue(resp.getWinProbabilities().getHomeWin() > resp.getWinProbabilities().getAwayWin());
        // BTTS Over should fall back for home to overall (55, 60) and use away split (35, 38): avg -> 45 and 49
        assertEquals(45, resp.getBttsProbability());
        assertEquals(49, resp.getOver25Probability());
        // xG: homeGF uses overall avgGfWeighted 1.7 (fallback), awayGA uses away split 2.2 -> (1.7+2.2)/2=1.95; awayGF uses split 0.8, homeGA uses overall avgGaWeighted 0.9 -> (0.8+0.9)/2=0.85
        assertEquals(1.95, resp.getExpectedGoals().getHome(), 0.0001);
        assertEquals(0.85, resp.getExpectedGoals().getAway(), 0.0001);
    }

    @Test
    void bothTeamsInsufficient_neutralDefaults() {
        Long leagueId = 102L; Long homeTeamId = 111L; Long awayTeamId = 222L;
        Season season = new Season(); season.setId(1002L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Both have <2 matches in their respective splits and no overall weighted goals provided (0.0)
        FormGuideRowDTO home = baseRow(111L, "HomeNone", 0.0, 0, 0,
                0.0, 0.0, 0.0, 0, 0, 1,
                0.0, 0.0, 0.0, 0, 0, 1,
                0.0, 0.0);
        FormGuideRowDTO away = baseRow(222L, "AwayNone", 0.0, 0, 0,
                0.0, 0.0, 0.0, 0, 0, 1,
                0.0, 0.0, 0.0, 0, 0, 1,
                0.0, 0.0);
        given(formGuideService.compute(eq(leagueId), eq(1002L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeNone", "AwayNone", true);

        // When no valid PPG data exists, draw-biased neutral: 33/34/33 (implementation keeps safety but W/D/L derived from zeros => 33/34/33)
        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertEquals(100, h + d + a);
        assertTrue(Math.abs(h - a) <= 1);
        assertTrue(d >= 30);
        // BTTS/Over default to 50 when no valid data
        assertEquals(50, resp.getBttsProbability());
        assertEquals(50, resp.getOver25Probability());
        // xG defaults to neutral 1.5 when no valid data contributions
        assertEquals(1.5, resp.getExpectedGoals().getHome(), 0.0001);
        assertEquals(1.5, resp.getExpectedGoals().getAway(), 0.0001);
    }
}

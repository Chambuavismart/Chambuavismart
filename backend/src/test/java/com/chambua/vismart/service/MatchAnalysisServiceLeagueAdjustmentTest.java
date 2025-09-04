package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.LeagueTableEntryDTO;
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
class MatchAnalysisServiceLeagueAdjustmentTest {

    @Mock private MatchAnalysisResultRepository cacheRepo;
    @Mock private FormGuideService formGuideService;
    @Mock private SeasonService seasonService;
    @Mock private MatchRepository matchRepository;
    @Mock private LeagueTableService leagueTableService;

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
    }

    private FormGuideRowDTO baseRow(long teamId, String name, double overallPpg) {
        return new FormGuideRowDTO(teamId, name, 6, 6, 0, 0, 0, 0, 0, 0, overallPpg, List.of(), 50, 0, 50, 0);
    }

    private LeagueTableEntryDTO entry(int pos, long teamId, String name, int mp, int pts, int gd) {
        return new LeagueTableEntryDTO(pos, teamId, name, mp, 0, 0, 0, 0, 0, gd, pts);
    }

    @Test
    void topVsBottom_boostsStrongerTeam_respectingHomeAway() {
        Long leagueId = 2000L; Long homeTeamId = 10L; Long awayTeamId = 20L;
        Season season = new Season(); season.setId(9999L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Base form roughly equal so league adjustment effect is observable
        FormGuideRowDTO home = baseRow(homeTeamId, "TopClub", 1.5);
        FormGuideRowDTO away = baseRow(awayTeamId, "BottomClub", 1.5);
        given(formGuideService.compute(eq(leagueId), eq(9999L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));
        // League table: home is 1st, away is 20th (assume 20 teams), both have mp>0
        List<LeagueTableEntryDTO> table = Arrays.asList(
                entry(1, homeTeamId, "TopClub", 10, 25, 15),
                entry(20, awayTeamId, "BottomClub", 10, 5, -20)
        );
        given(leagueTableService.computeTableBySeasonId(leagueId, 9999L)).willReturn(table);

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "TopClub", "BottomClub", true);

        int h = resp.getWinProbabilities().getHomeWin();
        int a = resp.getWinProbabilities().getAwayWin();
        assertTrue(h > a, "Home (top of table) should be boosted vs bottom");
        assertEquals(100, h + resp.getWinProbabilities().getDraw() + a);
    }

    @Test
    void equalPositions_smallDrawBoost_whenClose() {
        Long leagueId = 2001L; Long homeTeamId = 11L; Long awayTeamId = 21L;
        Season season = new Season(); season.setId(9998L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        FormGuideRowDTO home = baseRow(homeTeamId, "Mid1", 1.5);
        FormGuideRowDTO away = baseRow(awayTeamId, "Mid2", 1.5);
        given(formGuideService.compute(eq(leagueId), eq(9998L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));
        // Positions close -> delta small -> draw slight increase
        List<LeagueTableEntryDTO> table = Arrays.asList(
                entry(10, homeTeamId, "Mid1", 8, 12, 0),
                entry(11, awayTeamId, "Mid2", 8, 11, -1)
        );
        given(leagueTableService.computeTableBySeasonId(leagueId, 9998L)).willReturn(table);

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "Mid1", "Mid2", true);
        int d = resp.getWinProbabilities().getDraw();
        assertTrue(d >= 20, "Draw should not be reduced and likely slightly boosted when teams are close");
        assertEquals(100, resp.getWinProbabilities().getHomeWin() + d + resp.getWinProbabilities().getAwayWin());
    }

    @Test
    void missingData_orZeroMatches_skipsAdjustment() {
        Long leagueId = 2002L; Long homeTeamId = 12L; Long awayTeamId = 22L;
        Season season = new Season(); season.setId(9997L);
        given(seasonService.findCurrentSeason(leagueId)).willReturn(Optional.of(season));
        // Make baseline asymmetric so we can verify no change due to league adj when mp=0
        FormGuideRowDTO home = baseRow(homeTeamId, "HomeFew", 2.0);
        FormGuideRowDTO away = baseRow(awayTeamId, "AwayFew", 1.0);
        given(formGuideService.compute(eq(leagueId), eq(9997L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(Arrays.asList(home, away));
        // Table entries but with zero matches -> adjustment should skip
        List<LeagueTableEntryDTO> table = Arrays.asList(
                entry(1, homeTeamId, "HomeFew", 0, 0, 0),
                entry(2, awayTeamId, "AwayFew", 0, 0, 0)
        );
        given(leagueTableService.computeTableBySeasonId(leagueId, 9997L)).willReturn(table);

        MatchAnalysisResponse resp = service.analyzeDeterministic(leagueId, homeTeamId, awayTeamId,
                "League", "HomeFew", "AwayFew", true);
        int sum = resp.getWinProbabilities().getHomeWin() + resp.getWinProbabilities().getDraw() + resp.getWinProbabilities().getAwayWin();
        assertEquals(100, sum, "Probabilities must stay normalized");
        assertTrue(resp.getWinProbabilities().getHomeWin() > resp.getWinProbabilities().getAwayWin(), "Baseline form advantage remains");
    }
}

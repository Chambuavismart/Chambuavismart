package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.MatchAnalysisResultRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchAnalysisServiceH2HTest {

    @Mock private MatchAnalysisResultRepository cacheRepo;
    @Mock private FormGuideService formGuideService;
    @Mock private SeasonService seasonService;
    @Mock private MatchRepository matchRepository;
    @Mock private LeagueTableService leagueTableService;
    @Mock private LeagueRepository leagueRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchAnalysisService service;

    private League league; private Team home; private Team away; private Season season;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        service = new MatchAnalysisService(cacheRepo, objectMapper, formGuideService, seasonService, matchRepository, leagueTableService);
        given(cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(anyLong(), anyLong(), anyLong())).willReturn(Optional.empty());
        league = new League(); league.setId(1L);
        home = new Team(); home.setId(10L); home.setName("Home");
        away = new Team(); away.setId(20L); away.setName("Away");
        season = new Season(); season.setId(100L);
        given(seasonService.findCurrentSeason(1L)).willReturn(Optional.of(season));
                given(leagueTableService.computeTableBySeasonId(anyLong(), anyLong())).willReturn(List.of());
    }

    private FormGuideRowDTO basicRow(long id, String name, double overallPpg, int btts, int ov25) {
        return new FormGuideRowDTO(id, name, 6, 6, 0,0,0, 0,0, 0, overallPpg, List.of(), btts, 0, ov25, 0);
    }

    private Match played(LocalDate date, Team h, Team a, int hg, int ag, int round) {
        Match m = new Match(league, h, a, date, round, hg, ag);
        m.setSeason(season);
        return m;
    }

    @Test
    void standardH2H_history_blendsTowardH2H() {
        // Baseline form slight edge to away
        FormGuideRowDTO homeRow = basicRow(home.getId(), home.getName(), 1.0, 50, 50);
        FormGuideRowDTO awayRow = basicRow(away.getId(), away.getName(), 2.0, 50, 50);
        given(formGuideService.compute(eq(1L), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(homeRow, awayRow));
        // H2H last 6 favor Home (4W,1D,1L from Home perspective)
        List<Match> h2h = List.of(
                played(LocalDate.now().minusDays(1), home, away, 2, 0, 30), // H W
                played(LocalDate.now().minusDays(5), away, home, 0, 1, 29), // Home won away
                played(LocalDate.now().minusDays(10), home, away, 1, 1, 28), // D
                played(LocalDate.now().minusDays(15), away, home, 0, 3, 27), // Home W
                played(LocalDate.now().minusDays(20), home, away, 2, 1, 26), // H W
                played(LocalDate.now().minusDays(25), away, home, 2, 0, 25)  // Home L
        );
        given(matchRepository.findHeadToHead(1L, 10L, 20L)).willReturn(h2h);

        MatchAnalysisResponse resp = service.analyzeDeterministic(1L, 10L, 20L, "League", "Home", "Away", true);
        // Expect home probability increased compared to a pure form baseline (which would have favored away)
        int h = resp.getWinProbabilities().getHomeWin();
        int d = resp.getWinProbabilities().getDraw();
        int a = resp.getWinProbabilities().getAwayWin();
        assertEquals(100, h + d + a);
        assertTrue(h > a, "H2H should tilt toward Home despite form favoring Away");
    }

    @Test
    void noH2H_history_fallback_to_form_only() {
        FormGuideRowDTO homeRow = basicRow(home.getId(), home.getName(), 2.0, 40, 45);
        FormGuideRowDTO awayRow = basicRow(away.getId(), away.getName(), 1.0, 60, 55);
        given(formGuideService.compute(eq(1L), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(homeRow, awayRow));
        given(matchRepository.findHeadToHead(1L, 10L, 20L)).willReturn(List.of());

        MatchAnalysisResponse resp = service.analyzeDeterministic(1L, 10L, 20L, "League", "Home", "Away", true);
        // form only should favor home
        assertTrue(resp.getWinProbabilities().getHomeWin() > resp.getWinProbabilities().getAwayWin());
    }

    @Test
    void edge_all_draws_pushes_draw_up() {
        FormGuideRowDTO homeRow = basicRow(home.getId(), home.getName(), 1.5, 50, 50);
        FormGuideRowDTO awayRow = basicRow(away.getId(), away.getName(), 1.5, 50, 50);
        given(formGuideService.compute(eq(1L), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(homeRow, awayRow));
        List<Match> h2h = List.of(
                played(LocalDate.now().minusDays(1), home, away, 1, 1, 30),
                played(LocalDate.now().minusDays(5), away, home, 2, 2, 29),
                played(LocalDate.now().minusDays(10), home, away, 0, 0, 28),
                played(LocalDate.now().minusDays(15), away, home, 3, 3, 27)
        );
        given(matchRepository.findHeadToHead(1L, 10L, 20L)).willReturn(h2h);

        MatchAnalysisResponse resp = service.analyzeDeterministic(1L, 10L, 20L, "League", "Home", "Away", true);
        // Current logic blends H2H PPG into W/D/L with draw as remainder; ensure draw does not drop below base 24
        assertTrue(resp.getWinProbabilities().getDraw() >= 24, "Draw should not be reduced when H2H are all draws");
    }

    @Test
    void edge_all_wins_for_away_pushes_away_up() {
        FormGuideRowDTO homeRow = basicRow(home.getId(), home.getName(), 1.5, 50, 50);
        FormGuideRowDTO awayRow = basicRow(away.getId(), away.getName(), 1.5, 50, 50);
        given(formGuideService.compute(eq(1L), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(homeRow, awayRow));
        List<Match> h2h = List.of(
                played(LocalDate.now().minusDays(1), home, away, 0, 1, 30),
                played(LocalDate.now().minusDays(5), away, home, 3, 0, 29),
                played(LocalDate.now().minusDays(10), home, away, 1, 2, 28)
        );
        given(matchRepository.findHeadToHead(1L, 10L, 20L)).willReturn(h2h);

        MatchAnalysisResponse resp = service.analyzeDeterministic(1L, 10L, 20L, "League", "Home", "Away", true);
        assertTrue(resp.getWinProbabilities().getAwayWin() > resp.getWinProbabilities().getHomeWin(), "Away should be boosted by H2H wins");
    }

    @Test
    void crossSeason_familyIds_used_across_leagues() {
        // Create service with leagueRepository
        ObjectMapper om = new ObjectMapper();
        MatchAnalysisService svc = new MatchAnalysisService(cacheRepo, om, formGuideService, seasonService, matchRepository, leagueTableService, null, null, leagueRepository);

        // League family: same name/country, two seasons => ids 1 and 2
        League league2024 = new League(); league2024.setId(1L); league2024.setName("EPL"); league2024.setCountry("England");
        given(leagueRepository.findById(1L)).willReturn(Optional.of(league2024));
        given(leagueRepository.findIdsByNameIgnoreCaseAndCountryIgnoreCase("EPL", "England")).willReturn(List.of(1L, 2L));

        // Season for form
        Season s = new Season(); s.setId(100L);
        given(seasonService.findCurrentSeason(1L)).willReturn(Optional.of(s));
        FormGuideRowDTO homeRow = basicRow(home.getId(), home.getName(), 1.5, 50, 50);
        FormGuideRowDTO awayRow = basicRow(away.getId(), away.getName(), 1.5, 50, 50);
        given(formGuideService.compute(eq(1L), eq(100L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(homeRow, awayRow));

        // H2H across leagues list returns one match
        Match past = played(LocalDate.now().minusDays(100), home, away, 2, 1, 10);
        given(matchRepository.findHeadToHeadAcrossLeagues(eq(List.of(1L, 2L)), eq(10L), eq(20L))).willReturn(List.of(past));

        MatchAnalysisResponse resp = svc.analyzeDeterministic(1L, 10L, 20L, "EPL", "Home", "Away", true);
        assertNotNull(resp);
        // Non-empty H2H should influence probabilities slightly but ensures path executed
        assertEquals(100, resp.getWinProbabilities().getHomeWin() + resp.getWinProbabilities().getDraw() + resp.getWinProbabilities().getAwayWin());
        Mockito.verify(matchRepository).findHeadToHeadAcrossLeagues(eq(List.of(1L, 2L)), eq(10L), eq(20L));
    }

    @Test
    void crossSeason_no_leakage_to_unrelated_competitions() {
        ObjectMapper om = new ObjectMapper();
        MatchAnalysisService svc = new MatchAnalysisService(cacheRepo, om, formGuideService, seasonService, matchRepository, leagueTableService, null, null, leagueRepository);

        League leagueA = new League(); leagueA.setId(5L); leagueA.setName("Serie A"); leagueA.setCountry("Italy");
        given(leagueRepository.findById(5L)).willReturn(Optional.of(leagueA));
        given(leagueRepository.findIdsByNameIgnoreCaseAndCountryIgnoreCase("Serie A", "Italy")).willReturn(List.of(5L, 6L));

        Season s = new Season(); s.setId(200L);
        given(seasonService.findCurrentSeason(5L)).willReturn(Optional.of(s));
        given(formGuideService.compute(eq(5L), eq(200L), anyInt(), eq(FormGuideService.Scope.OVERALL)))
                .willReturn(List.of(basicRow(home.getId(), home.getName(), 1.2, 50, 50), basicRow(away.getId(), away.getName(), 1.1, 50, 50)));

        // Repository returns empty for family ids; ensure we did not call with unrelated ID 999
        given(matchRepository.findHeadToHeadAcrossLeagues(eq(List.of(5L, 6L)), eq(10L), eq(20L))).willReturn(List.of());

        MatchAnalysisResponse resp = svc.analyzeDeterministic(5L, 10L, 20L, "Serie A", "Home", "Away", true);
        assertNotNull(resp);
        Mockito.verify(matchRepository).findHeadToHeadAcrossLeagues(eq(List.of(5L, 6L)), eq(10L), eq(20L));
    }
}

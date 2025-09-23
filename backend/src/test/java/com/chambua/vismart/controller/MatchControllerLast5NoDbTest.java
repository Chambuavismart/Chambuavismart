package com.chambua.vismart.controller;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.service.FormGuideService;
import com.chambua.vismart.service.H2HService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for MatchController's Last-5 (H2H forms) assembly that avoids any DB access.
 *
 * This test constructs MatchController with Mockito mocks and verifies that:
 * - ID-based path is used.
 * - Global fallback path is taken for sparse season data.
 * - sourceLeague is provided without triggering lazy-loading (via projection or DTO field).
 *
 * No Spring context is started; no embedded database is created.
 */
public class MatchControllerLast5NoDbTest {

    private MatchRepository matchRepository;
    private H2HService h2hService;
    private com.chambua.vismart.config.FeatureFlags featureFlags;
    private FormGuideService formGuideService;
    private SeasonRepository seasonRepository;
    private com.chambua.vismart.service.LaTeXService laTeXService;
    private com.chambua.vismart.repository.TeamRepository teamRepository;

    private MatchController controller;

    @BeforeEach
    void setup() throws Exception {
        matchRepository = mock(MatchRepository.class);
        h2hService = mock(H2HService.class);
        featureFlags = mock(com.chambua.vismart.config.FeatureFlags.class);
        formGuideService = mock(FormGuideService.class);
        seasonRepository = mock(SeasonRepository.class);
        laTeXService = mock(com.chambua.vismart.service.LaTeXService.class);
        teamRepository = mock(com.chambua.vismart.repository.TeamRepository.class);

        controller = new MatchController(matchRepository, h2hService, featureFlags, formGuideService, seasonRepository, laTeXService);
        // Inject optional autowired fields via reflection to keep constructor minimal and avoid Spring context
        Field teamRepoField = MatchController.class.getDeclaredField("teamRepository");
        teamRepoField.setAccessible(true);
        teamRepoField.set(controller, teamRepository);
    }

    @Test
    void testGetH2HForms_IdBased_GlobalFallback_NoDbDrop() {
        Long leagueId = 2L;
        Long seasonId = 22L;
        Long homeId = 780L; // Ath Bilbao
        Long awayId = 651L; // Arsenal

        // Season with league
        Season season = new Season();
        League league = new League();
        league.setId(leagueId);
        season.setId(seasonId);
        season.setLeague(league);
        season.setName("2025/2026");
        when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));

        // Form rows: mark both as fallback, with sourceLeague pre-populated for home
        FormGuideRowDTO homeRow = new FormGuideRowDTO();
        homeRow.setTeamId(homeId);
        homeRow.setTeamName("Ath Bilbao");
        homeRow.setFallback(true);
        homeRow.setSourceLeague("La Liga");
        homeRow.setMatchesAvailable(3);
        homeRow.setLastResults(List.of("L","L","D"));
        homeRow.setPpg(0.4);
        homeRow.setW(0); homeRow.setD(1); homeRow.setL(2);
        homeRow.setMp(3); homeRow.setOver25Pct(0); homeRow.setBttsPct(0);

        FormGuideRowDTO awayRow = new FormGuideRowDTO();
        awayRow.setTeamId(awayId);
        awayRow.setTeamName("Arsenal");
        awayRow.setFallback(true);
        awayRow.setMatchesAvailable(3);
        awayRow.setLastResults(List.of("W","D","L"));
        awayRow.setPpg(1.4);
        awayRow.setW(1); awayRow.setD(1); awayRow.setL(1);
        awayRow.setMp(3); awayRow.setOver25Pct(0); awayRow.setBttsPct(0);

        when(formGuideService.computeForTeams(eq(leagueId), eq(seasonId), anyInt(), Mockito.argThat(ids -> ids != null && ids.contains(homeId) && ids.contains(awayId))))
                .thenReturn(List.of(homeRow, awayRow));

        // Season-scoped recent matches are empty to trigger fallback collection path in controller
        when(matchRepository.findRecentPlayedByTeamIdAndSeason(eq(homeId), eq(seasonId))).thenReturn(List.of());
        when(matchRepository.findRecentPlayedByTeamIdAndSeason(eq(awayId), eq(seasonId))).thenReturn(List.of());

        // Domestic recent matches (simulate 3 historical matches each in domestic leagues)
        // Stub team projections with domestic league ids and names
        com.chambua.vismart.repository.TeamRepository.TeamProjection projHome = Mockito.mock(com.chambua.vismart.repository.TeamRepository.TeamProjection.class);
        when(projHome.getLeagueId()).thenReturn(1L);
        when(projHome.getLeagueName()).thenReturn("La Liga");
        when(teamRepository.findTeamProjectionById(homeId)).thenReturn(projHome);

        com.chambua.vismart.repository.TeamRepository.TeamProjection projAway = Mockito.mock(com.chambua.vismart.repository.TeamRepository.TeamProjection.class);
        when(projAway.getLeagueId()).thenReturn(2L);
        when(projAway.getLeagueName()).thenReturn("Premier League");
        when(teamRepository.findTeamProjectionById(awayId)).thenReturn(projAway);

        when(matchRepository.findRecentPlayedByTeamIdAndLeague(eq(homeId), eq(1L))).thenReturn(fakeMatches(homeId, "Ath Bilbao", 3));
        when(matchRepository.findRecentPlayedByTeamIdAndLeague(eq(awayId), eq(2L))).thenReturn(fakeMatches(awayId, "Arsenal", 3));

        // Execute
        List<MatchController.H2HFormTeamResponse> resp = controller.getH2HForms(homeId, awayId, seasonId, null, null, null, null, false, 5);

        // Assert
        assertNotNull(resp);
        assertEquals(2, resp.size(), "Should return two team entries");

        MatchController.H2HFormTeamResponse h = resp.stream().filter(r -> "Ath Bilbao".equals(r.teamName())).findFirst().orElseThrow();
        MatchController.H2HFormTeamResponse a = resp.stream().filter(r -> "Arsenal".equals(r.teamName())).findFirst().orElseThrow();

        assertEquals("La Liga", h.sourceLeague());
        assertEquals("Premier League", a.sourceLeague());
        assertTrue(Boolean.TRUE.equals(h.last5().get("fallback")), "Home should indicate fallback");
        assertTrue(Boolean.TRUE.equals(a.last5().get("fallback")), "Away should indicate fallback");
        assertEquals(3, h.matches().size());
        assertEquals(3, a.matches().size());

        // Confirm no null pointers in seasonResolved and matchesAvailable formatting
        assertNotNull(h.seasonResolved());
        assertNotNull(h.matchesAvailable());
    }

    private List<Match> fakeMatches(Long teamId, String name, int count) {
        List<Match> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Match m = new Match();
            m.setId((long) (teamId * 100 + i));
            m.setDate(LocalDate.now().minusDays(10 + i));
            // Alternate home/away
            com.chambua.vismart.model.Team home = new com.chambua.vismart.model.Team();
            home.setName(i % 2 == 0 ? name : (name + " Opp"));
            com.chambua.vismart.model.Team away = new com.chambua.vismart.model.Team();
            away.setName(i % 2 == 0 ? (name + " Opp") : name);
            m.setHomeTeam(home);
            m.setAwayTeam(away);
            m.setHomeGoals(1 + (i % 3));
            m.setAwayGoals( (i % 2));
            list.add(m);
        }
        return list;
    }
}

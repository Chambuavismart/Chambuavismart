package com.chambua.vismart.service;

import com.chambua.vismart.config.FeatureFlags;
import com.chambua.vismart.dto.GoalDifferentialSummary;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

class H2HServiceFeatureFlagTest {
    private MatchRepository matchRepository;
    private TeamRepository teamRepository;
    private FeatureFlags featureFlags;
    private H2HService service;

    private Team h; private Team a;

    @BeforeEach
    void setup() {
        matchRepository = Mockito.mock(MatchRepository.class);
        teamRepository = Mockito.mock(TeamRepository.class);
        featureFlags = Mockito.mock(FeatureFlags.class);
        service = new H2HService(matchRepository, teamRepository, featureFlags);
        h = new Team(); h.setId(1L); h.setName("Home");
        a = new Team(); a.setId(2L); a.setName("Away");
    }

    private Match played(LocalDate d, Team home, Team away, int hg, int ag) {
        Match m = new Match();
        m.setHomeTeam(home); m.setAwayTeam(away);
        m.setDate(d); m.setRound(1);
        m.setHomeGoals(hg); m.setAwayGoals(ag);
        return m;
    }

    @Test
    void gd_disabled_when_flag_off() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(false);
        GoalDifferentialSummary gd = service.computeGoalDifferentialByNames("Home", "Away");
        assertTrue(gd.isInsufficientData());
        assertNull(gd.getAggregateGD());
    }

    @Test
    void insights_returns_limited_when_flag_off() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(false);
        String txt = service.generateInsightsText("X", "Y");
        assertEquals("Limited match history available.", txt);
    }

    @Test
    void insights_combines_when_on() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(true);
        // Prepare H2H for GD sentence
        given(teamRepository.findAllByNameOrAliasIgnoreCase(eq("Home"))).willReturn(List.of(h));
        given(teamRepository.findAllByNameOrAliasIgnoreCase(eq("Away"))).willReturn(List.of(a));
        given(matchRepository.findH2HByTeamIds(eq(1L), eq(2L))).willReturn(List.of(
                played(LocalDate.now().minusDays(1), h, a, 2,0),
                played(LocalDate.now().minusDays(3), a, h, 0,1),
                played(LocalDate.now().minusDays(5), h, a, 1,1)
        ));
        // Prepare recent matches for form/ppg for both teams
        given(matchRepository.findRecentPlayedByTeamName(eq("Home"))).willReturn(List.of(
                played(LocalDate.now().minusDays(1), h, a, 2,0),
                played(LocalDate.now().minusDays(2), a, h, 0,0)
        ));
        given(matchRepository.findRecentPlayedByTeamName(eq("Away"))).willReturn(List.of(
                played(LocalDate.now().minusDays(1), h, a, 2,0),
                played(LocalDate.now().minusDays(2), a, h, 0,0)
        ));
        String txt = service.generateInsightsText("Home", "Away");
        assertNotNull(txt);
        assertTrue(txt.contains("Home has +"));
    }

    @Test
    void gd_edge_cases_when_on() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(true);
        // Three draws -> agg=0, avg=0
        given(teamRepository.findAllByNameOrAliasIgnoreCase(eq("Home"))).willReturn(List.of(h));
        given(teamRepository.findAllByNameOrAliasIgnoreCase(eq("Away"))).willReturn(List.of(a));
        given(matchRepository.findH2HByTeamIds(eq(1L), eq(2L))).willReturn(List.of(
                played(LocalDate.now().minusDays(1), h, a, 1,1),
                played(LocalDate.now().minusDays(2), a, h, 2,2),
                played(LocalDate.now().minusDays(3), h, a, 0,0)
        ));
        GoalDifferentialSummary gd = service.computeGoalDifferentialByNames("Home","Away");
        assertFalse(gd.isInsufficientData());
        assertEquals(0, gd.getAggregateGD());
        assertEquals(0.0, gd.getAvgGD());
    }
}

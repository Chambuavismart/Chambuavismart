package com.chambua.vismart.controller;

import com.chambua.vismart.config.FeatureFlags;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.H2HService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

class MatchControllerFormFlagTest {
    private MatchRepository matchRepository;
    private H2HService h2hService;
    private FeatureFlags featureFlags;
    private MatchController controller;

    @BeforeEach
    void setup() {
        matchRepository = Mockito.mock(MatchRepository.class);
        h2hService = Mockito.mock(H2HService.class);
        featureFlags = Mockito.mock(FeatureFlags.class);
        controller = new MatchController(matchRepository, h2hService, featureFlags);
    }

    @Test
    void returns_empty_when_flag_off() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(false);
        var summary = controller.getFormByTeamName("Home");
        assertNotNull(summary);
        assertEquals(0, summary.getRecentResults().size());
        assertNotNull(summary.getPpgSeries());
        assertEquals(0, summary.getPpgSeries().size());
    }

    @Test
    void computes_when_flag_on() {
        given(featureFlags.isPredictiveH2HPhase1Enabled()).willReturn(true);
        Team h = new Team(); h.setName("Home");
        Team a = new Team(); a.setName("Opp");
        Match m1 = new Match(); m1.setHomeTeam(h); m1.setAwayTeam(a); m1.setHomeGoals(2); m1.setAwayGoals(1); m1.setDate(LocalDate.now()); m1.setRound(1);
        Match m2 = new Match(); m2.setHomeTeam(a); m2.setAwayTeam(h); m2.setHomeGoals(0); m2.setAwayGoals(0); m2.setDate(LocalDate.now().minusDays(1)); m2.setRound(1);
        given(matchRepository.findRecentPlayedByTeamName(eq("Home"))).willReturn(List.of(m1, m2));
        var summary = controller.getFormByTeamName("Home");
        assertEquals(2, summary.getRecentResults().size());
        assertEquals("W", summary.getRecentResults().get(0));
        assertNotNull(summary.getPpgSeries());
        assertTrue(summary.getPpgSeries().size() >= 1);
    }
}

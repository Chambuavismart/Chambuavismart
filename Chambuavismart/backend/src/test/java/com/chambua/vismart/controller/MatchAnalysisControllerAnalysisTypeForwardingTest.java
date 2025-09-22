package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.service.MatchAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchAnalysisController.class)
class MatchAnalysisControllerAnalysisTypeForwardingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private LeagueRepository leagueRepository;
    @MockBean private TeamRepository teamRepository;
    @MockBean private TeamAliasRepository teamAliasRepository;
    @MockBean private MatchAnalysisService matchAnalysisService;

    @Test
    void analyze_forwardsFixturesAnalysisType_toService() throws Exception {
        League league = new League();
        league.setId(61L);
        league.setName("Ligue 1");
        Mockito.when(leagueRepository.findById(61L)).thenReturn(Optional.of(league));

        MatchAnalysisResponse resp = new MatchAnalysisResponse();
        resp.setHomeTeam("Lyon");
        resp.setAwayTeam("Angers");
        resp.setLeague("Ligue 1");
        resp.setWinProbabilities(new MatchAnalysisResponse.WinProbabilities(47, 25, 28));
        resp.setBttsProbability(40);
        resp.setOver25Probability(55);
        resp.setExpectedGoals(new MatchAnalysisResponse.ExpectedGoals(1.33, 1.05));
        resp.setConfidenceScore(64);
        resp.setAdvice("Likely Over 2.5");

        Mockito.when(matchAnalysisService.analyzeDeterministic(
                Mockito.eq(61L), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq("Ligue 1"), Mockito.eq("Lyon"), Mockito.eq("Angers"), Mockito.eq(false), Mockito.eq("fixtures")
        )).thenReturn(resp);

        MatchAnalysisRequest req = new MatchAnalysisRequest();
        req.setLeagueId(61L);
        req.setHomeTeamName("Lyon");
        req.setAwayTeamName("Angers");
        req.setAnalysisType("fixtures");

        this.mockMvc.perform(post("/api/match-analysis/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam", is("Lyon")))
                .andExpect(jsonPath("$.awayTeam", is("Angers")))
                .andExpect(jsonPath("$.league", is("Ligue 1")));

        // verify forwarding of analysisType
        ArgumentCaptor<String> atCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(matchAnalysisService).analyzeDeterministic(
                Mockito.eq(61L), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq("Ligue 1"), Mockito.eq("Lyon"), Mockito.eq("Angers"), Mockito.eq(false), atCap.capture()
        );
        assertEquals("fixtures", atCap.getValue());
    }
}

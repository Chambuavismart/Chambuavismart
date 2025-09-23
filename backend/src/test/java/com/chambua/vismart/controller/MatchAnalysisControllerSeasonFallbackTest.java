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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.is;

@WebMvcTest(MatchAnalysisController.class)
class MatchAnalysisControllerSeasonFallbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private LeagueRepository leagueRepository;
    @MockBean private TeamRepository teamRepository;
    @MockBean private TeamAliasRepository teamAliasRepository;
    @MockBean private MatchAnalysisService matchAnalysisService;

    @Test
    void analyze_allowsNullSeasonId_and_usesNames() throws Exception {
        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        Mockito.when(leagueRepository.findById(39L)).thenReturn(Optional.of(league));

        MatchAnalysisResponse resp = new MatchAnalysisResponse();
        resp.setHomeTeam("Everton");
        resp.setAwayTeam("Newcastle");
        resp.setLeague("Premier League");
        MatchAnalysisResponse.WinProbabilities w = new MatchAnalysisResponse.WinProbabilities(42, 30, 28);
        resp.setWinProbabilities(w);
        resp.setBttsProbability(55);
        resp.setOver25Probability(51);
        resp.setExpectedGoals(new MatchAnalysisResponse.ExpectedGoals(1.25, 1.05));
        resp.setConfidenceScore(68);
        resp.setAdvice("Tight game; slight edge to home side.");

        Mockito.when(matchAnalysisService.analyzeDeterministic(
                Mockito.eq(39L), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq("Premier League"), Mockito.eq("Everton"), Mockito.eq("Newcastle"), Mockito.eq(false), Mockito.eq("match")
        )).thenReturn(resp);

        MatchAnalysisRequest req = new MatchAnalysisRequest();
        req.setLeagueId(39L);
        req.setHomeTeamName("Everton");
        req.setAwayTeamName("Newcastle");
        // seasonId omitted on purpose

        this.mockMvc.perform(post("/api/match-analysis/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam", is("Everton")))
                .andExpect(jsonPath("$.awayTeam", is("Newcastle")))
                .andExpect(jsonPath("$.league", is("Premier League")))
                .andExpect(jsonPath("$.winProbabilities.homeWin", is(42)))
                .andExpect(jsonPath("$.bttsProbability", is(55)))
                .andExpect(jsonPath("$.over25Probability", is(51)));
    }
}

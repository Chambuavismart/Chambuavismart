package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
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
class MatchAnalysisControllerNameResolutionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private LeagueRepository leagueRepository;
    @MockBean private TeamRepository teamRepository;
    @MockBean private TeamAliasRepository teamAliasRepository;
    @MockBean private MatchAnalysisService matchAnalysisService;

    @Test
    void analyze_idOnlyRequest_resolvesTeamNames_andCallsService() throws Exception {
        // Arrange league and teams
        League league = new League();
        league.setId(140L);
        league.setName("La Liga");
        Mockito.when(leagueRepository.findById(140L)).thenReturn(Optional.of(league));

        Team home = new Team();
        home.setId(8001L);
        home.setName("Elche");
        Team away = new Team();
        away.setId(8002L);
        away.setName("Espanyol");
        Mockito.when(teamRepository.findById(8001L)).thenReturn(Optional.of(home));
        Mockito.when(teamRepository.findById(8002L)).thenReturn(Optional.of(away));

        MatchAnalysisResponse resp = new MatchAnalysisResponse();
        resp.setHomeTeam("Elche");
        resp.setAwayTeam("Espanyol");
        resp.setLeague("La Liga");
        resp.setWinProbabilities(new MatchAnalysisResponse.WinProbabilities(37, 31, 32));
        resp.setBttsProbability(52);
        resp.setOver25Probability(49);
        resp.setExpectedGoals(new MatchAnalysisResponse.ExpectedGoals(1.12, 1.08));
        resp.setConfidenceScore(61);
        resp.setAdvice("Balanced game.");

        Mockito.when(matchAnalysisService.analyzeDeterministic(
                Mockito.eq(140L), Mockito.eq(8001L), Mockito.eq(8002L), Mockito.isNull(),
                Mockito.eq("La Liga"), Mockito.eq("Elche"), Mockito.eq("Espanyol"), Mockito.eq(false), Mockito.eq("match")
        )).thenReturn(resp);

        MatchAnalysisRequest req = new MatchAnalysisRequest();
        req.setLeagueId(140L);
        req.setHomeTeamId(8001L);
        req.setAwayTeamId(8002L);
        // names omitted on purpose

        // Act & Assert response
        this.mockMvc.perform(post("/api/match-analysis/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam", is("Elche")))
                .andExpect(jsonPath("$.awayTeam", is("Espanyol")))
                .andExpect(jsonPath("$.league", is("La Liga")));

        // Verify service invocation captured with resolved names
        ArgumentCaptor<Long> leagueIdCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> homeIdCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> awayIdCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> seasonIdCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> leagueNameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> homeNameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> awayNameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> refreshCap = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> analysisTypeCap = ArgumentCaptor.forClass(String.class);

        Mockito.verify(matchAnalysisService).analyzeDeterministic(
                leagueIdCap.capture(), homeIdCap.capture(), awayIdCap.capture(), seasonIdCap.capture(),
                leagueNameCap.capture(), homeNameCap.capture(), awayNameCap.capture(), refreshCap.capture(), analysisTypeCap.capture()
        );

        assertEquals(140L, leagueIdCap.getValue());
        assertEquals(8001L, homeIdCap.getValue());
        assertEquals(8002L, awayIdCap.getValue());
        assertEquals(null, seasonIdCap.getValue());
        assertEquals("La Liga", leagueNameCap.getValue());
        assertEquals("Elche", homeNameCap.getValue());
        assertEquals("Espanyol", awayNameCap.getValue());
        assertEquals(false, refreshCap.getValue());
        assertEquals("match", analysisTypeCap.getValue());
    }
}

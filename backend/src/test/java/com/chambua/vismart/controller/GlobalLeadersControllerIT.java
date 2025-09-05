package com.chambua.vismart.controller;

import com.chambua.vismart.model.*;
import com.chambua.vismart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GlobalLeadersControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;

    @BeforeEach
    void seed() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        seasonRepository.deleteAll();
        leagueRepository.deleteAll();

        League l = leagueRepository.save(new League("League A", "X", "2024/2025"));
        Season s = seasonRepository.save(new Season(l, "2024/2025", LocalDate.of(2024,8,1), null));
        Team a = teamRepository.save(new Team("A", l));
        Team b = teamRepository.save(new Team("B", l));

        Match m1 = new Match(l, a, b, LocalDate.of(2024,9,1), 1, 1, 1); m1.setSeason(s); m1.setStatus(MatchStatus.PLAYED); matchRepository.save(m1);
        Match m2 = new Match(l, a, b, LocalDate.of(2024,9,8), 2, 2, 3); m2.setSeason(s); m2.setStatus(MatchStatus.PLAYED); matchRepository.save(m2);
    }

    @Test
    void endpointReturnsList() throws Exception {
        mvc.perform(get("/api/global-leaders")
                        .param("category", "btts")
                        .param("limit", "5")
                        .param("minMatches", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].teamId").exists())
                .andExpect(jsonPath("$[0].teamName").exists())
                .andExpect(jsonPath("$[0].statPct").exists())
                .andExpect(jsonPath("$[0].matchesPlayed").exists())
                .andExpect(jsonPath("$[0].statCount").exists())
                .andExpect(jsonPath("$[0].rank").value(1));
    }
}

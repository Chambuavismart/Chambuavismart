package com.chambua.vismart.controller;

import com.chambua.vismart.model.*;
import com.chambua.vismart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
class AdminDiagnosticsControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private MatchRepository matchRepository;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;

    private League league;
    private Season season;
    private Team home;
    private Team away;

    @BeforeEach
    void setup() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        seasonRepository.deleteAll();
        leagueRepository.deleteAll();

        league = leagueRepository.save(new League("Test League", "AR", "2025/2026"));
        season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30)));
        home = teamRepository.save(new Team("Agropecuario", league));
        away = teamRepository.save(new Team("Gimnasia Mendoza", league));

        // Past scored but not played
        Match past = new Match(league, home, away, LocalDate.of(2025, 9, 1), 3, 1, 0);
        past.setSeason(season);
        past.setStatus(MatchStatus.SCHEDULED);
        matchRepository.save(past);

        // Future scored but not played (should not be normalized)
        Match futureNotPlayed = new Match(league, home, away, LocalDate.of(2025, 10, 1), 4, 2, 2);
        futureNotPlayed.setSeason(season);
        futureNotPlayed.setStatus(MatchStatus.SCHEDULED);
        matchRepository.save(futureNotPlayed);

        // Future with PLAYED status (anomaly)
        Match futurePlayed = new Match(league, home, away, LocalDate.of(2025, 12, 1), 8, 3, 2);
        futurePlayed.setSeason(season);
        futurePlayed.setStatus(MatchStatus.PLAYED);
        matchRepository.save(futurePlayed);
    }

    @Test
    void anomalies_and_normalize_flow() throws Exception {
        String today = "2025-09-15";
        // Anomalies before normalization
        mockMvc.perform(get("/api/admin/anomalies").param("today", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf", is(today)))
                .andExpect(jsonPath("$.scoredButNotPlayedPastCount", is(1)))
                .andExpect(jsonPath("$.playedFutureDateCount", is(1)));

        // Normalize
        mockMvc.perform(post("/api/admin/normalize").param("today", today).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", is(1)))
                .andExpect(jsonPath("$.today", is(today)));

        // Anomalies after normalization
        mockMvc.perform(get("/api/admin/anomalies").param("today", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoredButNotPlayedPastCount", is(0)))
                .andExpect(jsonPath("$.playedFutureDateCount", is(1)));
    }
}

package com.chambua.vismart.controller;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormGuideControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;

    private Long leagueId;

    @BeforeEach
    void setup() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        leagueRepository.deleteAll();
        League league = leagueRepository.save(new League("Test", "KE", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        matchRepository.saveAll(List.of(
            new Match(league, a, b, LocalDate.now().minusDays(1), 1, 1, 0),
            new Match(league, b, a, LocalDate.now(), 2, 2, 2)
        ));
        leagueId = league.getId();
    }

    @Test
    void endpointReturnsComputedRows() throws Exception {
        mockMvc.perform(get("/api/form-guide/" + leagueId)
                        .param("limit", "2")
                        .param("scope", "overall")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").exists())
                .andExpect(jsonPath("$[0].bttsPct").isNumber())
                .andExpect(jsonPath("$[0].over35Pct").isNumber());
    }

    @Test
    void endpointAcceptsLimitAll() throws Exception {
        mockMvc.perform(get("/api/form-guide/" + leagueId)
                        .param("limit", "all")
                        .param("scope", "overall")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").exists());
    }
}

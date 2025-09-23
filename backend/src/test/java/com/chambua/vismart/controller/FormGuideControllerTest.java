package com.chambua.vismart.controller;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.SeasonRepository;
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

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(controllers = FormGuideController.class)
@ActiveProfiles("test")
class FormGuideControllerTest {

    @Autowired private MockMvc mockMvc;
    @org.springframework.boot.test.mock.mockito.MockBean private com.chambua.vismart.service.FormGuideService formGuideService;

    private Long leagueId;
    private Long seasonId;

    @BeforeEach
    void setup() {
        // Use simple IDs; mock the service to avoid full Spring Boot context and DB
        leagueId = 1L;
        seasonId = 10L;
    }

    @Test
    void endpointReturnsComputedRows() throws Exception {
        // Mock service response
        java.util.List<String> seq = java.util.List.of("W", "D");
        com.chambua.vismart.dto.FormGuideRowDTO dto = new com.chambua.vismart.dto.FormGuideRowDTO(100L, "A", 2, 1, 1, 0, 3, 2, 1, 4, 2.0, seq, 55, 70, 60, 40);
        org.mockito.Mockito.when(formGuideService.compute(org.mockito.ArgumentMatchers.eq(leagueId), org.mockito.ArgumentMatchers.eq(seasonId), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(com.chambua.vismart.service.FormGuideService.Scope.OVERALL)))
                .thenReturn(java.util.List.of(dto));

        mockMvc.perform(get("/api/form-guide/" + leagueId)
                        .param("seasonId", seasonId.toString())
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
        // Mock service response for 'all'
        com.chambua.vismart.dto.FormGuideRowDTO dto = new com.chambua.vismart.dto.FormGuideRowDTO(200L, "B", 3, 1, 1, 1, 4, 4, 0, 4, 1.33, java.util.List.of("L","D","W"), 50, 60, 55, 45);
        org.mockito.Mockito.when(formGuideService.compute(org.mockito.ArgumentMatchers.eq(leagueId), org.mockito.ArgumentMatchers.eq(seasonId), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(com.chambua.vismart.service.FormGuideService.Scope.OVERALL)))
                .thenReturn(java.util.List.of(dto));

        mockMvc.perform(get("/api/form-guide/" + leagueId)
                        .param("seasonId", seasonId.toString())
                        .param("limit", "all")
                        .param("scope", "overall")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").exists());
    }
}

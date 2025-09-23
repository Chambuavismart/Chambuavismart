package com.chambua.vismart.controller;

import com.chambua.vismart.repository.CountryRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.service.MatchUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MatchUploadController.class)
class MatchUploadControllerContextsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private MatchUploadService service;
    @MockBean private LeagueRepository leagueRepository;
    @MockBean private MatchRepository matchRepository;
    @MockBean private CountryRepository countryRepository;

    @Test
    void getContexts_returnsCountriesAndCompetitions() throws Exception {
        var c1 = new com.chambua.vismart.model.Country("GB", "England");
        var c2 = new com.chambua.vismart.model.Country("KE", "Kenya");
        when(countryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/matches/upload/api/options/contexts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.countries").isArray())
                .andExpect(jsonPath("$.countries[0]").value("England"))
                .andExpect(jsonPath("$.competitions").exists())
                .andExpect(jsonPath("$.competitions.global").isArray())
                .andExpect(jsonPath("$.competitions.global[0]").value("FIFA — World Cup"));
    }
}

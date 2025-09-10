package com.chambua.vismart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArchivesImportControllerIT {

    @Autowired private MockMvc mvc;

    private MockMultipartFile loadFromProjectRoot(String filename) throws Exception {
        Path p = Path.of("C:", "Users", "Michael", "Desktop", "Chambuavismart001", filename);
        byte[] data = Files.readAllBytes(p);
        return new MockMultipartFile("file", filename, "text/csv", data);
    }

    @Test
    void listRuns_and_errors_endpoints_work_with_pagination() throws Exception {
        // Upload a CSV to create a run
        MockMultipartFile file = loadFromProjectRoot("E0.csv");
        mvc.perform(multipart("/archives/import/csv")
                        .file(file)
                        .param("competitionCode", "E0")
                        .param("season", "2024/2025")
                        .param("timezone", "Europe/London")
                        .param("provider", "football-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        // List runs (default page 0 size 20)
        mvc.perform(get("/archives/import/runs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].filename", notNullValue()))
                .andExpect(jsonPath("$[0].createdBy").exists());

        // Pagination: request size 1
        mvc.perform(get("/archives/import/runs").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Fetch errors for the first run (should be a list; may be empty for valid CSV)
        String runsJson = mvc.perform(get("/archives/import/runs"))
                .andReturn().getResponse().getContentAsString();
        // naive extract first id
        ObjectMapper om = new ObjectMapper();
        Long runId = om.readTree(runsJson).get(0).get("id").asLong();

        mvc.perform(get("/archives/import/runs/" + runId + "/errors"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // Create a malformed CSV to ensure errors exist
        String csv = "Div,Date,HomeTeam,AwayTeam,FTHG,FTAG\nE0,03/08/24,Team A,Team B,x,2\n";
        MockMultipartFile bad = new MockMultipartFile("file", "bad.csv", "text/csv", csv.getBytes());
        String runJson2 = mvc.perform(multipart("/archives/import/csv")
                        .file(bad)
                        .param("competitionCode", "E0")
                        .param("season", "2024/2025"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long badRunId = new ObjectMapper().readTree(runJson2).get("id").asLong();

        mvc.perform(get("/archives/import/runs/" + badRunId + "/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].rowNumber").exists())
                .andExpect(jsonPath("$[0].errorMessage").exists())
                .andExpect(jsonPath("$[0].rawData").exists());
    }
}

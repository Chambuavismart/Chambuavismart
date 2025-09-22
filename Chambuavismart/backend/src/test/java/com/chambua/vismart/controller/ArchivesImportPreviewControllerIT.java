package com.chambua.vismart.controller;

import com.chambua.vismart.repository.ImportRunRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.SeasonRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArchivesImportPreviewControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private MatchRepository matchRepository;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private ImportRunRepository importRunRepository;

    private MockMultipartFile loadFromProjectRoot(String filename) throws Exception {
        Path p = Path.of("C:", "Users", "Michael", "Desktop", "Chambuavismart001", filename);
        byte[] data = Files.readAllBytes(p);
        return new MockMultipartFile("file", filename, "text/csv", data);
    }

    @Test
    void preview_valid_csv_returns_headers_and_rows_without_db_writes() throws Exception {
        long matchesBefore = matchRepository.count();
        long leaguesBefore = leagueRepository.count();
        long seasonsBefore = seasonRepository.count();
        long runsBefore = importRunRepository.count();

        MockMultipartFile file = loadFromProjectRoot("E0.csv");
        mvc.perform(multipart("/archives/import/preview").file(file)
                        .param("limit", "15"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.headers", not(empty())))
                .andExpect(jsonPath("$.headers", hasItem("Date")))
                .andExpect(jsonPath("$.headers", hasItem("HomeTeam")))
                .andExpect(jsonPath("$.rows", not(empty())))
                .andExpect(jsonPath("$.rows", hasSize(lessThanOrEqualTo(15))))
                .andExpect(jsonPath("$.rows[0].status", anyOf(is("ok"), is("warn"), is("error"))));

        assertEquals(matchesBefore, matchRepository.count(), "Matches should be unchanged after preview");
        assertEquals(leaguesBefore, leagueRepository.count(), "Leagues should be unchanged after preview");
        assertEquals(seasonsBefore, seasonRepository.count(), "Seasons should be unchanged after preview");
        assertEquals(runsBefore, importRunRepository.count(), "Import runs should be unchanged after preview");
    }

    @Test
    void preview_invalid_row_reports_error() throws Exception {
        String csv = "Div,Date,HomeTeam,AwayTeam,FTHG,FTAG,FTR\n" +
                "E0,32/13/24,Team A,Team B,1,2,H\n" + // bad date
                "E0,03/08/24,Team C,Team D,x,2,H\n"; // non-numeric goals
        MockMultipartFile bad = new MockMultipartFile("file", "bad.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/archives/import/preview").file(bad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.rows[0].status", is("error")))
                .andExpect(jsonPath("$.rows[0].reason", containsString("Unparseable date")))
                .andExpect(jsonPath("$.rows[1].status", is("error")))
                .andExpect(jsonPath("$.rows[1].reason", anyOf(containsString("Non-numeric goals"), containsString("NumberFormat"))));
    }
}

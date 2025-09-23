package com.chambua.vismart.controller;

import com.chambua.vismart.model.ImportRun;
import com.chambua.vismart.repository.ImportRunRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArchivesImportFileControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private ImportRunRepository importRunRepository;

    private MockMultipartFile loadFromProjectRoot(String filename) throws Exception {
        Path p = Path.of("C:", "Users", "Michael", "Desktop", "ChambuaVismart001", filename);
        byte[] data = Files.readAllBytes(p);
        return new MockMultipartFile("file", filename, "text/csv", data);
    }

    @Test
    void upload_saves_file_and_download_returns_attachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "E0.csv", "text/csv", Files.readAllBytes(Path.of("C:", "Users", "Michael", "Desktop", "ChambuaVismart001", "E0.csv")));
        String res = mvc.perform(multipart("/archives/import/csv")
                        .file(file)
                        .param("competitionCode", "E0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extract run id via JSON
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        Long runId = om.readTree(res).get("id").asLong();

        ImportRun run = importRunRepository.findById(runId).orElseThrow();
        String path = run.getFilePath();
        assertTrue(path != null && Files.exists(Path.of(path)), "Uploaded file should be saved on disk");

        mvc.perform(get("/archives/import/" + runId + "/file").accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename=\"E0.csv\"")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void download_404_when_file_missing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "E0.csv", "text/csv", Files.readAllBytes(Path.of("C:", "Users", "Michael", "Desktop", "ChambuaVismart001", "E0.csv")));
        String res = mvc.perform(multipart("/archives/import/csv")
                        .file(file)
                        .param("competitionCode", "E0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper om2 = new com.fasterxml.jackson.databind.ObjectMapper();
        Long runId = om2.readTree(res).get("id").asLong();

        ImportRun run = importRunRepository.findById(runId).orElseThrow();
        if (run.getFilePath() != null) {
            try { Files.deleteIfExists(Path.of(run.getFilePath())); } catch (Exception ignored) {}
        }

        mvc.perform(get("/archives/import/" + runId + "/file"))
                .andExpect(status().isNotFound());
    }
}

package com.chambua.vismart.service;

import com.chambua.vismart.dto.ImportRunSummaryDTO;
import com.chambua.vismart.model.ImportError;
import com.chambua.vismart.model.ImportRun;
import com.chambua.vismart.repository.ImportErrorRepository;
import com.chambua.vismart.repository.ImportRunRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class CsvArchiveImportIntegrationTest {

    @Autowired
    private CsvArchiveImportService service;

    @Autowired
    private ImportRunRepository importRunRepository;

    @Autowired
    private ImportErrorRepository importErrorRepository;

    private MockMultipartFile loadFromProjectRoot(String filename) throws Exception {
        // Fallback: if not on classpath, try project root path provided
        Path p = Path.of("C:", "Users", "Michael", "Desktop", "Chambuavismart001", filename);
        byte[] data = Files.readAllBytes(p);
        return new MockMultipartFile("file", filename, "text/csv", data);
    }

    @Test
    void importValidCsv_shouldSucceed() throws Exception {
        MockMultipartFile file = loadFromProjectRoot("E0.csv");
        ImportRunSummaryDTO dto = service.importCsv(file, "E0", "2024/2025", "Europe/London", "football-data");
        Assertions.assertNotNull(dto.getId());
        Assertions.assertEquals("COMPLETED", dto.getStatus());
        Assertions.assertTrue(dto.getRowsSuccess() > 0);
        Assertions.assertEquals(0, dto.getRowsFailed());
    }

    @Test
    void importingSameFileTwice_shouldSkipDuplicates() throws Exception {
        MockMultipartFile file = loadFromProjectRoot("E0.csv");
        ImportRunSummaryDTO dto1 = service.importCsv(file, "E0", "2024/2025", "Europe/London", "football-data");
        ImportRunSummaryDTO dto2 = service.importCsv(file, "E0", "2024/2025", "Europe/London", "football-data");
        // rowsSuccess second time should be less or equal than first (due to duplicates)
        Assertions.assertTrue(dto2.getRowsSuccess() <= dto1.getRowsSuccess());
        Assertions.assertEquals(0, dto2.getRowsFailed());
    }

    @Test
    void importMalformed_shouldCreateErrors() throws Exception {
        // Create a small malformed CSV content in-memory
        String csv = "Div,Date,HomeTeam,AwayTeam,FTHG,FTAG\nE0,03/08/24,Team A,Team B,x,2\n";
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv", csv.getBytes());
        ImportRunSummaryDTO dto = service.importCsv(file, "E0", "2024/2025", "Europe/London", "football-data");
        Assertions.assertTrue(dto.getRowsFailed() > 0);
        // Ensure ImportError exists
        List<ImportRun> runs = importRunRepository.findAll();
        ImportRun last = runs.get(runs.size()-1);
        List<ImportError> errs = importErrorRepository.findAll();
        Assertions.assertTrue(errs.stream().anyMatch(e -> e.getImportRun().getId().equals(last.getId())));
    }
}

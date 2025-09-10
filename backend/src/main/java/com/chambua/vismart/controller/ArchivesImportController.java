package com.chambua.vismart.controller;

import com.chambua.vismart.dto.ImportErrorDTO;
import com.chambua.vismart.dto.ImportRunSummaryDTO;
import com.chambua.vismart.model.ImportError;
import com.chambua.vismart.model.ImportRun;
import com.chambua.vismart.repository.ImportErrorRepository;
import com.chambua.vismart.repository.ImportRunRepository;
import com.chambua.vismart.service.CsvArchiveImportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping
public class ArchivesImportController {

    private final CsvArchiveImportService csvArchiveImportService;
    private final ImportRunRepository importRunRepository;
    private final ImportErrorRepository importErrorRepository;

    public ArchivesImportController(CsvArchiveImportService csvArchiveImportService,
                                    ImportRunRepository importRunRepository,
                                    ImportErrorRepository importErrorRepository) {
        this.csvArchiveImportService = csvArchiveImportService;
        this.importRunRepository = importRunRepository;
        this.importErrorRepository = importErrorRepository;
    }

    @PostMapping(value = "/archives/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportRunSummaryDTO importCsv(@RequestParam("file") MultipartFile file,
                                         @RequestParam("competitionCode") String competitionCode,
                                         @RequestParam(value = "season", required = false) String season,
                                         @RequestParam(value = "timezone", required = false) String timezone,
                                         @RequestParam(value = "provider", required = false) String provider) throws Exception {
        return csvArchiveImportService.importCsv(file, competitionCode, season, timezone, provider);
    }

    @PostMapping(value = "/archives/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public com.chambua.vismart.dto.CsvPreviewResponse previewCsv(@RequestParam("file") MultipartFile file,
                                                                 @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) throws Exception {
        return csvArchiveImportService.previewCsv(file, limit);
    }

    @GetMapping("/archives/import/runs")
    public List<ImportRunSummaryDTO> listRuns(@RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ImportRun> p = importRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
        return p.map(this::toDto).getContent();
    }

    @GetMapping("/archives/import/runs/{id}/errors")
    public List<ImportErrorDTO> listErrors(@PathVariable("id") Long id) {
        List<ImportError> errors = importErrorRepository.findByImportRunId(id);
        return errors.stream().map(e -> new ImportErrorDTO(
                e.getId(),
                e.getRowNumber(),
                e.getReason(),
                e.getPayload()
        )).toList();
    }

    @GetMapping("/archives/import/{runId}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable("runId") Long runId) {
        Optional<ImportRun> opt = importRunRepository.findById(runId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ImportRun run = opt.get();
        String pathStr = run.getFilePath();
        if (pathStr == null || pathStr.isBlank()) return ResponseEntity.notFound().build();
        File f = Path.of(pathStr).toFile();
        if (!f.exists() || !f.isFile()) return ResponseEntity.notFound().build();
        String fname = (run.getFilename() != null && !run.getFilename().isBlank()) ? run.getFilename() : f.getName();
        Resource resource = new FileSystemResource(f);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .body(resource);
    }

    private ImportRunSummaryDTO toDto(ImportRun run) {
        String competitionCode = extractParam(run.getParams(), "competitionCode");
        return new ImportRunSummaryDTO(
                run.getId(),
                run.getStatus(),
                run.getRowsTotal(),
                run.getRowsSuccess(),
                run.getRowsFailed(),
                run.getProvider(),
                competitionCode,
                run.getFilename(),
                run.getCreatedBy(),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }

    private String extractParam(String json, String key) {
        if (json == null) return null;
        String regex = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }
}

package com.chambua.vismart.controller;

import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.PersistedFixtureAnalysis;
import com.chambua.vismart.repository.PersistedFixtureAnalysisRepository;
import com.chambua.vismart.service.LaTeXService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/persisted-analyses")
@CrossOrigin(origins = "*")
public class PersistedAnalysisController {

    private final PersistedFixtureAnalysisRepository repo;
    private final LaTeXService latexService;
    private final ObjectMapper objectMapper;

    @Value("${chambua.persistedDaily.enabled:false}")
    private boolean persistedDailyEnabled;

    public PersistedAnalysisController(PersistedFixtureAnalysisRepository repo, LaTeXService latexService, ObjectMapper objectMapper) {
        this.repo = repo;
        this.latexService = latexService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/today")
    public List<MatchAnalysisResponse> getTodayAnalyses() {
        if (!persistedDailyEnabled) return java.util.Collections.emptyList();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        return repo.findByAnalysisDate(today).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/today/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> getTodayPdf(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        List<MatchAnalysisResponse> analyses = getTodayAnalyses();
        byte[] pdfBytes;
        try {
            if (analyses.isEmpty()) {
                pdfBytes = latexService.minimalMessagePdf("No persisted analyses for " + today);
            } else {
                pdfBytes = latexService.buildConsolidatedDailyReport(analyses, today);
            }
        } catch (Exception e) {
            pdfBytes = latexService.minimalMessagePdf("Failed to build PDF for " + today + ": " + e.getMessage());
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=persisted-analyses-" + today + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(new ByteArrayResource(pdfBytes));
    }

    private MatchAnalysisResponse toResponse(PersistedFixtureAnalysis analysis) {
        try {
            return objectMapper.readValue(analysis.getResultJson(), MatchAnalysisResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize analysis: " + analysis.getId(), e);
        }
    }
}

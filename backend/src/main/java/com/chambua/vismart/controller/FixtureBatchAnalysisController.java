package com.chambua.vismart.controller;

import com.chambua.vismart.batch.JobModels;
import com.chambua.vismart.service.BatchAnalysisCoordinator;
import com.chambua.vismart.service.LaTeXService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fixture-analysis/batch")
@CrossOrigin(origins = "*")
public class FixtureBatchAnalysisController {
    private static final Logger log = LoggerFactory.getLogger(FixtureBatchAnalysisController.class);

    private final BatchAnalysisCoordinator coordinator;
    private final LaTeXService laTeXService;

    // very simple per-user rate limiting for MVP (same as match-analysis path)
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private LocalDate countsDate = LocalDate.now(ZoneId.of("Africa/Nairobi"));

    public FixtureBatchAnalysisController(BatchAnalysisCoordinator coordinator, LaTeXService laTeXService) {
        this.coordinator = coordinator;
        this.laTeXService = laTeXService;
    }

    @PostMapping
    public Map<String, String> start(@RequestParam(value = "date", required = false) String dateStr,
                                     @RequestParam(value = "seasonId", required = false) Long seasonId,
                                     @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh,
                                     @RequestParam(value = "analysisMode", required = false, defaultValue = "FIXTURE") String analysisMode,
                                     @RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
        LocalDate todayEat = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        LocalDate date = (dateStr == null || dateStr.isBlank()) ? todayEat : LocalDate.parse(dateStr);

        // reset counts when day changes
        if (!countsDate.equals(todayEat)) { counts.clear(); countsDate = todayEat; }
        String key = userId != null ? userId : (ip != null ? ip : "anonymous");
        int n = counts.getOrDefault(key, 0);
        if (n >= 5) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Daily limit reached (5)");
        }
        counts.put(key, n + 1);

        log.info("Starting fixture batch job with analysisMode: {}", analysisMode);
        if ("MATCH".equalsIgnoreCase(analysisMode)) {
            throw new IllegalArgumentException("MATCH mode not supported on fixture-batch endpoint; use /api/match-analysis/batch instead.");
        }

        log.info("[FixtureBatchAnalysisController][START] dateStr={} dateResolved={} seasonId={} refresh={} userKey={}", dateStr, date, seasonId, refresh, (userId!=null?userId:(ip!=null?ip:"anonymous")));
        String jobId = coordinator.start(date, seasonId, refresh);
        log.info("[FixtureBatchAnalysisController][STARTED] jobId={}", jobId);
        return Map.of("jobId", jobId);
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> status(@PathVariable String jobId) {
        JobModels.JobMetadata m = coordinator.get(jobId);
        if (m == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        log.debug("[FixtureBatchAnalysisController][STATUS] jobId={} status={} total={} completed={} failed={} inProgress={} etaSeconds={}", jobId, m.status, m.total.get(), m.completed.get(), m.failed.get(), m.inProgress.get(), m.etaSeconds());
        return Map.of(
                "jobId", m.jobId,
                "date", m.date.toString(),
                "status", m.status.toString(),
                "total", m.total.get(),
                "completed", m.completed.get(),
                "failed", m.failed.get(),
                "inProgress", m.inProgress.get(),
                "startedAt", m.startedAt == null ? null : m.startedAt.toString(),
                "finishedAt", m.finishedAt == null ? null : m.finishedAt.toString(),
                "etaSeconds", m.etaSeconds()
        );
    }

    @GetMapping("/{jobId}/results")
    public Page<JobModels.FixtureAnalysisResult> results(@PathVariable String jobId,
                                                         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                                         @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        List<JobModels.FixtureAnalysisResult> all = coordinator.getResults(jobId);
        int total = all != null ? all.size() : 0;
        // sort by league name then kickoff
        all.sort(Comparator
                .comparing((JobModels.FixtureAnalysisResult r) -> r.leagueName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.kickoff, Comparator.nullsLast(Comparator.naturalOrder())));
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<JobModels.FixtureAnalysisResult> content = all.subList(from, to);
        log.debug("[FixtureBatchAnalysisController][RESULTS] jobId={} page={} size={} total={} returning={}..{}", jobId, page, size, total, from, to);
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    @GetMapping("/{jobId}/pdf")
    public ResponseEntity<ByteArrayResource> consolidatedPdf(@PathVariable String jobId) throws Exception {
        JobModels.JobMetadata m = coordinator.get(jobId);
        if (m == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        long t0 = System.currentTimeMillis();
        byte[] pdf = laTeXService.buildConsolidatedDailyReportFromResults(m.results, m.date);
        long dur = System.currentTimeMillis() - t0;
        log.info("[LaTeXService][ConsolidatedPdf] jobId={}, durationMs={} bytes={} items={}", jobId, dur, (pdf!=null?pdf.length:0), (m.results!=null?m.results.size():0));
        ByteArrayResource res = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chambua-leo-" + m.date + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(res);
    }

    @GetMapping("/{jobId}/zip")
    public ResponseEntity<byte[]> zip(@PathVariable String jobId) throws Exception {
        JobModels.JobMetadata m = coordinator.get(jobId);
        if (m == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        byte[] zip = laTeXService.buildZipOfPerFixturePdfs(m.results);
        log.info("[LaTeXService][Zip] jobId={} bytes={} items={}", jobId, (zip!=null?zip.length:0), (m.results!=null?m.results.size():0));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chambua-leo-" + m.date + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
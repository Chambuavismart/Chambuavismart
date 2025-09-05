package com.chambua.vismart.controller;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.service.FormGuideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/form-guide")
@CrossOrigin(origins = "*")
public class FormGuideController {

    private static final Logger log = LoggerFactory.getLogger(FormGuideController.class);

    private final FormGuideService formGuideService;

    public FormGuideController(FormGuideService formGuideService) {
        this.formGuideService = formGuideService;
    }

    @GetMapping("/{leagueId}")
    public List<FormGuideRowDTO> getFormGuide(@PathVariable Long leagueId,
                                              @RequestParam(name = "seasonId", required = false) Long seasonId,
                                              @RequestParam(name = "limit", defaultValue = "6") String limitParam,
                                              @RequestParam(name = "scope", defaultValue = "overall") String scope) {
        long start = System.currentTimeMillis();
        log.info("[FormGuide][REQ] leagueId={}, seasonId={}, limit={}, scope={}", leagueId, seasonId, limitParam, scope);
        FormGuideService.Scope s = switch (scope.toLowerCase()) {
            case "home" -> FormGuideService.Scope.HOME;
            case "away" -> FormGuideService.Scope.AWAY;
            default -> FormGuideService.Scope.OVERALL;
        };
        int limit;
        if ("all".equalsIgnoreCase(limitParam)) {
            limit = Integer.MAX_VALUE; // effectively include all matches
        } else {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (NumberFormatException ex) {
                limit = 6;
            }
        }
        if (seasonId == null) {
            // Explicitly reject missing season to avoid server error and guide client
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seasonId query parameter is required for form guide");
        }
        try {
            List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, seasonId, limit, s);
            log.info("[FormGuide][OK] leagueId={}, seasonId={}, rows={}, ms={}", leagueId, seasonId, rows.size(), (System.currentTimeMillis() - start));
            return rows;
        } catch (IllegalArgumentException ex) {
            // Map service validation errors to 400 instead of 500
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("[FormGuide][ERR] leagueId={}, seasonId={}, msg={}", leagueId, seasonId, ex.toString());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Form guide computation failed: " + ex.getMessage(), ex);
        }
    }
}

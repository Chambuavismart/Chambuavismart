package com.chambua.vismart.controller;

import com.chambua.vismart.model.Season;
import com.chambua.vismart.service.SeasonService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leagues/{leagueId}/seasons")
@CrossOrigin(origins = "*")
public class SeasonController {

    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @GetMapping
    public List<Season> list(@PathVariable Long leagueId) {
        return seasonService.listForLeague(leagueId);
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long leagueId,
                                    @RequestBody CreateSeasonRequest req) {
        if (req == null || req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "name is required"));
        }
        Season s = seasonService.createSeason(leagueId, req.name, req.startDate, req.endDate);
        return ResponseEntity.ok(s);
    }

    @PatchMapping("/{seasonId}")
    public ResponseEntity<?> update(@PathVariable Long leagueId,
                                    @PathVariable Long seasonId,
                                    @RequestBody UpdateSeasonRequest req) {
        Season s = seasonService.updateSeason(seasonId, req.name, req.startDate, req.endDate, req.metadata);
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/{seasonId}")
    public ResponseEntity<?> delete(@PathVariable Long leagueId, @PathVariable Long seasonId) {
        seasonService.deleteSeason(seasonId);
        return ResponseEntity.noContent().build();
    }

    public static class CreateSeasonRequest {
        public String name;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate endDate;
    }

    public static class UpdateSeasonRequest {
        public String name;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate endDate;
        public String metadata;
    }
}

package com.chambua.vismart.controller;

import com.chambua.vismart.service.MatchUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/matches/upload")
@CrossOrigin(origins = "*")
public class MatchUploadController {

    private final MatchUploadService service;

    public MatchUploadController(MatchUploadService service) {
        this.service = service;
    }

    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCsv(
            @RequestParam String leagueName,
            @RequestParam String country,
            @RequestParam String season,
            @RequestParam(defaultValue = "true") boolean fullReplace,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            var result = service.uploadCsv(leagueName, country, season, file, fullReplace);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "inserted", result.insertedCount(),
                    "deleted", result.deletedCount(),
                    "errors", result.errors()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    public record TextUploadRequest(String leagueName, String country, String season, String text, Boolean fullReplace) {}

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadText(@RequestBody TextUploadRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing body"));
        boolean fullReplace = req.fullReplace() == null || req.fullReplace();
        try {
            var result = service.uploadText(req.leagueName(), req.country(), req.season(), req.text(), fullReplace);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "inserted", result.insertedCount(),
                    "deleted", result.deletedCount(),
                    "errors", result.errors()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
}

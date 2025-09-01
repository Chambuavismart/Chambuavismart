package com.chambua.vismart.controller;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.service.FormGuideService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/form-guide")
@CrossOrigin(origins = "*")
public class FormGuideController {

    private final FormGuideService formGuideService;

    public FormGuideController(FormGuideService formGuideService) {
        this.formGuideService = formGuideService;
    }

    @GetMapping("/{leagueId}")
    public List<FormGuideRowDTO> getFormGuide(@PathVariable Long leagueId,
                                              @RequestParam(name = "limit", defaultValue = "6") int limit,
                                              @RequestParam(name = "scope", defaultValue = "overall") String scope) {
        FormGuideService.Scope s = switch (scope.toLowerCase()) {
            case "home" -> FormGuideService.Scope.HOME;
            case "away" -> FormGuideService.Scope.AWAY;
            default -> FormGuideService.Scope.OVERALL;
        };
        return formGuideService.compute(leagueId, limit, s);
    }
}

package com.chambua.vismart.controller;

import com.chambua.vismart.dto.QuickInsightsResponse;
import com.chambua.vismart.service.QuickInsightsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
@CrossOrigin(origins = "*")
public class QuickInsightsController {

    private final QuickInsightsService quickInsightsService;

    public QuickInsightsController(QuickInsightsService quickInsightsService) {
        this.quickInsightsService = quickInsightsService;
    }

    @GetMapping("/quick")
    public QuickInsightsResponse getQuickInsights() {
        return quickInsightsService.getQuickInsightsNext48Hours();
    }
}

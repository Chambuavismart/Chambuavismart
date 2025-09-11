package com.chambua.vismart.controller;

import com.chambua.vismart.config.FeatureFlags;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {
    private final FeatureFlags featureFlags;

    public ConfigController(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    @GetMapping("/flags")
    public Map<String, Object> getFlags() {
        return Map.of(
                "predictiveH2HPhase1Enabled", featureFlags.isPredictiveH2HPhase1Enabled()
        );
    }
}

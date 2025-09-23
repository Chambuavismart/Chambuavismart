package com.chambua.vismart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlags {
    @Value("${predictive.h2h.phase1.enabled:true}")
    private boolean predictiveH2HPhase1Enabled;

    public boolean isPredictiveH2HPhase1Enabled() {
        return predictiveH2HPhase1Enabled;
    }
}

package com.chambua.vismart.config;

import com.chambua.vismart.service.FixtureRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FixtureRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(FixtureRefreshScheduler.class);

    private final FixtureRefreshService refreshService;

    public FixtureRefreshScheduler(FixtureRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    // Every 2 minutes, update today and yesterday fixtures if results were uploaded
    @Scheduled(cron = "0 */2 * * * *")
    public void refreshRecentFixtures() {
        try {
            int cnt = refreshService.refreshTodayAndYesterday();
            if (cnt > 0) {
                log.info("Background refresh updated {} fixtures.", cnt);
            }
        } catch (Exception e) {
            log.warn("Background fixture refresh failed: {}", e.getMessage());
        }
    }
}

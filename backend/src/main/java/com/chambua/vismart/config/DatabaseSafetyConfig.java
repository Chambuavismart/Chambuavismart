package com.chambua.vismart.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Startup safety checks to prevent destructive schema operations in non-test environments.
 *
 * Applies to ALL profiles. On startup, logs active profiles, ddl-auto, and datasource URL.
 * If ddl-auto is set to create or create-drop (dangerous) and the active profiles string
 * does not contain "test" (case-insensitive), the application will abort startup by
 * throwing an IllegalStateException.
 *
 * Additionally, if the datasource URL indicates an in-memory database (mem:), a warning is logged
 * to make operators aware that data will not persist across restarts.
 */
@Configuration
public class DatabaseSafetyConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSafetyConfig.class);

    @Value("${spring.profiles.active:default}")
    private String activeProfiles;

    @Value("${spring.jpa.hibernate.ddl-auto:validate}")
    private String ddlAuto;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @PostConstruct
    public void verifyHibernateDdlAutoSafety() {
        String profiles = safeLower(activeProfiles);
        String ddl = safeLower(ddlAuto).replace('_', '-'); // normalize create_drop -> create-drop
        String dsUrl = datasourceUrl == null ? "" : datasourceUrl;

        // Visibility logs
        log.info("[DB_SAFETY] Active profiles='{}', ddl-auto='{}', datasource='{}'", activeProfiles, ddlAuto, dsUrl);

        // Hard guard: never allow create/create-drop outside test profiles
        boolean dangerous = "create".equals(ddl) || "create-drop".equals(ddl);
        boolean isTestProfile = profiles.contains("test");
        if (dangerous && !isTestProfile) {
            throw new IllegalStateException("Dangerous ddl-auto detected in non-test profile—aborting startup to prevent data loss.");
        }

        // Soft guard: warn if using in-memory DB (non-persistent)
        if (dsUrl.toLowerCase().contains("mem:")) {
            log.warn("[DB_SAFETY] In-memory database detected—data will not persist across restarts. Consider switching to file-based or persistent DB.");
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}

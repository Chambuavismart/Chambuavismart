package com.chambua.vismart.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class FlywayRepairConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                try {
                    log.info("Running Flyway repair before migrate (to clean failed migrations)");
                    flyway.repair();
                } catch (Exception ex) {
                    log.warn("Flyway repair failed or not needed: {}", ex.getMessage());
                }
                flyway.migrate();
            }
        };
    }
}

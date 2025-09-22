package com.chambua.vismart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
@SpringBootApplication
@EnableScheduling
public class ChambuaViSmartApplication {

    private static final String TZ_ID = "Africa/Nairobi";

    @PostConstruct
    public void initTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(TZ_ID));
        // Optional: log the configured server time at startup for verification
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(TZ_ID));
        String ts = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        System.out.println("[Startup] Server timezone set to " + TZ_ID + ", current time: " + ts);
    }

    public static void main(String[] args) {
        SpringApplication.run(ChambuaViSmartApplication.class, args);
    }
}


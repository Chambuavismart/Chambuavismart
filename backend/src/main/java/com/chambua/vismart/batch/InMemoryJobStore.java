package com.chambua.vismart.batch;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@EnableScheduling
public class InMemoryJobStore {

    private final Map<String, JobModels.JobMetadata> jobs = new ConcurrentHashMap<>();

    public void put(JobModels.JobMetadata meta) {
        jobs.put(meta.jobId, meta);
    }

    public JobModels.JobMetadata get(String jobId) {
        return jobs.get(jobId);
    }

    @Scheduled(cron = "0 0 * * * *") // hourly cleanup
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(cutoff));
    }
}

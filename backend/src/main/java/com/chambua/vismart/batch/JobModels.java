package com.chambua.vismart.batch;

import com.chambua.vismart.dto.MatchAnalysisResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class JobModels {

    public enum JobStatus { PENDING, RUNNING, COMPLETED, FAILED }

    public static class FixtureAnalysisResult {
        public Long fixtureId;
        public Long leagueId;
        public String leagueName;
        public String leagueCountry;
        public String kickoff; // ISO date-time string
        public String homeTeam;
        public String awayTeam;
        public boolean success;
        public String error; // standardized error text when failed
        public long durationMs;
        public boolean cacheHit;
        public MatchAnalysisResponse payload; // nullable when failed
    }

    public static class JobMetadata {
        public final String jobId;
        public final LocalDate date;
        public volatile JobStatus status = JobStatus.PENDING;
        public final AtomicInteger total = new AtomicInteger(0);
        public final AtomicInteger completed = new AtomicInteger(0);
        public final AtomicInteger failed = new AtomicInteger(0);
        public final AtomicInteger inProgress = new AtomicInteger(0);
        public Instant startedAt;
        public Instant finishedAt;
        public final List<FixtureAnalysisResult> results = Collections.synchronizedList(new ArrayList<>());
        // metrics
        public final AtomicInteger cacheHits = new AtomicInteger(0);
        public final AtomicInteger sumDurationsMs = new AtomicInteger(0);

        public JobMetadata(LocalDate date) {
            this.jobId = UUID.randomUUID().toString();
            this.date = date;
        }

        public int avgDurationMs() {
            int comp = Math.max(1, completed.get());
            return sumDurationsMs.get() / comp;
        }

        public long etaSeconds() {
            int remaining = Math.max(0, total.get() - completed.get() - failed.get());
            return Math.round((remaining * (double) Math.max(1, avgDurationMs())) / 1000.0);
        }
    }
}

package com.chambua.vismart.service;

import com.chambua.vismart.batch.InMemoryJobStore;
import com.chambua.vismart.batch.JobModels;
import com.chambua.vismart.dto.FixtureDTO;
import com.chambua.vismart.dto.LeagueFixturesResponse;
import com.chambua.vismart.dto.MatchAnalysisRequest;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.League;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class BatchAnalysisCoordinator {
    private static final Logger log = LoggerFactory.getLogger(BatchAnalysisCoordinator.class);

    private final FixtureService fixtureService;
    private final FixtureAnalysisService fixtureAnalysisService;
    private final SeasonResolutionService seasonResolutionService;
    private final InMemoryJobStore jobStore;
    private final Executor executor;
    private final com.chambua.vismart.repository.SeasonRepository seasonRepository;

    public BatchAnalysisCoordinator(FixtureService fixtureService,
                                    FixtureAnalysisService fixtureAnalysisService,
                                    SeasonResolutionService seasonResolutionService,
                                    InMemoryJobStore jobStore,
                                    ThreadPoolTaskExecutor batchAnalysisExecutor,
                                    com.chambua.vismart.repository.SeasonRepository seasonRepository) {
        this.fixtureService = fixtureService;
        this.fixtureAnalysisService = fixtureAnalysisService;
        this.seasonResolutionService = seasonResolutionService;
        this.jobStore = jobStore;
        this.executor = batchAnalysisExecutor;
        this.seasonRepository = seasonRepository;
    }

    public String start(LocalDate date, Long seasonId, boolean refresh) {
        JobModels.JobMetadata job = new JobModels.JobMetadata(date);
        job.status = JobModels.JobStatus.RUNNING;
        job.startedAt = Instant.now();
        jobStore.put(job);

        processAsync(job, seasonId, refresh);
        return job.jobId;
    }

    @Async("batchAnalysisExecutor")
    protected void processAsync(JobModels.JobMetadata job, Long seasonId, boolean refresh) {
        try {
            // Fetch fixtures for date and season (season as name)
            String seasonName = null;
            if (seasonId != null) {
                try { seasonName = seasonRepository.findById(seasonId).map(s -> s.getName()).orElse(null); } catch (Exception ignore) {}
            }
            List<Fixture> fixtures = fixtureService.getFixturesByDate(job.date, seasonName);
            log.info("[BatchAnalysis][FixtureFetch] jobId={}, date={}, count={}", job.jobId, job.date, fixtures != null ? fixtures.size() : 0);
            // Sort by league then kickoff (nulls last)
            fixtures.sort(Comparator
                    .comparing((Fixture f) -> f.getLeague() != null ? f.getLeague().getName() : null, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(f -> f.getDateTime(), Comparator.nullsLast(Comparator.naturalOrder())));

            job.total.set(fixtures.size());
            log.info("[BatchAnalysis][Job] jobId={}, total={}, completed=0, failed=0, cacheHits=0", job.jobId, job.total.get());

            final int waveSize = 20;
            List<List<Fixture>> waves = new ArrayList<>();
            for (int i = 0; i < fixtures.size(); i += waveSize) {
                waves.add(fixtures.subList(i, Math.min(i + waveSize, fixtures.size())));
            }
            log.info("[BatchAnalysis][Waves] jobId={} waves={} waveSize={}", job.jobId, waves.size(), waveSize);

            int waveIndex = 0;
            for (List<Fixture> wave : waves) {
                waveIndex++;
                log.info("[BatchAnalysis][WaveStart] jobId={} waveIndex={} waveCount={}", job.jobId, waveIndex, wave.size());
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (Fixture f : wave) {
                    Long sid = seasonId; // capture for lambda
                    futures.add(CompletableFuture.runAsync(() -> analyzeOne(job, f, sid, refresh), executor));
                }
                // Wait this wave complete before next to protect DB
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                log.info("[BatchAnalysis][WaveEnd] jobId={} waveIndex={} completed={} failed={} inProgress={}", job.jobId, waveIndex, job.completed.get(), job.failed.get(), job.inProgress.get());
            }

            job.status = JobModels.JobStatus.COMPLETED;
            job.finishedAt = Instant.now();
            log.info("[BatchAnalysis][Job] jobId={}, total={}, completed={}, failed={}, cacheHits={}",
                    job.jobId, job.total.get(), job.completed.get(), job.failed.get(), job.cacheHits.get());
        } catch (Exception e) {
            job.status = JobModels.JobStatus.FAILED;
            job.finishedAt = Instant.now();
            log.error("Batch job failed jobId={}: {}", job.jobId, e.getMessage(), e);
        }
    }

    private void analyzeOne(JobModels.JobMetadata job, Fixture f, Long seasonId, boolean refresh) {
        long t0 = System.currentTimeMillis();
        JobModels.FixtureAnalysisResult r = new JobModels.FixtureAnalysisResult();
        r.fixtureId = f.getId();
        League league = f.getLeague();
        r.leagueId = league.getId();
        r.leagueName = league.getName();
        r.leagueCountry = league.getCountry();
        r.kickoff = f.getDateTime() == null ? null : f.getDateTime().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        r.homeTeam = f.getHomeTeam();
        r.awayTeam = f.getAwayTeam();
        if (log.isDebugEnabled()) {
            log.debug("[BatchAnalysis][PerFixture] jobId={}, fixtureId={}, leagueId={}, leagueName={}, homeTeam={}, awayTeam={}",
                    job.jobId, r.fixtureId, r.leagueId, r.leagueName, r.homeTeam, r.awayTeam);
        }
        job.inProgress.incrementAndGet();
        try {
            Long sid = seasonId != null ? seasonId : seasonResolutionService.resolveSeasonId(league.getId(), league.getSeason()).orElse(null);
            if (sid == null) throw new IllegalArgumentException("Season unresolved: " + (league.getSeason() == null ? "(null)" : league.getSeason())) ;

            log.debug("[FixtureBatch][PerFixture][Params] jobId={} fixtureId={} leagueId={} seasonId={} refresh={} home='{}' away='{}'", job.jobId, r.fixtureId, league.getId(), sid, refresh, r.homeTeam, r.awayTeam);
            // Use fixture-centric analysis (delegates to deterministic engine)
            // Note: Preferred entry point is FixtureAnalysisService (advanced fixture-centric)
            // It delegates to the shared deterministic engine under the hood by design.
            log.info("[FixtureBatch][PerFixture] Analyzing fixture {} vs {} using advanced FixtureAnalysisService", r.homeTeam, r.awayTeam);
            com.chambua.vismart.dto.MatchAnalysisResponse resp = fixtureAnalysisService.analyzeFixture(
                    league.getId(), sid,
                    null, f.getHomeTeam(),
                    null, f.getAwayTeam(),
                    refresh
            );
            r.payload = resp;
            r.success = true;
            r.cacheHit = (resp != null && resp.getCacheHit() != null) ? resp.getCacheHit() : false;
            if (r.cacheHit) { job.cacheHits.incrementAndGet(); }
            if (resp != null && resp.getWinProbabilities() != null) {
                log.debug("[FixtureBatch][PerFixture][Summary] fixtureId={} HW/DW/AW={}/{}/{} BTTS={} O2.5={} xG=({},{}) adviceLen={} h2hAlpha={} cacheHit={}",
                        r.fixtureId,
                        resp.getWinProbabilities().getHomeWin(),
                        resp.getWinProbabilities().getDraw(),
                        resp.getWinProbabilities().getAwayWin(),
                        resp.getBttsProbability(),
                        resp.getOver25Probability(),
                        resp.getExpectedGoals()!=null?resp.getExpectedGoals().getHome():0.0,
                        resp.getExpectedGoals()!=null?resp.getExpectedGoals().getAway():0.0,
                        (resp.getAdvice()!=null?resp.getAdvice().length():0),
                        resp.getH2hAlpha(),
                        r.cacheHit);
            }
            r.error = null;
            job.completed.incrementAndGet();
        } catch (IllegalArgumentException iae) {
            r.success = false;
            String msg = iae.getMessage();
            if (msg != null && msg.toLowerCase().contains("team")) {
                if (msg.toLowerCase().contains("home")) msg = "Team ID not found: " + f.getHomeTeam();
                else if (msg.toLowerCase().contains("away")) msg = "Team ID not found: " + f.getAwayTeam();
            }
            if (msg != null && msg.startsWith("Skipped:")) {
                r.error = msg;
            } else {
                r.error = (msg != null ? (msg.startsWith("Failed:") ? msg : ("Failed: " + msg)) : "Failed");
            }
            job.failed.incrementAndGet();
        } catch (Exception e) {
            r.success = false;
            r.error = "Failed: " + e.getMessage();
            job.failed.incrementAndGet();
        } finally {
            r.durationMs = System.currentTimeMillis() - t0;
            job.sumDurationsMs.addAndGet((int) r.durationMs);
            job.inProgress.decrementAndGet();
            synchronized (job.results) {
                job.results.add(r);
            }
            log.info("[FixtureBatch][PerFixture] fixtureId={}, durationMs={}, cacheHit={}", r.fixtureId, r.durationMs, r.cacheHit);
        }
    }

    public JobModels.JobMetadata get(String jobId) { return jobStore.get(jobId); }

    public List<JobModels.FixtureAnalysisResult> getResults(String jobId) {
        JobModels.JobMetadata m = jobStore.get(jobId);
        if (m == null) return List.of();
        return new ArrayList<>(m.results);
    }
}

package com.chambua.vismart.service;

import com.chambua.vismart.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;

@Service
public class DataNormalizationService {
    private static final Logger log = LoggerFactory.getLogger(DataNormalizationService.class);

    private final MatchRepository matchRepository;

    @Value("${app.normalizeOnStartup:false}")
    private boolean normalizeOnStartup;

    public DataNormalizationService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public static class NormalizationResult {
        public boolean dryRun;
        public long expectedAffected;
        public java.util.List<Long> sampleIds;
        public int updatedRows;
        public String message;
    }

    /**
     * Normalizes existing matches by setting status=PLAYED for any row that has explicit scores
     * (home_goals and away_goals not null) and a match date not in the future.
     * Safety: supports dry-run and guard against mass updates.
     */
    public NormalizationResult normalizePastScoredMatches(LocalDate today, boolean dryRun, boolean confirm) {
        if (today == null) today = LocalDate.now();
        NormalizationResult res = new NormalizationResult();
        res.dryRun = dryRun;
        try {
            long total = matchRepository.count();
            long affected = matchRepository.countWithGoalsButNotPlayedPast(today);
            java.util.List<Long> sample = matchRepository.findWithGoalsButNotPlayedPast(today).stream().limit(10).map(m -> m.getId()).toList();
            res.expectedAffected = affected;
            res.sampleIds = sample;
            if (dryRun) {
                res.updatedRows = 0;
                res.message = "Dry run: no updates performed";
                log.info("[DATA_NORMALIZATION][DRY_RUN] today={}, total={}, expectedAffected={}, sampleIds={}", today, total, affected, sample);
                return res;
            }
            if (!confirm) {
                throw new IllegalStateException("Confirmation required (confirm=true) for non-dry run");
            }
            if (total > 0 && affected > (total / 2)) {
                throw new IllegalStateException("Operation affects too many rows (>50%)—manual review required.");
            }
            int updated = matchRepository.normalizeScoredPastMatches(today);
            res.updatedRows = updated;
            res.message = "Updated rows: " + updated;
            log.info("[DATA_NORMALIZATION][APPLY] today={}, total={}, affectedBefore={}, updated={}", today, total, affected, updated);
            return res;
        } catch (Exception ex) {
            res.message = "Error: " + ex.getMessage();
            log.error("[DATA_NORMALIZATION][ERROR] {}", ex.toString());
            return res;
        }
    }

    /** Backward compatible shim used by older callers. */
    public int normalizePastScoredMatches(LocalDate today) {
        NormalizationResult r = normalizePastScoredMatches(today, true, false);
        return r.updatedRows;
    }

    @PostConstruct
    public void maybeNormalizeOnStartup() {
        if (!normalizeOnStartup) {
            log.info("[DATA_NORMALIZATION] Startup normalization disabled (app.normalizeOnStartup=false). Skipping.");
            return;
        }
        LocalDate today = LocalDate.now();
        // Force dry-run even if enabled, to avoid destructive startup actions
        NormalizationResult n = normalizePastScoredMatches(today, true, false);
        log.info("[DATA_NORMALIZATION] Startup normalization executed in DRY RUN. Expected affected: {}.", n.expectedAffected);
    }
}

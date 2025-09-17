package com.chambua.vismart.service;

import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.SeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SeasonResolutionService {
    private static final Logger log = LoggerFactory.getLogger(SeasonResolutionService.class);
    private final SeasonRepository seasonRepository;

    public SeasonResolutionService(SeasonRepository seasonRepository) {
        this.seasonRepository = seasonRepository;
    }

    public Optional<Long> resolveSeasonId(Long leagueId, String seasonStr) {
        if (leagueId == null) return Optional.empty();
        if (seasonStr != null && !seasonStr.isBlank()) {
            try {
                Optional<Season> exact = seasonRepository.findByLeagueIdAndNameIgnoreCase(leagueId, seasonStr);
                if (exact.isPresent()) return exact.map(Season::getId);
            } catch (Exception ignore) { }
            log.warn("Season unresolved: {} for leagueId={}", seasonStr, leagueId);
        }
        // fallback to latest season for the league
        try {
            Optional<Season> latest = seasonRepository.findTopByLeagueIdOrderByStartDateDesc(leagueId);
            return latest.map(Season::getId);
        } catch (Exception e) {
            log.warn("Failed to resolve latest season for leagueId={}: {}", leagueId, e.getMessage());
            return Optional.empty();
        }
    }
}

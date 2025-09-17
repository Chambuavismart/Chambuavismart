package com.chambua.vismart.repository;

import com.chambua.vismart.model.PersistedFixtureAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersistedFixtureAnalysisRepository extends JpaRepository<PersistedFixtureAnalysis, Long> {
    List<PersistedFixtureAnalysis> findByAnalysisDate(LocalDate analysisDate);
    List<PersistedFixtureAnalysis> findByAnalysisDateAndLeagueId(LocalDate analysisDate, Long leagueId);
    boolean existsByAnalysisDateAndLeagueIdAndHomeTeamIdAndAwayTeamId(LocalDate analysisDate, Long leagueId, Long homeTeamId, Long awayTeamId);
    Optional<PersistedFixtureAnalysis> findFirstByAnalysisDateAndLeagueIdAndHomeTeamIdAndAwayTeamId(LocalDate analysisDate, Long leagueId, Long homeTeamId, Long awayTeamId);
}

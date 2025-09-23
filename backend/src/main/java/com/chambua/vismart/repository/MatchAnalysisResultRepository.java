package com.chambua.vismart.repository;

import com.chambua.vismart.model.MatchAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchAnalysisResultRepository extends JpaRepository<MatchAnalysisResult, Long> {
    Optional<MatchAnalysisResult> findByLeagueIdAndHomeTeamIdAndAwayTeamId(Long leagueId, Long homeTeamId, Long awayTeamId);
}

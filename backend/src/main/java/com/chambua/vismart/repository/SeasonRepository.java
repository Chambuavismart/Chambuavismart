package com.chambua.vismart.repository;

import com.chambua.vismart.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {
    List<Season> findByLeagueIdOrderByStartDateDesc(Long leagueId);
    Optional<Season> findByLeagueIdAndNameIgnoreCase(Long leagueId, String name);

    // New: fetch the latest season by start date desc for a league (more efficient than fetching the list)
    @Query("SELECT s FROM Season s WHERE s.league.id = :leagueId ORDER BY s.startDate DESC")
    Optional<Season> findTopByLeagueIdOrderByStartDateDesc(@Param("leagueId") Long leagueId);
}
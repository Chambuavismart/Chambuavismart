package com.chambua.vismart.repository;

import com.chambua.vismart.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonRepository extends JpaRepository<Season, Long> {
    List<Season> findByLeagueIdOrderByStartDateDesc(Long leagueId);
    java.util.Optional<Season> findByLeagueIdAndNameIgnoreCase(Long leagueId, String name);
}
package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    long deleteByLeague(League league);
    List<Match> findByLeagueId(Long leagueId);

    java.util.Optional<Match> findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(Long leagueId, Integer round, Long homeTeamId, Long awayTeamId);

    List<Match> findByLeagueIdAndHomeTeamIdAndAwayTeamId(Long leagueId, Long homeTeamId, Long awayTeamId);
}

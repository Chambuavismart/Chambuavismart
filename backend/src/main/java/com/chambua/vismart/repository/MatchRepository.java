package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    long deleteByLeague(League league);
    List<Match> findByLeagueId(Long leagueId);
}

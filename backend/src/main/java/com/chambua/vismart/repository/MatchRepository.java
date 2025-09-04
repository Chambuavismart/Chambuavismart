package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    long deleteByLeague(League league);
    List<Match> findByLeagueId(Long leagueId);

    java.util.Optional<Match> findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(Long leagueId, Integer round, Long homeTeamId, Long awayTeamId);

    List<Match> findByLeagueIdAndHomeTeamIdAndAwayTeamId(Long leagueId, Long homeTeamId, Long awayTeamId);

    java.util.Optional<Match> findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(Long leagueId, Long homeTeamId, Long awayTeamId, java.time.LocalDate date);

    java.util.Optional<Match> findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(Long seasonId, Long homeTeamId, Long awayTeamId, java.time.LocalDate date);

    // New status-based helpers
    long countByLeagueIdAndStatus(Long leagueId, MatchStatus status);
    long countByLeagueIdAndStatusAndDateLessThanEqual(Long leagueId, MatchStatus status, LocalDate date);

    List<Match> findByLeagueIdAndSeasonIdAndStatus(Long leagueId, Long seasonId, MatchStatus status);

    // Head-to-head across any season, both orientations, played only
    @Query("select m from Match m where m.league.id = :leagueId and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId) or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId)) order by m.date desc, m.round desc")
    List<Match> findHeadToHead(@Param("leagueId") Long leagueId, @Param("homeTeamId") Long homeTeamId, @Param("awayTeamId") Long awayTeamId);

    // Head-to-head filtered by season, both orientations, played only
    @Query("select m from Match m where m.league.id = :leagueId and m.season.id = :seasonId and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId) or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId)) order by m.date desc, m.round desc")
    List<Match> findHeadToHeadBySeason(@Param("leagueId") Long leagueId, @Param("seasonId") Long seasonId, @Param("homeTeamId") Long homeTeamId, @Param("awayTeamId") Long awayTeamId);

    // Deprecated: goal-null inference; kept temporarily for backward compatibility in transitional code paths
    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId);

    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNullAndDateLessThanEqual(Long leagueId, LocalDate date);

    @Deprecated
    List<Match> findByLeagueIdAndSeasonIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId, Long seasonId);
}

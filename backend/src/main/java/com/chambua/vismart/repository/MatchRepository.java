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

    // Archives: optional helper without league/season for imports
    java.util.Optional<Match> findByHomeTeamIdAndAwayTeamIdAndDate(Long homeTeamId, Long awayTeamId, java.time.LocalDate date);

    boolean existsByChecksum(String checksum);

    // New status-based helpers
    long countByLeagueIdAndStatus(Long leagueId, MatchStatus status);
    long countByLeagueIdAndStatusAndDateLessThanEqual(Long leagueId, MatchStatus status, LocalDate date);

    List<Match> findByLeagueIdAndSeasonIdAndStatus(Long leagueId, Long seasonId, MatchStatus status);

    // Head-to-head across any season, both orientations, played only
    @Query("select m from Match m where m.league.id = :leagueId and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId) or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId)) order by m.date desc, m.round desc")
    List<Match> findHeadToHead(@Param("leagueId") Long leagueId, @Param("homeTeamId") Long homeTeamId, @Param("awayTeamId") Long awayTeamId);

    // Cross-league family head-to-head
    @Query("select m from Match m where m.league.id in :leagueIds and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId) or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId)) order by m.date desc, m.round desc")
    List<Match> findHeadToHeadAcrossLeagues(@Param("leagueIds") List<Long> leagueIds, @Param("homeTeamId") Long homeTeamId, @Param("awayTeamId") Long awayTeamId);

    // Head-to-head filtered by season, both orientations, played only
    @Query("select m from Match m where m.league.id = :leagueId and m.season.id = :seasonId and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId) or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId)) order by m.date desc, m.round desc")
    List<Match> findHeadToHeadBySeason(@Param("leagueId") Long leagueId, @Param("seasonId") Long seasonId, @Param("homeTeamId") Long homeTeamId, @Param("awayTeamId") Long awayTeamId);

    // Head-to-head across any season for sets of possible IDs on each side (aliases/duplicates), played only
    @Query("select m from Match m where m.league.id = :leagueId and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (((m.homeTeam.id in :homeIds and m.awayTeam.id in :awayIds) or (m.homeTeam.id in :awayIds and m.awayTeam.id in :homeIds))) order by m.date desc, m.round desc")
    List<Match> findHeadToHeadByTeamSets(@Param("leagueId") Long leagueId, @Param("homeIds") List<Long> homeIds, @Param("awayIds") List<Long> awayIds);

    // Cross-league family head-to-head for sets of team IDs
    @Query("select m from Match m where m.league.id in :leagueIds and m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (((m.homeTeam.id in :homeIds and m.awayTeam.id in :awayIds) or (m.homeTeam.id in :awayIds and m.awayTeam.id in :homeIds))) order by m.date desc, m.round desc")
    List<Match> findHeadToHeadByTeamSetsAcrossLeagues(@Param("leagueIds") List<Long> leagueIds, @Param("homeIds") List<Long> homeIds, @Param("awayIds") List<Long> awayIds);

    // Deprecated: goal-null inference; kept temporarily for backward compatibility in transitional code paths
    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId);

    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNullAndDateLessThanEqual(Long leagueId, LocalDate date);

    @Deprecated
    List<Match> findByLeagueIdAndSeasonIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId, Long seasonId);

    // Global total of matches that have a non-null result (interpreted as status=PLAYED)
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED")
    long countByResultIsNotNull();
}

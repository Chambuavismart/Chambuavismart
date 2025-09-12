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
    @Query("select max(ir.finishedAt) from Match m join m.importRun ir where m.season.id = :seasonId and ir.finishedAt is not null")
    java.time.Instant findLastImportFinishedAtBySeasonId(@Param("seasonId") Long seasonId);
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

    // Distinct played pairs for suggestions filtered by text in either team name
    @Query("select distinct m.homeTeam.name, m.awayTeam.name from Match m where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and (lower(m.homeTeam.name) like lower(concat('%', :q, '%')) or lower(m.awayTeam.name) like lower(concat('%', :q, '%')))")
    List<Object[]> findDistinctPlayedPairsByNameContains(@Param("q") String q);

    // Played matches by exact team names with orientation respected
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and lower(m.homeTeam.name) = lower(:homeName) and lower(m.awayTeam.name) = lower(:awayName) order by m.date desc, m.round desc")
    List<Match> findPlayedByExactNames(@Param("homeName") String homeName, @Param("awayName") String awayName);

    // Fallback: Played matches by fuzzy team names (contains, case-insensitive), orientation respected
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and lower(m.homeTeam.name) like lower(concat('%', :homeName, '%')) and lower(m.awayTeam.name) like lower(concat('%', :awayName, '%')) order by m.date desc, m.round desc")
    List<Match> findPlayedByFuzzyNames(@Param("homeName") String homeName, @Param("awayName") String awayName);

    // H2H by team IDs (orientation respected)
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where m.homeTeam.id = :homeId and m.awayTeam.id = :awayId and (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) order by m.date desc")
    List<Match> findH2HByTeamIds(@Param("homeId") Long homeId, @Param("awayId") Long awayId);

    // New: H2H by team IDs across both orientations within a specific season, played only OR with explicit scores present; eager-loaded to avoid LazyInitialization
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where m.season.id = :seasonId and (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and ((m.homeTeam.id = :homeId and m.awayTeam.id = :awayId) or (m.homeTeam.id = :awayId and m.awayTeam.id = :homeId)) order by m.date desc, m.round desc")
    List<Match> findH2HByTeamIdsAndSeason(@Param("homeId") Long homeId, @Param("awayId") Long awayId, @Param("seasonId") Long seasonId);

    // H2H across ALL leagues and seasons by SETS of team IDs per side (orientation respected)
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and ((m.homeTeam.id in :homeIds and m.awayTeam.id in :awayIds)) order by m.date desc, m.round desc")
    List<Match> findH2HByTeamIdSetsAllLeagues(@Param("homeIds") List<Long> homeIds, @Param("awayIds") List<Long> awayIds);

    // Deprecated: goal-null inference; kept temporarily for backward compatibility in transitional code paths
    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId);

    @Deprecated
    long countByLeagueIdAndHomeGoalsNotNullAndAwayGoalsNotNullAndDateLessThanEqual(Long leagueId, LocalDate date);

    @Deprecated
    List<Match> findByLeagueIdAndSeasonIdAndHomeGoalsNotNullAndAwayGoalsNotNull(Long leagueId, Long seasonId);

    // Global total of matches that have a non-null result (interpreted as status=PLAYED) or explicit goals recorded
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)")
    long countByResultIsNotNull();

    // Native fallback: counts rows regardless of JPA mapping quirks
    @Query(value = "select count(*) from matches m where m.status = 'PLAYED' or (m.home_goals is not null and m.away_goals is not null)", nativeQuery = true)
    long countByResultIsNotNullNative();

    // Count played matches involving a given team (either home or away)
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (m.homeTeam.id = :teamId or m.awayTeam.id = :teamId)")
    long countPlayedByTeam(@Param("teamId") Long teamId);

    // Count played matches by exact team name (case-insensitive), regardless of league/season or team id duplication
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName))")
    long countPlayedByTeamName(@Param("teamName") String teamName);

    // Wins for a given team name (case-insensitive) across all leagues/seasons
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((lower(m.homeTeam.name) = lower(:teamName) and m.homeGoals > m.awayGoals) or (lower(m.awayTeam.name) = lower(:teamName) and m.awayGoals > m.homeGoals))")
    long countWinsByTeamName(@Param("teamName") String teamName);

    // Draws for a given team name (case-insensitive) across all leagues/seasons
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName)) and m.homeGoals = m.awayGoals")
    long countDrawsByTeamName(@Param("teamName") String teamName);

    // Losses are total played involving team minus wins minus draws; having a direct query for completeness
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and ((lower(m.homeTeam.name) = lower(:teamName) and m.homeGoals < m.awayGoals) or (lower(m.awayTeam.name) = lower(:teamName) and m.awayGoals < m.homeGoals))")
    long countLossesByTeamName(@Param("teamName") String teamName);

    // BTTS (both teams to score) for a given team name (case-insensitive) across all leagues/seasons
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and m.homeGoals > 0 and m.awayGoals > 0 and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName))")
    long countBttsByTeamName(@Param("teamName") String teamName);

    // Over 2.5 goals for matches involving a given team name across all leagues/seasons
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (coalesce(m.homeGoals,0) + coalesce(m.awayGoals,0)) >= 3 and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName))")
    long countOver25ByTeamName(@Param("teamName") String teamName);

    // Over 1.5 goals for matches involving a given team name across all leagues/seasons
    @Query("select count(m) from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (coalesce(m.homeGoals,0) + coalesce(m.awayGoals,0)) >= 2 and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName))")
    long countOver15ByTeamName(@Param("teamName") String teamName);

    // Last N played matches for a given team id across all leagues/seasons (most recent first)
    @Query("select m from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (m.homeTeam.id = :teamId or m.awayTeam.id = :teamId) order by m.date desc, m.round desc")
    List<Match> findRecentPlayedByTeamId(@Param("teamId") Long teamId);

    // New: Last played matches for a given team id within a specific season (most recent first)
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and m.season.id = :seasonId and (m.homeTeam.id = :teamId or m.awayTeam.id = :teamId) order by m.date desc, m.round desc")
    List<Match> findRecentPlayedByTeamIdAndSeason(@Param("teamId") Long teamId, @Param("seasonId") Long seasonId);

    // New: Last played matches for a given team id within a specific league (most recent first)
    @Query("select m from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and m.league.id = :leagueId and (m.homeTeam.id = :teamId or m.awayTeam.id = :teamId) order by m.date desc, m.round desc")
    List<Match> findRecentPlayedByTeamIdAndLeague(@Param("teamId") Long teamId, @Param("leagueId") Long leagueId);

    // Last N played matches for a given team name across all leagues/seasons (most recent first)
    @Query("select m from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName)) order by m.date desc, m.round desc")
    List<Match> findRecentPlayedByTeamName(@Param("teamName") String teamName);

    // Seasons in which a given team name has played, ordered by Season.startDate desc (nulls last), then season id desc
    @Query("select s.id from Match m join m.season s where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName)) group by s.id, s.startDate order by s.startDate desc nulls last, s.id desc")
    List<Long> findSeasonIdsForTeamNameOrdered(@Param("teamName") String teamName);

    // Last played matches for a given team name within a specific season (most recent first)
    @Query("select m from Match m where m.status = com.chambua.vismart.model.MatchStatus.PLAYED and m.season.id = :seasonId and (lower(m.homeTeam.name) = lower(:teamName) or lower(m.awayTeam.name) = lower(:teamName)) order by m.date desc, m.round desc")
    List<Match> findRecentPlayedByTeamNameAndSeason(@Param("teamName") String teamName, @Param("seasonId") Long seasonId);

    // Count H2H matches between two team names regardless of orientation (case-insensitive), played only or explicit goals present
    @Query("select count(m) from Match m where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and ((lower(m.homeTeam.name) = lower(:teamA) and lower(m.awayTeam.name) = lower(:teamB)) or (lower(m.homeTeam.name) = lower(:teamB) and lower(m.awayTeam.name) = lower(:teamA)))")
    long countH2HByNamesAnyOrientation(@Param("teamA") String teamA, @Param("teamB") String teamB);

    // List H2H matches between two team names regardless of orientation (case-insensitive), played only or explicit goals present
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam left join fetch m.season where (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) and ((lower(m.homeTeam.name) = lower(:teamA) and lower(m.awayTeam.name) = lower(:teamB)) or (lower(m.homeTeam.name) = lower(:teamB) and lower(m.awayTeam.name) = lower(:teamA))) order by m.date desc, m.round desc")
    List<Match> findH2HByNamesAnyOrientation(@Param("teamA") String teamA, @Param("teamB") String teamB);
}

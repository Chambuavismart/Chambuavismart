package com.chambua.vismart.repository;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FixtureRepository extends JpaRepository<Fixture, Long> {
    // Earliest active fixture for a given team name (exact case-insensitive), eager-loading league to avoid LAZY issues
    @Query("select f from Fixture f join fetch f.league l where (lower(trim(f.homeTeam)) = lower(trim(:name)) or lower(trim(f.awayTeam)) = lower(trim(:name))) and f.status in ('UPCOMING','LIVE') order by f.dateTime asc")
    java.util.List<Fixture> findEarliestActiveByTeamName(@Param("name") String name);
    @Query("select f from Fixture f join fetch f.league l where (lower(f.homeTeam) like lower(concat(:q,'%')) or lower(f.awayTeam) like lower(concat(:q,'%'))) and f.status in ('UPCOMING','LIVE') order by f.dateTime asc")
    List<Fixture> searchActiveByTeamPrefix(@Param("q") String q);

    @Query("select f from Fixture f join fetch f.league l where (lower(f.homeTeam) like lower(concat(:q,'%')) or lower(f.awayTeam) like lower(concat(:q,'%'))) and f.status in ('UPCOMING','LIVE') and l.season = :season order by f.dateTime asc")
    List<Fixture> searchActiveByTeamPrefixAndSeason(@Param("q") String q, @Param("season") String season);
    List<Fixture> findByLeague_IdOrderByDateTimeAsc(Long leagueId);
    List<Fixture> findByLeague_IdAndStatusInOrderByDateTimeAsc(Long leagueId, Collection<FixtureStatus> statuses);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    long deleteByLeague_Id(Long leagueId);

    @Query("select distinct f.league.id from Fixture f")
    List<Long> findDistinctLeagueIdsWithFixtures();

    @Query("select f.league.id as leagueId, count(f) as cnt from Fixture f where f.status = 'UPCOMING' group by f.league.id")
    List<Object[]> countUpcomingByLeague();

    // Fetch fixtures within a specific date range (start inclusive, end exclusive)
    List<Fixture> findByDateTimeGreaterThanEqualAndDateTimeLessThanOrderByLeague_NameAscDateTimeAsc(LocalDateTime startInclusive, LocalDateTime endExclusive);

    @Query("select f from Fixture f where f.dateTime >= :start and f.dateTime < :end and f.league.season = :season order by f.league.name asc, f.dateTime asc")
    List<Fixture> findByDateRangeAndSeason(@Param("start") LocalDateTime startInclusive,
                                           @Param("end") LocalDateTime endExclusive,
                                           @Param("season") String season);

    // Exact date match (date-only) without season filter (MySQL)
    @Query(value = "select * from fixtures f where DATE(f.date_time) = :date and f.home_team <> 'Postp' order by f.league_id asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findByDateOnly(@Param("date") LocalDate date);

    // Exact date match with season filter (via league join)
    @Query(value = "select f.* from fixtures f join leagues l on l.id = f.league_id where DATE(f.date_time) = :date and l.season = :season and f.home_team <> 'Postp' order by l.name asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findByDateOnlyAndSeason(@Param("date") LocalDate date, @Param("season") String season);

    // Active (no results yet) fixtures for exact date without season filter
    @Query(value = "select * from fixtures f where DATE(f.date_time) = :date and f.status in ('UPCOMING','LIVE') and f.home_team <> 'Postp' order by f.league_id asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findActiveByDateOnly(@Param("date") LocalDate date);

    // Active fixtures for exact date with season filter
    @Query(value = "select f.* from fixtures f join leagues l on l.id = f.league_id where DATE(f.date_time) = :date and l.season = :season and f.status in ('UPCOMING','LIVE') and f.home_team <> 'Postp' order by l.name asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findActiveByDateOnlyAndSeason(@Param("date") LocalDate date, @Param("season") String season);

    // Pending results (missing scores) for exact date without season filter
    @Query(value = "select * from fixtures f where DATE(f.date_time) = :date and (f.home_score is null or f.away_score is null) and f.home_team <> 'Postp' order by f.league_id asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findPendingResultsByDateOnly(@Param("date") LocalDate date);

    // Pending results with season filter
    @Query(value = "select f.* from fixtures f join leagues l on l.id = f.league_id where DATE(f.date_time) = :date and l.season = :season and (f.home_score is null or f.away_score is null) and f.home_team <> 'Postp' order by l.name asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findPendingResultsByDateOnlyAndSeason(@Param("date") LocalDate date, @Param("season") String season);

    // Distinct available dates between range (for calendar dots), without season filter
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f where f.date_time >= ?1 and f.date_time < ?2 order by d", nativeQuery = true)
    List<java.sql.Date> findDistinctDatesBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);

    // Distinct available dates between range for a given season (via league join)
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f join leagues l on l.id = f.league_id where f.date_time >= ?1 and f.date_time < ?2 and l.season = ?3 order by d", nativeQuery = true)
    List<java.sql.Date> findDistinctDatesBetweenForSeason(LocalDateTime startInclusive, LocalDateTime endExclusive, String season);

    // Distinct available dates with at least one active (UPCOMING/LIVE) fixture, without season
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f where f.date_time >= ?1 and f.date_time < ?2 and f.status in ('UPCOMING','LIVE') order by d", nativeQuery = true)
    List<java.sql.Date> findActiveDistinctDatesBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);

    // Distinct available dates with at least one active fixture, with season filter
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f join leagues l on l.id = f.league_id where f.date_time >= ?1 and f.date_time < ?2 and l.season = ?3 and f.status in ('UPCOMING','LIVE') order by d", nativeQuery = true)
    List<java.sql.Date> findActiveDistinctDatesBetweenForSeason(LocalDateTime startInclusive, LocalDateTime endExclusive, String season);

    // Distinct available dates that have at least one fixture missing results (scores not updated), without season
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f where f.date_time >= ?1 and f.date_time < ?2 and (f.home_score is null or f.away_score is null) order by d", nativeQuery = true)
    List<java.sql.Date> findPendingDistinctDatesBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);

    // Distinct available dates with missing results for a given season
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f join leagues l on l.id = f.league_id where f.date_time >= ?1 and f.date_time < ?2 and l.season = ?3 and (f.home_score is null or f.away_score is null) order by d", nativeQuery = true)
    List<java.sql.Date> findPendingDistinctDatesBetweenForSeason(LocalDateTime startInclusive, LocalDateTime endExclusive, String season);

    // Finder for incremental upsert
    java.util.Optional<Fixture> findByLeague_IdAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCaseAndDateTime(Long leagueId, String homeTeam, String awayTeam, java.time.LocalDateTime dateTime);
}

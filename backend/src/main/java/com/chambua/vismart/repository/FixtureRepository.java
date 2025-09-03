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
    List<Fixture> findByLeague_IdOrderByDateTimeAsc(Long leagueId);
    List<Fixture> findByLeague_IdAndStatusInOrderByDateTimeAsc(Long leagueId, Collection<FixtureStatus> statuses);

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
    @Query(value = "select * from fixtures f where DATE(f.date_time) = :date order by f.league_id asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findByDateOnly(@Param("date") LocalDate date);

    // Exact date match with season filter (via league join)
    @Query(value = "select f.* from fixtures f join leagues l on l.id = f.league_id where DATE(f.date_time) = :date and l.season = :season order by l.name asc, f.date_time asc", nativeQuery = true)
    List<Fixture> findByDateOnlyAndSeason(@Param("date") LocalDate date, @Param("season") String season);

    // Distinct available dates between range (for calendar dots), without season filter
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f where f.date_time >= ?1 and f.date_time < ?2 order by d", nativeQuery = true)
    List<java.sql.Date> findDistinctDatesBetween(LocalDateTime startInclusive, LocalDateTime endExclusive);

    // Distinct available dates between range for a given season (via league join)
    @Query(value = "select distinct DATE(f.date_time) as d from fixtures f join leagues l on l.id = f.league_id where f.date_time >= ?1 and f.date_time < ?2 and l.season = ?3 order by d", nativeQuery = true)
    List<java.sql.Date> findDistinctDatesBetweenForSeason(LocalDateTime startInclusive, LocalDateTime endExclusive, String season);

    // Finder for incremental upsert
    java.util.Optional<Fixture> findByLeague_IdAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCaseAndDateTime(Long leagueId, String homeTeam, String awayTeam, java.time.LocalDateTime dateTime);
}

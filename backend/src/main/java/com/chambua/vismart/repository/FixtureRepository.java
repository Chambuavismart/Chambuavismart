package com.chambua.vismart.repository;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}

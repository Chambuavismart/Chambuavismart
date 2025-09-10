package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByLeagueAndNameIgnoreCase(League league, String name);
    Optional<Team> findByLeagueAndNameContainingIgnoreCase(League league, String namePart);

    // LeagueId-scoped variants for service usage without loading League entity
    List<Team> findAllByLeagueIdAndNameIgnoreCase(Long leagueId, String name);
    List<Team> findAllByLeagueIdAndNameContainingIgnoreCase(Long leagueId, String namePart);
}

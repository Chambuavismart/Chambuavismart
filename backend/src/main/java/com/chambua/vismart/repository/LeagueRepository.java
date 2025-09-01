package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {
    Optional<League> findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(String name, String country, String season);
}

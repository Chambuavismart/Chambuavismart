package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {
    Optional<League> findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(String name, String country, String season);

    // League family: all seasons with same name and country (case-insensitive)
    @Query("select l.id from League l where lower(l.name) = lower(:name) and lower(l.country) = lower(:country)")
    List<Long> findIdsByNameIgnoreCaseAndCountryIgnoreCase(@Param("name") String name, @Param("country") String country);
}

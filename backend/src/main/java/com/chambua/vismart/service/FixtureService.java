package com.chambua.vismart.service;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import com.chambua.vismart.repository.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FixtureService {

    private static final Logger log = LoggerFactory.getLogger(FixtureService.class);

    private final FixtureRepository fixtureRepository;

    public FixtureService(FixtureRepository fixtureRepository) {
        this.fixtureRepository = fixtureRepository;
    }

    public List<Fixture> getFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdOrderByDateTimeAsc(leagueId);
    }

    public List<Fixture> getUpcomingFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdAndStatusInOrderByDateTimeAsc(leagueId, Arrays.asList(FixtureStatus.UPCOMING, FixtureStatus.LIVE));
    }

    public List<Fixture> saveFixtures(List<Fixture> fixtures) {
        return fixtureRepository.saveAll(fixtures);
    }

    public List<Fixture> getFixturesByDate(LocalDate date, String season) {
        List<Fixture> results;
        if (season != null && !season.isBlank()) {
            results = fixtureRepository.findByDateOnlyAndSeason(date, season.trim());
        } else {
            results = fixtureRepository.findByDateOnly(date);
        }
        if (log.isDebugEnabled()) {
            log.debug("[FixtureService] getFixturesByDate date={} season={} -> {} fixtures", date, season, results != null ? results.size() : 0);
        }
        return results;
    }

    public Set<LocalDate> getAvailableDatesForMonth(int year, int month, String season) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        List<java.sql.Date> rows = (season != null && !season.isBlank())
                ? fixtureRepository.findDistinctDatesBetweenForSeason(start, end, season.trim())
                : fixtureRepository.findDistinctDatesBetween(start, end);
        return rows.stream().map(java.sql.Date::toLocalDate).collect(Collectors.toSet());
    }
}

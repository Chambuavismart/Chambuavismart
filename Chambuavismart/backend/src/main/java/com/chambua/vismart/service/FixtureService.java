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
        // Business rule: show fixtures for a date if results have NOT been updated yet.
        // Include fixtures that are either in UPCOMING/LIVE status OR have missing scores (null home/away).
        List<Fixture> active;
        List<Fixture> pendingScores;
        if (season != null && !season.isBlank()) {
            String s = season.trim();
            active = fixtureRepository.findActiveByDateOnlyAndSeason(date, s);
            pendingScores = fixtureRepository.findPendingResultsByDateOnlyAndSeason(date, s);
        } else {
            active = fixtureRepository.findActiveByDateOnly(date);
            pendingScores = fixtureRepository.findPendingResultsByDateOnly(date);
        }
        // Merge and distinct by id while preserving order (active first, then pending)
        java.util.LinkedHashMap<Long, Fixture> map = new java.util.LinkedHashMap<>();
        if (active != null) for (Fixture f : active) map.put(f.getId(), f);
        if (pendingScores != null) for (Fixture f : pendingScores) map.putIfAbsent(f.getId(), f);
        List<Fixture> results = new java.util.ArrayList<>(map.values());
        if (log.isDebugEnabled()) {
            log.debug("[FixtureService] getFixturesByDate (active+pending) date={} season={} -> {} fixtures", date, season, results != null ? results.size() : 0);
        }
        return results;
    }

    public Set<LocalDate> getAvailableDatesForMonth(int year, int month, String season) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        List<java.sql.Date> active = (season != null && !season.isBlank())
                ? fixtureRepository.findActiveDistinctDatesBetweenForSeason(start, end, season.trim())
                : fixtureRepository.findActiveDistinctDatesBetween(start, end);
        List<java.sql.Date> pending = (season != null && !season.isBlank())
                ? fixtureRepository.findPendingDistinctDatesBetweenForSeason(start, end, season.trim())
                : fixtureRepository.findPendingDistinctDatesBetween(start, end);
        java.util.LinkedHashSet<LocalDate> out = new java.util.LinkedHashSet<>();
        if (active != null) active.forEach(d -> out.add(d.toLocalDate()));
        if (pending != null) pending.forEach(d -> out.add(d.toLocalDate()));
        return out;
    }
}

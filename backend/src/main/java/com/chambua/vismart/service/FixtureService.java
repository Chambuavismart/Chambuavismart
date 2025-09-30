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

    /**
     * Find the earliest upcoming/live fixture for a given team name using a resilient strategy.
     * 1) Try exact (case-insensitive) name match on either home or away.
     * 2) Fallback to prefix search (first 8 chars), then pick the first fixture where either side
     *    contains the team name (case-insensitive) or vice versa.
     */
    public java.util.Optional<Fixture> findEarliestActiveByTeamNameFlexible(String teamName) {
        if (teamName == null || teamName.isBlank()) return java.util.Optional.empty();
        String raw = teamName.trim();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Helper to check if fixture is strictly in the future and UPCOMING
        java.util.function.Predicate<Fixture> isUpcomingFuture = f -> {
            try {
                return f != null && f.getDateTime() != null && f.getDateTime().isAfter(now) && f.getStatus() == com.chambua.vismart.model.FixtureStatus.UPCOMING;
            } catch (Exception e) {
                return false;
            }
        };
        // Helper to check if fixture is LIVE (active now)
        java.util.function.Predicate<Fixture> isLive = f -> {
            try {
                return f != null && f.getStatus() == com.chambua.vismart.model.FixtureStatus.LIVE;
            } catch (Exception e) {
                return false;
            }
        };

        try {
            var exact = fixtureRepository.findEarliestActiveByTeamName(raw);
            if (exact != null && !exact.isEmpty()) {
                // 1) Prefer earliest UPCOMING strictly in the future
                for (Fixture f : exact) {
                    if (isUpcomingFuture.test(f)) return java.util.Optional.of(f);
                }
                // 2) Otherwise, fall back to earliest LIVE (align with Fixtures tab treating LIVE as active)
                for (Fixture f : exact) {
                    if (isLive.test(f)) return java.util.Optional.of(f);
                }
            }
        } catch (Exception ignored) {}
        try {
            String prefix = raw.length() > 8 ? raw.substring(0, 8) : raw;
            var list = fixtureRepository.searchActiveByTeamPrefix(prefix.toLowerCase());
            if (list != null && !list.isEmpty()) {
                // 1) Prefer strict-future UPCOMING with best name match
                for (Fixture f : list) {
                    if (!isUpcomingFuture.test(f)) continue;
                    String h = f.getHomeTeam();
                    String a = f.getAwayTeam();
                    if (h != null && h.equalsIgnoreCase(raw)) return java.util.Optional.of(f);
                    if (a != null && a.equalsIgnoreCase(raw)) return java.util.Optional.of(f);
                    // contains either way (handle abbreviations)
                    if (h != null && (h.toLowerCase().contains(raw.toLowerCase()) || raw.toLowerCase().contains(h.toLowerCase()))) return java.util.Optional.of(f);
                    if (a != null && (a.toLowerCase().contains(raw.toLowerCase()) || raw.toLowerCase().contains(a.toLowerCase()))) return java.util.Optional.of(f);
                }
                // 2) Fallback within prefix list: first strictly-future UPCOMING fixture
                for (Fixture f : list) {
                    if (isUpcomingFuture.test(f)) return java.util.Optional.of(f);
                }
                // 3) Final fallback: earliest LIVE with best name match
                for (Fixture f : list) {
                    if (!isLive.test(f)) continue;
                    String h = f.getHomeTeam();
                    String a = f.getAwayTeam();
                    if (h != null && h.equalsIgnoreCase(raw)) return java.util.Optional.of(f);
                    if (a != null && a.equalsIgnoreCase(raw)) return java.util.Optional.of(f);
                    if (h != null && (h.toLowerCase().contains(raw.toLowerCase()) || raw.toLowerCase().contains(h.toLowerCase()))) return java.util.Optional.of(f);
                    if (a != null && (a.toLowerCase().contains(raw.toLowerCase()) || raw.toLowerCase().contains(a.toLowerCase()))) return java.util.Optional.of(f);
                }
                // 4) As a last resort, pick the first LIVE in the list
                for (Fixture f : list) {
                    if (isLive.test(f)) return java.util.Optional.of(f);
                }
            }
        } catch (Exception ignored2) {}
        return java.util.Optional.empty();
    }

    public List<Fixture> getFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdOrderByDateTimeAsc(leagueId)
                .stream()
                .filter(f -> f.getHomeTeam() != null && !f.getHomeTeam().equalsIgnoreCase("Postp"))
                .collect(Collectors.toList());
    }

    public List<Fixture> getUpcomingFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdAndStatusInOrderByDateTimeAsc(leagueId, Arrays.asList(FixtureStatus.UPCOMING, FixtureStatus.LIVE))
                .stream()
                .filter(f -> f.getHomeTeam() != null && !f.getHomeTeam().equalsIgnoreCase("Postp"))
                .collect(Collectors.toList());
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
        java.util.LinkedHashMap<Long, Fixture> map = new java.util.LinkedHashMap<>();
        if (active != null) for (Fixture f : active) map.put(f.getId(), f);
        if (pendingScores != null) for (Fixture f : pendingScores) map.putIfAbsent(f.getId(), f);
        List<Fixture> results = new java.util.ArrayList<>(map.values());
        // Defensive filter: exclude any malformed postponed markers masquerading as team names
        results = results.stream()
                .filter(f -> f.getHomeTeam() != null && !f.getHomeTeam().equalsIgnoreCase("Postp"))
                .collect(java.util.stream.Collectors.toList());
        if (log.isDebugEnabled()) {
            log.debug("[FixtureService] getFixturesByDate (active+pending) date={} season={} -> {} fixtures (after Postp filter)", date, season, results != null ? results.size() : 0);
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

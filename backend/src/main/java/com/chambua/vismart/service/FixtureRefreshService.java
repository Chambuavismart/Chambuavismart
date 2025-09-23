package com.chambua.vismart.service;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.FixtureRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FixtureRefreshService {
    private static final Logger log = LoggerFactory.getLogger(FixtureRefreshService.class);

    private final FixtureRepository fixtureRepository;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public FixtureRefreshService(FixtureRepository fixtureRepository, MatchRepository matchRepository, TeamRepository teamRepository) {
        this.fixtureRepository = fixtureRepository;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Refresh fixtures for a specific calendar date (local DB date) by checking any uploaded match results.
     * Returns the number of fixtures updated.
     */
    @Transactional
    public int refreshByDate(LocalDate date) {
        int updated = 0;
        List<Fixture> fixtures = fixtureRepository.findByDateOnly(date);
        for (Fixture f : fixtures) {
            if (f.getStatus() == FixtureStatus.FINISHED) continue;
            League league = f.getLeague();
            Optional<Team> homeOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, f.getHomeTeam());
            Optional<Team> awayOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, f.getAwayTeam());
            if (homeOpt.isEmpty() || awayOpt.isEmpty()) {
                continue; // cannot resolve to a match row
            }
            var matchOpt = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(
                    league.getId(), homeOpt.get().getId(), awayOpt.get().getId(), date);
            if (matchOpt.isPresent()) {
                var m = matchOpt.get();
                if (m.getHomeGoals() != null && m.getAwayGoals() != null) {
                    if (!m.getHomeGoals().equals(f.getHomeScore()) || !m.getAwayGoals().equals(f.getAwayScore()) || f.getStatus() != FixtureStatus.FINISHED) {
                        f.setHomeScore(m.getHomeGoals());
                        f.setAwayScore(m.getAwayGoals());
                        f.setStatus(FixtureStatus.FINISHED);
                        fixtureRepository.save(f);
                        updated++;
                    }
                }
            }
        }
        if (updated > 0 && log.isInfoEnabled()) {
            log.info("Fixture refresh for {}: {} fixture(s) updated", date, updated);
        }
        return updated;
    }

    /**
     * Refresh all fixtures scheduled today (and optionally yesterday) to catch recent uploads.
     */
    @Transactional
    public int refreshTodayAndYesterday() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        return refreshByDate(today) + refreshByDate(yesterday);
    }

    /**
     * Refresh all UPCOMING/LIVE fixtures of a league by matching exact calendar date.
     */
    @Transactional
    public int refreshLeague(Long leagueId) {
        int updated = 0;
        List<Fixture> fixtures = fixtureRepository.findByLeague_IdAndStatusInOrderByDateTimeAsc(leagueId, java.util.List.of(FixtureStatus.UPCOMING, FixtureStatus.LIVE));
        for (Fixture f : fixtures) {
            LocalDate date = f.getDateTime().toLocalDate();
            League league = f.getLeague();
            Optional<Team> homeOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, f.getHomeTeam());
            Optional<Team> awayOpt = teamRepository.findByLeagueAndNameIgnoreCase(league, f.getAwayTeam());
            if (homeOpt.isEmpty() || awayOpt.isEmpty()) continue;
            var matchOpt = matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamIdAndDate(
                    league.getId(), homeOpt.get().getId(), awayOpt.get().getId(), date);
            if (matchOpt.isPresent()) {
                var m = matchOpt.get();
                if (m.getHomeGoals() != null && m.getAwayGoals() != null) {
                    if (!m.getHomeGoals().equals(f.getHomeScore()) || !m.getAwayGoals().equals(f.getAwayScore()) || f.getStatus() != FixtureStatus.FINISHED) {
                        f.setHomeScore(m.getHomeGoals());
                        f.setAwayScore(m.getAwayGoals());
                        f.setStatus(FixtureStatus.FINISHED);
                        fixtureRepository.save(f);
                        updated++;
                    }
                }
            }
        }
        if (updated > 0 && log.isInfoEnabled()) {
            log.info("Fixture refresh for league {}: {} fixture(s) updated", leagueId, updated);
        }
        return updated;
    }
}

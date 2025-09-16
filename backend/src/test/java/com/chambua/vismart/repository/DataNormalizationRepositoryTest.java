package com.chambua.vismart.repository;

import com.chambua.vismart.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.boot.test.context.SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@org.springframework.transaction.annotation.Transactional
class DataNormalizationRepositoryTest {

    @Autowired private MatchRepository matchRepository;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;

    @Test
    void normalizeScoredPastMatches_updatesOnlyPastOrTodayWithScores_andLeavesFutureUntouched() {
        LocalDate today = LocalDate.of(2025, 9, 15);

        League league = leagueRepository.save(new League("Test League", "AR", "2025/2026"));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30)));
        Team home = teamRepository.save(new Team("Agropecuario", league));
        Team away = teamRepository.save(new Team("Gimnasia Mendoza", league));

        // Past match with scores but status not PLAYED (should be normalized)
        Match past = new Match(league, home, away, LocalDate.of(2025, 9, 1), 3, 1, 0);
        past.setSeason(season);
        past.setStatus(MatchStatus.SCHEDULED); // force not played
        matchRepository.save(past);

        // Future match with scores and not PLAYED (should NOT be normalized)
        Match futureNotPlayed = new Match(league, home, away, LocalDate.of(2025, 10, 1), 4, 2, 2);
        futureNotPlayed.setSeason(season);
        futureNotPlayed.setStatus(MatchStatus.SCHEDULED);
        matchRepository.save(futureNotPlayed);

        // Future match with PLAYED status (anomaly)
        Match futurePlayed = new Match(league, home, away, LocalDate.of(2025, 12, 1), 8, 3, 2);
        futurePlayed.setSeason(season);
        futurePlayed.setStatus(MatchStatus.PLAYED);
        matchRepository.save(futurePlayed);

        // Pre-asserts
        assertThat(matchRepository.countWithGoalsButNotPlayedPast(today)).isEqualTo(1);
        assertThat(matchRepository.countPlayedWithFutureDate(today)).isEqualTo(1);

        int updated = matchRepository.normalizeScoredPastMatches(today);
        assertThat(updated).isEqualTo(1);

        // Reload and verify
        Match reloadedPast = matchRepository.findById(past.getId()).orElseThrow();
        Match reloadedFuture = matchRepository.findById(futureNotPlayed.getId()).orElseThrow();
        assertThat(reloadedPast.getStatus()).isEqualTo(MatchStatus.PLAYED);
        assertThat(reloadedFuture.getStatus()).isEqualTo(MatchStatus.SCHEDULED);

        // Post-assert counts
        assertThat(matchRepository.countWithGoalsButNotPlayedPast(today)).isZero();
        assertThat(matchRepository.countPlayedWithFutureDate(today)).isEqualTo(1);
    }
}

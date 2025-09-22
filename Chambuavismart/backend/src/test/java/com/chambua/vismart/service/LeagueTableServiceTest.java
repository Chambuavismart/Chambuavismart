package com.chambua.vismart.service;

import com.chambua.vismart.dto.LeagueTableEntryDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LeagueTableServiceTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private LeagueTableService leagueTableService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        leagueTableService = new LeagueTableService(matchRepository, entityManager);
    }

    @Test
    void computesTableWithStandardRules() {
        League league = leagueRepository.save(new League("Premier", "Kenya", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Team c = teamRepository.save(new Team("C", league));

        // A 2-0 B, B 1-1 C, C 3-2 A
        Season season = seasonRepository.save(new Season(league, "2024/2025", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        Match m1 = new Match(league, a, b, LocalDate.now(), 1, 2, 0); m1.setSeason(season);
        Match m2 = new Match(league, b, c, LocalDate.now(), 2, 1, 1); m2.setSeason(season);
        Match m3 = new Match(league, c, a, LocalDate.now(), 3, 3, 2); m3.setSeason(season);
        matchRepository.saveAll(List.of(m1, m2, m3));

        List<LeagueTableEntryDTO> table = leagueTableService.computeTable(league.getId());

        // Expect positions sorted by points, then GD, then GF
        assertThat(table).hasSize(3);
        // Compute expected: A: W1 L1 MP2 GF4 GA3 GD1 Pts3; B: L1 D1 MP2 GF1 GA3 GD-2 Pts1; C: W1 D1 MP2 GF4 GA3 GD1 Pts4
        LeagueTableEntryDTO first = table.get(0);
        assertThat(first.getTeamName()).isEqualTo("C");
        assertThat(first.getPts()).isEqualTo(4);
        assertThat(first.getGd()).isEqualTo(1);
        assertThat(first.getMp()).isEqualTo(2);

        LeagueTableEntryDTO second = table.get(1);
        assertThat(second.getTeamName()).isEqualTo("A");
        assertThat(second.getPts()).isEqualTo(3);

        LeagueTableEntryDTO third = table.get(2);
        assertThat(third.getTeamName()).isEqualTo("B");
        assertThat(third.getPts()).isEqualTo(1);

        // Positions 1..n
        assertThat(table.get(0).getPosition()).isEqualTo(1);
        assertThat(table.get(1).getPosition()).isEqualTo(2);
        assertThat(table.get(2).getPosition()).isEqualTo(3);
    }

    @Test
    void excludesFutureMatchesAndCountsCompletedOnes() {
        League league = leagueRepository.save(new League("Premier", "Kenya", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));

        // Past match A 1-0 B
        Season season = seasonRepository.save(new Season(league, "2024/2025", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        Match past = new Match(league, a, b, LocalDate.now().minusDays(1), 1, 1, 0); past.setSeason(season);
        // Future match should be ignored in standings until played
        Match future = new Match(league, b, a, LocalDate.now().plusDays(3), 2, 2, 2); future.setSeason(season);

        matchRepository.saveAll(List.of(past, future));

        List<LeagueTableEntryDTO> table = leagueTableService.computeTable(league.getId());

        // Only the past completed fixture should count: A gets W=1, Pts=3; B gets L=1, Pts=0; MP=1 each
        assertThat(table).hasSize(2);
        LeagueTableEntryDTO first = table.get(0);
        LeagueTableEntryDTO second = table.get(1);
        // A should be first with 3 pts, MP=1
        if (first.getTeamName().equals("A")) {
            assertThat(first.getPts()).isEqualTo(3);
            assertThat(first.getMp()).isEqualTo(1);
            assertThat(second.getTeamName()).isEqualTo("B");
            assertThat(second.getPts()).isEqualTo(0);
            assertThat(second.getMp()).isEqualTo(1);
        } else {
            // In case order differs due to secondary sort for ties (shouldn't for 3 vs 0)
            assertThat(second.getTeamName()).isEqualTo("A");
            assertThat(second.getPts()).isEqualTo(3);
            assertThat(second.getMp()).isEqualTo(1);
            assertThat(first.getTeamName()).isEqualTo("B");
            assertThat(first.getPts()).isEqualTo(0);
            assertThat(first.getMp()).isEqualTo(1);
        }
    }
}

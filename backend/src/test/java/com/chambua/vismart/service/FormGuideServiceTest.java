package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
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
@Import(FormGuideService.class)
class FormGuideServiceTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private FormGuideService formGuideService;

    @Test
    void computesFormGuideOverallWithPercentagesAndSequence() {
        League league = leagueRepository.save(new League("Premier", "Kenya", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));

        // 3 recent matches for team A (two as home/away combined overall)
        matchRepository.saveAll(List.of(
            // most recent
            new Match(league, a, b, LocalDate.now(), 3, 2, 2),  // D, BTTS, >=3
            new Match(league, b, a, LocalDate.now().minusDays(1), 2, 0, 1), // A wins 1-0, not BTTS, <2
            new Match(league, a, b, LocalDate.now().minusDays(2), 1, 0, 3)  // L, BTTS no, >=3
        ));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), 3, FormGuideService.Scope.OVERALL);
        assertThat(rows).hasSize(2);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        // A results: D, W, L → W=1,D=1,L=1 ; GF = 2+1+0 = 3 ; GA = 2+0+3 = 5 ; Pts = 1+3+0 = 4 ; PPG = 1.33
        assertThat(aRow.getW()).isEqualTo(1);
        assertThat(aRow.getD()).isEqualTo(1);
        assertThat(aRow.getL()).isEqualTo(1);
        assertThat(aRow.getGf()).isEqualTo(3);
        assertThat(aRow.getGa()).isEqualTo(5);
        assertThat(aRow.getPts()).isEqualTo(4);
        assertThat(aRow.getPpg()).isEqualTo(1.33);
        assertThat(aRow.getLastResults()).containsExactly("D","W","L");
        // percentages over 3: BTTS 1/3=33, Over1.5: two matches totals (4 and 3) + one with 1 -> 2/3=67, Over2.5: two/3=67, Over3.5: one/3=33
        assertThat(aRow.getBttsPct()).isBetween(33, 34); // rounding safe
        assertThat(aRow.getOver15Pct()).isBetween(67, 67);
        assertThat(aRow.getOver25Pct()).isBetween(67, 67);
        assertThat(aRow.getOver35Pct()).isBetween(33, 34);
    }

    @Test
    void limitThreeVsTenProducesDifferentAggregates() {
        League league = leagueRepository.save(new League("Limit League", "KE", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        // Create 10 matches alternating for team A such that totals vary
        for (int i = 0; i < 10; i++) {
            // alternate home/away and scores to produce mix of W/D/L
            boolean home = (i % 2 == 0);
            int round = i + 1;
            LocalDate date = LocalDate.now().minusDays(10 - i);
            if (home) {
                matchRepository.save(new Match(league, a, b, date, round, (i % 3), (i % 2))); // varying goals
            } else {
                matchRepository.save(new Match(league, b, a, date, round, (i % 2), (i % 3))); // reverse
            }
        }
        List<FormGuideRowDTO> last3 = formGuideService.compute(league.getId(), 3, FormGuideService.Scope.OVERALL);
        List<FormGuideRowDTO> last10 = formGuideService.compute(league.getId(), 10, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO a3 = last3.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        FormGuideRowDTO a10 = last10.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        // With different N the points and PPG should differ in general
        assertThat(a3.getPts()).isNotEqualTo(a10.getPts());
        assertThat(a3.getPpg()).isNotEqualTo(a10.getPpg());
        // And sequence length should match limits
        assertThat(a3.getLastResults().size()).isEqualTo(3);
        assertThat(a10.getLastResults().size()).isEqualTo(10);
    }

    @Test
    void homeVsAwayScopeSplitsCorrectly() {
        League league = leagueRepository.save(new League("Scope League", "KE", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        // A is strong at home (wins), weak away (loses)
        matchRepository.saveAll(List.of(
                new Match(league, a, b, LocalDate.now().minusDays(4), 1, 2, 0), // home win
                new Match(league, a, b, LocalDate.now().minusDays(3), 2, 3, 1), // home win
                new Match(league, b, a, LocalDate.now().minusDays(2), 3, 2, 0), // away loss
                new Match(league, b, a, LocalDate.now().minusDays(1), 4, 1, 0)  // away loss
        ));
        FormGuideRowDTO aHome = formGuideService.compute(league.getId(), 10, FormGuideService.Scope.HOME)
                .stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        FormGuideRowDTO aAway = formGuideService.compute(league.getId(), 10, FormGuideService.Scope.AWAY)
                .stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        // Home should have higher points than away
        assertThat(aHome.getPts()).isGreaterThan(aAway.getPts());
        // Home W should be greater than Away W
        assertThat(aHome.getW()).isGreaterThan(aAway.getW());
        // Away L should be greater than Home L
        assertThat(aAway.getL()).isGreaterThan(aHome.getL());
    }
}

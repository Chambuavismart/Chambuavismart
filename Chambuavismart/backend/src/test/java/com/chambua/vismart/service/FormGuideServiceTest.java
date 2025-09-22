package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
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
@Import(FormGuideService.class)
class FormGuideServiceTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private FormGuideService formGuideService;

    @Test
    void computesFormGuideOverallWithPercentagesAndSequence() {
        League league = leagueRepository.save(new League("Premier", "Kenya", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));

        // Create a season and assign matches to it
        var season = seasonRepository.save(new Season(league, "2024/2025", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        Match m1 = new Match(league, a, b, LocalDate.now(), 3, 2, 2); m1.setSeason(season);
        Match m2 = new Match(league, b, a, LocalDate.now().minusDays(1), 2, 0, 1); m2.setSeason(season);
        Match m3 = new Match(league, a, b, LocalDate.now().minusDays(2), 1, 0, 3); m3.setSeason(season);
        matchRepository.saveAll(List.of(m1, m2, m3));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 3, FormGuideService.Scope.OVERALL);
        assertThat(rows).hasSize(2);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        // A results: D, W, L → W=1,D=1,L=1 ; GF = 2+1+0 = 3 ; GA = 2+0+3 = 5 ; Pts = 1+3+0 = 4 ; PPG = 1.33
        assertThat(aRow.getW()).isEqualTo(1);
        assertThat(aRow.getD()).isEqualTo(1);
        assertThat(aRow.getL()).isEqualTo(1);
        assertThat(aRow.getGf()).isEqualTo(3);
        assertThat(aRow.getGa()).isEqualTo(5);
        assertThat(aRow.getPts()).isEqualTo(4);
        // Weighted PPG expected around 1.36 with weights 1, 1/2, 1/3
        assertThat(aRow.getPpg()).isBetween(1.35, 1.37);
        assertThat(aRow.getLastResults()).containsExactly("D","W","L");
        // Weighted percentages (weights 1, 1/2, 1/3): BTTS 1.0 / 1.8333 ~= 55; Over1.5 and Over2.5 ~= 73; Over3.5 ~= 55
        assertThat(aRow.getBttsPct()).isBetween(54, 56);
        assertThat(aRow.getOver15Pct()).isBetween(72, 74);
        assertThat(aRow.getOver25Pct()).isBetween(72, 74);
        assertThat(aRow.getOver35Pct()).isBetween(54, 56);
    }

    @Test
    void limitThreeVsTenProducesDifferentAggregates() {
        League league = leagueRepository.save(new League("Limit League", "KE", "2024/2025"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        var season = seasonRepository.save(new Season(league, "2024/2025", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        // Create 10 matches alternating for team A such that totals vary
        for (int i = 0; i < 10; i++) {
            // alternate home/away and scores to produce mix of W/D/L
            boolean home = (i % 2 == 0);
            int round = i + 1;
            LocalDate date = LocalDate.now().minusDays(10 - i);
            Match m;
            if (home) {
                m = new Match(league, a, b, date, round, (i % 3), (i % 2)); // varying goals
            } else {
                m = new Match(league, b, a, date, round, (i % 2), (i % 3)); // reverse
            }
            m.setSeason(season);
            matchRepository.save(m);
        }
        List<FormGuideRowDTO> last3 = formGuideService.compute(league.getId(), season.getId(), 3, FormGuideService.Scope.OVERALL);
        List<FormGuideRowDTO> last10 = formGuideService.compute(league.getId(), season.getId(), 10, FormGuideService.Scope.OVERALL);
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
        var season = seasonRepository.save(new Season(league, "2024/2025", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        Match s1 = new Match(league, a, b, LocalDate.now().minusDays(4), 1, 2, 0); s1.setSeason(season);
        Match s2 = new Match(league, a, b, LocalDate.now().minusDays(3), 2, 3, 1); s2.setSeason(season);
        Match s3 = new Match(league, b, a, LocalDate.now().minusDays(2), 3, 2, 0); s3.setSeason(season);
        Match s4 = new Match(league, b, a, LocalDate.now().minusDays(1), 4, 1, 0); s4.setSeason(season);
        matchRepository.saveAll(List.of(s1, s2, s3, s4));
        FormGuideRowDTO aHome = formGuideService.compute(league.getId(), season.getId(), 10, FormGuideService.Scope.HOME)
                .stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        FormGuideRowDTO aAway = formGuideService.compute(league.getId(), season.getId(), 10, FormGuideService.Scope.AWAY)
                .stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();
        // Home should have higher points than away
        assertThat(aHome.getPts()).isGreaterThan(aAway.getPts());
        // Home W should be greater than Away W
        assertThat(aHome.getW()).isGreaterThan(aAway.getW());
        // Away L should be greater than Home L
        assertThat(aAway.getL()).isGreaterThan(aHome.getL());
    }
}

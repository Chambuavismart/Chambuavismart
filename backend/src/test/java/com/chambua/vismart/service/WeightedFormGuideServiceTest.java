package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.SeasonRepository;
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
class WeightedFormGuideServiceTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private FormGuideService formGuideService;

    private double weight(int idx) { return 1.0 / (1 + idx); }

    private double computeWeightedWinPct(List<String> lastResults) {
        double sumW = 0.0, winW = 0.0;
        for (int i = 0; i < lastResults.size(); i++) {
            double w = weight(i);
            sumW += w;
            if ("W".equals(lastResults.get(i))) winW += w;
        }
        return sumW == 0 ? 0.0 : (winW * 100.0 / sumW);
    }

    @Test
    void allWinsRemainApproximately100PercentWinAndPpgIs3() {
        League league = leagueRepository.save(new League("WL", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));

        // Create 5 consecutive wins for A (mixed home/away), most recent first has higher weight
        for (int i = 0; i < 5; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            int round = 10 - i;
            if (i % 2 == 0) {
                Match m = new Match(league, a, b, date, round, 2, 0); m.setSeason(season); matchRepository.save(m);
            } else {
                Match m = new Match(league, b, a, date, round, 0, 1); m.setSeason(season); matchRepository.save(m);
            }
        }

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        // PPG should be 3.0 as all wins regardless of weighting
        assertThat(aRow.getPpg()).isEqualTo(3.0);
        // Weighted win% computed from sequence should be ~100%
        double wWinPct = computeWeightedWinPct(aRow.getLastResults());
        assertThat(wWinPct).isBetween(99.9, 100.0);
    }

    @Test
    void lastMatchLossSignificantlyReducesWeightedWinComparedToUnweighted() {
        League league = leagueRepository.save(new League("WL2", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));

        // Build 5-match sequence for A: L (most recent), then 4 wins
        // i=0 (most recent): loss
        Match m0 = new Match(league, a, b, LocalDate.now(), 20, 0, 1); m0.setSeason(season);
        // i=1..4: wins
        Match m1 = new Match(league, a, b, LocalDate.now().minusDays(1), 19, 2, 0); m1.setSeason(season);
        Match m2 = new Match(league, b, a, LocalDate.now().minusDays(2), 18, 0, 1); m2.setSeason(season);
        Match m3 = new Match(league, a, b, LocalDate.now().minusDays(3), 17, 3, 1); m3.setSeason(season);
        Match m4 = new Match(league, b, a, LocalDate.now().minusDays(4), 16, 0, 2); m4.setSeason(season);
        matchRepository.saveAll(List.of(m0, m1, m2, m3, m4));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        // Unweighted: 4 wins out of 5 = 80% win, PPG = (4*3)/5 = 2.4
        double unweightedWinPct = 80.0;
        double weightedWinPct = computeWeightedWinPct(aRow.getLastResults());
        // Weighted win% should be notably below 80 due to the most recent being a loss.
        assertThat(weightedWinPct).isLessThan(75.0);

        // PPG should also be below unweighted 2.4 due to weighting
        assertThat(aRow.getPpg()).isLessThan(2.4);
    }

    @Test
    void recentBttsYesStreakBoostsWeightedBttsPercent() {
        League league = leagueRepository.save(new League("WLBTTS", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));
        // Sequence for A over 5: last 2 are BTTS Yes (higher weight), first 3 are No
        matchRepository.saveAll(List.of(
                new Match(league, a, b, LocalDate.now(), 10, 2, 1), // BTTS Yes, most recent
                new Match(league, b, a, LocalDate.now().minusDays(1), 9, 1, 1), // BTTS Yes
                new Match(league, a, b, LocalDate.now().minusDays(2), 8, 2, 0), // No
                new Match(league, b, a, LocalDate.now().minusDays(3), 7, 0, 1), // No
                new Match(league, a, b, LocalDate.now().minusDays(4), 6, 3, 0)  // No
        ).stream().peek(m -> m.setSeason(season)).toList());

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        // Unweighted BTTS% = 2/5 = 40%
        assertThat(aRow.getBttsPct()).isGreaterThan(40);
    }
}

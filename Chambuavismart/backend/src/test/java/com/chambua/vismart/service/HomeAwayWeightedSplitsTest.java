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
class HomeAwayWeightedSplitsTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private FormGuideService formGuideService;

    @Test
    void caseA_homeStrongAwayPoor_weightedHomePPGHighAwayLow() {
        League league = leagueRepository.save(new League("SplitA", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));

        // 3 recent home wins for A
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, a, b, LocalDate.now(), 10, 2, 0), season),
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(1), 9, 1, 0), season),
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(2), 8, 3, 1), season)
        ));
        // Poor away form: 2 recent away losses
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(3), 7, 2, 0), season),
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(4), 6, 1, 0), season)
        ));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        assertThat(aRow.getWeightedHomePPG()).isGreaterThan(2.5); // near 3
        assertThat(aRow.getWeightedAwayPPG()).isLessThan(0.5); // near 0
    }

    @Test
    void caseB_highScoringAwayOnly_weightedAwayGoalsForHighHomeLow() {
        League league = leagueRepository.save(new League("SplitB", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));

        // Recent high-scoring away matches for A
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, b, a, LocalDate.now(), 12, 2, 3), season),
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(1), 11, 1, 4), season),
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(2), 10, 2, 2), season)
        ));
        // Home matches low-scoring/poor
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(3), 9, 0, 1), season),
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(4), 8, 0, 0), season)
        ));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        assertThat(aRow.getWeightedAwayGoalsFor()).isGreaterThan(2.0);
        assertThat(aRow.getWeightedHomeGoalsFor()).isLessThan(0.5);
    }

    @Test
    void caseC_mixedData_homeAwayDivergeAcrossPercentsAndPpg() {
        League league = leagueRepository.save(new League("SplitC", "KE", "2025/2026"));
        Team a = teamRepository.save(new Team("A", league));
        Team b = teamRepository.save(new Team("B", league));
        Season season = seasonRepository.save(new Season(league, "2025/2026", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)));

        // Home: BTTS Yes and wins
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, a, b, LocalDate.now(), 14, 3, 2), season),
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(1), 13, 2, 1), season),
                withSeason(new Match(league, a, b, LocalDate.now().minusDays(2), 12, 1, 0), season)
        ));
        // Away: low scoring and losses
        matchRepository.saveAll(List.of(
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(3), 11, 1, 0), season), // A loses 0-1
                withSeason(new Match(league, b, a, LocalDate.now().minusDays(4), 10, 0, 0), season)  // draw 0-0
        ));

        List<FormGuideRowDTO> rows = formGuideService.compute(league.getId(), season.getId(), 5, FormGuideService.Scope.OVERALL);
        FormGuideRowDTO aRow = rows.stream().filter(r -> r.getTeamName().equals("A")).findFirst().orElseThrow();

        // Expect divergence
        assertThat(aRow.getWeightedHomePPG()).isGreaterThan(aRow.getWeightedAwayPPG());
        assertThat(aRow.getWeightedHomeBTTSPercent()).isGreaterThan(aRow.getWeightedAwayBTTSPercent());
        assertThat(aRow.getWeightedHomeOver25Percent()).isGreaterThan(aRow.getWeightedAwayOver25Percent());
    }

    private Match withSeason(Match m, Season s) { m.setSeason(s); return m; }
}

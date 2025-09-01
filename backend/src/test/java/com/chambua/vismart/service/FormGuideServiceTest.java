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
        // percentages over 3: BTTS 1/3=33, Over1.5: two matches totals (4 and 3) + one with 1 -> 2/3=67, Over2.5: two/3=67
        assertThat(aRow.getBttsPct()).isBetween(33, 34); // rounding safe
        assertThat(aRow.getOver15Pct()).isBetween(67, 67);
        assertThat(aRow.getOver25Pct()).isBetween(67, 67);
    }
}

package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.LeagueTableEntryDTO;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({LeagueTableService.class, FormGuideService.class})
class SeasonFilterIntegrationTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;

    @Autowired private LeagueTableService leagueTableService;
    @Autowired private FormGuideService formGuideService;

    @Test
    void leagueTable_filtersStrictlyBySeasonId_only() {
        League league = leagueRepository.save(new League("EPL", "England", "2025/2026"));
        Team a = teamRepository.save(new Team("Arsenal", league));
        Team b = teamRepository.save(new Team("Chelsea", league));

        Season s1 = seasonRepository.save(new Season(league, "2024/2025", LocalDate.of(2024,8,1), LocalDate.of(2025,5,31)));
        Season s2 = seasonRepository.save(new Season(league, "2025/2026", LocalDate.of(2025,8,1), LocalDate.of(2026,5,31)));

        // Two matches in S1: A beats B twice
        Match m1 = new Match(league, a, b, LocalDate.of(2024,9,1), 1, 2, 0); m1.setSeason(s1);
        Match m2 = new Match(league, b, a, LocalDate.of(2024,12,1), 5, 0, 1); m2.setSeason(s1);
        // Two matches in S2: B beats A twice
        Match m3 = new Match(league, a, b, LocalDate.of(2025,9,1), 1, 0, 2); m3.setSeason(s2);
        Match m4 = new Match(league, b, a, LocalDate.of(2026,2,1), 20, 3, 1); m4.setSeason(s2);
        matchRepository.saveAll(List.of(m1, m2, m3, m4));

        // Season-specific S1
        List<LeagueTableEntryDTO> tableS1 = leagueTableService.computeTableBySeasonId(league.getId(), s1.getId());
        // A should have 2W, 6pts; B 0pts
        LeagueTableEntryDTO aS1 = tableS1.stream().filter(r -> r.getTeamName().equals("Arsenal")).findFirst().orElseThrow();
        LeagueTableEntryDTO bS1 = tableS1.stream().filter(r -> r.getTeamName().equals("Chelsea")).findFirst().orElseThrow();
        assertThat(aS1.getMp()).isEqualTo(2); assertThat(aS1.getPts()).isEqualTo(6);
        assertThat(bS1.getMp()).isEqualTo(2); assertThat(bS1.getPts()).isEqualTo(0);

        // Season-specific S2
        List<LeagueTableEntryDTO> tableS2 = leagueTableService.computeTableBySeasonId(league.getId(), s2.getId());
        LeagueTableEntryDTO aS2 = tableS2.stream().filter(r -> r.getTeamName().equals("Arsenal")).findFirst().orElseThrow();
        LeagueTableEntryDTO bS2 = tableS2.stream().filter(r -> r.getTeamName().equals("Chelsea")).findFirst().orElseThrow();
        assertThat(aS2.getMp()).isEqualTo(2); assertThat(aS2.getPts()).isEqualTo(0);
        assertThat(bS2.getMp()).isEqualTo(2); assertThat(bS2.getPts()).isEqualTo(6);

        // No combined mode: calling without seasonId must throw
        try {
            leagueTableService.computeTableBySeasonId(league.getId(), null);
            assert false : "Expected IllegalArgumentException for missing seasonId";
        } catch (IllegalArgumentException expected) { /* ok */ }
    }

    @Test
    void formGuide_filtersStrictlyBySeasonId_only() {
        League league = leagueRepository.save(new League("EPL", "England", "2025/2026"));
        Team a = teamRepository.save(new Team("Arsenal", league));
        Team b = teamRepository.save(new Team("Chelsea", league));

        Season s1 = seasonRepository.save(new Season(league, "2024/2025", LocalDate.of(2024,8,1), LocalDate.of(2025,5,31)));
        Season s2 = seasonRepository.save(new Season(league, "2025/2026", LocalDate.of(2025,8,1), LocalDate.of(2026,5,31)));

        // One match each season with opposite winners
        Match m1 = new Match(league, a, b, LocalDate.of(2024,9,1), 1, 2, 0); m1.setSeason(s1);
        Match m2 = new Match(league, a, b, LocalDate.of(2025,9,1), 1, 0, 2); m2.setSeason(s2);
        matchRepository.saveAll(List.of(m1, m2));

        // Season-specific S1 should give A 3 pts in last 10 overall, Season S2 should give B 3 pts
        List<FormGuideRowDTO> s1Rows = formGuideService.compute(league.getId(), s1.getId(), 10, FormGuideService.Scope.OVERALL);
        List<FormGuideRowDTO> s2Rows = formGuideService.compute(league.getId(), s2.getId(), 10, FormGuideService.Scope.OVERALL);

        FormGuideRowDTO aS1 = s1Rows.stream().filter(r -> r.getTeamName().equals("Arsenal")).findFirst().orElseThrow();
        FormGuideRowDTO bS1 = s1Rows.stream().filter(r -> r.getTeamName().equals("Chelsea")).findFirst().orElseThrow();
        assertThat(aS1.getPts()).isEqualTo(3); assertThat(bS1.getPts()).isEqualTo(0);

        FormGuideRowDTO aS2 = s2Rows.stream().filter(r -> r.getTeamName().equals("Arsenal")).findFirst().orElseThrow();
        FormGuideRowDTO bS2 = s2Rows.stream().filter(r -> r.getTeamName().equals("Chelsea")).findFirst().orElseThrow();
        assertThat(aS2.getPts()).isEqualTo(0); assertThat(bS2.getPts()).isEqualTo(3);

        // No combined mode: calling without seasonId must throw
        try {
            formGuideService.compute(league.getId(), 10, FormGuideService.Scope.OVERALL);
            assert false : "Expected IllegalArgumentException for missing seasonId";
        } catch (IllegalArgumentException expected) { /* ok */ }
    }
}

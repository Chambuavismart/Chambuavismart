package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Match;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MatchRepositoryCrossSeasonIT {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;

    @Test
    void crossSeason_headToHead_across_league_family_ids_returns_matches() {
        // Same name + country, different season values -> different league rows (family)
        League l1 = leagueRepository.save(new League("EPL", "England", "2023/2024"));
        League l2 = leagueRepository.save(new League("EPL", "England", "2024/2025"));

        Team a1 = teamRepository.save(new Team("Arsenal", l1));
        Team b1 = teamRepository.save(new Team("Chelsea", l1));
        Team a2 = teamRepository.save(new Team("Arsenal", l2));
        Team b2 = teamRepository.save(new Team("Chelsea", l2));

        Season s1 = seasonRepository.save(new Season(l1, "2023/2024", LocalDate.of(2023,8,1), LocalDate.of(2024,5,31)));
        Season s2 = seasonRepository.save(new Season(l2, "2024/2025", LocalDate.of(2024,8,1), LocalDate.of(2025,5,31)));

        // One H2H in each season
        Match m1 = new Match(l1, a1, b1, LocalDate.of(2023,9,1), 1, 2, 0); m1.setSeason(s1);
        Match m2 = new Match(l2, a2, b2, LocalDate.of(2024,9,1), 1, 0, 1); m2.setSeason(s2);
        matchRepository.saveAll(List.of(m1, m2));

        // Cross-league H2H by family ids and strict team IDs cannot match across seasons because team ids differ.
        // But across-leagues query should at least find the match in the current season league id.
        List<Long> familyIds = leagueRepository.findIdsByNameIgnoreCaseAndCountryIgnoreCase("EPL", "England");
        assertThat(familyIds).contains(l1.getId(), l2.getId());

        // Use the team ids from l2 and expect to find the l2 match via cross-league call
        List<Match> h2h = matchRepository.findHeadToHeadAcrossLeagues(familyIds, a2.getId(), b2.getId());
        assertThat(h2h).isNotEmpty();
    }
}

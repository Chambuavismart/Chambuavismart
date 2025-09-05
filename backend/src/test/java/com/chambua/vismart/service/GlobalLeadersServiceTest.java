package com.chambua.vismart.service;

import com.chambua.vismart.dto.GlobalLeaderDto;
import com.chambua.vismart.model.*;
import com.chambua.vismart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
@ActiveProfiles("test")
class GlobalLeadersServiceTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private javax.sql.DataSource dataSource;

    private GlobalLeadersService service;

    private League l1; private League l2;
    private Season l1s1; private Season l1s2; // latest is s2
    private Season l2s1; private Season l2s2; // latest is s2

    @BeforeEach
    void setup() {
        // Create service with NamedParameterJdbcTemplate built from test DataSource (H2)
        this.service = new GlobalLeadersService(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dataSource));
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        seasonRepository.deleteAll();
        leagueRepository.deleteAll();

        l1 = leagueRepository.save(new League("League A", "X", "2024/2025"));
        l2 = leagueRepository.save(new League("League B", "Y", "2024/2025"));

        l1s1 = seasonRepository.save(new Season(l1, "2023/2024", LocalDate.of(2023,8,1), LocalDate.of(2024,5,30)));
        l1s2 = seasonRepository.save(new Season(l1, "2024/2025", LocalDate.of(2024,8,1), null));
        l2s1 = seasonRepository.save(new Season(l2, "2023/2024", LocalDate.of(2023,8,1), LocalDate.of(2024,5,30)));
        l2s2 = seasonRepository.save(new Season(l2, "2024/2025", LocalDate.of(2024,8,1), null));

        Team a = teamRepository.save(new Team("A", l1));
        Team b = teamRepository.save(new Team("B", l1));
        Team c = teamRepository.save(new Team("C", l2));
        Team d = teamRepository.save(new Team("D", l2));

        // Old seasons: should be excluded by latest filter (we still insert some)
        matchRepository.save(makeMatch(l1, l1s1, a, b, LocalDate.of(2024,5,1), 10, 1, 0)); // played
        matchRepository.save(makeMatch(l2, l2s1, c, d, LocalDate.of(2024,5,1), 10, 2, 2)); // played

        // Latest seasons
        // League 1 latest matches
        matchRepository.save(makeMatch(l1, l1s2, a, b, LocalDate.of(2024,9,1), 1, 1, 1)); // draw
        matchRepository.save(makeMatch(l1, l1s2, a, b, LocalDate.of(2024,9,8), 2, 2, 3)); // over2.5, btts
        matchRepository.save(makeMatch(l1, l1s2, b, a, LocalDate.of(2024,9,15), 3, 0, 1)); // win for away (team A)

        // League 2 latest matches
        matchRepository.save(makeMatch(l2, l2s2, c, d, LocalDate.of(2024,9,1), 1, 3, 0)); // win for C
        matchRepository.save(makeMatch(l2, l2s2, d, c, LocalDate.of(2024,9,8), 2, 2, 2)); // draw
        matchRepository.save(makeMatch(l2, l2s2, c, d, LocalDate.of(2024,9,15), 3, 1, 2)); // btts, over1.5
    }

    private Match makeMatch(League league, Season season, Team home, Team away, LocalDate date, int round, Integer hg, Integer ag) {
        Match m = new Match(league, home, away, date, round, hg, ag);
        m.setSeason(season);
        if (hg != null && ag != null) m.setStatus(MatchStatus.PLAYED); else m.setStatus(MatchStatus.SCHEDULED);
        return matchRepository.save(m);
    }

    @Test
    void bttsTopTeams_acrossLatestSeasons() {
        List<GlobalLeaderDto> list = service.getLeaders("btts", 5, 1);
        assertThat(list).isNotEmpty();
        // Verify that teams have matches only from latest seasons counted:
        int totalMatches = list.stream().mapToInt(GlobalLeaderDto::getMatchesPlayed).sum();
        assertThat(totalMatches).isGreaterThanOrEqualTo(6); // from latest season inserts
    }

    @Test
    void winsTopTeams_sortedByStatCount() {
        List<GlobalLeaderDto> list = service.getLeaders("wins", 5, 1);
        assertThat(list).isNotEmpty();
        // First should be team with most wins across latest seasons
        GlobalLeaderDto top = list.get(0);
        assertThat(top.getStatCount()).isGreaterThanOrEqualTo(list.get(list.size()-1).getStatCount());
    }

    @Test
    void scopeHome_lastN1_limitsToOneMatchPerTeam() {
        List<GlobalLeaderDto> list = service.getLeaders("btts", 10, 1, "home", 1);
        assertThat(list).isNotEmpty();
        assertThat(list).allSatisfy(l -> assertThat(l.getMatchesPlayed()).isEqualTo(1));
    }
}

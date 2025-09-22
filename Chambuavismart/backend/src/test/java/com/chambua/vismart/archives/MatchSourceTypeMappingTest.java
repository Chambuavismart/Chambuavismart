package com.chambua.vismart.archives;

import com.chambua.vismart.model.*;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.chambua.vismart.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MatchSourceTypeMappingTest {

    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SeasonRepository seasonRepository;

    @Test
    void persist_and_load_match_with_source_type_archive() {
        League league = leagueRepository.save(new League("EPL", "England", "2025/2026"));
        Team home = teamRepository.save(new Team("Arsenal", league));
        Team away = teamRepository.save(new Team("Chelsea", league));

        Match m = new Match(league, home, away, LocalDate.of(2025,9,1), 1, 1, 0);
        m.setSourceType(SourceType.ARCHIVE);
        // Season is mandatory in entity mapping, create a minimal one
        com.chambua.vismart.model.Season season = new com.chambua.vismart.model.Season(league, "2025/2026", LocalDate.of(2025,8,1), LocalDate.of(2026,5,31));
        season = seasonRepository.save(season);
        m.setSeason(season);

        m = matchRepository.save(m);
        Match reloaded = matchRepository.findById(m.getId()).orElseThrow();
        assertThat(reloaded.getSourceType()).isEqualTo(SourceType.ARCHIVE);
    }
}

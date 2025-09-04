package com.chambua.vismart.service;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.Team;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchUploadServiceTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private SeasonRepository seasonRepository;

    private MatchDataValidationService validationService;

    private MatchUploadService service;

    private League league;
    private Season season;

    @BeforeEach
    void setUp() {
        validationService = new MatchDataValidationService();
        service = new MatchUploadService(leagueRepository, teamRepository, matchRepository, validationService, seasonRepository);

        league = new League("EPL", "England", "2025/2026");
        league.setId(1L);
        when(leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(league));

        season = new Season();
        season.setId(10L);
        season.setLeague(league);
        season.setName("2025/2026");
        when(seasonRepository.findById(ArgumentMatchers.anyLong())).thenReturn(Optional.of(season));
        when(seasonRepository.findByLeagueIdAndNameIgnoreCase(eq(1L), anyString())).thenReturn(Optional.of(season));
    }

    @Test
    void uploadText_validFixture_saved() {
        // fixture mode: goals may be null
        String text = "Round 1\n2025-08-10, Arsenal - Chelsea, - -";
        Team a = new Team("Arsenal", league); a.setId(100L);
        Team b = new Team("Chelsea", league); b.setId(200L);
        when(teamRepository.findByLeagueAndNameIgnoreCase(eq(league), eq("Arsenal"))).thenReturn(Optional.of(a));
        when(teamRepository.findByLeagueAndNameIgnoreCase(eq(league), eq("Chelsea"))).thenReturn(Optional.of(b));
        when(matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(1L, 1, 100L, 200L)).thenReturn(Optional.empty());
        when(matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(1L, 100L, 200L)).thenReturn(java.util.List.of());
        when(matchRepository.findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(season.getId(), 100L, 200L, LocalDate.parse("2025-08-10"))).thenReturn(Optional.empty());

        var result = service.uploadText("EPL", "England", "2025/2026", season.getId(), text, false, false, true, false, true, false, false);
        assertThat(result.success()).isTrue();
        assertThat(result.insertedCount()).isEqualTo(1);
    }

    @Test
    void uploadText_duplicateWithinSeason_rejectedWithError() {
        String text = "Round 1\n2025-08-10, Arsenal - Chelsea, 1 - 0";
        Team a = new Team("Arsenal", league); a.setId(100L);
        Team b = new Team("Chelsea", league); b.setId(200L);
        when(teamRepository.findByLeagueAndNameIgnoreCase(eq(league), eq("Arsenal"))).thenReturn(Optional.of(a));
        when(teamRepository.findByLeagueAndNameIgnoreCase(eq(league), eq("Chelsea"))).thenReturn(Optional.of(b));
        when(matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(1L, 1, 100L, 200L)).thenReturn(Optional.empty());
        when(matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(1L, 100L, 200L)).thenReturn(java.util.List.of());
        when(matchRepository.findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(season.getId(), 100L, 200L, LocalDate.parse("2025-08-10")))
                .thenReturn(Optional.of(new com.chambua.vismart.model.Match()));

        assertThatThrownBy(() -> service.uploadText("EPL", "England", "2025/2026", season.getId(), text, false, false, false, true, true, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate match for season/date/teams");
    }

    @Test
    void uploadText_invalidGoals_rejectedWithError() {
        // Non-fixture mode requires valid integer goals
        String text = "Round 1\n2025-08-10, Arsenal - Chelsea, x - 2";
        var result = service.uploadText("EPL", "England", "2025/2026", season.getId(), text, false, false, false, true, true, true, false);
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anySatisfy(msg -> assertThat(msg).contains("Missing goals"));
    }

    @Test
    void uploadText_missingTeam_rejectedWithError() {
        String text = "Round 1\n2025-08-10,  - Chelsea, 1 - 0";
        var result = service.uploadText("EPL", "England", "2025/2026", season.getId(), text, false, false, false, true, true, true, false);
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anySatisfy(msg -> assertThat(msg).contains("Missing team name"));
    }
}

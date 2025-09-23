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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MatchUploadServiceCompetitionTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private SeasonRepository seasonRepository;

    private MatchDataValidationService validationService;

    private MatchUploadService service;

    private League league;
    private Season season;

    @BeforeEach
    void init() throws Exception {
        validationService = new MatchDataValidationService();
        service = new MatchUploadService(leagueRepository, teamRepository, matchRepository, validationService, seasonRepository);

        league = new League("UEFA CL", "UEFA — Champions League", "2024/2025");
        league.setId(123L);

        when(leagueRepository.findByNameIgnoreCaseAndCountryIgnoreCaseAndSeason(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(league));

        season = new Season();
        season.setId(55L);
        season.setLeague(league);
        season.setName("2024/2025");
        when(seasonRepository.findById(anyLong())).thenReturn(Optional.of(season));
        when(seasonRepository.findByLeagueIdAndNameIgnoreCase(eq(123L), anyString())).thenReturn(Optional.of(season));

        // Common match repository defaults
        when(matchRepository.findByLeagueIdAndRoundAndHomeTeamIdAndAwayTeamId(anyLong(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(matchRepository.findByLeagueIdAndHomeTeamIdAndAwayTeamId(anyLong(), anyLong(), anyLong()))
                .thenReturn(java.util.List.of());
        when(matchRepository.findBySeasonIdAndHomeTeamIdAndAwayTeamIdAndDate(anyLong(), anyLong(), anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    private void setCompetitionsFlag(boolean value) throws Exception {
        Field f = MatchUploadService.class.getDeclaredField("enableCompetitions");
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void uploadText_withCompetitionContext_flagEnabled_succeedsWithoutWarning() throws Exception {
        setCompetitionsFlag(true);
        String text = "Round 1\n2024-09-18, Barcelona - Bayern Munich, 2 - 1";
        Team home = new Team("Barcelona", league); home.setId(10L);
        Team away = new Team("Bayern Munich", league); away.setId(20L);
        // When autoCreateTeams=true path, service uses normalized-name lookup
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("barcelona"), eq(123L))).thenReturn(Optional.of(home));
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("bayern munich"), eq(123L))).thenReturn(Optional.of(away));

        var res = service.uploadText("UEFA CL", "UEFA - Champions League", "2024/2025", season.getId(), text,
                false, false, false, true, true, false, false);
        assertThat(res.success()).isTrue();
        assertThat(res.insertedCount()).isEqualTo(1);
        // should not produce unknown-context warnings when recognized as competition
        assertThat(res.warnings()).allMatch(w -> {
            if (w == null) return true;
            String s = w.toString().toLowerCase();
            return !s.contains("unknown contextlabel");
        });
    }

    @Test
    void uploadText_withUnknownContext_flagEnabled_addsWarningNotBlocking() throws Exception {
        setCompetitionsFlag(true);
        String text = "Round 1\n2024-09-18, Team A - Team B, 1 - 0";
        Team home = new Team("Team A", league); home.setId(10L);
        Team away = new Team("Team B", league); away.setId(20L);
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("team a"), eq(123L))).thenReturn(Optional.of(home));
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("team b"), eq(123L))).thenReturn(Optional.of(away));

        var res = service.uploadText("Some League", "UEFA Champions Cup" /* not allowlisted */ , "2024/2025", season.getId(), text,
                false, false, false, true, true, false, false);
        assertThat(res.success()).isTrue();
        assertThat(res.warnings()).anySatisfy(w -> assertThat(String.valueOf(w)).contains("Unknown contextLabel"));
    }

    @Test
    void uploadText_withCompetitionLabel_flagDisabled_behavesAsPlainCountry_noWarnings() throws Exception {
        setCompetitionsFlag(false);
        String text = "Round 1\n2024-09-18, Milan - Inter, 2 - 2";
        Team home = new Team("Milan", league); home.setId(10L);
        Team away = new Team("Inter", league); away.setId(20L);
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("milan"), eq(123L))).thenReturn(Optional.of(home));
        when(teamRepository.findByNormalizedNameAndLeagueId(eq("inter"), eq(123L))).thenReturn(Optional.of(away));

        var res = service.uploadText("UEFA CL", "UEFA — Champions League", "2024/2025", season.getId(), text,
                false, false, false, true, true, false, false);
        assertThat(res.success()).isTrue();
        assertThat(res.warnings()).isEmpty();
    }
}

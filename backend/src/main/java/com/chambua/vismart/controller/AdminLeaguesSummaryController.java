package com.chambua.vismart.controller;

import com.chambua.vismart.dto.AdminLeagueSummaryDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.MatchRepository;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.service.SeasonService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/leagues")
@CrossOrigin(origins = "*")
public class AdminLeaguesSummaryController {

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonService seasonService;
    private final MatchRepository matchRepository;

    public AdminLeaguesSummaryController(LeagueRepository leagueRepository,
                                         SeasonRepository seasonRepository,
                                         SeasonService seasonService,
                                         MatchRepository matchRepository) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.seasonService = seasonService;
        this.matchRepository = matchRepository;
    }

    @GetMapping("/summary")
    public List<AdminLeagueSummaryDTO> summary() {
        List<League> leagues = leagueRepository.findAll();
        // Sort by country then name for stable UI
        leagues.sort(Comparator.comparing(League::getCountry, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(League::getName, String.CASE_INSENSITIVE_ORDER));

        List<AdminLeagueSummaryDTO> out = new ArrayList<>();
        for (League l : leagues) {
            List<Season> seasons = seasonRepository.findByLeagueIdOrderByStartDateDesc(l.getId());
            List<AdminLeagueSummaryDTO.SeasonItem> seasonItems = seasons.stream()
                    .map(s -> new AdminLeagueSummaryDTO.SeasonItem(s.getId(), s.getName(), s.getStartDate(), s.getEndDate()))
                    .collect(Collectors.toList());

            var currentOpt = seasonService.findCurrentSeason(l.getId());
            Long currentId = currentOpt.map(Season::getId).orElse(null);
            String currentName = currentOpt.map(Season::getName).orElse(null);

            Instant lastUpdated = null;
            if (currentId != null) {
                // Prefer last import finished time; fallback to Season.updatedAt if null
                Instant fromImport = matchRepository.findLastImportFinishedAtBySeasonId(currentId);
                if (fromImport != null) {
                    lastUpdated = fromImport;
                } else {
                    lastUpdated = currentOpt.map(Season::getUpdatedAt).orElse(null);
                }
            }

            AdminLeagueSummaryDTO dto = new AdminLeagueSummaryDTO(
                    l.getId(),
                    l.getName(),
                    null, // category not modeled; leave null for now
                    l.getCountry(),
                    seasonItems,
                    currentId,
                    currentName,
                    lastUpdated
            );
            out.add(dto);
        }
        return out;
    }
}

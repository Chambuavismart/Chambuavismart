package com.chambua.vismart.controller;

import com.chambua.vismart.dto.FixtureDTO;
import com.chambua.vismart.dto.FixturesUploadRequest;
import com.chambua.vismart.dto.LeagueFixturesResponse;
import com.chambua.vismart.dto.LeagueWithUpcomingDTO;
import com.chambua.vismart.dto.UploadResultDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.FixtureRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.service.FixtureService;
import com.chambua.vismart.service.FixtureUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fixtures")
@CrossOrigin(origins = "*")
public class FixtureController {

    private final FixtureService fixtureService;
    private final LeagueRepository leagueRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureUploadService fixtureUploadService;

    public FixtureController(FixtureService fixtureService, LeagueRepository leagueRepository, FixtureRepository fixtureRepository, FixtureUploadService fixtureUploadService) {
        this.fixtureService = fixtureService;
        this.leagueRepository = leagueRepository;
        this.fixtureRepository = fixtureRepository;
        this.fixtureUploadService = fixtureUploadService;
    }

    @GetMapping("/leagues")
    public List<LeagueWithUpcomingDTO> getLeaguesWithFixtures() {
        List<Long> leagueIds = fixtureRepository.findDistinctLeagueIdsWithFixtures();
        Map<Long, Long> upcomingMap = fixtureRepository.countUpcomingByLeague().stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> ((Number) r[1]).longValue()
                ));
        List<League> leagues = leagueRepository.findAllById(leagueIds);
        leagues.sort(Comparator.comparing(League::getName));
        return leagues.stream()
                .map(l -> new LeagueWithUpcomingDTO(l.getId(), l.getName(), upcomingMap.getOrDefault(l.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @GetMapping("/{leagueId}")
    public LeagueFixturesResponse getFixturesForLeague(@PathVariable Long leagueId,
                                                       @RequestParam(value = "upcomingOnly", required = false, defaultValue = "false") boolean upcomingOnly) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        var fixtures = upcomingOnly ? fixtureService.getUpcomingFixturesByLeague(leagueId) : fixtureService.getFixturesByLeague(leagueId);
        var fixtureDtos = fixtures.stream().map(FixtureDTO::from).collect(Collectors.toList());
        return new LeagueFixturesResponse(league.getId(), league.getName(), fixtureDtos);
    }

    @PostMapping("/upload")
    public UploadResultDTO uploadFixtures(@RequestBody FixturesUploadRequest request){
        return fixtureUploadService.upload(request);
    }
}

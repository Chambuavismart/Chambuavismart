package com.chambua.vismart.controller;

import com.chambua.vismart.dto.FixtureDTO;
import com.chambua.vismart.dto.FixturesUploadRequest;
import com.chambua.vismart.dto.LeagueFixturesResponse;
import com.chambua.vismart.dto.LeagueWithUpcomingDTO;
import com.chambua.vismart.dto.UploadResultDTO;
import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.FixtureRepository;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.service.FixtureService;
import com.chambua.vismart.service.FixtureUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fixtures")
@CrossOrigin(origins = "*")
public class FixtureController {

    private static final Logger log = LoggerFactory.getLogger(FixtureController.class);

    private final FixtureService fixtureService;
    private final LeagueRepository leagueRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureUploadService fixtureUploadService;
    private final com.chambua.vismart.service.FixtureRefreshService fixtureRefreshService;

    public FixtureController(FixtureService fixtureService, LeagueRepository leagueRepository, FixtureRepository fixtureRepository, FixtureUploadService fixtureUploadService, com.chambua.vismart.service.FixtureRefreshService fixtureRefreshService) {
        this.fixtureService = fixtureService;
        this.leagueRepository = leagueRepository;
        this.fixtureRepository = fixtureRepository;
        this.fixtureUploadService = fixtureUploadService;
        this.fixtureRefreshService = fixtureRefreshService;
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
                .map(l -> new LeagueWithUpcomingDTO(
                        l.getId(),
                        l.getName(),
                        l.getCountry(),
                        l.getSeason(),
                        upcomingMap.getOrDefault(l.getId(), 0L)
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/{leagueId}")
    public LeagueFixturesResponse getFixturesForLeague(@PathVariable Long leagueId,
                                                       @RequestParam(value = "upcomingOnly", required = false, defaultValue = "false") boolean upcomingOnly,
                                                       @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        if (refresh) {
            try { fixtureRefreshService.refreshLeague(leagueId); } catch (Exception e) { log.warn("League refresh failed: {}", e.getMessage()); }
        }
        var fixtures = upcomingOnly ? fixtureService.getUpcomingFixturesByLeague(leagueId) : fixtureService.getFixturesByLeague(leagueId);
        var fixtureDtos = fixtures.stream().map(FixtureDTO::from).collect(Collectors.toList());
        return new LeagueFixturesResponse(league.getId(), league.getName(), league.getCountry(), fixtureDtos);
    }

    @GetMapping("/by-date")
    @Transactional(readOnly = true)
    public List<LeagueFixturesResponse> getFixturesByDate(@RequestParam("date") String dateIso,
                                                          @RequestParam(value = "season", required = false) String season,
                                                          @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        // Log at INFO to ensure visibility in default setups
        log.info("GET /api/fixtures/by-date date={} season={} refresh={}", dateIso, season, refresh);
        LocalDate date;
        try { date = LocalDate.parse(dateIso); } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD");
        }
        if (refresh) {
            try { fixtureRefreshService.refreshByDate(date); } catch (Exception e) { log.warn("Date refresh failed: {}", e.getMessage()); }
        }
        if (log.isDebugEnabled()) {
            log.debug("[FixtureController] /by-date date={} season={}", date, season);
        }
        List<Fixture> fixtures = fixtureService.getFixturesByDate(date, season);
        // group by league id to avoid lazy initialization of League proxies
        Map<Long, List<Fixture>> grouped = fixtures.stream()
                .collect(Collectors.groupingBy(f -> f.getLeague().getId()));

        // fetch league names and countries for collected ids
        List<Long> ids = new ArrayList<>(grouped.keySet());
        List<League> leagues = leagueRepository.findAllById(ids);
        Map<Long, String> idToName = leagues.stream().collect(Collectors.toMap(League::getId, League::getName));
        Map<Long, String> idToCountry = leagues.stream().collect(Collectors.toMap(League::getId, League::getCountry));

        List<LeagueFixturesResponse> out = new ArrayList<>();
        for (Map.Entry<Long, List<Fixture>> entry : grouped.entrySet()) {
            Long leagueId = entry.getKey();
            String leagueName = idToName.getOrDefault(leagueId, "League " + leagueId);
            String leagueCountry = idToCountry.getOrDefault(leagueId, "");
            // Map to DTOs and ensure fixtures are sorted by kickoff time ascending
            List<FixtureDTO> fds = entry.getValue().stream()
                    .map(FixtureDTO::from)
                    .sorted(Comparator.comparing(FixtureDTO::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            out.add(new LeagueFixturesResponse(leagueId, leagueName, leagueCountry, fds));
        }
        // Sort leagues by earliest kickoff among their fixtures (ascending). If a league has no fixtures, place it last.
        out.sort(Comparator.comparing(
                (LeagueFixturesResponse lfr) -> lfr.getFixtures() == null || lfr.getFixtures().isEmpty() ? null : lfr.getFixtures().get(0).getDateTime(),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        int total = out.stream().mapToInt(l -> l.getFixtures() == null ? 0 : l.getFixtures().size()).sum();
        log.info("/api/fixtures/by-date: {} leagues, {} fixtures for date={}", out.size(), total, date);
        return out;
    }

    @GetMapping("/available-dates")
    public Set<String> getAvailableDates(@RequestParam("year") int year,
                                         @RequestParam("month") int month,
                                         @RequestParam(value = "season", required = false) String season){
        log.info("GET /api/fixtures/available-dates year={} month={} season={}", year, month, season);
        if (month < 1 || month > 12) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
        var dates = fixtureService.getAvailableDatesForMonth(year, month, season);
        return dates.stream().map(LocalDate::toString).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<com.chambua.vismart.dto.SearchFixtureItemDTO> searchFixtures(@RequestParam("q") String q,
                                                                             @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
                                                                             @RequestParam(value = "season", required = false) String season) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 3) return java.util.Collections.emptyList();
        int lim = Math.max(1, Math.min(limit, 50));
        String qLower = query.toLowerCase();

        List<Fixture> matches = (season != null && !season.isBlank())
                ? fixtureRepository.searchActiveByTeamPrefixAndSeason(qLower, season.trim())
                : fixtureRepository.searchActiveByTeamPrefix(qLower);

        java.util.LinkedHashMap<String, Fixture> chosen = new java.util.LinkedHashMap<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Fixture f : matches) {
            if (f.getDateTime() == null) continue;
            if (f.getDateTime().isBefore(now)) continue; // nearest upcoming only
            String home = f.getHomeTeam() == null ? "" : f.getHomeTeam();
            String away = f.getAwayTeam() == null ? "" : f.getAwayTeam();
            String key = null;
            if (home.toLowerCase().startsWith(qLower)) key = home.toLowerCase();
            else if (away.toLowerCase().startsWith(qLower)) key = away.toLowerCase();
            if (key == null) continue;
            if (!chosen.containsKey(key)) {
                chosen.put(key, f); // matches are already ordered by dateTime asc
            }
            if (chosen.size() >= lim) break;
        }

        List<com.chambua.vismart.dto.SearchFixtureItemDTO> out = new java.util.ArrayList<>();
        for (Fixture f : chosen.values()) {
            var league = f.getLeague();
            out.add(new com.chambua.vismart.dto.SearchFixtureItemDTO(
                    league != null ? league.getId() : null,
                    league != null ? league.getName() : null,
                    league != null ? league.getCountry() : null,
                    com.chambua.vismart.dto.FixtureDTO.from(f)
            ));
            if (out.size() >= lim) break;
        }
        return out;
    }

    @PostMapping("/upload")
    public UploadResultDTO uploadFixtures(@RequestBody FixturesUploadRequest request){
        return fixtureUploadService.upload(request);
    }

    @PostMapping("/upload-text")
    public UploadResultDTO uploadFixturesText(@RequestBody FixturesUploadRequest request){
        return fixtureUploadService.upload(request);
    }

    @PostMapping(value = "/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResultDTO uploadFixturesCsv(@RequestParam("leagueId") Long leagueId,
                                             @RequestParam(value = "season", required = false) String season,
                                             @RequestParam(value = "fullReplace", required = false, defaultValue = "false") boolean fullReplace,
                                             @RequestPart("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            return fixtureUploadService.uploadCsv(leagueId, season, fullReplace, content);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read CSV file: " + e.getMessage());
        }
    }
}

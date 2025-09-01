package com.chambua.vismart.controller;

import com.chambua.vismart.dto.LeagueTableEntryDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.service.LeagueTableService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/league")
@CrossOrigin(origins = "*")
public class LeagueController {

    private final LeagueTableService leagueTableService;
    private final LeagueRepository leagueRepository;

    public LeagueController(LeagueTableService leagueTableService, LeagueRepository leagueRepository) {
        this.leagueTableService = leagueTableService;
        this.leagueRepository = leagueRepository;
    }

    @GetMapping
    public List<League> listLeagues() {
        return leagueRepository.findAll();
    }

    @GetMapping("/{leagueId}/table")
    public List<LeagueTableEntryDTO> getLeagueTable(@PathVariable Long leagueId,
                                                    @RequestParam(value = "season", required = false) String season) {
        if (season != null) {
            League league = leagueRepository.findById(leagueId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
            if (!season.equals(league.getSeason())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season mismatch for leagueId");
            }
        }
        return leagueTableService.computeTable(leagueId, season);
    }
}

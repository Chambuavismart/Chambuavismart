package com.chambua.vismart.controller;

import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.LeagueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@CrossOrigin(origins = "*")
public class LeaguesQueryController {

    private final LeagueRepository leagueRepository;

    public LeaguesQueryController(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @GetMapping
    public List<League> listLeagues(){
        return leagueRepository.findAll();
    }

    // Alias for frontend requirement: /api/leagues/list
    @GetMapping("/list")
    public List<League> listLeaguesAlias(){
        return leagueRepository.findAll();
    }

    @GetMapping("/{id}")
    public League getLeague(@PathVariable Long id){
        return leagueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
    }
}
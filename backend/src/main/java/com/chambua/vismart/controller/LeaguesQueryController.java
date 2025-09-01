package com.chambua.vismart.controller;

import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.LeagueRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
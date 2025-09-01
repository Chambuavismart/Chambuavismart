package com.chambua.vismart.controller;

import com.chambua.vismart.dto.LeagueTableDebugDTO;
import com.chambua.vismart.service.LeagueTableService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/league")
@CrossOrigin(origins = "*")
public class AdminLeagueDebugController {

    private final LeagueTableService leagueTableService;

    public AdminLeagueDebugController(LeagueTableService leagueTableService) {
        this.leagueTableService = leagueTableService;
    }

    @GetMapping("/{leagueId}/debug-table")
    public LeagueTableDebugDTO debug(@PathVariable Long leagueId) {
        return leagueTableService.getDiagnostics(leagueId);
    }
}

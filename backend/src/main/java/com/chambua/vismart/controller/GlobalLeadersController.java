package com.chambua.vismart.controller;

import com.chambua.vismart.dto.GlobalLeaderDto;
import com.chambua.vismart.service.GlobalLeadersService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/global-leaders")
public class GlobalLeadersController {

    private final GlobalLeadersService service;

    public GlobalLeadersController(GlobalLeadersService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<GlobalLeaderDto>> get(
            @RequestParam(name = "category") String category,
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestParam(name = "minMatches", defaultValue = "3") int minMatches,
            @RequestParam(name = "scope", defaultValue = "overall") String scope,
            @RequestParam(name = "lastN", defaultValue = "0") int lastN
    ) {
        if (limit <= 0) limit = 5;
        if (limit > 50) limit = 50; // safety cap
        if (minMatches < 1) minMatches = 1;
        if (lastN < 0) lastN = 0;
        List<GlobalLeaderDto> res = service.getLeaders(category, limit, minMatches, scope, lastN);
        return ResponseEntity.ok(res);
    }
}

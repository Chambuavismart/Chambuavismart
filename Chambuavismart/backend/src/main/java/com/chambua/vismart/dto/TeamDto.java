package com.chambua.vismart.dto;

public record TeamDto(
        Long id,
        String name,
        String alias,
        Long leagueId,
        String leagueName
) {}

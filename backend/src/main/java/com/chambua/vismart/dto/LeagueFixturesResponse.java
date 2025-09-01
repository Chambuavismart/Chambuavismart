package com.chambua.vismart.dto;

import java.util.List;

public class LeagueFixturesResponse {
    private Long leagueId;
    private String leagueName;
    private List<FixtureDTO> fixtures;

    public LeagueFixturesResponse(Long leagueId, String leagueName, List<FixtureDTO> fixtures) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.fixtures = fixtures;
    }

    public Long getLeagueId() { return leagueId; }
    public String getLeagueName() { return leagueName; }
    public List<FixtureDTO> getFixtures() { return fixtures; }
}

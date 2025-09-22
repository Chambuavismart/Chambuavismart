package com.chambua.vismart.dto;

import java.util.List;

public class LeagueFixturesResponse {
    private Long leagueId;
    private String leagueName;
    private String leagueCountry;
    private List<FixtureDTO> fixtures;

    public LeagueFixturesResponse(Long leagueId, String leagueName, String leagueCountry, List<FixtureDTO> fixtures) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.leagueCountry = leagueCountry;
        this.fixtures = fixtures;
    }

    public Long getLeagueId() { return leagueId; }
    public String getLeagueName() { return leagueName; }
    public String getLeagueCountry() { return leagueCountry; }
    public List<FixtureDTO> getFixtures() { return fixtures; }
}

package com.chambua.vismart.dto;

public class SearchFixtureItemDTO {
    private Long leagueId;
    private String leagueName;
    private String leagueCountry;
    private FixtureDTO fixture;

    public SearchFixtureItemDTO() {}

    public SearchFixtureItemDTO(Long leagueId, String leagueName, String leagueCountry, FixtureDTO fixture) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.leagueCountry = leagueCountry;
        this.fixture = fixture;
    }

    public Long getLeagueId() { return leagueId; }
    public String getLeagueName() { return leagueName; }
    public String getLeagueCountry() { return leagueCountry; }
    public FixtureDTO getFixture() { return fixture; }
}

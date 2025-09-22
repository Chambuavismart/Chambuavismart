package com.chambua.vismart.dto;

public class LeagueWithUpcomingDTO {
    private Long leagueId;
    private String leagueName;
    private String leagueCountry;
    private String season;
    private long upcomingCount;

    public LeagueWithUpcomingDTO(Long leagueId, String leagueName, String leagueCountry, String season, long upcomingCount) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.leagueCountry = leagueCountry;
        this.season = season;
        this.upcomingCount = upcomingCount;
    }

    public Long getLeagueId() { return leagueId; }
    public String getLeagueName() { return leagueName; }
    public String getLeagueCountry() { return leagueCountry; }
    public String getSeason() { return season; }
    public long getUpcomingCount() { return upcomingCount; }
}

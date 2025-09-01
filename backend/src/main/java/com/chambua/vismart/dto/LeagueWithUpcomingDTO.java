package com.chambua.vismart.dto;

public class LeagueWithUpcomingDTO {
    private Long leagueId;
    private String leagueName;
    private long upcomingCount;

    public LeagueWithUpcomingDTO(Long leagueId, String leagueName, long upcomingCount) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.upcomingCount = upcomingCount;
    }

    public Long getLeagueId() { return leagueId; }
    public String getLeagueName() { return leagueName; }
    public long getUpcomingCount() { return upcomingCount; }
}

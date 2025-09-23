package com.chambua.vismart.dto;

import java.time.LocalDate;

public class MatchIngestItem {
    private Long leagueId;
    private String leagueName;
    private String country;
    private String season;

    private LocalDate date;
    private Integer round;

    private String homeTeamName;
    private String awayTeamName;

    private Integer homeGoals;
    private Integer awayGoals;

    public MatchIngestItem() {}

    public MatchIngestItem(Long leagueId, String leagueName, String country, String season,
                           LocalDate date, Integer round,
                           String homeTeamName, String awayTeamName,
                           Integer homeGoals, Integer awayGoals) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.country = country;
        this.season = season;
        this.date = date;
        this.round = round;
        this.homeTeamName = homeTeamName;
        this.awayTeamName = awayTeamName;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
    }

    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }
    public Integer getHomeGoals() { return homeGoals; }
    public void setHomeGoals(Integer homeGoals) { this.homeGoals = homeGoals; }
    public Integer getAwayGoals() { return awayGoals; }
    public void setAwayGoals(Integer awayGoals) { this.awayGoals = awayGoals; }
}

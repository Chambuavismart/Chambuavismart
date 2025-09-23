package com.chambua.vismart.dto;

import java.time.LocalDate;

public class HeadToHeadMatchDto {
    private LocalDate date;
    private String competition; // league short name, e.g. "PL"
    private String homeTeam;
    private String awayTeam;
    private int homeGoals;
    private int awayGoals;

    public HeadToHeadMatchDto() {}

    public HeadToHeadMatchDto(LocalDate date, String competition, String homeTeam, String awayTeam, int homeGoals, int awayGoals) {
        this.date = date;
        this.competition = competition;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getCompetition() { return competition; }
    public void setCompetition(String competition) { this.competition = competition; }
    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
    public int getHomeGoals() { return homeGoals; }
    public void setHomeGoals(int homeGoals) { this.homeGoals = homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public void setAwayGoals(int awayGoals) { this.awayGoals = awayGoals; }
}

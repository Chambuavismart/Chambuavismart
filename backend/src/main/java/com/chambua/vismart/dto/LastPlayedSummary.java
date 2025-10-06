package com.chambua.vismart.dto;

public class LastPlayedSummary {
    private String team;
    private String priorResult; // Win/Draw/Loss from this team's perspective
    private String priorScoreLine; // e.g., 1-1 from this team's perspective
    private String opponent;
    private java.time.LocalDate date;

    public LastPlayedSummary() {}

    public LastPlayedSummary(String team, String priorResult, String priorScoreLine, String opponent, java.time.LocalDate date) {
        this.team = team;
        this.priorResult = priorResult;
        this.priorScoreLine = priorScoreLine;
        this.opponent = opponent;
        this.date = date;
    }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getPriorResult() { return priorResult; }
    public void setPriorResult(String priorResult) { this.priorResult = priorResult; }

    public String getPriorScoreLine() { return priorScoreLine; }
    public void setPriorScoreLine(String priorScoreLine) { this.priorScoreLine = priorScoreLine; }

    public String getOpponent() { return opponent; }
    public void setOpponent(String opponent) { this.opponent = opponent; }

    public java.time.LocalDate getDate() { return date; }
    public void setDate(java.time.LocalDate date) { this.date = date; }
}

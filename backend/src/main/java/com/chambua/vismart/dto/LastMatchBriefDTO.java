package com.chambua.vismart.dto;

import java.time.LocalDate;

public class LastMatchBriefDTO {
    private LocalDate date; // match date
    private String season;  // season label if available
    private String opponent;
    private String result; // W/D/L from the perspective of the requested team
    private String scoreLine; // e.g., 2-1 (myGoals-oppGoals)

    public LastMatchBriefDTO() {}

    public LastMatchBriefDTO(LocalDate date, String season, String opponent, String result, String scoreLine) {
        this.date = date;
        this.season = season;
        this.opponent = opponent;
        this.result = result;
        this.scoreLine = scoreLine;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getOpponent() { return opponent; }
    public void setOpponent(String opponent) { this.opponent = opponent; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getScoreLine() { return scoreLine; }
    public void setScoreLine(String scoreLine) { this.scoreLine = scoreLine; }
}
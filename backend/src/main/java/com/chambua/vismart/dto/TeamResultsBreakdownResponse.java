package com.chambua.vismart.dto;

public class TeamResultsBreakdownResponse {
    private long total;
    private long wins;
    private long draws;
    private long losses;
    private long btts;
    private long over25;
    private long over15;

    public TeamResultsBreakdownResponse() {}

    public TeamResultsBreakdownResponse(long total, long wins, long draws, long losses) {
        this.total = total;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
    }

    public TeamResultsBreakdownResponse(long total, long wins, long draws, long losses, long btts) {
        this.total = total;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.btts = btts;
    }

    public TeamResultsBreakdownResponse(long total, long wins, long draws, long losses, long btts, long over25, long over15) {
        this.total = total;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.btts = btts;
        this.over25 = over25;
        this.over15 = over15;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public long getWins() { return wins; }
    public void setWins(long wins) { this.wins = wins; }

    public long getDraws() { return draws; }
    public void setDraws(long draws) { this.draws = draws; }

    public long getLosses() { return losses; }
    public void setLosses(long losses) { this.losses = losses; }

    public long getBtts() { return btts; }
    public void setBtts(long btts) { this.btts = btts; }

    public long getOver25() { return over25; }
    public void setOver25(long over25) { this.over25 = over25; }

    public long getOver15() { return over15; }
    public void setOver15(long over15) { this.over15 = over15; }
}

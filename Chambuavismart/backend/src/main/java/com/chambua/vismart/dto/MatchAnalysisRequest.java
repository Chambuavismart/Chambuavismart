package com.chambua.vismart.dto;

public class MatchAnalysisRequest {
    private Long leagueId;
    private Long seasonId; // optional: backend falls back to current season if omitted
    private Long homeTeamId;
    private Long awayTeamId;

    // Optional: allow name-based resolution for convenience when IDs are unknown on client
    private String homeTeamName;
    private String awayTeamName;

    // Differentiates backend logic between tabs: "fixtures" or "match" (default: "match")
    private String analysisType;

    // Optional: force refresh (recompute and overwrite cache)
    private boolean refresh;

    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }

    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }

    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }

    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public boolean isRefresh() { return refresh; }
    public void setRefresh(boolean refresh) { this.refresh = refresh; }
}

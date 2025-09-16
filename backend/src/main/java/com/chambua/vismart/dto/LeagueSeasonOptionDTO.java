package com.chambua.vismart.dto;

public class LeagueSeasonOptionDTO {
    private Long leagueId;
    private String season; // e.g., 2025/2026
    private String label;  // display label for dropdown, typically same as season

    public LeagueSeasonOptionDTO() {}

    public LeagueSeasonOptionDTO(Long leagueId, String season, String label) {
        this.leagueId = leagueId;
        this.season = season;
        this.label = label;
    }

    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}

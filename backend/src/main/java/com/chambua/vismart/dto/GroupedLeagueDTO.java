package com.chambua.vismart.dto;

import java.util.List;

public class GroupedLeagueDTO {
    private String country;
    private String leagueName;
    private String groupLabel; // e.g., "England — English Premier League"
    private List<LeagueSeasonOptionDTO> options; // one per season (latest -> oldest)

    public GroupedLeagueDTO() {}

    public GroupedLeagueDTO(String country, String leagueName, String groupLabel, List<LeagueSeasonOptionDTO> options) {
        this.country = country;
        this.leagueName = leagueName;
        this.groupLabel = groupLabel;
        this.options = options;
    }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }

    public String getGroupLabel() { return groupLabel; }
    public void setGroupLabel(String groupLabel) { this.groupLabel = groupLabel; }

    public List<LeagueSeasonOptionDTO> getOptions() { return options; }
    public void setOptions(List<LeagueSeasonOptionDTO> options) { this.options = options; }
}

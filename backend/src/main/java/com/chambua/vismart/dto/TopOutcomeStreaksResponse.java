package com.chambua.vismart.dto;

import java.util.List;

public class TopOutcomeStreaksResponse {
    private String teamName;
    private int consideredMatches;
    private List<OutcomeStreakDTO> topStreaks; // up to 2

    public TopOutcomeStreaksResponse() {}

    public TopOutcomeStreaksResponse(String teamName, int consideredMatches, List<OutcomeStreakDTO> topStreaks) {
        this.teamName = teamName;
        this.consideredMatches = consideredMatches;
        this.topStreaks = topStreaks;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public int getConsideredMatches() { return consideredMatches; }
    public void setConsideredMatches(int consideredMatches) { this.consideredMatches = consideredMatches; }

    public List<OutcomeStreakDTO> getTopStreaks() { return topStreaks; }
    public void setTopStreaks(List<OutcomeStreakDTO> topStreaks) { this.topStreaks = topStreaks; }
}

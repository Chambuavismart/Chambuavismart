package com.chambua.vismart.dto;

public class GlobalLeaderDto {
    private Long teamId;
    private String teamName;
    private String teamSlug;
    private String teamLogoUrl;
    private double statPct; // 0-100
    private int matchesPlayed;
    private int statCount; // raw count for the category
    private String category;
    private int rank;

    public GlobalLeaderDto() {}

    public GlobalLeaderDto(Long teamId, String teamName, String teamSlug, String teamLogoUrl, double statPct,
                           int matchesPlayed, int statCount, String category, int rank) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamSlug = teamSlug;
        this.teamLogoUrl = teamLogoUrl;
        this.statPct = statPct;
        this.matchesPlayed = matchesPlayed;
        this.statCount = statCount;
        this.category = category;
        this.rank = rank;
    }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getTeamSlug() { return teamSlug; }
    public void setTeamSlug(String teamSlug) { this.teamSlug = teamSlug; }

    public String getTeamLogoUrl() { return teamLogoUrl; }
    public void setTeamLogoUrl(String teamLogoUrl) { this.teamLogoUrl = teamLogoUrl; }

    public double getStatPct() { return statPct; }
    public void setStatPct(double statPct) { this.statPct = statPct; }

    public int getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }

    public int getStatCount() { return statCount; }
    public void setStatCount(int statCount) { this.statCount = statCount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
}

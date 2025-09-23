package com.chambua.vismart.dto;

public class FixturesUploadRequest {
    private Long leagueId;
    private String season;
    private boolean fullReplace;
    private String rawText;
    private boolean strictMode; // preserve legacy behavior when true

    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public boolean isFullReplace() { return fullReplace; }
    public void setFullReplace(boolean fullReplace) { this.fullReplace = fullReplace; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public boolean isStrictMode() { return strictMode; }
    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }
}
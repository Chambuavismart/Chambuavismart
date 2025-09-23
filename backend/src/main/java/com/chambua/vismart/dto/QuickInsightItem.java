package com.chambua.vismart.dto;

public class QuickInsightItem {
    private Long fixtureId;
    private Long leagueId;
    private String league;
    private String home;
    private String away;
    private java.time.Instant kickoff; // UTC instant for frontend simplicity
    private java.util.List<String> triggers; // reasons that qualified the match

    // Convenience: primary trigger string (first of triggers) for simple UIs
    private String trigger;

    public QuickInsightItem() {}

    public QuickInsightItem(Long fixtureId, Long leagueId, String league, String home, String away, java.time.Instant kickoff, java.util.List<String> triggers) {
        this.fixtureId = fixtureId;
        this.leagueId = leagueId;
        this.league = league;
        this.home = home;
        this.away = away;
        this.kickoff = kickoff;
        this.triggers = triggers;
        this.trigger = (triggers != null && !triggers.isEmpty()) ? triggers.get(0) : null;
    }

    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }
    public String getHome() { return home; }
    public void setHome(String home) { this.home = home; }
    public String getAway() { return away; }
    public void setAway(String away) { this.away = away; }
    public java.time.Instant getKickoff() { return kickoff; }
    public void setKickoff(java.time.Instant kickoff) { this.kickoff = kickoff; }
    public java.util.List<String> getTriggers() { return triggers; }
    public void setTriggers(java.util.List<String> triggers) { this.triggers = triggers; this.trigger = (triggers != null && !triggers.isEmpty()) ? triggers.get(0) : null; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
}

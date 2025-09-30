package com.chambua.vismart.dto;

/**
 * Timeline row for a team's streak context before each match it played.
 */
public class StreakTimelineItem {
    private Long matchId;
    private String date;       // yyyy-MM-dd
    private String time;       // HH:mm (server-local)
    private String league;
    private String season;

    private String homeTeam;
    private String awayTeam;
    private Integer homeGoals;
    private Integer awayGoals;

    private boolean home;      // whether the selected team was home in this match
    private String opponent;
    private String outcome;    // W/D/L from perspective of selected team

    private String activeStreakType;   // W/D/L or null if none
    private int activeStreakCount;     // count for active streak entering the match

    private String longestToDateType;  // W/D/L for historical longest up to this match (inclusive of active before kick-off)
    private int longestToDateCount;

    // Opponent's longest-to-date context before this match
    private String opponentLongestToDateType;
    private int opponentLongestToDateCount;

    // Readable summary of matchup longest-streak relationship
    private String matchupSummary;

    public StreakTimelineItem() {}

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
    public Integer getHomeGoals() { return homeGoals; }
    public void setHomeGoals(Integer homeGoals) { this.homeGoals = homeGoals; }
    public Integer getAwayGoals() { return awayGoals; }
    public void setAwayGoals(Integer awayGoals) { this.awayGoals = awayGoals; }
    public boolean isHome() { return home; }
    public void setHome(boolean home) { this.home = home; }
    public String getOpponent() { return opponent; }
    public void setOpponent(String opponent) { this.opponent = opponent; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getActiveStreakType() { return activeStreakType; }
    public void setActiveStreakType(String activeStreakType) { this.activeStreakType = activeStreakType; }
    public int getActiveStreakCount() { return activeStreakCount; }
    public void setActiveStreakCount(int activeStreakCount) { this.activeStreakCount = activeStreakCount; }
    public String getLongestToDateType() { return longestToDateType; }
    public void setLongestToDateType(String longestToDateType) { this.longestToDateType = longestToDateType; }
    public int getLongestToDateCount() { return longestToDateCount; }
    public void setLongestToDateCount(int longestToDateCount) { this.longestToDateCount = longestToDateCount; }
    public String getOpponentLongestToDateType() { return opponentLongestToDateType; }
    public void setOpponentLongestToDateType(String opponentLongestToDateType) { this.opponentLongestToDateType = opponentLongestToDateType; }
    public int getOpponentLongestToDateCount() { return opponentLongestToDateCount; }
    public void setOpponentLongestToDateCount(int opponentLongestToDateCount) { this.opponentLongestToDateCount = opponentLongestToDateCount; }
    public String getMatchupSummary() { return matchupSummary; }
    public void setMatchupSummary(String matchupSummary) { this.matchupSummary = matchupSummary; }
}

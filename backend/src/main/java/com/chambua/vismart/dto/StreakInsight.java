package com.chambua.vismart.dto;

/**
 * Historical streak insight for a team.
 * Carries a compact textual summary plus structured percentages for UI usage.
 */
public class StreakInsight {
    private String teamName;
    private String pattern; // e.g., "3W", "2D", "4L"
    private int instances;  // how many times such a streak occurred in the past (pre-match)

    // Percentages for the match that followed such streaks
    private int nextWinPct;
    private int nextDrawPct;
    private int nextLossPct;
    private int over15Pct;
    private int over25Pct;
    private int over35Pct;
    private int bttsPct;

    // Readable sentence e.g. "Team A has had 120 instances of a 2D streak..."
    private String summaryText;

    public StreakInsight() {}

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public int getInstances() { return instances; }
    public void setInstances(int instances) { this.instances = instances; }
    public int getNextWinPct() { return nextWinPct; }
    public void setNextWinPct(int nextWinPct) { this.nextWinPct = nextWinPct; }
    public int getNextDrawPct() { return nextDrawPct; }
    public void setNextDrawPct(int nextDrawPct) { this.nextDrawPct = nextDrawPct; }
    public int getNextLossPct() { return nextLossPct; }
    public void setNextLossPct(int nextLossPct) { this.nextLossPct = nextLossPct; }
    public int getOver15Pct() { return over15Pct; }
    public void setOver15Pct(int over15Pct) { this.over15Pct = over15Pct; }
    public int getOver25Pct() { return over25Pct; }
    public void setOver25Pct(int over25Pct) { this.over25Pct = over25Pct; }
    public int getOver35Pct() { return over35Pct; }
    public void setOver35Pct(int over35Pct) { this.over35Pct = over35Pct; }
    public int getBttsPct() { return bttsPct; }
    public void setBttsPct(int bttsPct) { this.bttsPct = bttsPct; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
}
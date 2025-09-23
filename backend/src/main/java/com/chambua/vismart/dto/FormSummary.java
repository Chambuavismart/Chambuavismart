package com.chambua.vismart.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Last-5 matches form summary for a team.
 */
public class FormSummary {
    private List<String> recentResults; // most recent first, values: "W","D","L"
    private String currentStreak; // e.g., "3W", "2D", "1L"; "0" if none
    private int winRate; // percent across last-5 (rounded)
    private int pointsEarned; // 3*W + 1*D
    // New: cumulative PPG after each of the last N matches (N<=5), most recent first
    private List<Double> ppgSeries;

    public FormSummary() {
        this.recentResults = new ArrayList<>();
        this.ppgSeries = new ArrayList<>();
    }

    public FormSummary(List<String> recentResults, String currentStreak, int winRate, int pointsEarned) {
        this.recentResults = recentResults != null ? recentResults : new ArrayList<>();
        this.currentStreak = currentStreak;
        this.winRate = winRate;
        this.pointsEarned = pointsEarned;
        this.ppgSeries = new ArrayList<>();
    }

    public List<String> getRecentResults() { return recentResults; }
    public void setRecentResults(List<String> recentResults) { this.recentResults = recentResults; }

    public String getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(String currentStreak) { this.currentStreak = currentStreak; }

    public int getWinRate() { return winRate; }
    public void setWinRate(int winRate) { this.winRate = winRate; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public List<Double> getPpgSeries() { return ppgSeries; }
    public void setPpgSeries(List<Double> ppgSeries) { this.ppgSeries = ppgSeries; }
}

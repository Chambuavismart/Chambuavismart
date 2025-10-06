package com.chambua.vismart.dto;

import java.time.LocalDate;

public class Over15StreakProfile {
    private Long teamId;
    private String teamName;

    // Current ongoing streak from the most recent played match backwards
    private int currentStreakLength;
    private LocalDate currentStreakStartDate; // date of the oldest match in the current streak
    private LocalDate currentStreakEndDate;   // date of the most recent match in the current streak

    // Longest streak across entire history
    private int longestStreakLength;
    private LocalDate longestStreakStartDate;
    private LocalDate longestStreakEndDate;

    // If there are multiple longest streaks with the same length, capture the most recent occurrence
    private LocalDate mostRecentLongestStartDate;
    private LocalDate mostRecentLongestEndDate;

    // Diagnostics
    private int totalMatchesConsidered;

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public int getCurrentStreakLength() { return currentStreakLength; }
    public void setCurrentStreakLength(int currentStreakLength) { this.currentStreakLength = currentStreakLength; }

    public LocalDate getCurrentStreakStartDate() { return currentStreakStartDate; }
    public void setCurrentStreakStartDate(LocalDate currentStreakStartDate) { this.currentStreakStartDate = currentStreakStartDate; }

    public LocalDate getCurrentStreakEndDate() { return currentStreakEndDate; }
    public void setCurrentStreakEndDate(LocalDate currentStreakEndDate) { this.currentStreakEndDate = currentStreakEndDate; }

    public int getLongestStreakLength() { return longestStreakLength; }
    public void setLongestStreakLength(int longestStreakLength) { this.longestStreakLength = longestStreakLength; }

    public LocalDate getLongestStreakStartDate() { return longestStreakStartDate; }
    public void setLongestStreakStartDate(LocalDate longestStreakStartDate) { this.longestStreakStartDate = longestStreakStartDate; }

    public LocalDate getLongestStreakEndDate() { return longestStreakEndDate; }
    public void setLongestStreakEndDate(LocalDate longestStreakEndDate) { this.longestStreakEndDate = longestStreakEndDate; }

    public LocalDate getMostRecentLongestStartDate() { return mostRecentLongestStartDate; }
    public void setMostRecentLongestStartDate(LocalDate mostRecentLongestStartDate) { this.mostRecentLongestStartDate = mostRecentLongestStartDate; }

    public LocalDate getMostRecentLongestEndDate() { return mostRecentLongestEndDate; }
    public void setMostRecentLongestEndDate(LocalDate mostRecentLongestEndDate) { this.mostRecentLongestEndDate = mostRecentLongestEndDate; }

    public int getTotalMatchesConsidered() { return totalMatchesConsidered; }
    public void setTotalMatchesConsidered(int totalMatchesConsidered) { this.totalMatchesConsidered = totalMatchesConsidered; }

    // New: last-20 window Over 1.5 stats
    private int last20Over15Count;
    private int last20Over15Pct;
    private int last20Considered;

    public int getLast20Over15Count() { return last20Over15Count; }
    public void setLast20Over15Count(int last20Over15Count) { this.last20Over15Count = last20Over15Count; }

    public int getLast20Over15Pct() { return last20Over15Pct; }
    public void setLast20Over15Pct(int last20Over15Pct) { this.last20Over15Pct = last20Over15Pct; }

    public int getLast20Considered() { return last20Considered; }
    public void setLast20Considered(int last20Considered) { this.last20Considered = last20Considered; }
}

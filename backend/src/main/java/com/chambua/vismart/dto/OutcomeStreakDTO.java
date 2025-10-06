package com.chambua.vismart.dto;

import java.time.LocalDate;

public class OutcomeStreakDTO {
    private String outcome; // W/D/L
    private int length;
    private LocalDate startDate; // inclusive
    private LocalDate endDate;   // inclusive

    public OutcomeStreakDTO() {}

    public OutcomeStreakDTO(String outcome, int length, LocalDate startDate, LocalDate endDate) {
        this.outcome = outcome;
        this.length = length;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}

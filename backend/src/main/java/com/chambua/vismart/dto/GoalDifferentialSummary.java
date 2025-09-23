package com.chambua.vismart.dto;

import java.util.ArrayList;
import java.util.List;

public class GoalDifferentialSummary {
    private Integer aggregateGD; // sum of (scored - conceded)
    private Double avgGD; // average per match
    private List<Integer> perMatchGD; // ordered list (most recent first if that is how H2H is provided)
    private boolean insufficientData; // true when fewer than 3 H2H matches

    public GoalDifferentialSummary() {
        this.perMatchGD = new ArrayList<>();
    }

    public GoalDifferentialSummary(Integer aggregateGD, Double avgGD, List<Integer> perMatchGD, boolean insufficientData) {
        this.aggregateGD = aggregateGD;
        this.avgGD = avgGD;
        this.perMatchGD = perMatchGD != null ? perMatchGD : new ArrayList<>();
        this.insufficientData = insufficientData;
    }

    public Integer getAggregateGD() { return aggregateGD; }
    public void setAggregateGD(Integer aggregateGD) { this.aggregateGD = aggregateGD; }

    public Double getAvgGD() { return avgGD; }
    public void setAvgGD(Double avgGD) { this.avgGD = avgGD; }

    public List<Integer> getPerMatchGD() { return perMatchGD; }
    public void setPerMatchGD(List<Integer> perMatchGD) { this.perMatchGD = perMatchGD; }

    public boolean isInsufficientData() { return insufficientData; }
    public void setInsufficientData(boolean insufficientData) { this.insufficientData = insufficientData; }
}
